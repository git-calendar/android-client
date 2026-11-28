package dev.jotalac.ui.calendar

import dev.jotalac.ui.theme.AppIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.jotalac.R
import dev.jotalac.core.Tag
import dev.jotalac.ui.common.FieldHeight
import dev.jotalac.ui.common.FormSelect
import dev.jotalac.ui.common.LocalDisabledAlpha
import dev.jotalac.ui.common.FormButton
import dev.jotalac.ui.common.FormButtons
import dev.jotalac.ui.common.FormDialog
import dev.jotalac.ui.common.FormHint
import dev.jotalac.ui.common.FormTextField
import dev.jotalac.ui.common.formTextStyle
import dev.jotalac.ui.theme.TagColor
import dev.jotalac.ui.theme.contentColorOn

/** Create or edit a tag, or the untagged color of a calendar. Ported from web-client TagModal.vue. */
@Composable
fun TagDialog(
    target: TagEditTarget,
    defaultColor: String,
    /** Tags of the same calendar that the events of a deleted tag can be moved to. */
    otherTags: List<Tag>,
    untaggedColor: String,
    onSave: (name: String, color: String) -> Unit,
    onDelete: (deleteEvents: Boolean, moveToId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val untagged = target.tag?.id == ""
    var name by rememberSaveable { mutableStateOf(target.tag?.name.orEmpty()) }
    var color by rememberSaveable { mutableStateOf(target.tag?.color ?: if (untagged) defaultColor else "blue") }
    var nameError by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }

    if (confirmingDelete && target.tag != null) {
        DeleteTagDialog(target.tag, otherTags, untaggedColor, onDelete = { deleteEvents, moveToId ->
            onDelete(deleteEvents, moveToId)
            onDismiss()
        }, onCancel = { confirmingDelete = false }, onDismiss = onDismiss)
        return
    }

    FormDialog(
        onDismiss,
        if (target.tag == null) stringResource(R.string.create_tag, target.calendar)
        else stringResource(R.string.edit_tag, target.tag.name),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FormTextField(
                value = if (untagged) stringResource(R.string.tag_untagged) else name,
                onValueChange = { name = it; nameError = false },
                placeholder = stringResource(R.string.tag_name),
                enabled = !untagged,
                isError = nameError,
                modifier = Modifier.fillMaxWidth(),
            )
            if (nameError) FormHint(stringResource(R.string.error_tag_name_required), color = MaterialTheme.colorScheme.error)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${stringResource(R.string.tag_color)}:", style = formTextStyle())
            ColorPicker(color) { color = it }
        }
        FormButtons {
            FormButton(stringResource(R.string.save), {
                if (!untagged && name.isBlank()) {
                    nameError = true
                } else {
                    onSave(name.trim(), color)
                    onDismiss()
                }
            })
            FormButton(stringResource(R.string.close), onDismiss)
            if (target.tag != null && !untagged) {
                FormButton(stringResource(R.string.delete), { confirmingDelete = true }, danger = true)
            }
        }
    }
}

/** Asks what happens to the events of a deleted tag: moved to another tag (or untagged), or deleted. */
@Composable
private fun DeleteTagDialog(
    tag: Tag,
    otherTags: List<Tag>,
    untaggedColor: String,
    onDelete: (deleteEvents: Boolean, moveToId: String) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    var deleteEvents by rememberSaveable { mutableStateOf(false) }
    var moveToId by rememberSaveable { mutableStateOf("") }

    val untaggedLabel = stringResource(R.string.tag_untagged)
    val options = listOf(untaggedLabel to "") + otherTags.mapNotNull { t -> t.id?.let { t.name to it } }
    val moveTo = otherTags.find { it.id == moveToId }

    FormDialog(onDismiss) {
        Text(stringResource(R.string.tag_delete_events_question, tag.name), style = formTextStyle())
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FormRadio(!deleteEvents, { deleteEvents = false }) {
                Text("${stringResource(R.string.tag_move_to)}:", style = formTextStyle())
                FormSelect(
                    moveTo?.name ?: untaggedLabel,
                    options,
                    { moveToId = it },
                    Modifier.weight(1f),
                    enabled = !deleteEvents,
                    leading = TagColor.of(moveTo?.color ?: untaggedColor).color,
                )
            }
            FormRadio(deleteEvents, { deleteEvents = true }) {
                Text(stringResource(R.string.tag_delete_events), style = formTextStyle())
            }
        }
        FormButtons {
            FormButton(stringResource(R.string.delete), { onDelete(deleteEvents, if (deleteEvents) "" else moveToId) }, danger = true)
            FormButton(stringResource(R.string.cancel), onCancel)
        }
    }
}

/** A round radio dot followed by its content, like the web client's radio labels. */
@Composable
private fun FormRadio(selected: Boolean, onSelect: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = FieldHeight)
            .selectable(selected, role = Role.RadioButton, onClick = onSelect),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(colors.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(colors.onSurface))
        }
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TagColor.entries.forEach { option ->
            val label = stringResource(option.label)
            Box(
                Modifier
                    .size(32.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(option.color)
                    .then(
                        if (option.id == selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, MaterialTheme.shapes.small)
                        else Modifier
                    )
                    .clickable(role = Role.RadioButton) { onSelect(option.id) }
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                if (option.id == selected) Icon(AppIcons.Check, null, Modifier.size(18.dp), tint = contentColorOn(option.color))
            }
        }
        Spacer(Modifier.width(1.dp))
    }
}
