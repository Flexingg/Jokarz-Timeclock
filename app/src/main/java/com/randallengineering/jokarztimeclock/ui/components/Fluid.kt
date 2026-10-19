package com.randallengineering.jokarztimeclock.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.randallengineering.jokarztimeclock.ui.theme.AppMotion
import com.randallengineering.jokarztimeclock.ui.theme.FluidGlass
import com.randallengineering.jokarztimeclock.ui.theme.FluidLight
import com.randallengineering.jokarztimeclock.ui.theme.FluidRadius
import com.randallengineering.jokarztimeclock.ui.theme.GlassLevel
import com.randallengineering.jokarztimeclock.ui.theme.LocalFluid
import com.randallengineering.jokarztimeclock.ui.theme.PillShape
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.launch

/*
 * The "Living Flow" building blocks: calm cards with a contour glow, frosted floating chrome, light
 * that follows the finger, and a page with a soft primary-coloured wash. Every colour is a scheme
 * role at an alpha from FluidTokens, so all of this follows the selected palette.
 */

fun ColorScheme.isDark(): Boolean = background.luminance() < 0.5f

/** The page behind everything: a near-neutral base so cards and glass read as layers above it. */
@Composable
fun fluidPageColor(): Color {
    val s = MaterialTheme.colorScheme
    return if (s.isDark()) s.surfaceContainerLowest else s.surfaceContainer
}

/** A calm content card's fill: near-opaque, one step above the page. */
@Composable
fun fluidCardColor(): Color {
    val s = MaterialTheme.colorScheme
    return if (s.isDark()) s.surfaceContainer else s.surfaceContainerLowest
}

/** The 1 dp luminous edge every card and glass element carries; an accent edge when [selected]. */
@Composable
fun fluidContour(selected: Boolean = false): BorderStroke {
    val s = MaterialTheme.colorScheme
    if (selected) return BorderStroke(1.dp, s.primary.copy(alpha = FluidLight.CONTOUR_SELECTED))
    val brush = if (s.isDark()) {
        Brush.verticalGradient(
            listOf(s.onSurface.copy(alpha = FluidLight.CONTOUR_DARK_TOP), s.onSurface.copy(alpha = FluidLight.CONTOUR_DARK_BOTTOM))
        )
    } else {
        Brush.verticalGradient(
            listOf(s.outlineVariant.copy(alpha = FluidLight.CONTOUR_LIGHT_TOP), s.outlineVariant.copy(alpha = FluidLight.CONTOUR_LIGHT_BOTTOM))
        )
    }
    return BorderStroke(1.dp, brush)
}

/** The fill a glass surface shows (over its blurred backdrop, or alone when glass is off). */
@Composable
fun fluidGlassFill(level: GlassLevel): Color {
    val s = MaterialTheme.colorScheme
    val base = if (s.isDark()) s.surfaceContainerHigh else s.surfaceContainerLowest
    return if (!LocalFluid.current.glass) base else base.copy(alpha = if (s.isDark()) level.darkFill else level.lightFill)
}

/**
 * Frosted glass for floating chrome: blurs the content scrolling beneath (Haze) and tints it with
 * [fluidGlassFill], with a contour glow and a soft float shadow. Opaque when glass is off.
 */
@Composable
fun Modifier.fluidGlass(shape: Shape, level: GlassLevel = FluidGlass.thin, shadow: Dp = 10.dp): Modifier {
    val env = LocalFluid.current
    val fill = fluidGlassFill(level)
    val contour = fluidContour()
    val page = fluidPageColor()
    val glassed = if (env.glass && env.hazeState != null) {
        this.hazeChild(env.hazeState, shape, HazeStyle(page, listOf(HazeTint(fill)), level.blur, 0.06f, HazeTint(fill.copy(alpha = 1f))))
    } else {
        this.background(fill.copy(alpha = 1f), shape)
    }
    return this
        .shadow(shadow, shape, clip = false, ambientColor = MaterialTheme.colorScheme.scrim, spotColor = MaterialTheme.colorScheme.scrim)
        .then(glassed.clip(shape))
        .border(contour, shape)
}

/**
 * A soft radial highlight centred where the finger went down; it fades out over
 * [FluidLight.TOUCH_GLOW_FADE_MS] after release. Drawn above the content, clipped to [shape].
 */
@Composable
fun Modifier.touchGlow(interactionSource: InteractionSource, shape: Shape = RectangleShape): Modifier {
    val s = MaterialTheme.colorScheme
    val tint = if (s.isDark()) s.onSurface else s.surfaceContainerLowest
    val peak = if (s.isDark()) FluidLight.TOUCH_GLOW_ALPHA_DARK else FluidLight.TOUCH_GLOW_ALPHA_LIGHT
    val alpha = remember { Animatable(0f) }
    var at by remember { mutableStateOf(Offset.Unspecified) }
    androidx.compose.runtime.LaunchedEffect(interactionSource) {
        val scope = this
        interactionSource.interactions.collect { i ->
            when (i) {
                is PressInteraction.Press -> {
                    at = i.pressPosition
                    alpha.snapTo(1f)
                }
                is PressInteraction.Release, is PressInteraction.Cancel ->
                    scope.launch { alpha.animateTo(0f, tween(FluidLight.TOUCH_GLOW_FADE_MS)) }
            }
        }
    }
    return this.drawWithContent {
        drawContent()
        val a = alpha.value
        if (a > 0f && at != Offset.Unspecified) {
            val radius = size.minDimension * FluidLight.TOUCH_GLOW_RADIUS
            val brush = Brush.radialGradient(
                listOf(tint.copy(alpha = peak * a * 2.4f), tint.copy(alpha = 0f)),
                center = at,
                radius = radius
            )
            drawOutline(shape.createOutline(size, layoutDirection, this), brush)
        }
    }
}

/** The page's ambient light: a primary-coloured wash behind the top of the screen. */
@Composable
fun Modifier.fluidAmbientLight(): Modifier {
    val s = MaterialTheme.colorScheme
    val a = if (s.isDark()) FluidLight.AMBIENT_ALPHA_DARK else FluidLight.AMBIENT_ALPHA_LIGHT
    val page = fluidPageColor()
    return this
        .background(page)
        .drawBehind {
            drawRect(
                Brush.radialGradient(
                    listOf(s.primary.copy(alpha = a), s.primary.copy(alpha = 0f)),
                    center = Offset(size.width * 0.5f, size.height * 0.08f),
                    radius = size.width * 0.95f
                )
            )
        }
}

/**
 * A calm content card: near-opaque fill, [FluidRadius.lg] corners and a contour glow. Tappable when
 * [onClick] is given (press-scale, ripple and touch glow through [expressiveClickable]).
 */
@Composable
fun FluidCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    color: Color = fluidCardColor(),
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Surface(
        shape = shape,
        color = color,
        border = fluidContour(selected),
        modifier = if (onClick != null) modifier.expressiveClickable(shape = shape, onClick = onClick) else modifier,
        content = content
    )
}

/**
 * A segmented pill (e.g. Gross / Take Home): the selected option sits on a lighter accent pill that
 * glides between options on the stage ease (no overshoot), with a touch glow on each option.
 */
@Composable
fun <T> FluidSegmented(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp
) {
    val s = MaterialTheme.colorScheme
    val index = options.indexOf(selected).coerceAtLeast(0)
    BoxWithConstraints(
        modifier = modifier
            .height(height)
            .clip(PillShape)
            .background(s.onSurface.copy(alpha = 0.06f))
            .padding(3.dp)
    ) {
        val segment = maxWidth / options.size
        val x by animateDpAsState(segment * index, AppMotion.stageSpec(), label = "segment")
        Box(
            modifier = Modifier
                .offset(x = x)
                .width(segment)
                .fillMaxHeight()
                .shadow(4.dp, PillShape, clip = false, ambientColor = s.scrim, spotColor = s.scrim)
                .background(s.primary, PillShape)
                .border(fluidContour(), PillShape)
        )
        Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { i, option ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(PillShape)
                        .expressiveClickable(shape = PillShape) { onSelect(option) }
                ) {
                    Text(
                        text = label(option),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (i == index) s.onPrimary else s.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** One action on the floating island. */
@Immutable
data class IslandAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val badge: Int = 0,
    val alert: Boolean = false
)

/**
 * The floating "island" bar: a frosted pill floating above the content, which scrolls (and blurs)
 * underneath it, plus a separate circular glass button for the primary action.
 */
@Composable
fun FluidIsland(
    primary: IslandAction,
    actions: List<IslandAction>,
    modifier: Modifier = Modifier
) {
    val s = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(60.dp)
                .fluidGlass(CircleShape)
                .expressiveClickable(shape = CircleShape, onClick = primary.onClick)
        ) {
            Icon(primary.icon, contentDescription = primary.label, tint = s.primary, modifier = Modifier.size(26.dp))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .height(60.dp)
                .fluidGlass(PillShape)
                .padding(horizontal = 6.dp)
        ) {
            actions.forEach { a ->
                val tint = if (a.alert) s.error else s.onSurface
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(vertical = 4.dp)
                        .clip(PillShape)
                        .expressiveClickable(shape = PillShape, onClick = a.onClick)
                ) {
                    Box {
                        Icon(a.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                        if (a.badge > 0) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 8.dp, y = (-4).dp)
                                    .size(16.dp)
                                    .background(s.error, CircleShape)
                            ) {
                                Text("${a.badge}", style = MaterialTheme.typography.labelSmall, color = s.onError)
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = a.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = tint,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** A 44 dp circular glass button for the top bar, with an optional count badge and an "on" accent. */
@Composable
fun FluidIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    badge: Int = 0
) {
    val s = MaterialTheme.colorScheme
    Box(modifier = modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .background(if (active) s.primary.copy(alpha = 0.18f) else fluidCardColor(), CircleShape)
                .border(fluidContour(selected = active), CircleShape)
                .clip(CircleShape)
                .expressiveClickable(shape = CircleShape, onClick = onClick)
        ) {
            Icon(icon, contentDescription = contentDescription, tint = if (active) s.primary else s.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        if (badge > 0) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp)
                    .background(s.error, CircleShape)
            ) {
                Text("$badge", style = MaterialTheme.typography.labelSmall, color = s.onError)
            }
        }
    }
}
