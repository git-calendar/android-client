package dev.jotalac.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.color.ColorProvider as DayNightColorProvider
import dev.jotalac.AppContainer
import dev.jotalac.EventRef
import dev.jotalac.MainActivity
import dev.jotalac.R
import dev.jotalac.container
import dev.jotalac.core.CoreException
import dev.jotalac.data.CalendarRepository
import dev.jotalac.data.ColorSource
import dev.jotalac.data.ThemeMode
import dev.jotalac.data.WidgetTheme
import dev.jotalac.ui.common.TimeFormatter
import dev.jotalac.ui.common.use24Hour
import dev.jotalac.ui.theme.TagColor
import dev.jotalac.ui.theme.contentColorOn
import dev.jotalac.ui.theme.colorScheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import dev.jotalac.ui.common.formattingLocale
import dev.jotalac.ui.common.shortName
import java.util.Locale

/**
 * A list entry already formatted for display, so the widget's composition does no work. A null
 * [color] marks today's "Nothing scheduled" placeholder, drawn in the app's neutral colors.
 */
private data class EventChip(val title: String, val time: String?, val color: Color?, val ref: EventRef? = null)

/**
 * One list item: a day's label beside up to [ScheduleWidget.CHIPS_PER_BLOCK] of its events.
 * Busier days continue in further blocks without a label, since Glance caps a column's children.
 */
private data class DayBlock(
    /** Stable list item ID: the day and the block's index within it. */
    val id: Long,
    val day: LocalDate,
    val month: String?,
    /** "15. Sun" like the app's day headers, on the day's first block only. */
    val label: String?,
    val isToday: Boolean,
    val chips: List<EventChip>,
)

/**
 * The app's light and dark colors for the widget, following the app's theme setting: [forceDark] is
 * null to follow the system like the app does by default.
 */
private data class Palette(val light: ColorScheme, val dark: ColorScheme, val forceDark: Boolean?) {
    fun of(pick: (ColorScheme) -> Color): ColorProvider = when (forceDark) {
        null -> DayNightColorProvider(day = pick(light), night = pick(dark))
        true -> ColorProvider(pick(dark))
        false -> ColorProvider(pick(light))
    }
}

/** The red Nothing uses for small accents (brand guidelines). */
private val NothingRed = Color(0xFFD71921)

/**
 * Nothing OS's widget look: a near-black (near-white in light mode) neutral tinted by the wallpaper,
 * like its own widgets (tone 10 and about 93), with plain white or black text and Nothing's red accent.
 */
private fun nothingScheme(context: Context, dark: Boolean): ColorScheme {
    fun system(id: Int) = Color(context.getColor(id))
    val base = colorScheme(context, dark, ColorSource.Dynamic)
    return if (dark) {
        base.copy(
            surface = system(android.R.color.system_neutral1_900),
            onSurface = Color.White,
            onSurfaceVariant = system(android.R.color.system_neutral2_200),
            surfaceVariant = system(android.R.color.system_neutral1_800),
            primary = NothingRed,
            onPrimary = Color.White,
        )
    } else {
        base.copy(
            surface = system(android.R.color.system_neutral1_50),
            onSurface = Color.Black,
            onSurfaceVariant = system(android.R.color.system_neutral2_700),
            surfaceVariant = system(android.R.color.system_neutral1_100),
            primary = NothingRed,
            onPrimary = Color.White,
        )
    }
}

private data class ScheduleState(
    val theme: WidgetTheme,
    val palette: Palette,
    val today: LocalDate,
    val header: String,
    val headerWeekday: String,
    val blocks: List<DayBlock>,
)

/**
 * Home-screen list of upcoming events, modelled on Google Calendar's schedule widget: today's date
 * and a new-event button on top, then today (with "Nothing scheduled" when it has no events left)
 * and the days with events in the next [DAYS] days, each day labelled once.
 * Tapping an event opens it in the app's editor; tapping a day opens the app on that day.
 */
class ScheduleWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = load(context)
        provideContent {
            // Glance keeps a session alive for a while, and updating a live session only recomposes it,
            // so reload here on every refresh request and at midnight instead of only on session start.
            var state by remember { mutableStateOf(initial) }
            LaunchedEffect(Unit) {
                reloads.drop(1).collectLatest { state = load(context) }
            }
            LaunchedEffect(state.today) {
                val midnight = state.today.plusDays(1).atStartOfDay(ZoneId.systemDefault())
                delay(Duration.between(ZonedDateTime.now(), midnight).toMillis().coerceAtLeast(0) + 1_000)
                requestReload()
            }
            Content(context, state)
        }
    }

    private suspend fun load(context: Context): ScheduleState {
        val container = context.container
        val locale = formattingLocale(AppCompatDelegate.getApplicationLocales()[0] ?: Locale.getDefault())
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val headerWeekday = now.format(DateTimeFormatter.ofPattern("EEEE", locale))
        val header = now.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMMd"), locale))

        val entries = try {
            if (container.ready.await() != null) emptyList() else upcoming(container, now)
        } catch (e: CoreException) {
            Log.e(TAG, "cannot load events: ${e.message}")
            emptyList()
        }

        val settings = container.settings.current()
        val scheme = { dark: Boolean ->
            when (settings.widgetTheme) {
                WidgetTheme.App -> colorScheme(context, dark, settings.colorSource)
                WidgetTheme.Nothing -> nothingScheme(context, dark)
            }
        }
        val palette = Palette(
            light = scheme(false),
            dark = scheme(true),
            forceDark = when (settings.themeMode) {
                ThemeMode.System -> null
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            },
        )
        val times = TimeFormatter(use24Hour(context, settings), locale)
        val monthFormat = DateTimeFormatter.ofPattern("LLLL", locale)
        // today's date is already in the title, so its row just says "Today"
        fun dayLabel(day: LocalDate) =
            if (day == now.toLocalDate()) context.getString(R.string.today)
            else "${day.dayOfMonth}. ${day.dayOfWeek.shortName(locale)}"
        var lastMonth = now.toLocalDate().withDayOfMonth(1)
        val blocks = entries.groupBy { it.day }.flatMap { (day, dayEntries) ->
            val month = day.withDayOfMonth(1)
            val monthLabel = if (month != lastMonth) day.format(monthFormat) else null
            lastMonth = month
            val chips = dayEntries.map { entry ->
                val event = entry.event
                EventChip(
                    title = event.title,
                    time = when (entry.span) {
                        EntrySpan.AllDay -> null
                        EntrySpan.Within -> times.range(event.from, event.to)
                        EntrySpan.Starts -> "${times.time(event.from)} –"
                        EntrySpan.Ends -> "– ${times.time(event.to)}"
                    },
                    color = TagColor.of(container.repository.colorId(event.calendar, event.tagId, settings)).color,
                    ref = event.id?.let { EventRef(it, event.from.toInstant()) },
                )
            }
            chips.chunked(CHIPS_PER_BLOCK).mapIndexed { index, chunk ->
                DayBlock(
                    id = day.toEpochDay() * 100 + index,
                    day = day,
                    month = if (index == 0) monthLabel else null,
                    label = if (index == 0) dayLabel(day) else null,
                    isToday = day == now.toLocalDate(),
                    chips = chunk,
                )
            }
        }
        val today = now.toLocalDate()
        // Like Google Calendar, today is always listed, even when it has no events left.
        val todayBlock = if (blocks.firstOrNull()?.day == today) emptyList() else listOf(
            DayBlock(
                id = today.toEpochDay() * 100,
                day = today,
                month = null,
                label = dayLabel(today),
                isToday = true,
                chips = listOf(EventChip(context.getString(R.string.widget_nothing_scheduled), null, null)),
            )
        )
        return ScheduleState(settings.widgetTheme, palette, today, header, headerWeekday, todayBlock + blocks)
    }

    private suspend fun upcoming(container: AppContainer, now: ZonedDateTime): List<ScheduleEntry> {
        val from = now.toLocalDate().atStartOfDay(now.zone)
        val filter = CalendarRepository.buildFilter(container.settings.currentHiddenTags())
        val events = container.core.getEvents(from, from.plusDays(DAYS.toLong()), filter)
        return buildSchedule(events, now, DAYS, limit = MAX_ROWS)
    }

    @Composable
    private fun Content(context: Context, state: ScheduleState) {
        val today = state.today
        val colors = state.palette
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                // tells the launcher this is the widget's background, so opening and closing the app morphs
                // between the app's window and this box and its corners instead of guessing
                .appWidgetBackground()
                .background(colors.of { it.surface })
                .let {
                    when (state.theme) {
                        // nearly square like the app's dialogs
                        WidgetTheme.App -> it.cornerRadius(6.dp)
                        // the launcher's rounding, like Nothing's own widgets
                        WidgetTheme.Nothing -> it.cornerRadius(android.R.dimen.system_app_widget_background_radius)
                    }
                }
                // all round, so the list's last event isn't cut by the corners; Nothing's are much rounder
                .padding(if (state.theme == WidgetTheme.Nothing) 16.dp else 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
                Column(modifier = GlanceModifier.defaultWeight().clickable(openDay(context, today))) {
                    Text(
                        state.headerWeekday,
                        style = TextStyle(color = colors.of { it.onSurfaceVariant }, fontSize = 13.sp),
                        maxLines = 1,
                    )
                    Text(
                        state.header,
                        style = TextStyle(
                            color = colors.of { it.onSurface },
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        maxLines = 1,
                    )
                }
                // the app's new-event button in the accent color: nearly square, round like Nothing's buttons there
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = GlanceModifier
                        .size(36.dp)
                        .cornerRadius(if (state.theme == WidgetTheme.Nothing) 18.dp else 3.dp)
                        .background(colors.of { it.primary })
                        .clickable(newEvent(context)),
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.fi_plus),
                        contentDescription = context.getString(R.string.new_event),
                        colorFilter = ColorFilter.tint(colors.of { it.onPrimary }),
                        modifier = GlanceModifier.size(18.dp),
                    )
                }
            }
            Spacer(GlanceModifier.height(8.dp))

            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(state.blocks, itemId = { it.id }) { block -> DayRow(context, colors, block) }
            }
        }
    }

    /** A day's date on the left, in one line like the app's day headers, beside its events. */
    @Composable
    private fun DayRow(context: Context, colors: Palette, block: DayBlock) {
        Column(modifier = GlanceModifier.fillMaxWidth().clickable(openDay(context, block.day))) {
            if (block.month != null) {
                // the month's name, then a dashed line in the grid's color across the rest of the width
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
                ) {
                    Text(
                        block.month.replaceFirstChar { it.titlecase() },
                        style = TextStyle(
                            color = colors.of { it.onSurface },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        maxLines = 1,
                    )
                    Image(
                        provider = ImageProvider(R.drawable.widget_dashed_line),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        colorFilter = ColorFilter.tint(colors.of { it.outlineVariant }),
                        modifier = GlanceModifier.defaultWeight().height(2.dp).padding(start = 8.dp),
                    )
                }
            }
            Row(modifier = GlanceModifier.fillMaxWidth().padding(top = if (block.label != null) 6.dp else 0.dp)) {
                Box(modifier = GlanceModifier.width(60.dp).padding(top = 2.dp, end = 8.dp)) {
                    if (block.label != null) {
                        Text(
                            block.label,
                            style = TextStyle(
                                color = if (block.isToday) colors.of { it.primary } else colors.of { it.onSurfaceVariant },
                                fontSize = 13.sp,
                                fontWeight = if (block.isToday) FontWeight.Medium else FontWeight.Normal,
                            ),
                            maxLines = 1,
                        )
                    }
                }
                Column(modifier = GlanceModifier.defaultWeight()) {
                    block.chips.forEach { chip -> Chip(context, colors, chip) }
                }
            }
        }
    }

    /**
     * An event exactly like the app's [dev.jotalac.ui.calendar.EventChip]: square, the event color at half
     * strength over the background with a solid 3 dp line of it on the left, the title and the time.
     * The placeholder is only its muted text.
     */
    @Composable
    private fun Chip(context: Context, colors: Palette, chip: EventChip) {
        val fill = chip.color?.copy(alpha = 0.5f)
        // opaque, as the line is the solid color showing through the start padding of an outer box:
        // a line filling the chip's height breaks the launcher's measuring of list items.
        // The placeholder gets the widget's own background rather than none, as the launcher recycles list rows.
        val background = { scheme: ColorScheme -> fill?.compositeOver(scheme.surface) ?: scheme.surface }
        val content = { scheme: ColorScheme -> fill?.let { contentColorOn(background(scheme)) } ?: scheme.onSurfaceVariant }
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                // apart like separate events, not the 1 dp the app leaves between neighbors in the grid
                .padding(bottom = 4.dp)
                // opens the event in the app's editor; the placeholder and events without an ID open the day
                .let { modifier -> chip.ref?.let { modifier.clickable(openEvent(context, it)) } ?: modifier },
        ) {
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(chip.color?.let { ColorProvider(it) } ?: colors.of(background))
                    .padding(start = if (chip.color != null) 3.dp else 0.dp),
            ) {
                Column(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .background(colors.of(background))
                        .padding(horizontal = 3.dp, vertical = 2.dp),
                ) {
                    Text(
                        chip.title,
                        style = TextStyle(color = colors.of(content), fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                    )
                    if (chip.time != null) {
                        Text(
                            chip.time,
                            style = TextStyle(color = colors.of { content(it).copy(alpha = 0.8f) }, fontSize = 11.sp),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "ScheduleWidget"

        /** How far ahead the widget lists events. */
        const val DAYS = 30

        /** Keeps the widget's RemoteViews small even for very busy calendars. */
        private const val MAX_ROWS = 150

        /** Events per list item; Glance allows at most 10 children in a column. */
        const val CHIPS_PER_BLOCK = 8

        /** Redraws placed widgets whenever events, calendars or settings change. */
        @OptIn(FlowPreview::class)
        fun observe(context: Context, container: AppContainer, scope: CoroutineScope) {
            scope.launch {
                combine(
                    container.repository.revision,
                    container.repository.calendars,
                    container.settings.settings,
                    container.settings.hiddenTags,
                ) { _, _, _, _ -> }
                    .drop(1)
                    .debounce(1_000)
                    .collectLatest { refresh(context) }
            }
        }

        /** Bumped to reload live widget sessions; collected inside the composition. */
        private val reloads = MutableStateFlow(0L)

        fun requestReload() = reloads.update { it + 1 }

        /** Reloads live widgets and starts a session for the others. */
        suspend fun refresh(context: Context) {
            requestReload()
            ScheduleWidget().updateAll(context)
        }

        // Each intent gets its own data URI, so the launcher keeps one PendingIntent per day.
        private fun openDay(context: Context, day: LocalDate) = actionStartActivity(
            Intent(context, MainActivity::class.java)
                .setData("gitcalendar://day/$day".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_DATE, day.toString())
        )

        private fun openEvent(context: Context, ref: EventRef) = actionStartActivity(
            Intent(context, MainActivity::class.java)
                .setData("gitcalendar://event/${ref.id}/${ref.from.toEpochMilli()}".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_EVENT, ref.id)
                .putExtra(MainActivity.EXTRA_OPEN_EVENT_FROM, ref.from.toEpochMilli())
        )

        private fun newEvent(context: Context) = actionStartActivity(
            Intent(context, MainActivity::class.java)
                .setData("gitcalendar://new-event".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_NEW_EVENT, true)
        )
    }
}

class ScheduleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScheduleWidget()

    // periodic system updates (updatePeriodMillis) must reload a live session too
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        ScheduleWidget.requestReload()
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }
}
