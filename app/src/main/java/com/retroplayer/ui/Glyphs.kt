package com.retroplayer.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Pixel-style vector glyphs (never emoji), drawn white so [Glyph] can tint them. */
object Glyphs {
    val Note = glyph(16f, 16f) { fill("M5 2h9v9.5a2.5 2.5 0 1 1-2-2.45V5H7v7.5A2.5 2.5 0 1 1 5 10.05z") }
    val Dots = glyph(14f, 4f) { fill(rect(0f, 0f, 3f, 3f) + rect(5f, 0f, 3f, 3f) + rect(10f, 0f, 3f, 3f)) }
    val Minimize = glyph(10f, 10f) { fill(rect(1f, 7f, 8f, 2f)) }
    val Maximize = glyph(10f, 10f) { stroke(rect(1f, 1f, 8f, 8f), 2f) }
    val Restore = glyph(12f, 12f) { stroke(rect(0.75f, 3.75f, 7.5f, 7.5f) + "M3.75 3.75V0.75h7.5v7.5h-3", 1.5f) }

    val ListenAgain = glyph(16f, 16f) { stroke(circle(8f, 8f, 6.5f) + "M8 4v4l3 2", 2f) }
    val Library = glyph(16f, 16f) {
        fill(rect(1f, 2f, 3f, 12f) + rect(6f, 2f, 3f, 12f))
        addGroup(rotate = -12f, pivotX = 12f, pivotY = 9f)
        fill(rect(10.5f, 3f, 3f, 11.5f))
        clearGroup()
    }
    val Playing = glyph(16f, 16f) { fill(rect(1f, 7f, 3f, 8f) + rect(6f, 2f, 3f, 13f) + rect(11f, 5f, 3f, 10f)) }
    val Queue = glyph(16f, 16f) { fill(rect(1f, 2f, 14f, 2f) + rect(1f, 7f, 14f, 2f) + rect(1f, 12f, 9f, 2f)) }

    val PlaySmall = glyph(8f, 10f) { fill("M0 0l8 5-8 5z") }
    val PauseSmall = glyph(8f, 10f) { fill(rect(0f, 0f, 3f, 10f) + rect(5f, 0f, 3f, 10f)) }
    val Play = glyph(18f, 20f) { fill("M0 0l18 10L0 20z") }
    val Pause = glyph(18f, 20f) { fill(rect(0f, 0f, 6f, 20f) + rect(11f, 0f, 6f, 20f)) }
    val Previous = glyph(22f, 16f) { fill("M10 0v16L0 8zM21 0v16L11 8z") }
    val Next = glyph(22f, 16f) { fill("M0 0v16l10-8zM11 0v16l10-8z") }
    val Shuffle = glyph(22f, 18f) {
        stroke("M1 4h4l10 10h4M1 14h4l3-3M12 7l3-3h4M17 1l3 3-3 3M17 11l3 3-3 3", 2f, StrokeCap.Square)
    }
    val Repeat = glyph(22f, 18f) { stroke("M3 9V5h15M15 2l3 3-3 3M19 9v4H4M7 16l-3-3 3-3", 2f, StrokeCap.Square) }
    /** Green play triangle on buttons, and the marker on the current queue row. */
    val PlayMarker = glyph(12f, 14f) { fill("M0 0l12 7-12 7z") }

    val Search = glyph(16f, 16f) { stroke(circle(6.5f, 6.5f, 4.5f) + "M10 10l5 5", 2f) }
    val Edit = glyph(14f, 14f) { stroke("M2 12l1-4 7-7 3 3-7 7z", 2f) }
    val Grip = glyph(10f, 16f) {
        fill(
            rect(0f, 1f, 3f, 3f) + rect(6f, 1f, 3f, 3f) + rect(0f, 7f, 3f, 3f) +
                rect(6f, 7f, 3f, 3f) + rect(0f, 13f, 3f, 3f) + rect(6f, 13f, 3f, 3f),
        )
    }
    val Close = glyph(12f, 12f) { stroke("M1 1l10 10M11 1L1 11", 2.5f) }
    /** Two-color: draw it with `color = null`. */
    val Info = glyph(16f, 16f) {
        fill(rect(0f, 0f, 16f, 16f), Retro.TitleBar)
        fill(rect(7f, 3f, 2f, 2f) + rect(7f, 7f, 2f, 6f))
    }
    val SortUp = glyph(8f, 6f) { fill("M4 0l4 6H0z") }

    // Queue menu
    val PlayNow = glyph(16f, 16f) { fill("M3 1l11 7-11 7z") }
    val PlayNext = glyph(16f, 16f) { fill("M1 2l7 5-7 5z" + rect(10f, 2f, 5f, 2f) + rect(10f, 7f, 5f, 2f) + rect(1f, 13f, 14f, 2f)) }
    val MoveToEnd = glyph(16f, 16f) { stroke("M8 2v11M3 9l5 5 5-5", 2f) }
    val Artist = glyph(16f, 16f) { stroke(circle(8f, 5f, 3f) + "M2 15c0-3.5 2.7-5 6-5s6 1.5 6 5", 2f) }
    val Share = glyph(16f, 16f) { stroke("M8 11V2M4 5l4-4 4 4M2 9v6h12V9", 2f) }
    val Remove = glyph(16f, 16f) { stroke("M3 3l10 10M13 3L3 13", 2.5f) }
}

private fun glyph(width: Float, height: Float, paths: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        defaultWidth = width.dp,
        defaultHeight = height.dp,
        viewportWidth = width,
        viewportHeight = height,
    ).apply(paths).build()

private fun ImageVector.Builder.fill(pathData: String, color: Color = Color.White) {
    addPath(addPathNodes(pathData), fill = SolidColor(color))
}

private fun ImageVector.Builder.stroke(pathData: String, width: Float, cap: StrokeCap = StrokeCap.Butt) {
    addPath(addPathNodes(pathData), stroke = SolidColor(Color.White), strokeLineWidth = width, strokeLineCap = cap)
}

private fun rect(x: Float, y: Float, w: Float, h: Float) = "M$x ${y}h${w}v${h}h${-w}z"

private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"
