package com.randallengineering.jokarztimeclock.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.DialogProperties
import com.randallengineering.jokarztimeclock.ui.theme.DialogMotion
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

/**
 * Closes the enclosing [ExpressiveAlertDialog] by playing its entry transition in reverse, then
 * calling its `onDismissRequest`. Null outside a dialog.
 */
val LocalDialogBack = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * Material 3 `AlertDialog` (same slots, same layout, the theme's 28 dp `extraLarge` shape) whose
 * entry is a short scale + fade, and whose every "back" — a Cancel / Close button
 * ([DialogBackButton]), a scrim tap, the system back button, or the predictive back gesture —
 * plays that same transition in REVERSE before the dialog is removed. The frames come from
 * [DialogMotion], so opening and closing are the same curve run in opposite directions; the back
 * gesture scrubs it with the finger and either finishes the close or returns to open on cancel.
 * With animations off (reduced motion) it opens and closes instantly.
 */
@Composable
fun ExpressiveAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties()
) {
    val reducedMotion = rememberReducedMotion()
    val progress = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismissRequest)
    var closing by remember { mutableStateOf(false) }
    val spec = tween<Float>(DialogMotion.DURATION_MS, easing = LinearEasing)

    LaunchedEffect(Unit) { progress.animateTo(1f, spec) }

    val back: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                if (!reducedMotion) progress.animateTo(0f, spec)
                dismiss()
            }
        }
    }

    AlertDialog(
        onDismissRequest = back,
        confirmButton = {
            // Inside the dialog window, so it sees the dialog's back dispatcher (not the activity's).
            PredictiveBackHandler(enabled = !closing) { gesture ->
                try {
                    gesture.collect { event ->
                        if (!reducedMotion) progress.snapTo(DialogMotion.progressForBackGesture(event.progress))
                    }
                    back()
                } catch (e: CancellationException) {
                    // Gesture cancelled: settle back to fully open, then let the cancellation through.
                    scope.launch { progress.animateTo(1f, spec) }
                    throw e
                }
            }
            CompositionLocalProvider(LocalDialogBack provides back) { confirmButton() }
        },
        dismissButton = dismissButton?.let { slot -> { CompositionLocalProvider(LocalDialogBack provides back) { slot() } } },
        icon = icon,
        title = title,
        text = text?.let { slot -> { CompositionLocalProvider(LocalDialogBack provides back) { slot() } } },
        shape = MaterialTheme.shapes.extraLarge,
        properties = properties,
        modifier = modifier.graphicsLayer {
            val p = progress.value
            alpha = DialogMotion.alphaAt(p)
            scaleX = DialogMotion.scaleAt(p)
            scaleY = scaleX
        }
    )
}

/**
 * The dialog's "back" action (Cancel / Close / Done): closes the enclosing [ExpressiveAlertDialog]
 * with its entry transition reversed. Outside such a dialog it falls back to [onClick].
 */
@Composable
fun DialogBackButton(text: String = "Cancel", filled: Boolean = false, onClick: () -> Unit = {}) {
    val back = LocalDialogBack.current ?: onClick
    if (filled) {
        ScaledButton(onClick = back) { Text(text) }
    } else {
        ScaledTextButton(onClick = back) { Text(text) }
    }
}
