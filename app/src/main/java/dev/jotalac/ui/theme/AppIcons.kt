package dev.jotalac.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import dev.jotalac.R

/**
 * The web client's icons: Feather, plus the few Tabler and Lucide ones it uses, converted from
 * vue-icons-plus into res/drawable. Icons the web client does not have come from the same packs.
 */
object AppIcons {
    val ArrowLeft: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.fi_arrow_left)
    val Calendar: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.fi_calendar)
    val CalendarMonth: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.tb_calendar_month)
    val CalendarWeek: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.tb_calendar_week)
    val Check: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.fi_check)
    val ChevronDown: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.fi_chevron_down)
    val ChevronLeft: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.fi_chevron_left)
    val ChevronRight: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.fi_chevron_right)
    val Clock: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.fi_clock)
    val Cloud: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.fi_cloud)
    val CloudOff: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.fi_cloud_off)
    val Columns1: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.tb_columns_1)
    val Columns3: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.tb_columns_3)
    val Edit: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.fi_edit_2)
    val Eye: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.fi_eye)
    val EyeOff: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.fi_eye_off)
    val File: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.fi_file)
    val FileText: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.fi_file_text)
    val Folder: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.fi_folder)
    val GitBranch: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.fi_git_branch)
    val Github: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.fi_github)
    val HelpCircle: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.fi_help_circle)
    val LayoutSidebar: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.tb_layout_sidebar)
    val Link: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.fi_link)
    val Lock: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.fi_lock)
    val Map: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.fi_map)
    val MapPin: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.fi_map_pin)
    val Plus: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.fi_plus)
    val RefreshCcw: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.lu_refresh_ccw)
    val RefreshCw: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.fi_refresh_cw)
    val Settings: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.fi_settings)
    val Smartphone: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.fi_smartphone)
    val Video: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.fi_video)
    val X: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.fi_x)
}
