package com.randallengineering.jokarztimeclock.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.randallengineering.jokarztimeclock.engine.LiveChipStatus
import com.randallengineering.jokarztimeclock.engine.LiveChipStatusReader
import com.randallengineering.jokarztimeclock.ui.theme.ExpressiveShapes

/**
 * Live-chip health card.
 *
 * ColourOS/Android will happily grant every in-app permission and still freeze the app in the
 * background, so this card is deliberately explicit about the three things that make the status bar
 * chronometer reliable:
 *
 *  1. `POST_NOTIFICATIONS` (Android 13+) — without it no notification, and therefore no chip. The
 *     app stays fully usable; the owner only loses the ticking timer and milestone alerts.
 *  2. Battery-optimisation exemption — without it ColorOS may freeze the process so the chip stops.
 *  3. Autostart / "Allow background running" — ColorOS-specific app-launch control.
 *
 * Below that, [ChipDiagnostics] reports what Android 16 actually did with the notification (read
 * back from the posted notification by [LiveChipStatusReader]) plus the running build, so "is the
 * chip working?" is answered by the OS rather than guessed. It is shown while a shift runs, or
 * whenever the owner has Live Updates switched off (the one case they can fix before clocking in).
 *
 * The battery/notification card renders **nothing** when all is well, so it never adds clutter once
 * set up.
 */
@Composable
fun LiveChipHealthCard(
    isClockedIn: Boolean,
    liveNotificationEnabled: Boolean,
    notificationsPermissionGranted: Boolean,
    batteryExempt: Boolean,
    chipSnapshot: LiveChipStatusReader.Snapshot?,
    versionLabel: String,
    onAllowNotifications: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onOpenAutostart: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenPromotionSettings: () -> Unit,
    onRecheckChip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val showDiagnostics = notificationsPermissionGranted && chipSnapshot != null && (
        (isClockedIn && liveNotificationEnabled) ||
            (chipSnapshot.apiLevel >= LiveChipStatus.PROMOTION_API && !chipSnapshot.canPostPromoted)
        )

    Column(modifier = modifier.fillMaxWidth()) {
        HealthWarnings(
            isClockedIn = isClockedIn,
            liveNotificationEnabled = liveNotificationEnabled,
            notificationsPermissionGranted = notificationsPermissionGranted,
            batteryExempt = batteryExempt,
            onAllowNotifications = onAllowNotifications,
            onRequestBatteryExemption = onRequestBatteryExemption,
            onOpenAutostart = onOpenAutostart,
            onOpenNotificationSettings = onOpenNotificationSettings
        )
        if (showDiagnostics) {
            Spacer(modifier = Modifier.height(6.dp))
            ChipDiagnostics(
                snapshot = chipSnapshot,
                versionLabel = versionLabel,
                onOpenPromotionSettings = onOpenPromotionSettings,
                onRecheck = onRecheckChip
            )
        }
    }
}

/** The honest Android 16 promotion verdict, the build number, and the Live Updates settings button. */
@Composable
private fun ChipDiagnostics(
    snapshot: LiveChipStatusReader.Snapshot,
    versionLabel: String,
    onOpenPromotionSettings: () -> Unit,
    onRecheck: () -> Unit
) {
    val verdict = snapshot.verdict
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        shape = ExpressiveShapes.Container
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (verdict.promoted) Icons.Filled.CheckCircle else Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(verdict.headline, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(verdict.detail, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "$versionLabel • Android API ${snapshot.apiLevel}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (snapshot.apiLevel >= LiveChipStatus.PROMOTION_API) {
                    Button(onClick = onOpenPromotionSettings) { Text("Live Updates settings", fontSize = 12.sp) }
                }
                TextButton(onClick = onRecheck) { Text("Re-check", fontSize = 12.sp) }
            }
        }
    }
}

/** Notification-permission and ColorOS battery guidance; renders nothing when both are satisfied. */
@Composable
private fun HealthWarnings(
    isClockedIn: Boolean,
    liveNotificationEnabled: Boolean,
    notificationsPermissionGranted: Boolean,
    batteryExempt: Boolean,
    onAllowNotifications: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onOpenAutostart: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showWhy by remember { mutableStateOf(false) }

    val needsNotifications = !notificationsPermissionGranted
    val needsBattery = !batteryExempt

    if (!needsNotifications && !needsBattery) {
        if (isClockedIn && liveNotificationEnabled) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = modifier.fillMaxWidth().padding(vertical = 2.dp)
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Live status bar timer is on — the system keeps it ticking.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
        ),
        shape = ExpressiveShapes.Container
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (needsNotifications) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.NotificationsOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Notifications are off — no live timer chip",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Clock in/out, history, totals and payroll all still work. You only lose the " +
                        "ticking status bar timer and the shift milestone alerts.",
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onAllowNotifications) { Text("Allow notifications", fontSize = 12.sp) }
                    TextButton(onClick = onOpenNotificationSettings) { Text("Phone settings", fontSize = 12.sp) }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (needsBattery) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.BatteryAlert,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Stop ColorOS from pausing the app",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Android is allowed to freeze background apps to save power. If it freezes this " +
                        "one, the shift keeps running but the status bar timer can stop moving. " +
                        "Exempt it from battery optimisation.",
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRequestBatteryExemption) { Text("Battery: no restrictions", fontSize = 12.sp) }
                    TextButton(onClick = onOpenAutostart) { Text("Autostart settings", fontSize = 12.sp) }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            TextButton(onClick = { showWhy = true }) {
                Text("Why does this matter on my Oppo?", fontSize = 12.sp)
            }
        }
    }

    if (showWhy) {
        AlertDialog(
            onDismissRequest = { showWhy = false },
            title = { Text("Keeping the live timer alive", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "The timer in the status bar is drawn by Android itself, so nothing in this " +
                            "app has to wake up or run a loop for it to move.",
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Two things can still stop it:\n\n" +
                            "1. Notifications blocked — there is nowhere to draw it.\n\n" +
                            "2. ColorOS battery management — when the phone decides the app is idle it " +
                            "can freeze or kill background work, including the small service that holds " +
                            "the chip. Exempting the app from battery optimisation, and enabling " +
                            "Autostart / \"Allow background running\" in Battery settings, stops that.\n\n" +
                            "Swiping the app away from Recents does NOT close the shift: the service " +
                            "and the chip keep running.",
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = { Button(onClick = { showWhy = false }) { Text("Got it") } },
            dismissButton = {
                TextButton(onClick = { showWhy = false; onOpenAutostart() }) { Text("Open settings") }
            }
        )
    }
}
