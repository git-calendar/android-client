package dev.jotalac.ui.calendar

import dev.jotalac.ui.theme.AppIcons
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
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
            // the launcher foreground's logo takes only its middle 36%, the rest is padding for the icon mask
            Box(Modifier.size(24.dp)) {
                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(24.dp / 0.36f))
            }
            Text(
                stringResource(R.string.app_name),
                Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        NavigationDrawerItem(
            shape = MaterialTheme.shapes.small,
            label = { Text(stringResource(R.string.go_to_date)) },
            icon = { Icon(AppIcons.Calendar, null) },
            selected = false,
            onClick = { pickingDate = true },
        )
        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        // the calendar list, ported from web-client CalendarList.vue
        Row(Modifier.fillMaxWidth().padding(start = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${stringResource(R.string.calendars_title)}:",
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
            ListAction(AppIcons.Plus, stringResource(R.string.create_calendar), 20.dp, actions.createCalendar)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            calendars.forEach { CalendarItem(it, settings, hidden, actions) }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        NavigationDrawerItem(
            shape = MaterialTheme.shapes.small,
            label = { Text(stringResource(R.string.settings)) },
            icon = { Icon(AppIcons.Settings, null) },
            selected = false,
            onClick = actions.settings,
        )
        NavigationDrawerItem(
            shape = MaterialTheme.shapes.small,
            label = { Text(stringResource(R.string.guide)) },
            icon = { Icon(AppIcons.HelpCircle, null) },
            selected = false,
            onClick = actions.guide,
        )
        NavigationDrawerItem(
            shape = MaterialTheme.shapes.small,
            label = { Text(stringResource(R.string.source_code)) },
            icon = { Icon(AppIcons.Github, null) },
            selected = false,
            onClick = actions.sourceCode,
        )
    }
    if (pickingDate) DatePickerFor(date, onDismiss = { pickingDate = false }, onPick = actions.pickDate)
}

@Composable
private fun CalendarItem(calendar: Calendar, settings: Settings, hidden: HiddenTags, actions: DrawerActions) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            Modifier.fillMaxWidth().height(32.dp).padding(start = 6.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    calendar.name,
                    Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val type = stringResource(if (calendar.isICal) R.string.calendar_ical else R.string.calendar_git_repository)
                Icon(
                    if (calendar.isICal) AppIcons.Link else AppIcons.GitBranch,
                    type,
                    Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (calendar.isWritable) {
                ListAction(AppIcons.Plus, stringResource(R.string.create_tag, calendar.name), 18.dp) {
                    actions.editTag(TagEditTarget(calendar.name, null))
                }
            }
            ListAction(AppIcons.Edit, stringResource(R.string.edit_calendar, calendar.name), 16.dp) {
                actions.editCalendar(calendar)
            }
        }
        val untagged = Tag(id = "", name = stringResource(R.string.tag_untagged), color = settings.untaggedColors[calendar.name] ?: settings.defaultEventColor)
        (listOf(untagged) + calendar.tagList).forEach { tag ->
            val visible = "${calendar.name}:${tag.id.orEmpty()}" !in hidden
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(MaterialTheme.shapes.small)
                    .toggleable(visible, role = Role.Checkbox) { actions.toggleTag(calendar.name, tag.id, !it) }
                    .padding(start = 6.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // a square like the web checkbox, filled with the tag color while the tag is shown
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(if (visible) TagColor.of(tag.color).color else MaterialTheme.colorScheme.surfaceContainerHighest),
                )
                Text(
                    tag.name,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Untagged only stores a local color, so it stays editable on read-only calendars.
                if (tag.id == "" || calendar.isWritable) {
                    ListAction(AppIcons.Edit, stringResource(R.string.edit_tag, tag.name), 14.dp) {
                        actions.editTag(TagEditTarget(calendar.name, tag))
                    }
                }
            }
        }
    }
}

/** A transparent icon button of the calendar list, small like the web client's. */
@Composable
private fun ListAction(icon: ImageVector, description: String, iconSize: Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(28.dp).clip(MaterialTheme.shapes.small).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, Modifier.size(iconSize))
    }
}
