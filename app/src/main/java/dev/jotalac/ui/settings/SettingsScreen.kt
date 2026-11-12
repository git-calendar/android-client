package dev.jotalac.ui.settings

import dev.jotalac.ui.theme.AppIcons
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jotalac.AppContainer
import dev.jotalac.R
import dev.jotalac.data.CalendarViewType
import dev.jotalac.data.ColorSource
import dev.jotalac.data.Settings
import dev.jotalac.data.ThemeMode
import dev.jotalac.data.TimeFormat
import dev.jotalac.ui.calendar.ColorPicker
import dev.jotalac.ui.common.ConfirmDialog
import dev.jotalac.ui.common.MessageDialog
import dev.jotalac.ui.common.FormButton
import dev.jotalac.ui.common.FormCheckbox
import dev.jotalac.ui.common.FormGap
import dev.jotalac.ui.common.FormHint
import dev.jotalac.ui.common.FormRow
import dev.jotalac.ui.common.FormSelect
import dev.jotalac.ui.common.formTextStyle
import androidx.compose.foundation.layout.width
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.Alignment
import dev.jotalac.ui.common.currentLocale
import dev.jotalac.ui.common.longName
import dev.jotalac.ui.common.userMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek

/** In-app languages: tag to native name. "qgt" is the private-use tag of the joke Git language. */
private val LANGUAGES = listOf("en" to "English", "cs" to "Čeština", "qgt" to "Git")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit, onFiles: () -> Unit) {
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    fun update(transform: (Settings) -> Settings) {
        scope.launch { container.settings.update(transform) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.ArrowLeft, stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = FormGap, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(FormGap),
        ) {
            Section(stringResource(R.string.settings_appearance))
            LanguageSetting()
            Setting(
                stringResource(R.string.settings_theme), settings.themeMode,
                listOf(
                    ThemeMode.System to R.string.settings_theme_auto,
                    ThemeMode.Light to R.string.settings_theme_light,
                    ThemeMode.Dark to R.string.settings_theme_dark,
                ),
            ) { v -> update { it.copy(themeMode = v) } }
            Setting(
                stringResource(R.string.settings_colors), settings.colorSource,
                listOf(ColorSource.Dynamic to R.string.settings_colors_dynamic, ColorSource.Git to R.string.settings_colors_git),
            ) { v -> update { it.copy(colorSource = v) } }

            Section(stringResource(R.string.settings_calendar))
            Setting(
                stringResource(R.string.settings_default_view), settings.defaultView,
                listOf(
                    CalendarViewType.Day to R.string.view_day,
                    CalendarViewType.FourDays to R.string.view_4d,
                    CalendarViewType.Week to R.string.view_week,
                    CalendarViewType.Month to R.string.view_month,
                ),
            ) { v -> update { it.copy(defaultView = v) } }
            Setting(
                stringResource(R.string.settings_time_format), settings.timeFormat,
                listOf(
                    TimeFormat.System to R.string.settings_time_system,
                    TimeFormat.H12 to R.string.settings_12h,
                    TimeFormat.H24 to R.string.settings_24h,
                ),
            ) { v -> update { it.copy(timeFormat = v) } }
            val locale = currentLocale()
            FormRow(stringResource(R.string.settings_week_start)) {
                FormSelect(
                    settings.weekStart.longName(locale),
                    DayOfWeek.entries.map { it.longName(locale) to it },
                    { v -> update { it.copy(weekStart = v) } },
                    Modifier.width(SelectWidth),
                )
            }
            FormRow(stringResource(R.string.settings_hour_range)) {
                val hours = (0..24).map { "%02d:00".format(it) to it }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FormSelect(
                        "%02d:00".format(settings.dayViewStartHour), hours,
                        { v -> update { it.copy(dayViewStartHour = v) } }, Modifier.width(HourSelectWidth),
                    )
                    Text("–", style = formTextStyle())
                    FormSelect(
                        "%02d:00".format(settings.dayViewEndHour), hours,
                        { v -> update { it.copy(dayViewEndHour = v) } }, Modifier.width(HourSelectWidth),
                    )
                }
            }
            CheckSetting(
                stringResource(R.string.settings_lock_scroll), stringResource(R.string.settings_lock_scroll_summary),
                settings.lockScroll,
            ) { v -> update { it.copy(lockScroll = v) } }
            FormRow(stringResource(R.string.settings_drag_precision)) {
                FormSelect(
                    stringResource(R.string.settings_minutes, settings.dragPrecisionMinutes),
                    listOf(15, 30, 60).map { stringResource(R.string.settings_minutes, it) to it },
                    { v -> update { it.copy(dragPrecisionMinutes = v) } },
                    Modifier.width(SelectWidth),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.settings_default_color), style = formTextStyle())
                ColorPicker(settings.defaultEventColor) { v -> update { it.copy(defaultEventColor = v) } }
            }
            CheckSetting(
                stringResource(R.string.settings_background_sync), stringResource(R.string.settings_background_sync_summary),
                settings.backgroundSync,
            ) { v -> update { it.copy(backgroundSync = v) } }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DataManagement(container)

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Section(stringResource(R.string.settings_advanced))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FormButton(
                    stringResource(R.string.settings_files), onFiles, Modifier.fillMaxWidth(), icon = AppIcons.Folder,
                )
                FormHint(stringResource(R.string.settings_files_summary))
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}

private val SelectWidth = 176.dp
private val HourSelectWidth = 92.dp

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun <T> Setting(label: String, value: T, options: List<Pair<T, Int>>, onChange: (T) -> Unit) {
    FormRow(label) {
        FormSelect(
            stringResource(options.first { it.first == value }.second),
            options.map { stringResource(it.second) to it.first },
            onChange,
            Modifier.width(SelectWidth),
        )
    }
}

@Composable
private fun CheckSetting(label: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Column {
        FormCheckbox(label, checked, onChange, Modifier.fillMaxWidth(), spread = true)
        FormHint(summary)
    }
}

@Composable
private fun LanguageSetting() {
    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore(',')
    val system = stringResource(R.string.settings_language_system)
    val options = listOf(system to "") + LANGUAGES.map { it.second to it.first }
    FormRow(stringResource(R.string.settings_language)) {
        FormSelect(
            options.firstOrNull { it.second == current }?.first ?: system,
            options,
            { tag ->
                AppCompatDelegate.setApplicationLocales(
                    if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
                )
            },
            Modifier.width(SelectWidth),
        )
    }
}

/** Export and restore, ported from web-client DataManagement.vue. */
@Composable
private fun DataManagement(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val calendars by container.repository.calendars.collectAsStateWithLifecycle()
    var exportCalendar by remember { mutableStateOf("") }
    var zip by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<Pair<Uri, String>?>(null) }
    val selectedIsICal = calendars.firstOrNull { it.name == exportCalendar }?.isICal == true

    val fileName = (exportCalendar.ifEmpty { "git-calendar-data" }) + if (zip) ".zip" else ".ics"
    val createZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { exportTo(it, container, context, exportCalendar, true, scope, { b -> busy = b }) { m -> message = m } }
    }
    val createIcs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { uri ->
        uri?.let { exportTo(it, container, context, exportCalendar, false, scope, { b -> busy = b }) { m -> message = m } }
    }
    val openZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: uri.lastPathSegment.orEmpty()
            pendingRestore = uri to name
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(FormGap)) {
        Section(stringResource(R.string.data_title))
        val all = stringResource(R.string.data_all)
        FormRow(stringResource(R.string.data_export_calendar)) {
            FormSelect(
                exportCalendar.ifEmpty { all },
                listOf(all to "") + calendars.map { it.name to it.name },
                {
                    exportCalendar = it
                    // iCal subscriptions cannot be exported as a Git ZIP.
                    if (calendars.firstOrNull { c -> c.name == it }?.isICal == true) zip = false
                },
                Modifier.width(SelectWidth),
                enabled = !busy,
            )
        }
        FormRow(stringResource(R.string.data_export_format)) {
            FormSelect(
                if (zip) "ZIP" else "iCalendar",
                if (selectedIsICal) listOf("iCalendar" to false) else listOf("ZIP" to true, "iCalendar" to false),
                { zip = it },
                Modifier.width(SelectWidth),
                enabled = !busy,
            )
        }
        FormButton(
            stringResource(R.string.data_export),
            { if (zip) createZip.launch(fileName) else createIcs.launch(fileName) },
            Modifier.fillMaxWidth(),
            enabled = !busy,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FormButton(
                stringResource(R.string.data_import_zip),
                { openZip.launch(arrayOf("application/zip", "application/octet-stream")) },
                Modifier.fillMaxWidth(),
                enabled = !busy,
            )
            FormHint(stringResource(R.string.data_restore_warning), color = MaterialTheme.colorScheme.error)
        }
    }

    val restored = stringResource(R.string.data_restore_success)
    pendingRestore?.let { (uri, name) ->
        ConfirmDialog(
            stringResource(R.string.data_confirm_restore, name), stringResource(R.string.import_btn),
            onConfirm = {
                busy = true
                scope.launch {
                    try {
                        val bytes = withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                        }
                        container.core.restoreZip(bytes)
                        container.core.loadCalendars()
                        container.reloadCalendars()
                        message = restored
                    } catch (e: Exception) {
                        message = e.userMessage(context)
                    } finally {
                        busy = false
                    }
                }
            },
            onDismiss = { pendingRestore = null },
        )
    }
    message?.let { MessageDialog(it, { message = null }) }
}

private fun exportTo(
    uri: Uri,
    container: AppContainer,
    context: android.content.Context,
    calendar: String,
    zip: Boolean,
    scope: kotlinx.coroutines.CoroutineScope,
    setBusy: (Boolean) -> Unit,
    report: (String) -> Unit,
) {
    setBusy(true)
    scope.launch {
        try {
            val bytes = if (zip) container.core.exportZip(calendar) else container.core.exportICal(calendar)
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            }
            report(context.getString(R.string.data_export_success, uri.lastPathSegment.orEmpty()))
        } catch (e: Exception) {
            report(e.userMessage(context, calendar))
        } finally {
            setBusy(false)
        }
    }
}
