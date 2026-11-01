package com.retroplayer.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.retroplayer.R
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Design tokens: Windows 95 window chrome in charcoal on a dark desktop,
 * with a green LCD for everything the player shows.
 */
object Retro {
    val Desktop = Color(0xFF333333)
    val Face = Color(0xFF4A4A4A)
    val FacePressed = Color(0xFF404040)
    val BevelHighlight = Color(0xFF8A8A8A)
    val BevelLight = Color(0xFF5E5E5E)
    val BevelShadow = Color(0xFF262626)
    val BevelDark = Color(0xFF0B0B0B)
    /** Outer top/left edge of window frames, outer bottom/right edge of sunken panels, and etched lines. */
    val BevelEtch = Color(0xFF7C7C7C)
    /** Bottom/right edge of the thin sunken status-bar fields. */
    val BevelField = Color(0xFF6A6A6A)
    val TextOnFace = Color(0xFFECECEC)
    val TextDisabled = Color(0xFF8A8A8A)
    /** The one title-bar color. Alternates that also work: 0xFF000080 (navy) and 0xFF5C5C5C (gray). */
    val TitleBar = Color(0xFF1F5A2B)
    val TitleText = Color.White
    val LcdBg = Color(0xFF071408)
    val LcdText = Color(0xFF9BE57C)
    val LcdTextDim = Color(0xFF78B863)
    val LcdTextFaint = Color(0xFF5F9A4F)
    val LcdRowDivider = Color(0xFF1F3D1F)
    /** Slightly lighter LCD background behind the library's letter headers. */
    val LcdGroupBg = Color(0xFF0C200E)
    val CurrentRowBg = Color(0xFF9BE57C)
    val CurrentRowText = Color(0xFF071408)
    val CurrentRowSub = Color(0xFF1D4A1F)
    val SelectedRowBg = TitleBar
    val SelectedRowText = Color.White
    val SelectedRowSub = Color(0xFFD4ECD0)
    val LedOff = Color(0xFF1B2B1A)
    val LedOn = Color(0xFF9BE57C)
    val ScrollTrack = Color(0xFF3A3A3A)
    val ScrollTrackAlt = Color(0xFF262626)
    val MenuShadow = Color(0x73000000)
}

object RetroFonts {
    /** VT323: big LCD digits and LCD titles. */
    val Lcd = FontFamily(Font(R.font.vt323_regular))
    /** IBM Plex Mono: track titles, artists, list text and inputs. */
    val Mono = FontFamily(
        Font(R.font.ibm_plex_mono_regular),
        Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
    )
    /** Pixelify Sans: window chrome (title bars, buttons, menus, tabs, status bar). */
    val Chrome = FontFamily(
        Font(R.font.pixelify_sans_regular),
        Font(R.font.pixelify_sans_semibold, FontWeight.SemiBold),
    )
}

/** Color for text and glyphs that don't set one, so buttons and LCD panels can recolor their content. */
val LocalContentColor = compositionLocalOf { Retro.TextOnFace }

fun retroStyle(
    color: Color,
    size: TextUnit,
    font: FontFamily = RetroFonts.Mono,
    bold: Boolean = false,
    align: TextAlign = TextAlign.Start,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
) = TextStyle(
    color = color,
    fontSize = size,
    fontFamily = font,
    fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
    textAlign = align,
    letterSpacing = letterSpacing,
    lineHeight = lineHeight,
    // Like CSS line-height: spare or missing space is split evenly above and below the glyphs.
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

@Composable
fun RetroText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    size: TextUnit = 14.sp,
    font: FontFamily = RetroFonts.Mono,
    bold: Boolean = false,
    maxLines: Int = 1,
    align: TextAlign = TextAlign.Start,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = retroStyle(color, size, font, bold, align, letterSpacing, lineHeight),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** A label whose letter after '&' is underlined, as in Windows menus: "&Share…" shows "Share…" with S underlined. */
fun accel(label: String): AnnotatedString = buildAnnotatedString {
    val amp = label.indexOf('&')
    if (amp < 0 || amp == label.lastIndex) {
        append(label)
    } else {
        append(label.substring(0, amp))
        withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(label[amp + 1]) }
        append(label.substring(amp + 2))
    }
}

/** A vector glyph at its own size, tinted [color] (null keeps the glyph's own colors). Never read by screen readers. */
@Composable
fun Glyph(icon: ImageVector, modifier: Modifier = Modifier, color: Color? = LocalContentColor.current) {
    Image(
        painter = rememberVectorPainter(icon),
        contentDescription = null,
        modifier = modifier.size(icon.defaultWidth, icon.defaultHeight),
        colorFilter = color?.let { ColorFilter.tint(it) },
    )
}

enum class Bevel { Raised, Window, Pressed, Sunken, Field }

/** Win95 border: two 1dp lines per edge (one for [Bevel.Field]), no blur, no rounded corners. */
fun Modifier.bevel(style: Bevel = Bevel.Raised): Modifier = drawBehind {
    val w = 1.dp.toPx().roundToInt().coerceAtLeast(1).toFloat()
    when (style) {
        Bevel.Raised -> {
            edge(Retro.BevelHighlight, Retro.BevelDark, 0f, w)
            edge(Retro.BevelLight, Retro.BevelShadow, w, w)
        }
        Bevel.Window -> {
            edge(Retro.BevelEtch, Retro.BevelDark, 0f, w)
            edge(Retro.BevelLight, Retro.BevelShadow, w, w)
        }
        Bevel.Pressed -> {
            edge(Retro.BevelDark, Retro.BevelHighlight, 0f, w)
            edge(Retro.BevelShadow, Retro.BevelLight, w, w)
        }
        Bevel.Sunken -> {
            edge(Retro.BevelShadow, Retro.BevelEtch, 0f, w)
            edge(Retro.BevelDark, Retro.BevelLight, w, w)
        }
        Bevel.Field -> edge(Retro.BevelShadow, Retro.BevelField, 0f, w)
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

/** The 1dp dotted focus rectangle, [inset] inside the bounds. */
fun Modifier.dottedOutline(visible: Boolean, color: Color, inset: Dp): Modifier =
    if (!visible) this else drawWithContent {
        drawContent()
        val px = 1.dp.toPx().roundToInt().coerceAtLeast(1).toFloat()
        val i = inset.toPx() + px / 2
        drawRect(
            color,
            topLeft = Offset(i, i),
            size = Size(size.width - 2 * i, size.height - 2 * i),
            style = Stroke(px, pathEffect = PathEffect.dashPathEffect(floatArrayOf(px, px))),
        )
    }

/** 1dp dashed line along the bottom, between LCD list rows. */
fun Modifier.dashedDivider(): Modifier = drawBehind {
    val px = 1.dp.toPx().roundToInt().coerceAtLeast(1).toFloat()
    val y = size.height - px / 2
    drawLine(
        Retro.LcdRowDivider, Offset(0f, y), Offset(size.width, y), px,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3 * px, 3 * px)),
    )
}

/**
 * Win95 push button: raised, and sunken (darker, content nudged 1dp right and down) while
 * pressed or while [latched], e.g. a toggle that is on or the active tab.
 */
@Composable
fun Win95Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    latched: Boolean = false,
    /** [Role.Switch] reports [latched] as on/off and [Role.Tab] as selected; anything else is a plain button. */
    role: Role = Role.Button,
    contentColor: Color = Retro.TextOnFace,
    /** What a screen reader says instead of the content, for icon-only buttons. */
    description: String? = null,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val sunken = latched || (pressed && enabled)
    val action = when (role) {
        Role.Switch -> Modifier.toggleable(latched, interaction, null, enabled, role) { onClick() }
        Role.Tab -> Modifier.selectable(latched, interaction, null, enabled, role, onClick)
        else -> Modifier.clickable(interaction, null, enabled, role = role, onClick = onClick)
    }
    Box(
        modifier
            .defaultMinSize(44.dp, 44.dp)
            .background(if (sunken) Retro.FacePressed else Retro.Face)
            .bevel(if (sunken) Bevel.Pressed else Bevel.Raised)
            .dottedOutline(focused, Retro.TextOnFace, 5.dp)
            .then(action)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides if (enabled) contentColor else Retro.TextDisabled) {
            Box(
                (if (sunken) Modifier.offset(1.dp, 1.dp) else Modifier)
                    .then(if (description != null) Modifier.clearAndSetSemantics {} else Modifier),
                contentAlignment = Alignment.Center,
            ) { content() }
        }
    }
}

/**
 * A 40×24 title-bar button. It looks small, but Compose stretches the touch area of
 * clickables smaller than 48dp, so it still meets the 44dp minimum.
 */
@Composable
fun TitleBarButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    latched: Boolean = false,
    height: Dp = 24.dp,
) {
    Win95Button(onClick, Modifier.size(40.dp, height), enabled, latched, description = description) { Glyph(icon) }
}

/** Raised window frame with a title bar: [icon], bold [title] and [trailing] items such as a count or buttons. */
@Composable
fun Win95Window(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    /** The mini player's smaller title bar. */
    compact: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.background(Retro.Face).bevel(Bevel.Window).padding(3.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(if (compact) 26.dp else 30.dp)
                .background(Retro.TitleBar)
                .padding(start = 6.dp, end = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Glyph(icon, Modifier.size(if (compact) 14.dp else 16.dp), Retro.TitleText)
            RetroText(
                title,
                Modifier.weight(1f).semantics { heading() },
                Retro.TitleText,
                if (compact) 13.sp else 15.sp,
                RetroFonts.Chrome,
                bold = true,
                letterSpacing = 0.02.em,
            )
            CompositionLocalProvider(LocalContentColor provides Retro.TitleText) { trailing() }
        }
        content()
    }
}

/** Sunken dark-green screen. */
@Composable
fun LcdPanel(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.background(Retro.LcdBg).bevel(Bevel.Sunken).padding(2.dp)) {
        CompositionLocalProvider(LocalContentColor provides Retro.LcdText) { content() }
    }
}

/** 8×8 indicator lamp. When lit it glows in stepped squares, since nothing here is blurred. */
@Composable
fun Led(on: Boolean, modifier: Modifier = Modifier) {
    Spacer(
        modifier.size(8.dp).drawBehind {
            if (on) {
                for ((spread, alpha) in listOf(6.dp to 0.08f, 4.dp to 0.14f, 2.dp to 0.25f)) {
                    val s = spread.toPx()
                    drawRect(Retro.LedOn.copy(alpha = alpha), Offset(-s, -s), Size(size.width + 2 * s, size.height + 2 * s))
                }
                drawRect(Retro.LedOn)
            } else {
                val px = 1.dp.toPx().roundToInt().coerceAtLeast(1).toFloat()
                drawRect(Retro.LedOff)
                drawRect(Color.Black, size = Size(size.width, px))
                drawRect(Color.Black, size = Size(px, size.height))
            }
        },
    )
}

/** The thin progress line under the compact LCD titles. */
@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(4.dp).background(Retro.LedOff)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(Retro.LcdText))
    }
}

fun progress(positionMs: Long, durationMs: Long): Float =
    if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

/** Sunken LCD groove with a raised thumb. Tap or drag anywhere to seek. */
@Composable
fun Win95Slider(
    positionMs: Long,
    durationMs: Long,
    onScrub: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fraction = progress(positionMs, durationMs)
    val scrub by rememberUpdatedState(onScrub)
    val seek by rememberUpdatedState(onSeek)
    var scrubMs by remember { mutableLongStateOf(0L) }
    fun toMs(x: Float, width: Int) = ((x / width).coerceIn(0f, 1f) * durationMs).toLong()
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .semantics {
                contentDescription = "Seek"
                if (durationMs > 0) {
                    stateDescription = "${formatTime(positionMs)} of ${formatTime(durationMs)}"
                    progressBarRangeInfo = ProgressBarRangeInfo(positionMs.toFloat(), 0f..durationMs.toFloat())
                    setProgress { seek(it.toLong().coerceIn(0, durationMs)); true }
                } else {
                    disabled()
                }
            }
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
    ) {
        val thumbWidth = 22.dp
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(14.dp)
                .background(Retro.LcdBg)
                .bevel(Bevel.Sunken)
                .padding(3.dp),
        ) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Retro.LcdText))
        }
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = (maxWidth - thumbWidth) * fraction)
                .size(thumbWidth, 36.dp)
                .background(Retro.Face)
                .bevel(),
            contentAlignment = Alignment.Center,
        ) {
            // Grip: a tiny etched line.
            Column {
                Spacer(Modifier.size(8.dp, 1.dp).background(Retro.BevelShadow))
                Spacer(Modifier.size(8.dp, 1.dp).background(Retro.BevelHighlight))
            }
        }
    }
}

sealed interface MenuEntry

/** A menu command. Put '&' before the accelerator letter, as in "&Share…". */
class MenuItem(
    val label: String,
    val icon: ImageVector? = null,
    val bold: Boolean = false,
    val onClick: () -> Unit,
) : MenuEntry

data object MenuSeparator : MenuEntry

/** Opens below its anchor ([x] from the anchor's left edge, or flush with its right edge), above it if there is no room. */
class MenuPosition(private val x: Int = 0, private val alignEnd: Boolean = false) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val left = if (alignEnd) anchorBounds.right - popupContentSize.width else anchorBounds.left + x
        val fitsBelow = anchorBounds.bottom + popupContentSize.height <= windowSize.height
        val top = if (fitsBelow || anchorBounds.top < popupContentSize.height) anchorBounds.bottom
        else anchorBounds.top - popupContentSize.height
        return IntOffset(
            left.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            top.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
    }
}

/** Raised popup menu with a hard offset shadow. Picking an item closes the menu first. */
@Composable
fun Win95Menu(
    entries: List<MenuEntry>,
    onDismiss: () -> Unit,
    position: PopupPositionProvider,
    width: Dp,
) {
    Popup(popupPositionProvider = position, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        val hasIcons = entries.any { it is MenuItem && it.icon != null }
        Column(
            Modifier
                .padding(end = 6.dp, bottom = 6.dp)
                .drawBehind { drawRect(Retro.MenuShadow, Offset(6.dp.toPx(), 6.dp.toPx()), size) }
                .width(width)
                .background(Retro.Face)
                .bevel()
                .padding(3.dp),
        ) {
            for (entry in entries) {
                when (entry) {
                    MenuSeparator -> Column(Modifier.padding(horizontal = 4.dp, vertical = 3.dp)) {
                        Spacer(Modifier.fillMaxWidth().height(1.dp).background(Retro.BevelShadow))
                        Spacer(Modifier.fillMaxWidth().height(1.dp).background(Retro.BevelEtch))
                    }
                    is MenuItem -> MenuRow(entry, hasIcons) {
                        onDismiss()
                        entry.onClick()
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(item: MenuItem, hasIcons: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val hot = pressed || focused || hovered
    val color = if (hot) Retro.TitleText else Retro.TextOnFace
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(if (hot) Retro.TitleBar else Color.Transparent)
            .clickable(interaction, null, role = Role.Button, onClick = onClick)
            .padding(start = if (hasIcons) 10.dp else 28.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item.icon?.let { Glyph(it, color = color) }
        BasicText(
            accel(item.label),
            Modifier.weight(1f, fill = false),
            style = retroStyle(color, 15.sp, RetroFonts.Chrome, bold = item.bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A row of thin sunken fields; the first one takes the spare width. */
@Composable
fun StatusBar(fields: List<String>, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(start = 6.dp, top = 6.dp, end = 6.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        fields.forEachIndexed { i, text ->
            RetroText(
                text,
                (if (i == 0) Modifier.weight(1f) else Modifier)
                    .bevel(Bevel.Field)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                Retro.TextOnFace,
                12.sp,
                RetroFonts.Chrome,
            )
        }
    }
}

/** Sunken LCD search box with a magnifier. [label] is what a screen reader calls the field. */
@Composable
fun LcdSearchField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    Row(
        modifier
            .height(44.dp)
            .background(Retro.LcdBg)
            .bevel(Bevel.Sunken)
            .padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Glyph(Glyphs.Search, color = Retro.LcdText)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = label },
            singleLine = true,
            textStyle = retroStyle(Retro.LcdText, 15.sp),
            cursorBrush = SolidColor(Retro.LcdText),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            decorationBox = { field ->
                Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        RetroText(placeholder, Modifier.clearAndSetSemantics {}, Retro.LcdTextFaint, 15.sp)
                    }
                    field()
                }
            },
        )
        if (value.isNotEmpty()) {
            Box(
                Modifier
                    .size(44.dp)
                    .clickable(null, indication = null, role = Role.Button) { onChange("") }
                    .semantics { contentDescription = "Clear" },
                contentAlignment = Alignment.Center,
            ) { Glyph(Glyphs.Close, Modifier.size(10.dp), Retro.LcdText) }
        } else {
            Spacer(Modifier.width(2.dp))
        }
    }
}

/** Win95 scrollbar for long lists: drag the thumb or touch the checkered track to jump there. */
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
            .width(18.dp)
            .fillMaxHeight()
            .drawWithCache {
                val square = 2.dp.toPx()
                val checks = Path()
                var y = 0f
                var row = 0
                while (y < size.height) {
                    var x = if (row % 2 == 0) square else 0f
                    while (x < size.width) {
                        checks.addRect(Rect(x, y, x + square, y + square))
                        x += 2 * square
                    }
                    y += square
                    row++
                }
                onDrawBehind {
                    drawRect(Retro.ScrollTrack)
                    drawPath(checks, Retro.ScrollTrackAlt)
                }
            }
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

/**
 * Decorative level meter: [count] bars that bounce between random heights while [running]
 * and freeze where they are otherwise. Draws only, so screen readers skip it.
 */
@Composable
fun LevelBars(
    count: Int,
    barWidth: Dp,
    gap: Dp,
    height: Dp,
    color: Color,
    running: Boolean,
    modifier: Modifier = Modifier,
) {
    val clock = rememberRunningClock(running)
    Canvas(modifier.size(barWidth * count + gap * (count - 1), height)) {
        val seconds = clock.floatValue
        val w = barWidth.toPx()
        val step = w + gap.toPx()
        for (i in 0 until count) {
            val h = size.height * barLevel(i, seconds)
            drawRect(color, Offset(i * step, size.height - h), Size(w, h))
        }
    }
}

/** Seconds that only tick while [running]. Read it while drawing, so frames don't recompose. */
@Composable
private fun rememberRunningClock(running: Boolean): MutableFloatState {
    val seconds = remember { mutableFloatStateOf(0f) }
    if (running) {
        LaunchedEffect(Unit) {
            var last = withFrameNanos { it }
            while (true) {
                withFrameNanos { now ->
                    seconds.floatValue += (now - last) / 1e9f
                    last = now
                }
            }
        }
    }
    return seconds
}

/** Height (0..1) of [bar]: eases from one random level to the next, each bar at its own pace. */
private fun barLevel(bar: Int, seconds: Float): Float {
    val u = seconds / (0.22f + bar % 5 * 0.06f) + bar * 0.37f
    val k = floor(u).toInt()
    val f = u - k
    return lerp(randomLevel(bar, k), randomLevel(bar, k + 1), f * f * (3 - 2 * f))
}

private fun randomLevel(bar: Int, k: Int): Float {
    var h = bar * 374761393 + k * 668265263
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return 0.12f + 0.83f * ((h and 0xFFFF) / 65535f)
}

/**
 * One line that never ellipsizes: if it doesn't fit, it slides to its end and back,
 * resting about a fifth of the time at each end, 9 s per direction.
 */
@Composable
fun MarqueeText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    var boxWidth by remember { mutableIntStateOf(0) }
    var textWidth by remember { mutableIntStateOf(0) }
    val overflow = (textWidth - boxWidth).coerceAtLeast(0)
    Box(modifier.fillMaxWidth().clipToBounds().onSizeChanged { boxWidth = it.width }) {
        val shift = if (overflow > 0) {
            key(text) {
                rememberInfiniteTransition(label = "marquee").animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        keyframes {
                            durationMillis = 9_000
                            0f at 1_800
                            1f at 7_200
                        },
                        RepeatMode.Reverse,
                    ),
                    label = "marquee",
                )
            }
        } else {
            null
        }
        BasicText(
            text,
            Modifier
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .onSizeChanged { textWidth = it.width }
                .graphicsLayer { translationX = -overflow * (shift?.value ?: 0f) },
            style = style,
            maxLines = 1,
            softWrap = false,
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

/** Zero-padded to three digits, like an LCD track counter. */
fun pad3(n: Int): String = String.format(Locale.US, "%03d", n)
