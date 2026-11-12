package dev.jotalac.ui.calendars

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.jotalac.AppContainer
import dev.jotalac.R
import dev.jotalac.core.Calendar
import dev.jotalac.data.RemoteUrl
import dev.jotalac.ui.common.ConfirmDialog
import dev.jotalac.ui.common.FieldAction
import dev.jotalac.ui.common.FormButton
import dev.jotalac.ui.common.FormButtons
import dev.jotalac.ui.common.FormCheckbox
import dev.jotalac.ui.common.FormGap
import dev.jotalac.ui.common.FormHint
import dev.jotalac.ui.common.FormSelect
import dev.jotalac.ui.common.FormTextField
import dev.jotalac.ui.common.FormToggle
import dev.jotalac.ui.common.MessageDialog
import dev.jotalac.ui.common.formTextStyle
import dev.jotalac.ui.theme.TagColor
import androidx.compose.material3.TopAppBarDefaults
import dev.jotalac.ui.common.userMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CreateMode(val label: Int) {
    Init(R.string.calendar_mode_init), Clone(R.string.calendar_mode_clone), ICal(R.string.calendar_mode_ical)
}

enum class ICalSource(val label: Int) { Url(R.string.calendar_ical_url), File(R.string.calendar_ical_file) }

/** Create, edit or remove a calendar. Ported from web-client CalendarModal.vue. */
class CalendarEditorViewModel(private val container: AppContainer, editName: String?) : ViewModel() {
    val existing: Calendar? = editName?.let { container.repository.calendar(it) }
    val isNew get() = existing == null
    val writableCalendars get() = container.repository.calendars.value.filter { it.isWritable }

    var mode by mutableStateOf(CreateMode.Init)
    var icalSource by mutableStateOf(ICalSource.Url)
    var name by mutableStateOf(existing?.name.orEmpty())
    var url by mutableStateOf("")
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var encrypted by mutableStateOf(false)
    var encryptionKey by mutableStateOf("")
    var readonly by mutableStateOf(existing?.readonly ?: false)
    var destination by mutableStateOf(writableCalendars.firstOrNull()?.name.orEmpty())
    var destinationTag by mutableStateOf("")
    var file by mutableStateOf<Uri?>(null)
    var fileName by mutableStateOf("")

    var error by mutableStateOf<Int?>(null)
    var failure by mutableStateOf<Throwable?>(null)
    var busy by mutableStateOf(false)
    val done = MutableStateFlow(false)

    /** URL and credentials as stored before editing, to tell whether the remote changed. */
    private val originalRemote = existing?.let { if (it.isICal) it.icalUrl else it.remoteUrl }.orEmpty()

    init {
        existing?.let { cal ->
            if (cal.isICal) {
                mode = CreateMode.ICal
                url = cal.icalUrl
            } else {
                val auth = RemoteUrl.split(cal.remoteUrl)
                url = auth.url
                username = auth.username
                password = auth.password
            }
        }
    }

    val isICalFile get() = isNew && mode == CreateMode.ICal && icalSource == ICalSource.File
    val isICalUrl get() = mode == CreateMode.ICal && (!isNew || icalSource == ICalSource.Url)

    fun tagsOf(calendar: String) = container.repository.calendar(calendar)?.tagList.orEmpty()

    private fun validate(): Boolean {
        name = name.trim()
        url = url.trim()
        fun fail(message: Int) = false.also { error = message }

        if (isICalFile) {
            if (writableCalendars.isEmpty()) return fail(R.string.no_writable_calendars)
            if (file == null) return fail(R.string.error_ical_file_required)
            if (writableCalendars.none { it.name == destination }) return fail(R.string.error_ical_destination_required)
            return true
        }
        if (!(isNew && mode == CreateMode.Clone) && name.isEmpty()) return fail(R.string.error_calendar_name_required)
        if (url.isEmpty()) {
            if (isNew && mode == CreateMode.Clone) return fail(R.string.error_remote_url_required)
            if (isICalUrl) return fail(R.string.error_ical_url_required)
            if (isNew && encrypted && encryptionKey.isEmpty()) return fail(R.string.error_encryption_key_required)
            return true
        }
        if (RemoteUrl.parse(url) == null) return fail(R.string.error_invalid_url)
        if (!isICalUrl && !RemoteUrl.isGitRepo(url)) return fail(R.string.error_remote_url_end_dot_git)
        if (isNew && encrypted && encryptionKey.isEmpty()) return fail(R.string.error_encryption_key_required)
        return true
    }

    fun submit(readFile: suspend (Uri) -> String) {
        if (busy || !validate()) return
        busy = true
        viewModelScope.launch {
            try {
                val core = container.core
                val key = if (encrypted) encryptionKey else ""
                val remote = RemoteUrl.withAuth(url, username, password)
                val cal = existing
                when {
                    cal != null -> update(cal, remote)
                    isICalFile -> {
                        core.importICalFile(destination, destinationTag.ifEmpty { null }, readFile(file!!))
                        container.sync.requestSync()
                    }
                    mode == CreateMode.ICal -> core.importICalUrl(name, url)
                    mode == CreateMode.Clone -> core.cloneCalendar(remote, key, readonly)
                    else -> {
                        core.createCalendar(name, key)
                        if (url.isNotEmpty()) core.updateRemote(name, remote, false)
                    }
                }
                if (mode != CreateMode.ICal || isICalFile) core.loadCalendars()
                container.reloadCalendars()
                done.value = true
            } catch (e: Exception) {
                failure = e
                runCatching { container.reloadCalendars() }
            } finally {
                busy = false
            }
        }
    }

    private suspend fun update(cal: Calendar, remote: String) {
        val core = container.core
        var current = cal.name
        if (name != cal.name) {
            core.renameCalendar(cal.name, name)
            container.settings.renameCalendarReferences(cal.name, name)
            current = name
        }
        if (cal.isICal) {
            if (url != cal.icalUrl) core.updateICalUrl(current, url)
            return
        }
        val newRemote = if (url.isEmpty()) "" else remote
        if (newRemote != originalRemote || readonly != cal.readonly) {
            core.updateRemote(current, newRemote, url.isNotEmpty() && readonly)
            if (newRemote.isNotEmpty()) container.sync.sync()
        }
    }

    fun delete() {
        val cal = existing ?: return
        busy = true
        viewModelScope.launch {
            try {
                container.core.removeCalendar(cal.name)
                container.core.loadCalendars()
                container.reloadCalendars()
                done.value = true
            } catch (e: Exception) {
                failure = e
            } finally {
                busy = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarEditorScreen(vm: CalendarEditorViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val done by vm.done.collectAsStateWithLifecycle()
    LaunchedEffect(done) { if (done) onClose() }
    var confirmDelete by remember { mutableStateOf(false) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.file = uri
            vm.fileName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: uri.lastPathSegment.orEmpty()
        }
    }
    val readFile: suspend (Uri) -> String = { uri ->
        withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }.orEmpty()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (vm.isNew) stringResource(R.string.create_calendar)
                        else stringResource(R.string.edit_calendar, vm.existing?.name.orEmpty())
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
            if (vm.isNew) {
                FormToggle(
                    CreateMode.entries.map { stringResource(it.label) to it }, vm.mode, { vm.mode = it }, Modifier.fillMaxWidth(),
                )
                if (vm.mode == CreateMode.ICal) {
                    FormToggle(
                        ICalSource.entries.map { stringResource(it.label) to it }, vm.icalSource, { vm.icalSource = it },
                        Modifier.fillMaxWidth(),
                    )
                }
                FormHint(
                    stringResource(
                        when {
                            vm.isICalFile -> R.string.calendar_file_import_description
                            vm.mode == CreateMode.ICal -> R.string.calendar_url_import_description
                            vm.mode == CreateMode.Clone -> R.string.calendar_clone_description
                            else -> R.string.calendar_init_description
                        }
                    ),
                )
            }

            if (vm.isICalFile) {
                ICalFileFields(vm) { pickFile.launch(arrayOf("text/calendar", "application/octet-stream", "*/*")) }
            } else {
                if (!(vm.isNew && vm.mode == CreateMode.Clone)) {
                    Field(vm.name, { vm.name = it }, stringResource(R.string.calendar_name))
                }
                val urlLabel = if (vm.isICalUrl) R.string.calendar_ical else R.string.calendar_git_repository
                Field(
                    vm.url, { vm.url = it },
                    stringResource(urlLabel) + if (vm.isNew && vm.mode == CreateMode.Init) " (${stringResource(R.string.optional)})" else "",
                    keyboard = KeyboardType.Uri,
                )
                if (!vm.isICalUrl) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "${stringResource(R.string.calendar_git_credentials)} (${stringResource(R.string.optional)})",
                            style = formTextStyle(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(FormGap)) {
                            Field(vm.username, { vm.username = it }, stringResource(R.string.calendar_username), Modifier.weight(1f))
                            SecretField(vm.password, { vm.password = it }, stringResource(R.string.calendar_password), Modifier.weight(1f))
                        }
                    }
                    if ((vm.isNew && vm.mode == CreateMode.Clone) || (!vm.isNew && vm.url.isNotBlank())) {
                        FormCheckbox(stringResource(R.string.calendar_readonly), vm.readonly, { vm.readonly = it })
                    }
                    if (vm.isNew) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            FormCheckbox(stringResource(R.string.calendar_encrypted), vm.encrypted, { vm.encrypted = it })
                            if (vm.encrypted) {
                                SecretField(vm.encryptionKey, { vm.encryptionKey = it }, stringResource(R.string.calendar_encryption_key))
                            }
                        }
                    } else if (vm.existing?.encrypted == true) {
                        FormHint(stringResource(R.string.calendar_is_encrypted))
                    }
                }
            }

            FormButtons {
                FormButton(
                    stringResource(
                        when {
                            !vm.isNew -> R.string.save
                            vm.isICalFile -> R.string.import_btn
                            else -> R.string.create
                        }
                    ),
                    { vm.submit(readFile) },
                    enabled = !vm.busy && !(vm.isICalFile && vm.writableCalendars.isEmpty()),
                )
                if (!vm.isNew) {
                    FormButton(stringResource(R.string.delete), { confirmDelete = true }, enabled = !vm.busy, danger = true)
                }
            }
            Spacer(Modifier.size(16.dp))
        }
    }

    vm.error?.let { MessageDialog(stringResource(it), { vm.error = null }) }
    vm.failure?.let { MessageDialog(it.userMessage(context, vm.name), { vm.failure = null }) }
    if (confirmDelete) {
        ConfirmDialog(stringResource(R.string.confirm_calendar_delete), stringResource(R.string.delete), vm::delete, danger = true) {
            confirmDelete = false
        }
    }
}

@Composable
private fun ICalFileFields(vm: CalendarEditorViewModel, pick: () -> Unit) {
    if (vm.writableCalendars.isEmpty()) {
        FormHint(stringResource(R.string.no_writable_calendars), color = MaterialTheme.colorScheme.error)
        return
    }
    FormButton(
        vm.fileName.ifEmpty { stringResource(R.string.calendar_choose_file) }, pick, Modifier.fillMaxWidth(),
        icon = Icons.Outlined.FileOpen,
    )
    val untagged = stringResource(R.string.tag_untagged)
    val tags = vm.tagsOf(vm.destination)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("${stringResource(R.string.calendar_destination)}:", style = formTextStyle())
        Row(horizontalArrangement = Arrangement.spacedBy(FormGap)) {
            FormSelect(
                vm.destination,
                vm.writableCalendars.map { it.name to it.name },
                {
                    vm.destination = it
                    vm.destinationTag = ""
                },
                Modifier.weight(1f),
            )
            val tag = tags.firstOrNull { it.id == vm.destinationTag }
            FormSelect(
                tag?.name ?: untagged,
                listOf(untagged to "") + tags.map { it.name to it.id.orEmpty() },
                { vm.destinationTag = it },
                Modifier.weight(1f),
                leading = tag?.let { TagColor.of(it.color).color },
            )
        }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboard: KeyboardType = KeyboardType.Text,
) {
    FormTextField(
        value = value,
        onValueChange = onChange,
        placeholder = placeholder,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, autoCorrectEnabled = false),
        modifier = modifier,
    )
}

@Composable
private fun SecretField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier.fillMaxWidth()) {
    var visible by remember { mutableStateOf(false) }
    FormTextField(
        value = value,
        onValueChange = onChange,
        placeholder = placeholder,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        trailing = {
            FieldAction(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null) { visible = !visible }
        },
        modifier = modifier,
    )
}
