# Dependensi (diverifikasi 2026-11-09)

Semua versi di `gradle/libs.versions.toml`. Semua modul Media3 memakai versi yang sama.

| Komponen | Versi | Sumber verifikasi |
|---|---|---|
| Android Gradle Plugin | 9.3.2 | Kombinasi AGP/Gradle/Kotlin yang dipakai android/nowinandroid (main) |
| Gradle | 9.4.0 | idem |
| Kotlin + Compose compiler plugin | 2.3.0 | idem; AGP 9 built-in Kotlin (tanpa `kotlin-android`) |
| AndroidX Media3 (exoplayer, ui, transformer, effect, common) | 1.11.1 | androidx/media RELEASENOTES: 1.11.1 rilis 2026-09-10 |
| Compose BOM | 2026.04.01 | Android Developers Blog (April 2026) |
| core-ktx 1.15.0, activity-compose 1.9.3, lifecycle 2.10.0, coroutines 1.10.1 | — | nowinandroid |
| JUnit | 4.13.2 | — |

Catatan API Media3 yang diverifikasi dari source `release`:
- `EditedMediaItemSequence` constructor dihapus di 1.9 → dipakai `EditedMediaItemSequence.withAudioAndVideoFrom(...)`.
- `Composition.Builder(sequence, ...)`, `setHdrMode`, `Transformer.Builder` setter, `Listener.onCompleted/onError`, `getProgress`, `cancel`.
- `ChannelMixingMatrix.createForConstantGain` (1–2 kanal) / `createForConstantPower` (≤6→2) + `scaleBy`.

`:core:timeline` tidak punya dependensi runtime (JSON ditulis sendiri).
Tidak ada FFmpeg, tidak ada library pihak ketiga non-AndroidX.
