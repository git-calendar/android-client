package dev.jotalac.ui.common

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.jotalac.R
import dev.jotalac.data.Settings
import dev.jotalac.data.TimeFormat
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// ------------------------------------------- errors --------------------------------------------

/** The core's error codes, which start its error texts ("NETWORK: …"), and their messages. See core/pkg/errcode. */
private val CORE_ERRORS = mapOf(
    "NETWORK" to R.string.core_error_network,
    "PROXY" to R.string.core_error_proxy,
    "AUTH" to R.string.core_error_auth,
    "FORBIDDEN" to R.string.core_error_forbidden,
    "RATELIMIT" to R.string.core_error_rate_limit,
    "VALIDATION" to R.string.core_error_validation,
    "STORAGE" to R.string.core_error_storage,
    "CALENDAR_EXISTS" to R.string.core_error_calendar_exists,
    "CALENDAR_NOT_FOUND" to R.string.core_error_calendar_not_found,
    "READONLY" to R.string.core_error_readonly,
    "EVENT_NOT_FOUND" to R.string.core_error_event_not_found,
    "TAG_EXISTS" to R.string.core_error_tag_exists,
    "TAG_NOT_FOUND" to R.string.core_error_tag_not_found,
    "NAME_REQUIRED" to R.string.core_error_name_required,
    "DECRYPTION" to R.string.core_error_decryption,
)
private val CODE = Regex("""\b([A-Z_]+): """)
private val CALENDAR = Regex("""calendar "([^"]*)": """)

/**
 * User-facing text for an error, like web-client services/errorHandling.ts formatErrorForUser: the
 * translated message of the core's error code, else the core's own text starting with a capital.
 */
fun Throwable.userMessage(context: Context, calendarName: String? = null): String {
    val raw = message ?: toString()
    val code = CODE.findAll(raw).firstNotNullOfOrNull { CORE_ERRORS[it.groupValues[1]] }
    // the core's own text already names its calendars
    if (code == null && CALENDAR.containsMatchIn(raw)) return raw.sentence()
    val message = code?.let { context.getString(it) } ?: raw.sentence()
    val calendars = CALENDAR.findAll(raw).map { it.groupValues[1] }.distinct().toList()
        .ifEmpty { listOfNotNull(calendarName?.trim()?.takeIf { it.isNotEmpty() }) }
    if (calendars.isEmpty()) return message
    return context.getString(R.string.core_error_calendar, calendars.joinToString("”, “"), message)
}

/** Starts with a capital letter and ends with a full stop, like the app's own messages. */
private fun String.sentence(): String {
    val text = trim().replaceFirstChar { it.uppercaseChar() }
    return if (text.isEmpty() || text.last() in ".!?…") text else "$text."
}

@Composable
fun MessageDialog(message: String, onDismiss: () -> Unit, title: String? = null) {
    FormDialog(onDismiss, title) {
        Text(message, style = formTextStyle())
        FormButtons { FormButton(stringResource(R.string.ok), onDismiss) }
    }
}

@Composable
fun ConfirmDialog(
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    danger: Boolean = false,
    onDismiss: () -> Unit,
) {
    FormDialog(onDismiss) {
        Text(message, style = formTextStyle())
        FormButtons {
            FormButton(confirmLabel, { onDismiss(); onConfirm() }, danger = danger)
            FormButton(stringResource(R.string.cancel), onDismiss)
        }
    }
}

// ------------------------------------------ formatting -----------------------------------------

@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = formattingLocale(LocalConfiguration.current.locales[0] ?: Locale.getDefault())

/**
 * The made-up Git language (qgt) has no date data in Java, so months come out as "M01"; it formats
 * dates like English instead.
 */
fun formattingLocale(locale: Locale): Locale = if (locale.language == "qgt") Locale.ENGLISH else locale

fun use24Hour(context: Context, settings: Settings): Boolean = when (settings.timeFormat) {
    TimeFormat.System -> DateFormat.is24HourFormat(context)
    TimeFormat.H12 -> false
    TimeFormat.H24 -> true
}

/** Formats times following the time format setting, e.g. "14:30" or "2:30 PM". */
class TimeFormatter(private val is24: Boolean, private val locale: Locale) {
    private val formatter = DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a", locale)

    fun time(time: ZonedDateTime): String = time.format(formatter)
    fun time(time: LocalTime): String = time.format(formatter)
    fun range(from: ZonedDateTime, to: ZonedDateTime) = "${time(from)} - ${time(to)}"

    /** A time without AM/PM for tight spots like the hour column: "14:30" or "2:30". */
    fun shortTime(time: LocalTime): String = if (is24) time(time) else time.format(DateTimeFormatter.ofPattern("h:mm", locale))

    /** Hour gutter label: "06:00" or "6 AM". */
    fun hourLabel(hour: Int): String {
        val h = ((hour % 24) + 24) % 24
        return if (is24) "%02d:00".format(h) else "${if (h % 12 == 0) 12 else h % 12} ${if (h < 12) "AM" else "PM"}"
    }
}

@Composable
fun rememberTimeFormatter(settings: Settings): TimeFormatter {
    val context = LocalContext.current
    return TimeFormatter(use24Hour(context, settings), currentLocale())
}

fun String.capitalized(locale: Locale): String = replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

fun DayOfWeek.shortName(locale: Locale): String = getDisplayName(TextStyle.SHORT, locale).capitalized(locale)
fun DayOfWeek.longName(locale: Locale): String = getDisplayName(TextStyle.FULL_STANDALONE, locale).capitalized(locale)
/** The first two letters of the short name, "Su" for Sunday; narrow names repeat letters ("S" for both weekend days). */
fun DayOfWeek.twoLetterName(locale: Locale): String = shortName(locale).take(2)

fun YearMonth.title(locale: Locale): String =
    DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(this).capitalized(locale)

/** Week days in display order, starting at [start]. */
fun weekDays(start: DayOfWeek): List<DayOfWeek> = List(7) { start.plus(it.toLong()) }
