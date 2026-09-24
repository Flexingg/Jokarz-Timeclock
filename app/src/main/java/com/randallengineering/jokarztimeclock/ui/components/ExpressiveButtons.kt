package com.randallengineering.jokarztimeclock.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ChipColors
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.randallengineering.jokarztimeclock.ui.theme.ConnectedButtonShapes
import com.randallengineering.jokarztimeclock.ui.theme.ExpressiveButtonSize
import com.randallengineering.jokarztimeclock.ui.theme.Transparent

/*
 * M3 Expressive buttons. Every button here is a pill at its size (ExpressiveButtonSize), keeps the
 * M3 ripple, and adds the slight press-scale from ExpressiveMotion. Colours are scheme roles only:
 *   filled   = primary / onPrimary
 *   tonal    = secondaryContainer / onSecondaryContainer
 *   outlined = transparent + 1 dp `outline` border
 */

enum class ButtonVariant { Filled, Tonal, Outlined }

/** A sized, pill-shaped M3 button with an optional leading icon. */
@Composable
fun ExpressiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Filled,
    size: ExpressiveButtonSize = ExpressiveButtonSize.Medium,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    shape: Shape = size.shape,
    colors: ButtonColors? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val sized = modifier
        .height(size.height)
        .pressScale(interaction)
    val padding = PaddingValues(horizontal = size.horizontalPadding)
    val content: @Composable RowScope.() -> Unit = { ButtonLabel(text, icon, size) }
    when (variant) {
        ButtonVariant.Filled -> Button(
            onClick = onClick,
            modifier = sized,
            enabled = enabled,
            shape = shape,
            colors = colors ?: ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            contentPadding = padding,
            interactionSource = interaction,
            content = content
        )
        ButtonVariant.Tonal -> FilledTonalButton(
            onClick = onClick,
            modifier = sized,
            enabled = enabled,
            shape = shape,
            colors = colors ?: ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            contentPadding = padding,
            interactionSource = interaction,
            content = content
        )
        ButtonVariant.Outlined -> OutlinedButton(
            onClick = onClick,
            modifier = sized,
            enabled = enabled,
            shape = shape,
            colors = colors ?: ButtonDefaults.outlinedButtonColors(containerColor = Transparent),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            contentPadding = padding,
            interactionSource = interaction,
            content = content
        )
    }
}

@Composable
private fun ButtonLabel(text: String, icon: ImageVector?, size: ExpressiveButtonSize) {
    if (icon != null) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(size.iconSize))
        Spacer(modifier = Modifier.width(size.iconSpacing))
    }
    Text(
        text = text,
        style = size.labelStyle(MaterialTheme.typography),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** One action in a [ConnectedButtonGroup]. */
class ConnectedAction(
    val text: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val variant: ButtonVariant = ButtonVariant.Tonal,
    val enabled: Boolean = true,
    val colors: ButtonColors? = null,
    val weight: Float = 1f
)

/**
 * A connected button group: a Row with items [ConnectedButtonShapes.Gap] apart where only the
 * adjoining inner corners shrink to [ConnectedButtonShapes.InnerCorner] and the outer corners stay
 * fully round. Items share the width by [ConnectedAction.weight]; a label that does not fit ellipsizes.
 *
 * Deliberately a Row and not material3's `ButtonGroup` (present in 1.5.0-alpha10): that container
 * moves every item whose max intrinsic width does not fit into an overflow menu, which on a narrow
 * phone or at a large font scale would hide "Clock Out" behind a "⋮". An action here is never hidden.
 */
@Composable
fun ConnectedButtonGroup(
    actions: List<ConnectedAction>,
    modifier: Modifier = Modifier,
    size: ExpressiveButtonSize = ExpressiveButtonSize.Small
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ConnectedButtonShapes.Gap),
        modifier = modifier
    ) {
        actions.forEachIndexed { index, action ->
            ExpressiveButton(
                text = action.text,
                onClick = action.onClick,
                variant = action.variant,
                size = size,
                icon = action.icon,
                enabled = action.enabled,
                shape = ConnectedButtonShapes.shapeFor(index, actions.size),
                colors = action.colors,
                modifier = Modifier.weight(action.weight)
            )
        }
    }
}

/**
 * A connected single-choice group (e.g. Gross / Take Home, Merge / Replace): the official M3
 * [ToggleButton] on connected shapes, selected = primary / onPrimary, unselected = secondaryContainer.
 * Laid out like [ConnectedButtonGroup], so no option is ever moved out of sight.
 */
@Composable
fun <T> ConnectedToggleGroup(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    size: ExpressiveButtonSize = ExpressiveButtonSize.Small
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ConnectedButtonShapes.Gap),
        modifier = modifier
    ) {
        options.forEachIndexed { index, option ->
            val interaction = remember { MutableInteractionSource() }
            val shape = ConnectedButtonShapes.shapeFor(index, options.size)
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { onSelect(option) },
                shapes = ToggleButtonShapes(shape, shape, shape),
                colors = ToggleButtonDefaults.toggleButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    checkedContainerColor = MaterialTheme.colorScheme.primary,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = size.horizontalPadding),
                interactionSource = interaction,
                modifier = Modifier
                    .weight(1f)
                    .height(size.height)
                    .pressScale(interaction)
            ) {
                Text(
                    text = label(option),
                    style = size.labelStyle(MaterialTheme.typography),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** A pressed-scale wrapper for library buttons that already own their ripple (icon buttons, chips). */
@Composable
fun rememberPressScaleSource(): Pair<MutableInteractionSource, Modifier> {
    val interaction = remember { MutableInteractionSource() }
    return interaction to Modifier.pressScale(interaction)
}


/*
 * Drop-in M3 buttons with the press-scale added (same parameters and defaults as the library's, minus
 * the interaction source, which they own). UI code uses these instead of the bare library buttons so
 * every tappable part both ripples and squashes slightly; PressFeedbackEnforcementTest checks it.
 */

@Composable
fun ScaledButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val (interaction, scale) = rememberPressScaleSource()
    Button(
        onClick = onClick, modifier = modifier.then(scale), enabled = enabled, shape = shape, colors = colors,
        border = border, contentPadding = contentPadding, interactionSource = interaction, content = content
    )
}

@Composable
fun ScaledFilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.filledTonalShape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val (interaction, scale) = rememberPressScaleSource()
    FilledTonalButton(
        onClick = onClick, modifier = modifier.then(scale), enabled = enabled, shape = shape, colors = colors,
        border = border, contentPadding = contentPadding, interactionSource = interaction, content = content
    )
}

/** Outlined = transparent container + a 1 dp `outline` border (the M3 Expressive brief). */
@Composable
fun ScaledOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(containerColor = Transparent),
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val (interaction, scale) = rememberPressScaleSource()
    OutlinedButton(
        onClick = onClick, modifier = modifier.then(scale), enabled = enabled, shape = shape, colors = colors,
        border = border, contentPadding = contentPadding, interactionSource = interaction, content = content
    )
}

@Composable
fun ScaledTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.textShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val (interaction, scale) = rememberPressScaleSource()
    TextButton(
        onClick = onClick, modifier = modifier.then(scale), enabled = enabled, shape = shape, colors = colors,
        contentPadding = contentPadding, interactionSource = interaction, content = content
    )
}

@Composable
fun ScaledElevatedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.elevatedShape,
    colors: ButtonColors = ButtonDefaults.elevatedButtonColors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val (interaction, scale) = rememberPressScaleSource()
    ElevatedButton(
        onClick = onClick, modifier = modifier.then(scale), enabled = enabled, shape = shape, colors = colors,
        contentPadding = contentPadding, interactionSource = interaction, content = content
    )
}

@Composable
fun ScaledIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    content: @Composable () -> Unit
) {
    val (interaction, scale) = rememberPressScaleSource()
    IconButton(
        onClick = onClick, modifier = modifier.then(scale), enabled = enabled, colors = colors,
        interactionSource = interaction, content = content
    )
}

@Composable
fun ScaledFilledTonalIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = IconButtonDefaults.filledShape,
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
    content: @Composable () -> Unit
) {
    val (interaction, scale) = rememberPressScaleSource()
    FilledTonalIconButton(
        onClick = onClick, modifier = modifier.then(scale), enabled = enabled, shape = shape, colors = colors,
        interactionSource = interaction, content = content
    )
}

@Composable
fun ScaledAssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = AssistChipDefaults.shape,
    colors: ChipColors = AssistChipDefaults.assistChipColors(),
    border: BorderStroke? = AssistChipDefaults.assistChipBorder(enabled)
) {
    val (interaction, scale) = rememberPressScaleSource()
    AssistChip(
        onClick = onClick, label = label, modifier = modifier.then(scale), enabled = enabled,
        leadingIcon = leadingIcon, shape = shape, colors = colors, border = border, interactionSource = interaction
    )
}
