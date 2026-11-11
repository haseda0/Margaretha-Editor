package id.haseda.margaretha.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Original line icon set for Margaretha (24-unit grid, 1.8 stroke, round caps).
 * Drawn in-house so the app has no dependency on the deprecated material-icons artifacts.
 */
object MIcons {
    private fun icon(name: String, strokes: List<String> = emptyList(), fills: List<String> = emptyList()): ImageVector {
        val b = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        fills.forEach { d -> b.addPath(PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.White)) }
        strokes.forEach { d ->
            b.addPath(
                PathParser().parsePathString(d).toNodes(),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    val Play = icon("play", fills = listOf("M8 5.2v13.6a0.8 0.8 0 0 0 1.2 0.7l10.6-6.8a0.8 0.8 0 0 0 0-1.4L9.2 4.5A0.8 0.8 0 0 0 8 5.2z"))
    val Pause = icon("pause", fills = listOf("M7 5h3.2v14H7zM13.8 5H17v14h-3.2z"))
    val StepBack = icon("step_back", strokes = listOf("M17 6.5 11 12l6 5.5", "M7.5 6v12"))
    val StepForward = icon("step_fwd", strokes = listOf("M7 6.5 13 12l-6 5.5", "M16.5 6v12"))
    val Undo = icon("undo", strokes = listOf("M9 14 4 9l5-5", "M4 9h10.5a5.5 5.5 0 0 1 0 11H11"))
    val Redo = icon("redo", strokes = listOf("M15 14l5-5-5-5", "M20 9H9.5a5.5 5.5 0 0 0 0 11H13"))
    val Split = icon(
        "split",
        strokes = listOf(
            "M3.5 6.5a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0",
            "M3.5 17.5a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0",
            "M20 4 8.1 15.9",
            "M14.5 14.5 20 20",
            "M8.1 8.1 12 12",
        ),
    )
    val Delete = icon("delete", strokes = listOf("M4 6.5h16", "M9 6.5V4.5h6v2", "M18.5 6.5l-0.9 13a1.5 1.5 0 0 1-1.5 1.4H7.9a1.5 1.5 0 0 1-1.5-1.4l-0.9-13", "M10 11v5.5", "M14 11v5.5"))
    val Duplicate = icon("duplicate", strokes = listOf("M9.5 8.5h9a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1h-9a1 1 0 0 1-1-1v-9a1 1 0 0 1 1-1z", "M5.5 15.5h-0.5a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1h9.5a1 1 0 0 1 1 1v0.5"))
    val Add = icon("add", strokes = listOf("M12 5v14", "M5 12h14"))
    val Export = icon("export", strokes = listOf("M12 15V3.5", "M7.5 8 12 3.5 16.5 8", "M5 14v4.5A1.5 1.5 0 0 0 6.5 20h11a1.5 1.5 0 0 0 1.5-1.5V14"))
    val Back = icon("back", strokes = listOf("M15 18.5 8.5 12 15 5.5"))
    val Volume = icon("volume", strokes = listOf("M11 5.5 6.5 9H4v6h2.5l4.5 3.5z", "M15.5 9a4.2 4.2 0 0 1 0 6", "M18.3 6.3a8.2 8.2 0 0 1 0 11.4"))
    val Mute = icon("mute", strokes = listOf("M11 5.5 6.5 9H4v6h2.5l4.5 3.5z", "M21 9.5l-5 5", "M16 9.5l5 5"))
    val MoveLeft = icon("move_left", strokes = listOf("M11.5 17 6.5 12l5-5", "M18 17l-5-5 5-5"))
    val MoveRight = icon("move_right", strokes = listOf("M12.5 17l5-5-5-5", "M6 17l5-5-5-5"))
    val TrimStart = icon("trim_start", strokes = listOf("M7 4v16", "M11 12h9", "M14 9l-3 3 3 3"))
    val TrimEnd = icon("trim_end", strokes = listOf("M17 4v16", "M4 12h9", "M10 9l3 3-3 3"))
    val More = icon("more", fills = listOf("M10.5 5.5a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0", "M10.5 12a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0", "M10.5 18.5a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0"))
    val Check = icon("check", strokes = listOf("M5 12.5 9.5 17 19 7"))
    val Close = icon("close", strokes = listOf("M6 6l12 12", "M18 6 6 18"))
    val Share = icon("share", strokes = listOf("M4.5 12v6.5A1.5 1.5 0 0 0 6 20h12a1.5 1.5 0 0 0 1.5-1.5V12", "M16 6.5 12 2.5 8 6.5", "M12 2.5V15"))
    val Edit = icon("edit", strokes = listOf("M4 20h4.2L19 9.2a2 2 0 0 0 0-2.8l-1.4-1.4a2 2 0 0 0-2.8 0L4 15.8z", "M13.5 6.5l4 4"))
    val Film = icon("film", strokes = listOf("M5 4h14a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z", "M8 4v16", "M16 4v16", "M4 9h4", "M16 9h4", "M4 15h4", "M16 15h4"))
    val Link = icon("link", strokes = listOf("M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1", "M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1"))
    val Sparkle = icon("sparkle", fills = listOf("M12 2.8l1.9 5.3 5.3 1.9-5.3 1.9L12 17.2l-1.9-5.3L4.8 10l5.3-1.9z", "M18.5 15.5l0.8 2.2 2.2 0.8-2.2 0.8-0.8 2.2-0.8-2.2-2.2-0.8 2.2-0.8z"))
    val Warning = icon("warning", strokes = listOf("M12 4 2.8 19.5h18.4z", "M12 10v4.5", "M12 17.2v0.1"))
    val Text = icon("text", strokes = listOf("M5 6.5V4.5h14v2", "M12 4.5v15", "M9 19.5h6"))
    val Music = icon("music", strokes = listOf("M9 17.5V5.5l10-2v12", "M3.5 17.5a2.75 2.75 0 1 0 5.5 0a2.75 2.75 0 1 0-5.5 0", "M13.5 15.5a2.75 2.75 0 1 0 5.5 0a2.75 2.75 0 1 0-5.5 0"))
    val Subtitle = icon("subtitle", strokes = listOf("M4 5.5h16a1 1 0 0 1 1 1v11a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-11a1 1 0 0 1 1-1z", "M6.5 14.5h4", "M13 14.5h4.5", "M6.5 11h7"))
    val Effects = icon("effects", strokes = listOf("M4.5 19.5 14.5 9.5", "M14 5v-2", "M19 10h2", "M17.5 6.5 19 5", "M17.5 13.5 19 15", "M10.5 6.5 9 5"))
    val Palette = icon(
        "palette",
        strokes = listOf("M12 3.5a8.5 8.5 0 1 0 0 17c1.2 0 1.8-0.9 1.5-1.9-0.4-1.3 0.4-2.6 1.9-2.6H17a3.5 3.5 0 0 0 3.5-3.5c0-5-3.8-9-8.5-9z"),
        fills = listOf("M7 11.5a1.2 1.2 0 1 0 2.4 0a1.2 1.2 0 1 0-2.4 0", "M10.3 7.6a1.2 1.2 0 1 0 2.4 0a1.2 1.2 0 1 0-2.4 0", "M14.6 8.6a1.2 1.2 0 1 0 2.4 0a1.2 1.2 0 1 0-2.4 0"),
    )
    val Speed = icon("speed", strokes = listOf("M4.5 17a8.5 8.5 0 1 1 15 0", "M12 14.5l4.5-5.5"))
    val Transform = icon("transform", strokes = listOf("M6.5 3v12.5a2 2 0 0 0 2 2H21", "M3 6.5h12.5a2 2 0 0 1 2 2V21"))
    val Library = icon("library", strokes = listOf("M4 4h6.5v6.5H4z", "M13.5 4H20v6.5h-6.5z", "M4 13.5h6.5V20H4z", "M13.5 13.5H20V20h-6.5z"))
    val Lock = icon("lock", strokes = listOf("M7.5 11V8a4.5 4.5 0 0 1 9 0v3", "M6 11h12a1 1 0 0 1 1 1v7a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1v-7a1 1 0 0 1 1-1z"))
    val Eye = icon("eye", strokes = listOf("M2.5 12s3.5-6.5 9.5-6.5S21.5 12 21.5 12s-3.5 6.5-9.5 6.5S2.5 12 2.5 12z", "M9.5 12a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0"))
    val EyeOff = icon("eye_off", strokes = listOf("M2.5 12s3.5-6.5 9.5-6.5S21.5 12 21.5 12s-3.5 6.5-9.5 6.5S2.5 12 2.5 12z", "M4 4l16 16"))
    val Fullscreen = icon("fullscreen", strokes = listOf("M4 9V4h5", "M20 9V4h-5", "M4 15v5h5", "M20 15v5h-5"))
    val Stop = icon("stop", fills = listOf("M6.5 6.5h11v11h-11z"))
    val Settings = icon("settings", strokes = listOf("M4 7h9.5", "M18 7h2", "M4 17h3.5", "M12 17h8", "M13.5 7a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0-4.4 0", "M7.5 17a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0-4.4 0"))
    val Compare = icon("compare", strokes = listOf("M12 3v18", "M9 6H5a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h4", "M15 6h4a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1h-4"))
    val Layers = icon("layers", strokes = listOf("M12 4 3.5 8.5 12 13l8.5-4.5z", "M3.5 12.5 12 17l8.5-4.5"))
    val Transition = icon("transition", strokes = listOf("M4 6.5v11l7-5.5z", "M20 6.5v11l-7-5.5z"))
    val Image = icon("image", strokes = listOf("M5 4.5h14a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1v-13a1 1 0 0 1 1-1z", "M4.5 16l4.5-4.5 4 4 2.5-2.5 4 4", "M14 9a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0"))
    val Reset = icon("reset", strokes = listOf("M4.5 12a7.5 7.5 0 1 0 2.4-5.5", "M4.5 4.5v4.5H9"))
    val Canvas = icon("canvas", strokes = listOf("M7 3.5h10a1 1 0 0 1 1 1v15a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1v-15a1 1 0 0 1 1-1z", "M2.5 8v8", "M21.5 8v8"))
}
