package dev.jotalac.ui.settings

import dev.jotalac.ui.theme.AppIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.jotalac.R

private data class GuideSection(val title: String, val steps: List<String>, val note: String? = null)

// The guide is English only.
private val SECTIONS = listOf(
    GuideSection(
        "Quick start",
        listOf(
            "Add a calendar: open the menu with the button at the top left and tap + next to Calendars.",
            "Create an event: tap the + button at the bottom right, or press and hold an empty time and drag.",
            "Sync: calendars with a Git remote sync on their own. Tap the cloud icon in the top bar to sync right away.",
        ),
    ),
    GuideSection(
        "Create a calendar",
        listOf(
            "Tap + next to Calendars and choose Init.",
            "Enter a name, such as My Calendar.",
            "To sync it, create an empty repository with your Git provider and paste its HTTPS URL. The URL must end in .git.",
            "For a private repository, enter your username and an access token.",
            "Turn on Encrypted if you want, keep the key somewhere safe, and save.",
        ),
    ),
    GuideSection(
        "Clone a calendar",
        listOf(
            "Copy the repository's HTTPS URL, for example https://host/user/calendar.git.",
            "Tap + next to Calendars and choose Clone.",
            "Paste the URL and add your credentials if the repository is private.",
            "Turn on Read-only if you only want to view the calendar.",
            "If the repository is encrypted, turn on Encrypted and enter its key.",
        ),
        "Encryption keys cannot be recovered. Every phone or computer that opens the calendar needs the same key.",
    ),
    GuideSection(
        "iCalendar",
        listOf(
            "Import a file: the events of an .ics file are copied into one of your calendars. Later changes to the file are not picked up.",
            "Subscribe to a URL: an .ics link becomes a read-only calendar that refreshes when it syncs and stays available offline.",
        ),
        "Events in a subscribed calendar can only be changed in the service that publishes it.",
    ),
    GuideSection(
        "Gestures",
        listOf(
            "Open: tap an event to see or edit it.",
            "Create: press and hold an empty time or the all-day row, drag over the range, then let go.",
            "Move: press and hold an event and drag it to another time or day.",
            "Resize: press and hold the top or bottom edge of an event and drag.",
            "Switch period: swipe left or right.",
            "Day: tap a day in the month view to open it; Back returns to the month.",
            "Zoom: pinch the timeline to make hours taller, then scroll up and down.",
        ),
        "Events in read-only calendars cannot be moved or edited.",
    ),
    GuideSection(
        "Top bar and menu",
        listOf(
            "Cloud icon: shows the sync status; tap it to sync now.",
            "Today: jumps back to today.",
            "View: switches between the day, 4-day, week and month views.",
            "Go to date: in the menu, jumps to any date.",
            "Tags: the checkboxes under each calendar show or hide its tags; the pencil changes a tag's name or color.",
        ),
    ),
    GuideSection(
        "Reminders and widget",
        listOf(
            "Reminders: add them to an event to get a notification before it starts.",
            "Background sync: keeps calendars and reminders up to date while the app is closed. Turn it off in Settings.",
            "Widget: hold an empty spot on your home screen, tap Widgets and add Upcoming events from Git Calendar.",
        ),
    ),
    GuideSection(
        "Settings",
        listOf(
            "Hour range: the hours the timeline shows on screen.",
            "Fit hours to screen: shows the hour range without scrolling.",
            "Drag precision: how far an event moves per step when dragged.",
            "Colors: Git colors or colors from your wallpaper.",
            "Data: export your calendars or restore a ZIP backup.",
        ),
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("How to use Git Calendar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.ArrowLeft, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Repositories are your calendars, and events are stored in them. You can work locally or connect a remote Git repository to sync your calendars.",
                style = MaterialTheme.typography.bodyLarge,
            )
            SECTIONS.forEach { section ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(section.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    section.steps.forEachIndexed { i, step ->
                        Row {
                            Text("${i + 1}.", Modifier.width(24.dp), style = MaterialTheme.typography.bodyMedium)
                            Text(step, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    section.note?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
