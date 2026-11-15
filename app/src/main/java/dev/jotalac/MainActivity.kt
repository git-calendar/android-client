package dev.jotalac

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jotalac.ui.AppNavigation
import dev.jotalac.ui.theme.GitCalendarTheme
import java.time.Instant
import java.time.LocalDate

// AppCompatActivity so in-app language changes also apply on Android 12.
class MainActivity : AppCompatActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // a recreated activity (rotation, process restore) gets its old intent again; handle it only once
        val fromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (savedInstanceState == null && !fromHistory) handleIntent(intent)
        // Like the web client, ask at startup: reminders may arrive from other devices through sync.
        if (savedInstanceState == null &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            GitCalendarTheme(settings.themeMode, settings.colorSource) {
                AppNavigation(container)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Opens the day of a tapped reminder notification or widget entry, the widget's tapped event, or a new event from the widget. */
    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_OPEN_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.let { container.openDate.value = it }
        if (intent?.getBooleanExtra(EXTRA_NEW_EVENT, false) == true) container.newEventRequested.value = true
        val eventId = intent?.getStringExtra(EXTRA_OPEN_EVENT)
        val eventFrom = intent?.getLongExtra(EXTRA_OPEN_EVENT_FROM, Long.MIN_VALUE) ?: Long.MIN_VALUE
        if (eventId != null && eventFrom != Long.MIN_VALUE) {
            container.openEvent.value = EventRef(eventId, Instant.ofEpochMilli(eventFrom))
        }
    }

    companion object {
        const val EXTRA_OPEN_DATE = "open_date"
        const val EXTRA_NEW_EVENT = "new_event"
        const val EXTRA_OPEN_EVENT = "open_event"

        /** Start of the tapped occurrence in epoch milliseconds. */
        const val EXTRA_OPEN_EVENT_FROM = "open_event_from"
    }
}
