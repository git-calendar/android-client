package dev.jotalac.ui.calendar

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.jotalac.R
import dev.jotalac.core.Calendar
import dev.jotalac.core.Tag
import dev.jotalac.data.HiddenTags
import dev.jotalac.data.Settings
import dev.jotalac.ui.event.DatePickerFor
import dev.jotalac.ui.theme.TagColor
import java.time.LocalDate

class DrawerActions(
    val pickDate: (LocalDate) -> Unit,
    val createCalendar: () -> Unit,
    val editCalendar: (Calendar) -> Unit,
    val editTag: (TagEditTarget) -> Unit,
    val toggleTag: (calendar: String, tagId: String?, hidden: Boolean) -> Unit,
    val settings: () -> Unit,
    val guide: () -> Unit,
    val sourceCode: () -> Unit,
)

@Composable
fun DrawerContent(
    date: LocalDate,
    settings: Settings,
    calendars: List<Calendar>,
    hidden: HiddenTags,
    actions: DrawerActions,
) {
    var pickingDate by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(40.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
        }
        NavigationDrawerItem(
            shape = MaterialTheme.shapes.small,
            label = { Text(stringResource(R.string.go_to_date)) },
            icon = { Icon(Icons.Outlined.EditCalendar, null) },
            selected = false,
            onClick = { pickingDate = true },
        )
        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
            Text(
                stringResource(R.string.calendars_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = actions.createCalendar) {
                Icon(Icons.Outlined.Add, stringResource(R.string.create_calendar))
            }
        }
        calendars.forEach { CalendarItem(it, settings, hidden, actions) }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        NavigationDrawerItem(
            shape = MaterialTheme.shapes.small,
            label = { Text(stringResource(R.string.settings)) },
            icon = { Icon(Icons.Outlined.Settings, null) },
            selected = false,
            onClick = actions.settings,
        )
        NavigationDrawerItem(
            shape = MaterialTheme.shapes.small,
            label = { Text(stringResource(R.string.guide)) },
            icon = { Icon(Icons.AutoMirrored.Outlined.HelpOutline, null) },
            selected = false,
            onClick = actions.guide,
        )
        NavigationDrawerItem(
            shape = MaterialTheme.shapes.small,
            label = { Text(stringResource(R.string.source_code)) },
            icon = { Icon(Icons.Outlined.Code, null) },
            selected = false,
            onClick = actions.sourceCode,
        )
    }
    if (pickingDate) DatePickerFor(date, onDismiss = { pickingDate = false }, onPick = actions.pickDate)
}

@Composable
private fun CalendarItem(calendar: Calendar, settings: Settings, hidden: HiddenTags, actions: DrawerActions) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
            Icon(
                when {
                    calendar.isICal -> Icons.Outlined.Link
                    // no remote: stored only on this device
                    calendar.remoteUrl.isEmpty() -> Icons.Outlined.PhoneAndroid
                    else -> Icons.Outlined.AccountTree
                },
                null,
                Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                calendar.name,
                Modifier.weight(1f).padding(start = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (calendar.encrypted) {
                Icon(Icons.Outlined.Lock, stringResource(R.string.calendar_is_encrypted), Modifier.size(16.dp))
            }
            if (calendar.isWritable) {
                IconButton(onClick = { actions.editTag(TagEditTarget(calendar.name, null)) }) {
                    Icon(Icons.Outlined.Add, stringResource(R.string.create_tag, calendar.name), Modifier.size(20.dp))
                }
            }
            IconButton(onClick = { actions.editCalendar(calendar) }) {
                Icon(Icons.Outlined.Edit, stringResource(R.string.edit_calendar, calendar.name), Modifier.size(20.dp))
            }
        }
        val untagged = Tag(id = "", name = stringResource(R.string.tag_untagged), color = settings.untaggedColors[calendar.name] ?: settings.defaultEventColor)
        (listOf(untagged) + calendar.tagList).forEach { tag ->
            val key = "${calendar.name}:${tag.id.orEmpty()}"
            val visible = key !in hidden
            val color = TagColor.of(tag.color).color
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .clickable { actions.toggleTag(calendar.name, tag.id, visible) }
                    .padding(start = 16.dp),
            ) {
                Checkbox(
                    checked = visible,
                    onCheckedChange = { actions.toggleTag(calendar.name, tag.id, !it) },
                    colors = CheckboxDefaults.colors(checkedColor = color, uncheckedColor = color),
                )
                Text(
                    tag.name,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Untagged only stores a local color, so it stays editable on read-only calendars.
                if (tag.id == "" || calendar.isWritable) {
                    IconButton(onClick = { actions.editTag(TagEditTarget(calendar.name, tag)) }) {
                        Icon(Icons.Outlined.Edit, stringResource(R.string.edit_tag, tag.name), Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
