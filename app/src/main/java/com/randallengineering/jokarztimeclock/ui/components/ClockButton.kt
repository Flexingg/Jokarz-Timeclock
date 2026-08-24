package com.randallengineering.jokarztimeclock.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.randallengineering.jokarztimeclock.ui.theme.EmeraldSuccess
import com.randallengineering.jokarztimeclock.ui.theme.PurplePrimary
import com.randallengineering.jokarztimeclock.ui.theme.RoseError

@Composable
fun ClockButton(
    isClockedIn: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val buttonColor by animateColorAsState(
        targetValue = if (isClockedIn) RoseError else PurplePrimary,
        animationSpec = tween(400),
        label = "buttonColor"
    )

    val statusText = if (isClockedIn) "Working..." else "Ready to Work"
    val statusColor = if (isClockedIn) EmeraldSuccess else Color(0xFF94A3B8)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = statusText.uppercase(),
            color = statusColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(160.dp)
        ) {
            // Pulsing ring effect when clocked in
            if (isClockedIn) {
                Canvas(modifier = Modifier.size(160.dp)) {
                    drawCircle(
                        color = RoseError.copy(alpha = pulseAlpha),
                        radius = (size.minDimension / 2f) * pulseScale
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = buttonColor,
                shadowElevation = if (isClockedIn) 12.dp else 8.dp,
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isClockedIn) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isClockedIn) "CLOCK OUT" else "CLOCK IN",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}
