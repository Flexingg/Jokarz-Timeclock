package com.randallengineering.jokarztimeclock.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.randallengineering.jokarztimeclock.data.models.PayMode
import com.randallengineering.jokarztimeclock.data.models.Session
import com.randallengineering.jokarztimeclock.data.models.TimeclockState
import com.randallengineering.jokarztimeclock.engine.PayrollEngine
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import com.randallengineering.jokarztimeclock.ui.components.ScaledButton
import com.randallengineering.jokarztimeclock.ui.components.ScaledFilledTonalButton
import com.randallengineering.jokarztimeclock.ui.components.ScaledIconButton
import com.randallengineering.jokarztimeclock.ui.components.expressiveClickable
import com.randallengineering.jokarztimeclock.ui.components.ExpressiveAlertDialog
import com.randallengineering.jokarztimeclock.ui.components.DialogBackButton
import com.randallengineering.jokarztimeclock.ui.components.rememberPressScaleSource
import com.randallengineering.jokarztimeclock.ui.theme.PillShape
import java.util.Locale

enum class OtFilter {
    ALL, PENDING, SUBMITTED
}

@Composable
fun OvertimeSystemInputDialog(
    state: TimeclockState,
    onDismiss: () -> Unit,
    onTogglePutInSystem: (sessionIndex: Int, isPutIn: Boolean) -> Unit,
    onMarkAllPutInSystem: (sessionIndices: List<Int>, isPutIn: Boolean) -> Unit,
    onEditSession: (sessionIndex: Int, session: Session) -> Unit
) {
    val context = LocalContext.current
    var currentFilter by remember { mutableStateOf(OtFilter.ALL) }

    val allOtSessions = remember(state.sessions, state.settings) {
        PayrollEngine.getOtSessions(state)
    }

    val pendingOtSessions = remember(allOtSessions) {
        allOtSessions.filter { !it.second.isPutInSystem }
    }

    val submittedOtSessions = remember(allOtSessions) {
        allOtSessions.filter { it.second.isPutInSystem }
    }

    val displayedSessions = when (currentFilter) {
        OtFilter.ALL -> allOtSessions
        OtFilter.PENDING -> pendingOtSessions
        OtFilter.SUBMITTED -> submittedOtSessions
    }

    val totalPendingOtHours = pendingOtSessions.sumOf { PayrollEngine.calculateSessionOt(it.second, state) }
    val totalSubmittedOtHours = submittedOtSessions.sumOf { PayrollEngine.calculateSessionOt(it.second, state) }

    val rate = if (state.displayMode == PayMode.GROSS) state.grossRate else state.netRate
    val pendingOtEarnings = totalPendingOtHours * rate * state.settings.otMultiplier

    val sdfDay = SimpleDateFormat("EEE, MMM d, yyyy", Locale.US)
    val sdfTime = SimpleDateFormat("h:mm a", Locale.US)

    ExpressiveAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.FactCheck,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Overtime System Input",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Track overtime to enter into payroll",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (allOtSessions.isNotEmpty()) {
                    ScaledIconButton(
                        onClick = {
                            val sb = StringBuilder()
                            sb.append("Randall Engineering Overtime Summary\n")
                            sb.append("------------------------------------\n")
                            sb.append("Total Pending OT: ${String.format(Locale.US, "%.2f", totalPendingOtHours)} hrs\n\n")
                            allOtSessions.forEach { (_, sess) ->
                                val ot = PayrollEngine.calculateSessionOt(sess, state)
                                val status = if (sess.isPutInSystem) "[INPUT]" else "[PENDING]"
                                val dateStr = sdfDay.format(Date(sess.start))
                                val durStr = PayrollEngine.formatDurationShort(sess.end - sess.start)
                                sb.append("$status $dateStr: ${String.format(Locale.US, "%.2f", ot)}h OT (Shift: $durStr)")
                                if (sess.note.isNotBlank()) sb.append(" - Note: ${sess.note}")
                                sb.append("\n")
                            }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("OT Summary", sb.toString()))
                            Toast.makeText(context, "Overtime summary copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Copy Summary",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Top Summary KPI Cards
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Pending Card
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = if (totalPendingOtHours > 0) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.HourglassTop,
                                    contentDescription = null,
                                    tint = if (totalPendingOtHours > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "PENDING INPUT",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (totalPendingOtHours > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${String.format(Locale.US, "%.2f", totalPendingOtHours)} hrs",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (totalPendingOtHours > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                            if (!state.settings.hideMoneyAmounts && totalPendingOtHours > 0) {
                                Text(
                                    text = PayrollEngine.formatMoney(pendingOtEarnings),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Submitted Card
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "ENTERED / DONE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${String.format(Locale.US, "%.2f", totalSubmittedOtHours)} hrs",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${submittedOtSessions.size} shifts",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Filter Chips & Mark All Action
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OtFilterChip(
                            selected = currentFilter == OtFilter.ALL,
                            onClick = { currentFilter = OtFilter.ALL },
                            label = { Text("All (${allOtSessions.size})", style = MaterialTheme.typography.bodySmall) },
                        )
                        OtFilterChip(
                            selected = currentFilter == OtFilter.PENDING,
                            onClick = { currentFilter = OtFilter.PENDING },
                            label = { Text("Pending (${pendingOtSessions.size})", style = MaterialTheme.typography.bodySmall) },
                        )
                        OtFilterChip(
                            selected = currentFilter == OtFilter.SUBMITTED,
                            onClick = { currentFilter = OtFilter.SUBMITTED },
                            label = { Text("Done (${submittedOtSessions.size})", style = MaterialTheme.typography.bodySmall) },
                        )
                    }
                }

                if (pendingOtSessions.isNotEmpty() && (currentFilter == OtFilter.ALL || currentFilter == OtFilter.PENDING)) {
                    Spacer(modifier = Modifier.height(6.dp))
                    ScaledFilledTonalButton(
                        onClick = {
                            val indicesToMark = pendingOtSessions.map { it.first }
                            onMarkAllPutInSystem(indicesToMark, true)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Mark All Pending as Input (${pendingOtSessions.size})", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(6.dp))

                // Sessions List
                if (displayedSessions.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (currentFilter == OtFilter.PENDING && allOtSessions.isNotEmpty()) "All overtime shifts have been put into system!" else "No overtime shifts found",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    ) {
                        items(displayedSessions, key = { it.second.id }) { (origIndex, session) ->
                            val otHours = PayrollEngine.calculateSessionOt(session, state)
                            val cal = Calendar.getInstance().apply { timeInMillis = session.start }
                            val isMonThu = cal.get(Calendar.DAY_OF_WEEK) in Calendar.MONDAY..Calendar.THURSDAY
                            val durMs = session.end - session.start
                            val durStr = PayrollEngine.formatDurationShort(durMs)
                            val otPay = otHours * rate * state.settings.otMultiplier

                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = if (session.isPutInSystem) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                tonalElevation = if (session.isPutInSystem) 0.dp else 2.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    // Checkbox for Boolean state
                                    Checkbox(
                                        checked = session.isPutInSystem,
                                        onCheckedChange = { isChecked ->
                                            onTogglePutInSystem(origIndex, isChecked)
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = MaterialTheme.colorScheme.tertiary,
                                            checkmarkColor = MaterialTheme.colorScheme.onTertiary
                                        )
                                    )

                                    Spacer(modifier = Modifier.width(6.dp))

                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .expressiveClickable { onEditSession(origIndex, session) }
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = sdfDay.format(Date(session.start)),
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = if (session.isPutInSystem) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                                            )
                                            // Overtime Badge
                                            Surface(
                                                shape = MaterialTheme.shapes.extraSmall,
                                                color = if (session.isPutInSystem) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer
                                            ) {
                                                Text(
                                                    text = "+${String.format(Locale.US, "%.2f", otHours)}h OT",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = if (session.isPutInSystem) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))

                                        Text(
                                            text = "${sdfTime.format(Date(session.start))} → ${sdfTime.format(Date(session.end))} ($durStr shift)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Spacer(modifier = Modifier.height(3.dp))

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = MaterialTheme.shapes.extraSmall,
                                                color = MaterialTheme.colorScheme.surface
                                            ) {
                                                Text(
                                                    text = if (isMonThu) "Weekday Shift (>12.5h Cliff)" else "Weekend Overtime",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }

                                            if (!state.settings.hideMoneyAmounts) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = PayrollEngine.formatMoney(otPay),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.tertiary
                                                )
                                            }
                                        }

                                        if (session.note.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Rounded.Notes,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(11.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = session.note,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { DialogBackButton("Close", filled = true, onClick = onDismiss) }
    )
}

/** One of the All / Pending / Done filters: an M3 filter chip, pill-shaped, with the press-scale. */
@Composable
private fun OtFilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit) {
    val (interaction, pressScale) = rememberPressScaleSource()
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        shape = PillShape,
        interactionSource = interaction,
        modifier = pressScale
    )
}
