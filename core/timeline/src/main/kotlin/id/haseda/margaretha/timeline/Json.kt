package id.haseda.margaretha.timeline

/**
 * Minimal, dependency-free JSON reader/writer used for the project file format.
 * Kept in-house so the core module has zero third-party dependencies and builds anywhere.
 */
sealed interface Json {
    data class Obj(val fields: Map<String, Json>) : Json
    data class Arr(val items: List<Json>) : Json
    data class Str(val value: String) : Json
    data class Num(val raw: String) : Json
    data class Bool(val value: Boolean) : Json
    data object Null : Json

    companion object {
        fun parse(text: String): Json = Parser(text).parseDocument()

        fun write(value: Json): String = StringBuilder().also { writeTo(it, value) }.toString()

        private fun writeTo(sb: StringBuilder, value: Json) {
            when (value) {
                is Obj -> {
                    sb.append('{')
                    var first = true
                    for ((k, v) in value.fields) {
                        if (!first) sb.append(',')
                        first = false
                        writeString(sb, k)
                        sb.append(':')
                        writeTo(sb, v)
                    }
                    sb.append('}')
                }
                is Arr -> {
                    sb.append('[')
                    value.items.forEachIndexed { i, item ->
                        if (i > 0) sb.append(',')
                        writeTo(sb, item)
                    }
                    sb.append(']')
                }
                is Str -> writeString(sb, value.value)
                is Num -> sb.append(value.raw)
                is Bool -> sb.append(if (value.value) "true" else "false")
                Null -> sb.append("null")
            }
        }

        private fun writeString(sb: StringBuilder, s: String) {
            sb.append('"')
            for (c in s) {
                when (c) {
                    '"' -> sb.append("\\\"")
                    '\\' -> sb.append("\\\\")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    '\b' -> sb.append("\\b")
                    '\u000C' -> sb.append("\\f")
                    else -> if (c < ' ') sb.append("\\u").append(c.code.toString(16).padStart(4, '0')) else sb.append(c)
                }
            }
            sb.append('"')
        }
    }
}

class JsonException(message: String) : Exception(message)

private class Parser(private val s: String) {
    private var i = 0
    private var depth = 0

    fun parseDocument(): Json {
        skipWs()
        val v = parseValue()
        skipWs()
        if (i != s.length) throw JsonException("Data berlebih di posisi $i")
        return v
    }

    private fun parseValue(): Json {
        skipWs()
        if (i >= s.length) throw JsonException("JSON terpotong")
        return when (val c = s[i]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> Json.Str(parseString())
            't' -> literal("true", Json.Bool(true))
            'f' -> literal("false", Json.Bool(false))
            'n' -> literal("null", Json.Null)
            else -> if (c == '-' || c.isDigit()) parseNumber() else throw JsonException("Karakter tak terduga '$c' di $i")
        }
    }

    private fun enter() {
        if (++depth > 64) throw JsonException("Struktur JSON terlalu dalam")
    }

    private fun parseObject(): Json {
        enter()
        i++ // {
        val map = LinkedHashMap<String, Json>()
        skipWs()
        if (peek() == '}') { i++; depth--; return Json.Obj(map) }
        while (true) {
            skipWs()
            if (peek() != '"') throw JsonException("Kunci objek harus string di $i")
            val key = parseString()
            skipWs()
            expect(':')
            map[key] = parseValue()
            skipWs()
            when (peek()) {
                ',' -> i++
                '}' -> { i++; depth--; return Json.Obj(map) }
                else -> throw JsonException("Diharapkan ',' atau '}' di $i")
            }
        }
    }

    private fun parseArray(): Json {
        enter()
        i++ // [
        val list = ArrayList<Json>()
        skipWs()
        if (peek() == ']') { i++; depth--; return Json.Arr(list) }
        while (true) {
            list += parseValue()
            skipWs()
            when (peek()) {
                ',' -> i++
                ']' -> { i++; depth--; return Json.Arr(list) }
                else -> throw JsonException("Diharapkan ',' atau ']' di $i")
            }
        }
    }

    private fun parseString(): String {
        expect('"')
        val sb = StringBuilder()
        while (true) {
            if (i >= s.length) throw JsonException("String tidak ditutup")
            val c = s[i++]
            when {
                c == '"' -> return sb.toString()
                c == '\\' -> {
                    if (i >= s.length) throw JsonException("Escape terpotong")
                    when (val e = s[i++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (i + 4 > s.length) throw JsonException("Escape unicode terpotong")
                            val hex = s.substring(i, i + 4)
                            sb.append(hex.toIntOrNull(16)?.toChar() ?: throw JsonException("Escape unicode salah"))
                            i += 4
                        }
                        else -> throw JsonException("Escape tidak dikenal '\\$e'")
                    }
                }
                c < ' ' -> throw JsonException("Karakter kontrol dalam string")
                else -> sb.append(c)
            }
        }
    }

    private fun parseNumber(): Json {
        val start = i
        if (peek() == '-') i++
        while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
        val raw = s.substring(start, i)
        raw.toDoubleOrNull() ?: throw JsonException("Angka tidak valid '$raw'")
        return Json.Num(raw)
    }

    private fun literal(word: String, value: Json): Json {
        if (!s.startsWith(word, i)) throw JsonException("Literal tidak valid di $i")
        i += word.length
        return value
    }

    private fun expect(c: Char) {
        if (peek() != c) throw JsonException("Diharapkan '$c' di $i")
        i++
    }

    private fun peek(): Char? = if (i < s.length) s[i] else null

    private fun skipWs() {
        while (i < s.length && s[i].isWhitespace()) i++
    }
}

// ---- Typed accessors -------------------------------------------------------------------------

internal fun Json.obj(): Json.Obj = this as? Json.Obj ?: throw JsonException("Diharapkan objek")
internal fun Json.Obj.req(key: String): Json = fields[key] ?: throw JsonException("Field '$key' hilang")
internal fun Json.Obj.str(key: String): String = (req(key) as? Json.Str)?.value ?: throw JsonException("Field '$key' harus teks")
internal fun Json.Obj.long(key: String): Long =
    (req(key) as? Json.Num)?.raw?.toLongOrNull() ?: throw JsonException("Field '$key' harus bilangan bulat")
internal fun Json.Obj.int(key: String): Int = long(key).let {
    if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else throw JsonException("Field '$key' di luar jangkauan")
}
internal fun Json.Obj.float(key: String, default: Float): Float =
    when (val v = fields[key]) {
        null -> default
        is Json.Num -> v.raw.toFloatOrNull() ?: throw JsonException("Field '$key' harus angka")
        else -> throw JsonException("Field '$key' harus angka")
    }
internal fun Json.Obj.bool(key: String, default: Boolean): Boolean =
    when (val v = fields[key]) {
        null -> default
        is Json.Bool -> v.value
        else -> throw JsonException("Field '$key' harus boolean")
    }
internal fun Json.Obj.arr(key: String): List<Json> = (req(key) as? Json.Arr)?.items ?: throw JsonException("Field '$key' harus array")

internal fun jsonOf(vararg pairs: Pair<String, Json>): Json.Obj = Json.Obj(linkedMapOf(*pairs))
internal fun String.j(): Json = Json.Str(this)
internal fun Long.j(): Json = Json.Num(toString())
internal fun Int.j(): Json = Json.Num(toString())
internal fun Float.j(): Json = Json.Num(toString())
internal fun Boolean.j(): Json = Json.Bool(this)
