package dev.jotalac.ui.common

import dev.jotalac.ui.theme.AppIcons
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

// Compact form controls styled like the web client's inputs (styles.css): flat and filled, no
// borders or floating labels, square corners and one height for fields, selects and buttons.

/** Height of every control, like the web client's 2 rem inputs, selects and buttons. */
val FieldHeight = 36.dp

/** Space between form rows, the web client's 1 rem gap. */
val FormGap = 16.dp

private const val DisabledAlpha = 0.5f
private val FieldPadding = 10.dp

@Composable
fun formTextStyle(): TextStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface)

@Composable
private fun placeholderColor() = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)

/** The filled box behind every control; [highlight] is the pressed, focused or checked shade. */
@Composable
private fun Modifier.field(highlight: Boolean, isError: Boolean = false, danger: Boolean = false): Modifier {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    val background = when {
        danger -> colors.error.copy(alpha = if (highlight) 0.45f else 0.3f).compositeOver(colors.background)
        highlight -> colors.surfaceContainerHighest
        else -> colors.surfaceContainerHigh
    }
    return clip(shape)
        .background(background)
        .then(if (isError || danger) Modifier.border(1.dp, colors.error, shape) else Modifier)
}

/** A clickable control box that darkens and drops a pixel while pressed, like the web buttons. */
@Composable
private fun FieldSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    danger: Boolean = false,
    isError: Boolean = false,
    role: Role = Role.Button,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .heightIn(min = FieldHeight)
            .offset(y = if (pressed) 1.dp else 0.dp)
            .alpha(if (enabled) 1f else DisabledAlpha)
            .field(pressed || selected, isError, danger)
            .clickable(interaction, indication = null, enabled = enabled, role = role, onClick = onClick),
        contentAlignment = contentAlignment,
        content = content,
    )
}

/** A text input with a [placeholder] instead of a label. */
@Composable
fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    isError: Boolean = false,
    textStyle: TextStyle = formTextStyle(),
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.alpha(if (enabled) 1f else DisabledAlpha),
        enabled = enabled,
        textStyle = textStyle,
        keyboardOptions = keyboardOptions,
        singleLine = singleLine,
        minLines = minLines,
        visualTransformation = visualTransformation,
        interactionSource = interaction,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = FieldHeight)
                    .field(focused, isError)
                    .padding(start = FieldPadding, end = if (trailing == null) FieldPadding else 0.dp),
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
            ) {
                Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = textStyle, color = placeholderColor(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    inner()
                }
                trailing?.invoke()
            }
        },
    )
}

/** A small icon button sized to sit inside a [FormTextField]. */
@Composable
fun FieldAction(icon: ImageVector, description: String?, onClick: () -> Unit) {
    Box(
        Modifier.size(FieldHeight).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun FormButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    icon: ImageVector? = null,
) {
    FieldSurface(onClick, modifier, enabled, danger = danger) {
        Row(
            Modifier.padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let { Icon(it, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface) }
            Text(text, style = formTextStyle(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun FormIconButton(icon: ImageVector, description: String, onClick: () -> Unit, enabled: Boolean = true) {
    FieldSurface(onClick, Modifier.size(FieldHeight), enabled) {
        Icon(icon, description, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
    }
}

/** A value that opens a picker when tapped, like the web client's date and time inputs. */
@Composable
fun FormValue(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, isError: Boolean = false) {
    FieldSurface(onClick, modifier, enabled, isError = isError, contentAlignment = Alignment.CenterStart) {
        Text(
            text,
            Modifier.padding(horizontal = FieldPadding),
            style = formTextStyle(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A select: the chosen option with a chevron, opening a menu as wide as itself. */
@Composable
fun <T> FormSelect(
    selected: String,
    options: List<Pair<String, T>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    disabled: Set<T> = emptySet(),
    leading: Color? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var width by remember { mutableIntStateOf(0) }
    Box(modifier.onSizeChanged { width = it.width }) {
        FieldSurface(
            { expanded = true }, Modifier.fillMaxWidth(), enabled, selected = expanded,
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(Modifier.padding(start = FieldPadding, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (leading != null) {
                    Box(Modifier.size(12.dp).clip(MaterialTheme.shapes.extraSmall).background(leading))
                    Box(Modifier.width(8.dp))
                }
                Text(selected, Modifier.weight(1f), style = formTextStyle(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(AppIcons.ChevronDown, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(with(LocalDensity.current) { width.toDp() }),
            shape = MaterialTheme.shapes.small,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            options.forEach { (text, value) ->
                DropdownMenuItem(
                    text = { Text(text, style = formTextStyle()) },
                    enabled = value !in disabled,
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

/** A label followed by a small square box, filled when checked, like the web checkboxes. */
@Composable
fun FormCheckbox(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Puts the box at the far end, for settings rows. */
    spread: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .heightIn(min = FieldHeight)
            .alpha(if (enabled) 1f else DisabledAlpha)
            .toggleable(checked, remember { MutableInteractionSource() }, null, enabled, Role.Checkbox, onChange),
        horizontalArrangement = if (spread) Arrangement.SpaceBetween else Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f, fill = spread), style = formTextStyle())
        Box(
            Modifier.size(18.dp).clip(MaterialTheme.shapes.small).background(if (checked) colors.onSurface else colors.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(AppIcons.Check, null, Modifier.size(14.dp), tint = colors.surface)
        }
    }
}

/** One of several options in a single box, like the web client's MultiToggle. */
@Composable
fun <T> FormToggle(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .heightIn(min = FieldHeight)
            .clip(MaterialTheme.shapes.small)
            .background(colors.surfaceContainerHigh)
            .padding(2.dp),
    ) {
        options.forEach { (text, value) ->
            val on = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = FieldHeight - 4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (on) colors.surfaceContainerHighest else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(value) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text, style = formTextStyle(), color = colors.onSurface.copy(alpha = if (on) 1f else 0.5f), maxLines = 1)
            }
        }
    }
}

/** A box that can be switched on and off, for picking several options like weekdays. */
@Composable
fun FormChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FieldSurface(onClick, modifier, selected = selected, role = Role.Checkbox) {
        Text(
            text,
            style = formTextStyle(),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (selected) 1f else 0.5f),
            maxLines = 1,
        )
    }
}

/** A label on the left and its control on the right, like the web settings rows. */
@Composable
fun FormRow(label: String, modifier: Modifier = Modifier, control: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FormGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = formTextStyle())
        control()
    }
}

/** Secondary text under or between controls. */
@Composable
fun FormHint(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)) {
    Text(text, modifier, style = MaterialTheme.typography.bodySmall, color = color)
}

/** A modal form like the web client's: the background color with a faint border and square corners. */
@Composable
fun FormDialog(onDismiss: () -> Unit, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Column(Modifier.padding(FormGap), verticalArrangement = Arrangement.spacedBy(FormGap)) {
                title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                content()
            }
        }
    }
}

/** The centered button row at the bottom of a form. */
@Composable
fun FormButtons(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FormGap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
