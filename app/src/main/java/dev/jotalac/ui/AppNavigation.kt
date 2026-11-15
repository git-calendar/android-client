package dev.jotalac.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.jotalac.AppContainer
import dev.jotalac.EditorRequest
import dev.jotalac.EventRef
import dev.jotalac.core.CoreException
import dev.jotalac.core.Event
import java.time.ZoneId
import dev.jotalac.ui.calendar.CalendarNavigation
import dev.jotalac.ui.calendar.CalendarScreen
import dev.jotalac.ui.calendar.CalendarViewModel
import dev.jotalac.ui.calendar.newEventDraft
import dev.jotalac.ui.calendars.CalendarEditorScreen
import dev.jotalac.ui.common.WideDialogProperties
import dev.jotalac.ui.calendars.CalendarEditorViewModel
import dev.jotalac.ui.event.EventEditorScreen
import dev.jotalac.ui.event.EventEditorViewModel
import dev.jotalac.ui.settings.FilesScreen
import dev.jotalac.ui.settings.GuideScreen
import dev.jotalac.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable object CalendarRoute
@Serializable object EventEditorRoute
@Serializable data class CalendarEditorRoute(val name: String? = null)
@Serializable object SettingsRoute
@Serializable object GuideRoute
@Serializable object FilesRoute

@Composable
fun AppNavigation(container: AppContainer) {
    val nav = rememberNavController()
    val back: () -> Unit = { nav.popBackStack() }

    // the widget's new-event button: open the editor with a draft once calendars are loaded
    LaunchedEffect(Unit) {
        container.newEventRequested.collect { requested ->
            if (!requested) return@collect
            container.newEventRequested.value = false
            if (container.ready.await() != null) return@collect
            container.editorRequest.value = EditorRequest(newEventDraft(container.repository.defaultCalendar()), null)
            nav.navigate(EventEditorRoute) { launchSingleTop = true }
        }
    }

    // an event tapped in the widget: open it in the editor like a tap in the calendar does
    LaunchedEffect(Unit) {
        container.openEvent.collect { ref ->
            if (ref == null) return@collect
            container.openEvent.value = null
            if (container.ready.await() != null) return@collect
            val event = findEvent(container, ref) ?: return@collect
            container.editorRequest.value = EditorRequest(event, null)
            nav.navigate(EventEditorRoute) { launchSingleTop = true }
        }
    }

    NavHost(nav, startDestination = CalendarRoute) {
        composable<CalendarRoute> {
            val vm = viewModel { CalendarViewModel(container, createSavedStateHandle()) }
            CalendarScreen(
                vm,
                CalendarNavigation(
                    openEditor = { nav.navigate(EventEditorRoute) { launchSingleTop = true } },
                    createCalendar = { nav.navigate(CalendarEditorRoute()) },
                    editCalendar = { nav.navigate(CalendarEditorRoute(it.name)) },
                    settings = { nav.navigate(SettingsRoute) },
                    guide = { nav.navigate(GuideRoute) },
                ),
            )
        }
        // the editors float over the calendar like the tag dialog
        dialog<EventEditorRoute>(dialogProperties = WideDialogProperties) {
            val vm = viewModel { EventEditorViewModel(container) }
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            EventEditorScreen(vm, settings, back)
        }
        dialog<CalendarEditorRoute>(dialogProperties = WideDialogProperties) { entry ->
            val route = entry.toRoute<CalendarEditorRoute>()
            val vm = viewModel { CalendarEditorViewModel(container, route.name) }
            CalendarEditorScreen(vm, back)
        }
        // a screen still fading out sits on top and takes taps meant for the one below;
        // dropUnlessResumed ignores them so its back arrow can't pop the calendar too
        composable<SettingsRoute> {
            SettingsScreen(container, dropUnlessResumed(block = back), dropUnlessResumed { nav.navigate(FilesRoute) })
        }
        composable<GuideRoute> { GuideScreen(dropUnlessResumed(block = back)) }
        composable<FilesRoute> { FilesScreen(container.core.storageRoot, dropUnlessResumed(block = back)) }
    }
}

/**
 * The occurrence [ref] points to, as the calendar shows it, or null when it is gone (deleted or synced
 * away since the widget drew it).
 */
private suspend fun findEvent(container: AppContainer, ref: EventRef): Event? = try {
    val from = ref.from.atZone(ZoneId.systemDefault())
    container.core.getEvents(from, from.plusMinutes(1), null)
        .firstOrNull { it.id == ref.id && it.from.toInstant() == ref.from }
} catch (e: CoreException) {
    null
}
