package dev.jotalac.ui.event

import android.Manifest
import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.VideoCall
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jotalac.R
import dev.jotalac.core.UpdateStrategy
import dev.jotalac.data.EventLocation
import dev.jotalac.data.Frequency
import dev.jotalac.data.RepeatEnd
import dev.jotalac.data.Settings
import dev.jotalac.ui.common.ConfirmDialog
import dev.jotalac.ui.common.FieldAction
import dev.jotalac.ui.common.FormButton
import dev.jotalac.ui.common.FormButtons
import dev.jotalac.ui.common.FormCheckbox
import dev.jotalac.ui.common.FormChip
import dev.jotalac.ui.common.FormDialog
import dev.jotalac.ui.common.FormGap
import dev.jotalac.ui.common.FormHint
import dev.jotalac.ui.common.FormIconButton
import dev.jotalac.ui.common.FormSelect
import dev.jotalac.ui.common.FormTextField
import dev.jotalac.ui.common.FormToggle
import dev.jotalac.ui.common.FormValue
import dev.jotalac.ui.common.MessageDialog
import dev.jotalac.ui.common.currentLocale
import dev.jotalac.ui.common.formTextStyle
import dev.jotalac.ui.common.rememberTimeFormatter
import dev.jotalac.ui.common.shortName
import dev.jotalac.ui.common.use24Hour
import dev.jotalac.ui.common.userMessage
import dev.jotalac.ui.common.weekDays
import dev.jotalac.ui.theme.TagColor
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Width of the "From:" and "To:" labels, so the date and time fields line up. */
private val DateLabelWidth = 44.dp

/** Indent of the repeat end and reminder rows under their headings, like the web client on a phone. */
private val SubRowIndent = 24.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditorScreen(vm: EventEditorViewModel, settings: Settings, onClose: () -> Unit) {
    val context = LocalContext.current
    val done by vm.done.collectAsStateWithLifecycle()
    LaunchedEffect(done) { if (done) onClose() }
    var confirmDelete by remember { mutableStateOf(false) }
    var customDialog by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val enabled = !vm.readonly && !vm.saving

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when {
                                vm.readonly -> R.string.event_view
                                vm.isNew -> R.string.event_new
                                else -> R.string.event_edit
                            }
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, stringResource(R.string.close)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FormGap, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(FormGap),
        ) {
            if (vm.readonly) FormHint(stringResource(R.string.event_readonly))
            FormTextField(
                value = vm.title,
                onValueChange = { vm.title = it; vm.error = null },
                placeholder = stringResource(R.string.event_title),
                enabled = enabled,
                isError = vm.error == R.string.error_missing_title,
                textStyle = formTextStyle().copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DateTimeRow(stringResource(R.string.event_from), vm.fromDate, vm.fromTime, vm.allDay, enabled, settings,
                    onDate = { d ->
                        // Keep the duration when the start date moves, like most calendar apps.
                        val shift = java.time.temporal.ChronoUnit.DAYS.between(vm.fromDate, d)
                        vm.fromDate = d
                        vm.toDate = vm.toDate.plusDays(shift)
                    },
                    onTime = { vm.fromTime = it })
                DateTimeRow(stringResource(R.string.event_to), vm.toDate, vm.toTime, vm.allDay, enabled, settings,
                    onDate = { vm.toDate = it }, onTime = { vm.toTime = it },
                    isError = vm.error == R.string.error_bad_to_date)
            }
            FormCheckbox(stringResource(R.string.event_entire_day), vm.allDay, { vm.allDay = it }, enabled = enabled)

            RepeatFields(vm, enabled) { customDialog = true }
            ReminderFields(vm, enabled) {
                if (vm.addReminder()) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            CalendarAndTag(vm, enabled)
            LocationField(vm, enabled)
            FormTextField(
                value = vm.description,
                onValueChange = { vm.description = it },
                placeholder = stringResource(R.string.event_description),
                singleLine = false,
                minLines = 3,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )

            if (!vm.readonly) {
                FormButtons {
                    FormButton(stringResource(R.string.save), vm::save, enabled = enabled)
                    if (!vm.isNew) {
                        FormButton(stringResource(R.string.delete), { confirmDelete = true }, enabled = enabled, danger = true)
                    }
                }
            }
            Spacer(Modifier.size(16.dp))
        }
    }

    vm.error?.let { MessageDialog(stringResource(it), { vm.error = null }) }
    vm.failure?.let { MessageDialog(it.userMessage(context, vm.calendar), { vm.failure = null }) }
    if (confirmDelete) {
        ConfirmDialog(stringResource(R.string.event_delete_confirm), stringResource(R.string.delete), vm::delete, danger = true) {
            confirmDelete = false
        }
    }
    vm.strategyPrompt?.let {
        StrategyDialog(onPick = vm::applyStrategy, onDismiss = { vm.strategyPrompt = null })
    }
    if (customDialog) CustomRepeatDialog(vm) { customDialog = false }
}

// ---------------------------------------- date & time ------------------------------------------

@Composable
private fun mediumDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(currentLocale()))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeRow(
    label: String,
    date: LocalDate,
    time: LocalTime,
    allDay: Boolean,
    enabled: Boolean,
    settings: Settings,
    onDate: (LocalDate) -> Unit,
    onTime: (LocalTime) -> Unit,
    isError: Boolean = false,
) {
    val locale = currentLocale()
    val times = rememberTimeFormatter(settings)
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("$label:", Modifier.width(DateLabelWidth), style = formTextStyle())
        FormValue(
            "${mediumDate(date)} (${date.dayOfWeek.shortName(locale)})",
            { pickDate = true },
            Modifier.weight(1f),
            enabled = enabled,
            isError = isError,
        )
        if (!allDay) {
            FormValue(times.time(time), { pickTime = true }, Modifier.width(96.dp), enabled = enabled, isError = isError)
        }
    }

    if (pickDate) {
        DatePickerFor(date, onDismiss = { pickDate = false }, onPick = onDate)
    }
    if (pickTime) {
        val context = LocalContext.current
        val state = rememberTimePickerState(time.hour, time.minute, use24Hour(context, settings))
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = { onTime(LocalTime.of(state.hour, state.minute)); pickTime = false }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(stringResource(R.string.cancel)) } },
            text = { TimePicker(state) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerFor(date: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) { DatePicker(state) }
}

// ------------------------------------------- repeat --------------------------------------------

@Composable
private fun RepeatFields(vm: EventEditorViewModel, enabled: Boolean, openCustom: () -> Unit) {
    var pickEnd by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${stringResource(R.string.repeat)}:", style = formTextStyle())
            FormSelect(
                selected = stringResource(vm.repeatMode.label),
                options = RepeatMode.entries.map { stringResource(it.label) to it },
                onSelect = { mode ->
                    vm.selectRepeat(mode)
                    if (mode == RepeatMode.Custom) openCustom()
                },
                modifier = Modifier.weight(1f),
                enabled = enabled,
            )
            if (vm.repeatMode == RepeatMode.Custom) {
                FormButton(stringResource(R.string.repeat_edit), openCustom, enabled = enabled)
            }
        }
        if (vm.repeatMode == RepeatMode.Custom) {
            FormHint(customSummary(vm), Modifier.padding(start = SubRowIndent))
        } else if (vm.repeatMode != RepeatMode.Never) {
            Row(
                Modifier.padding(start = SubRowIndent),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("${stringResource(R.string.repeat_end)}:", style = formTextStyle())
                FormSelect(
                    selected = stringResource(vm.endMode.label),
                    options = EndMode.entries.map { stringResource(it.label) to it },
                    onSelect = { vm.repeatTouched = true; vm.endMode = it },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                )
                when (vm.endMode) {
                    EndMode.Never -> {}
                    EndMode.On -> FormValue(mediumDate(vm.endDate), { pickEnd = true }, Modifier.weight(1f), enabled = enabled)
                    EndMode.After -> CountField(vm.count, enabled) { vm.repeatTouched = true; vm.count = it }
                }
            }
        }
    }
    if (pickEnd) DatePickerFor(vm.endDate, { pickEnd = false }) { vm.repeatTouched = true; vm.endDate = it }
}

@Composable
private fun customSummary(vm: EventEditorViewModel): String {
    val rule = vm.custom
    val locale = currentLocale()
    val unit = stringResource(rule.frequency.unitLabel)
    val days = if (rule.frequency == Frequency.WEEKLY) {
        " · " + weekDays(java.time.DayOfWeek.MONDAY).filter { it in rule.weekdays }.joinToString { it.shortName(locale) }
    } else ""
    val end = when (val e = rule.end) {
        RepeatEnd.Never -> ""
        is RepeatEnd.After -> " · ${stringResource(R.string.repeat_end_after)} ${e.count} ${stringResource(R.string.repeat_times)}"
        is RepeatEnd.On -> " · ${stringResource(R.string.repeat_end_on)} " +
            e.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    }
    return "${stringResource(R.string.repeat_every)} ${rule.interval} $unit$days$end"
}

private val Frequency.unitLabel: Int
    get() = when (this) {
        Frequency.DAILY -> R.string.repeat_unit_day
        Frequency.WEEKLY -> R.string.repeat_unit_week
        Frequency.MONTHLY -> R.string.repeat_unit_month
        Frequency.YEARLY -> R.string.repeat_unit_year
    }

@Composable
private fun CountField(value: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FormTextField(
            value = text,
            onValueChange = { t ->
                text = t.filter(Char::isDigit).take(4)
                onChange(text.toIntOrNull() ?: 0)
            },
            placeholder = "",
            enabled = enabled,
            isError = value < 1,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(56.dp),
        )
        Text(stringResource(R.string.repeat_times), style = formTextStyle())
    }
}

/** Custom recurrence editor, ported from web-client RepeatModal.vue. */
@Composable
private fun CustomRepeatDialog(vm: EventEditorViewModel, onDismiss: () -> Unit) {
    val locale = currentLocale()
    var rule by remember { mutableStateOf(vm.custom) }
    var endMode by remember {
        mutableStateOf(
            when (rule.end) {
                RepeatEnd.Never -> EndMode.Never
                is RepeatEnd.After -> EndMode.After
                is RepeatEnd.On -> EndMode.On
            }
        )
    }
    var count by remember { mutableStateOf((rule.end as? RepeatEnd.After)?.count ?: 5) }
    var until by remember { mutableStateOf((rule.end as? RepeatEnd.On)?.date ?: vm.fromDate.plusWeeks(1)) }
    var pickUntil by remember { mutableStateOf(false) }
    val canSave = rule.interval >= 1 && (rule.frequency != Frequency.WEEKLY || rule.weekdays.isNotEmpty()) &&
        (endMode != EndMode.After || count >= 1)

    FormDialog(onDismiss, stringResource(R.string.repeat_custom)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.repeat_every), style = formTextStyle())
            var text by remember { mutableStateOf(rule.interval.toString()) }
            FormTextField(
                value = text,
                onValueChange = { t ->
                    text = t.filter(Char::isDigit).take(3)
                    rule = rule.copy(interval = text.toIntOrNull() ?: 0)
                },
                placeholder = "",
                isError = rule.interval < 1,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(56.dp),
            )
            FormSelect(
                selected = stringResource(rule.frequency.unitLabel),
                options = Frequency.entries.map { stringResource(it.unitLabel) to it },
                onSelect = { f ->
                    rule = rule.copy(
                        frequency = f,
                        weekdays = if (f == Frequency.WEEKLY && rule.weekdays.isEmpty()) setOf(vm.fromDate.dayOfWeek) else rule.weekdays,
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }
        if (rule.frequency == Frequency.WEEKLY) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                weekDays(java.time.DayOfWeek.MONDAY).forEach { day ->
                    val on = day in rule.weekdays
                    FormChip(
                        day.shortName(locale),
                        on,
                        { rule = rule.copy(weekdays = if (on) rule.weekdays - day else rule.weekdays + day) },
                        Modifier.weight(1f),
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${stringResource(R.string.repeat_ends)}:", style = formTextStyle())
            FormToggle(EndMode.entries.map { stringResource(it.label) to it }, endMode, { endMode = it }, Modifier.fillMaxWidth())
            when (endMode) {
                EndMode.Never -> {}
                EndMode.After -> CountField(count, true) { count = it }
                EndMode.On -> FormValue(mediumDate(until), { pickUntil = true }, Modifier.fillMaxWidth())
            }
        }
        FormButtons {
            FormButton(
                stringResource(R.string.save),
                {
                    vm.repeatTouched = true
                    vm.custom = rule.copy(
                        end = when (endMode) {
                            EndMode.Never -> RepeatEnd.Never
                            EndMode.After -> RepeatEnd.After(count)
                            EndMode.On -> RepeatEnd.On(until)
                        },
                    )
                    onDismiss()
                },
                enabled = canSave,
            )
            FormButton(stringResource(R.string.cancel), onDismiss)
        }
    }
    if (pickUntil) DatePickerFor(until, { pickUntil = false }) { until = it }
}

// ------------------------------------------ reminders ------------------------------------------

@Composable
fun reminderLabel(minutes: Long): String = when {
    minutes == 0L -> stringResource(R.string.reminder_at_start)
    minutes % 1440 == 0L -> pluralStringResource(R.plurals.reminder_days_before, (minutes / 1440).toInt(), (minutes / 1440).toInt())
    minutes % 60 == 0L -> pluralStringResource(R.plurals.reminder_hours_before, (minutes / 60).toInt(), (minutes / 60).toInt())
    else -> pluralStringResource(R.plurals.reminder_minutes_before, minutes.toInt(), minutes.toInt())
}

@Composable
private fun ReminderFields(vm: EventEditorViewModel, enabled: Boolean, onAdd: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.reminders), style = formTextStyle())
            if (enabled && vm.reminders.size < REMINDER_PRESETS.size) {
                FormIconButton(Icons.Outlined.Add, stringResource(R.string.reminder_add), onAdd)
            }
        }
        vm.reminders.toList().forEachIndexed { index, minutes ->
            Row(
                Modifier.padding(start = SubRowIndent),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Presets used by other rows are not offered again; a non-preset value from imported data stays visible.
                val options = (REMINDER_PRESETS.sorted() + minutes).distinct().sorted()
                    .filter { it == minutes || it !in vm.reminders }
                FormSelect(
                    selected = reminderLabel(minutes),
                    options = options.map { reminderLabel(it) to it },
                    onSelect = { vm.reminders[index] = it },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                )
                if (enabled) {
                    FormButton(stringResource(R.string.reminder_remove), { vm.reminders.removeAt(index) })
                }
            }
        }
    }
}

// --------------------------------------- calendar & tag ----------------------------------------

@Composable
private fun CalendarAndTag(vm: EventEditorViewModel, enabled: Boolean) {
    val calendars = vm.calendars
    val tags = vm.tagsOf(vm.calendar)
    val untagged = stringResource(R.string.tag_untagged)
    val selectedTag = tags.firstOrNull { it.id == vm.tagId }
    Row(horizontalArrangement = Arrangement.spacedBy(FormGap)) {
        FormSelect(
            selected = vm.calendar,
            options = calendars.map { it.name to it.name },
            onSelect = { vm.selectCalendar(it) },
            modifier = Modifier.weight(1f),
            enabled = enabled,
            disabled = calendars.filter { !it.isWritable }.map { it.name }.toSet(),
        )
        FormSelect(
            selected = selectedTag?.name ?: untagged,
            options = listOf(untagged to "") + tags.map { it.name to it.id.orEmpty() },
            onSelect = { vm.tagId = it.ifEmpty { null } },
            modifier = Modifier.weight(1f),
            enabled = enabled,
            leading = selectedTag?.let { TagColor.of(it.color).color },
        )
    }
}

// ------------------------------------------ strategy -------------------------------------------

/** Which occurrences of a repeating event to change, ported from web-client StrategyModal.vue. */
@Composable
fun StrategyDialog(onPick: (UpdateStrategy) -> Unit, onDismiss: () -> Unit) {
    FormDialog(onDismiss, stringResource(R.string.strategy_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                Triple(UpdateStrategy.All, R.string.strategy_all, R.string.strategy_all_body),
                Triple(UpdateStrategy.Following, R.string.strategy_following, R.string.strategy_following_body),
                Triple(UpdateStrategy.Current, R.string.strategy_current, R.string.strategy_current_body),
            ).forEach { (strategy, title, body) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { onPick(strategy) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StrategyIcon(strategy)
                    Column {
                        Text(stringResource(title), style = formTextStyle())
                        FormHint(stringResource(body))
                    }
                }
            }
        }
        FormButtons { FormButton(stringResource(R.string.cancel), onDismiss) }
    }
}

/** Five occurrence blocks with the affected ones filled, like web-client StrategySVG.vue. */
@Composable
private fun StrategyIcon(strategy: UpdateStrategy) {
    val filled = when (strategy) {
        UpdateStrategy.All -> 0..4
        UpdateStrategy.Following -> 2..4
        UpdateStrategy.Current -> 2..2
    }
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 0..4) {
            Box(
                Modifier
                    .size(width = 6.dp, height = 18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (i in filled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    ),
            )
        }
    }
}

/**
 * Location with suggestions from earlier events and the address search, and a button that opens the
 * place in the maps app or joins a meeting link. The button also works on read-only events.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationField(vm: EventEditorViewModel, enabled: Boolean) {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    // the text the menu was closed at (picked or dismissed); it opens again once the text changes
    var closedAt by remember { mutableStateOf<String?>(null) }
    val past = EventLocation.matching(vm.pastLocations, vm.location, 4)
    val addresses = vm.addressSuggestions
        .filter { address -> past.none { it.equals(address, ignoreCase = true) } && !address.equals(vm.location.trim(), ignoreCase = true) }
        .take(4)
    val expanded = enabled && focused && closedAt != vm.location && (past.isNotEmpty() || addresses.isNotEmpty())
    val isLink = EventLocation.meetingUrl(vm.location) != null

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = {}) {
        FormTextField(
            value = vm.location,
            onValueChange = vm::editLocation,
            placeholder = stringResource(R.string.event_location),
            enabled = enabled,
            trailing = if (vm.location.isBlank()) null else {
                {
                    FieldAction(
                        if (isLink) Icons.Outlined.VideoCall else Icons.Outlined.Map,
                        stringResource(if (isLink) R.string.location_join else R.string.location_open_map),
                    ) {
                        val intent = EventLocation.intent(vm.location) ?: return@FieldAction
                        try {
                            context.startActivity(intent)
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, R.string.location_no_app, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled)
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { closedAt = vm.location },
            shape = MaterialTheme.shapes.small,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            past.forEach { suggestion ->
                LocationSuggestion(suggestion, Icons.Outlined.History) {
                    vm.pickLocation(suggestion)
                    closedAt = suggestion
                }
            }
            addresses.forEach { suggestion ->
                LocationSuggestion(suggestion, Icons.Outlined.Place) {
                    vm.pickLocation(suggestion)
                    closedAt = suggestion
                }
            }
        }
    }
}

@Composable
private fun LocationSuggestion(text: String, icon: ImageVector, onPick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, style = formTextStyle(), maxLines = 2) },
        leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
        onClick = onPick,
    )
}
