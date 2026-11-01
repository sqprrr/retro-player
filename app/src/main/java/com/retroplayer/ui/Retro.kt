package com.retroplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The whole palette: window gray, beige input fields and a green LCD.
 * Dark mode swaps the gray for charcoal; the LCD stays the same.
 */
object Retro {
    /** Set by the activity before composing. Android recreates the activity when the system theme changes. */
    var dark = false

    val Face get() = if (dark) Color(0xFF3C3C3C) else Color(0xFFC0C0C0)
    val Highlight get() = if (dark) Color(0xFF707070) else Color(0xFFFFFFFF)
    val Light get() = if (dark) Color(0xFF545454) else Color(0xFFDFDFDF)
    val Shadow get() = if (dark) Color(0xFF262626) else Color(0xFF808080)
    val Dark get() = if (dark) Color(0xFF0A0A0A) else Color(0xFF202020)
    val Ink get() = if (dark) Color(0xFFE6E6E6) else Color(0xFF000000)
    val Disabled get() = if (dark) Color(0xFF7A7A7A) else Color(0xFF808080)
    val Beige get() = if (dark) Color(0xFF2B2920) else Color(0xFFEFE8D2)
    val Lcd get() = if (dark) Color(0xFF081A0B) else Color(0xFF0F2A12)
    val LcdText = Color(0xFF9CE57A)
    val LcdDim = Color(0xFF62A052)
}

// Force text presentation so the symbols never turn into color emoji.
const val PLAY = "▶︎"
const val PAUSE = "❚❚"
const val PREV = "◀︎◀︎"
const val NEXT = "▶︎▶︎"
const val SHUFFLE = "⤨"

@Composable
fun RetroText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Retro.Ink,
    size: TextUnit = 14.sp,
    bold: Boolean = false,
    maxLines: Int = 1,
    align: TextAlign = TextAlign.Start,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = align,
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Classic two-line 3D border: raised (light top-left) or sunken (dark top-left). */
fun Modifier.bevel(sunken: Boolean = false): Modifier = drawBehind {
    val w = 1.5.dp.toPx().roundToInt().toFloat()
    if (sunken) {
        edge(Retro.Shadow, Retro.Highlight, 0f, w)
        edge(Retro.Dark, Retro.Light, w, w)
    } else {
        edge(Retro.Highlight, Retro.Dark, 0f, w)
        edge(Retro.Light, Retro.Shadow, w, w)
    }
}

private fun DrawScope.edge(topLeft: Color, bottomRight: Color, inset: Float, w: Float) {
    val width = size.width - 2 * inset
    val height = size.height - 2 * inset
    drawRect(topLeft, Offset(inset, inset), Size(width, w))
    drawRect(topLeft, Offset(inset, inset), Size(w, height))
    drawRect(bottomRight, Offset(inset, size.height - inset - w), Size(width, w))
    drawRect(bottomRight, Offset(size.width - inset - w, inset), Size(w, height))
}

/** Square gray button that looks pushed in while pressed, or while [latched] (for toggles and tabs). */
@Composable
fun RetroButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    latched: Boolean = false,
    size: TextUnit = 15.sp,
    /** What a screen reader says instead of the label, for symbol-only buttons. */
    description: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val sunken = latched || (pressed && enabled)
    Box(
        modifier
            .heightIn(min = 44.dp)
            .background(if (latched) Retro.Light else Retro.Face)
            .bevel(sunken)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        val textModifier = if (sunken) Modifier.offset(1.dp, 1.dp) else Modifier
        RetroText(
            label,
            modifier = if (description != null) textModifier.clearAndSetSemantics {} else textModifier,
            color = if (enabled) Retro.Ink else Retro.Disabled,
            size = size,
            bold = true,
            align = TextAlign.Center,
        )
    }
}

/** Sunken dark-green screen. */
@Composable
fun LcdPanel(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.background(Retro.Lcd).bevel(sunken = true).padding(4.dp), content = content)
}

/** A sunken groove with a square slider thumb. Tap or drag to seek. */
@Composable
fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    onScrub: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val scrub by rememberUpdatedState(onScrub)
    val seek by rememberUpdatedState(onSeek)
    var scrubMs by remember { mutableLongStateOf(0L) }
    fun toMs(x: Float, width: Int) = ((x / width).coerceIn(0f, 1f) * durationMs).toLong()
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .pointerInput(durationMs) {
                detectTapGestures { if (durationMs > 0) seek(toMs(it.x, size.width)) }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { scrubMs = toMs(it.x, size.width); scrub(scrubMs) },
                    onHorizontalDrag = { change, _ -> scrubMs = toMs(change.position.x, size.width); scrub(scrubMs) },
                    onDragEnd = { if (durationMs > 0) seek(scrubMs) },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val thumbWidth = 20.dp
        LcdPanel(Modifier.fillMaxWidth().height(12.dp)) {}
        Box(
            Modifier
                .padding(horizontal = 3.dp)
                .fillMaxWidth(fraction)
                .height(6.dp)
                .background(Retro.LcdText),
        )
        Box(
            Modifier
                .offset(x = (maxWidth - thumbWidth) * fraction)
                .width(thumbWidth)
                .fillMaxHeight()
                .background(Retro.Face)
                .bevel(),
        )
    }
}

/** Old-school scrollbar for long lists: drag the thumb or touch the track to jump there. */
@Composable
fun Scrollbar(state: LazyListState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val metrics by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            Triple(state.firstVisibleItemIndex, info.visibleItemsInfo.size, info.totalItemsCount)
        }
    }
    val (first, visible, total) = metrics
    if (total == 0 || visible >= total) return
    // Read inside the gesture, so a changing visible count doesn't restart a drag.
    val current by rememberUpdatedState(metrics)
    val minThumb = 40.dp
    BoxWithConstraints(
        modifier
            .width(22.dp)
            .fillMaxHeight()
            .background(Retro.Light)
            .bevel(sunken = true)
            .pointerInput(state) {
                fun jumpTo(y: Float) {
                    val (_, shown, count) = current
                    val thumb = maxOf(size.height * shown / count.toFloat(), minThumb.toPx())
                    val fraction = ((y - thumb / 2) / (size.height - thumb)).coerceIn(0f, 1f)
                    scope.launch { state.scrollToItem((fraction * (count - shown)).roundToInt()) }
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    jumpTo(down.position.y)
                    verticalDrag(down.id) { change ->
                        change.consume()
                        jumpTo(change.position.y)
                    }
                }
            },
    ) {
        val thumbHeight = maxOf(maxHeight * visible / total, minThumb)
        val fraction = (first.toFloat() / (total - visible)).coerceIn(0f, 1f)
        Box(
            Modifier
                .offset(y = (maxHeight - thumbHeight) * fraction)
                .fillMaxWidth()
                .height(thumbHeight)
                .background(Retro.Face)
                .bevel(),
        )
    }
}

fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0)) / 1000
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}
