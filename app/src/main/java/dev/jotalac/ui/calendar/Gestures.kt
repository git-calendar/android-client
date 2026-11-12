package dev.jotalac.ui.calendar

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import dev.jotalac.ui.theme.contentColorOn

/** Callbacks of [calendarGestures]. Positions are local to the element the modifier is on. */
interface CalendarGestureHandler {
    fun onTap(position: Offset)

    /** Returns false to ignore the long press (for example on a read-only event). */
    fun onDragStart(position: Offset): Boolean
    fun onDrag(position: Offset)
    fun onDragEnd()
    fun onDragCancel()
}

private enum class PressResult { Tap, Cancel }

/**
 * Tap, or long-press-then-drag, like the web client's touch handling: moving before the long press
 * fires leaves the gesture to scrolling and paging, a second finger leaves it to pinch zoom.
 */
@Composable
fun Modifier.calendarGestures(handler: () -> CalendarGestureHandler): Modifier {
    // The gesture loop outlives recompositions; always ask the latest lambda for a handler.
    val latest by rememberUpdatedState(handler)
    return pointerInput(Unit) { detectCalendarGestures { latest() } }
}

private suspend fun PointerInputScope.detectCalendarGestures(handler: () -> CalendarGestureHandler) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val press = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.count { it.pressed } > 1) return@withTimeoutOrNull PressResult.Cancel
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull PressResult.Cancel
                if (change.changedToUp()) {
                    return@withTimeoutOrNull if (change.isConsumed) PressResult.Cancel else PressResult.Tap
                }
                if (change.isConsumed) return@withTimeoutOrNull PressResult.Cancel
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                    return@withTimeoutOrNull PressResult.Cancel
                }
            }
            @Suppress("UNREACHABLE_CODE")
            PressResult.Cancel
        }
        when (press) {
            PressResult.Tap -> handler().onTap(down.position)
            PressResult.Cancel -> Unit
            null -> {
                if (!handler().onDragStart(down.position)) return@awaitEachGesture
                down.consume()
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null) {
                        handler().onDragCancel()
                        break
                    }
                    if (change.changedToUp()) {
                        change.consume()
                        handler().onDragEnd()
                        break
                    }
                    change.consume()
                    handler().onDrag(change.position)
                }
            }
        }
    }
}

/** Two-finger pinch that reports zoom factors, leaving one-finger gestures alone. */
fun Modifier.pinchZoom(onZoom: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            if (event.changes.count { it.pressed } >= 2) {
                val zoom = event.calculateZoom()
                if (zoom != 1f) {
                    onZoom(zoom)
                    event.changes.forEach { it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}

private val TitleLineHeight = 14.sp
private val SideLine = 3.dp

/**
 * An event block in the timeline, all-day bar or month grid, styled like the web client: square, the
 * event color at half strength with a solid line of it on the left (dashed and lighter for a [ghost]
 * drag preview). The title wraps over every full line that fits, without an ellipsis; the subtitle
 * is shown below it only when a line is left for it. [textScale] shrinks the text, like the
 * zoomed-out timeline does, so more of the title fits.
 */
@Composable
fun EventChip(
    title: String,
    subtitle: String?,
    color: Color,
    modifier: Modifier = Modifier,
    ghost: Boolean = false,
    compact: Boolean = false,
    textScale: Float = 1f,
) {
    // see-through like the web client; the text color is picked against the fill over the surface
    val fill = color.copy(alpha = if (ghost) 0.2f else 0.5f)
    val content = contentColorOn(fill.compositeOver(MaterialTheme.colorScheme.surface))
    val titleLineHeight = TitleLineHeight * textScale
    BoxWithConstraints(
        modifier
            // a small gap keeps neighboring and stacked events apart
            .padding(end = 1.dp, bottom = 1.dp)
            .background(fill)
            .drawBehind {
                val width = SideLine.toPx()
                val dash = 6.dp.toPx()
                drawLine(
                    color,
                    Offset(width / 2, 0f),
                    Offset(width / 2, size.height),
                    strokeWidth = width,
                    pathEffect = if (ghost) PathEffect.dashPathEffect(floatArrayOf(dash, dash)) else null,
                )
            }
            .padding(start = SideLine + 3.dp, end = 3.dp, top = if (compact) 1.dp else 2.dp, bottom = if (compact) 1.dp else 2.dp),
    ) {
        val lineHeight = with(LocalDensity.current) { titleLineHeight.toDp() }
        val lines = if (constraints.hasBoundedHeight) (maxHeight / lineHeight).toInt().coerceAtLeast(1) else Int.MAX_VALUE
        var titleLines by remember { mutableIntStateOf(1) }
        Column {
            Text(
                title,
                color = content,
                fontSize = 12.sp * textScale,
                lineHeight = titleLineHeight,
                fontWeight = FontWeight.SemiBold,
                maxLines = lines,
                // a single line is cut at the edge rather than at a word, to show as much as possible
                softWrap = lines > 1,
                overflow = TextOverflow.Clip,
                style = MaterialTheme.typography.labelMedium,
                onTextLayout = { titleLines = it.lineCount },
            )
            if (subtitle != null && titleLines < lines) {
                Text(
                    subtitle,
                    color = content.copy(alpha = 0.8f),
                    fontSize = 11.sp * textScale,
                    lineHeight = 13.sp * textScale,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}
