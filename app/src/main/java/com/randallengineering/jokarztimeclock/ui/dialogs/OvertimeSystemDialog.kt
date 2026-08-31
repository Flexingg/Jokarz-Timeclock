package com.randallengineering.jokarztimeclock.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.randallengineering.jokarztimeclock.data.models.PayMode
import com.randallengineering.jokarztimeclock.data.models.Session
import com.randallengineering.jokarztimeclock.data.models.TimeclockState
import com.randallengineering.jokarztimeclock.engine.PayrollEngine
import com.randallengineering.jokarztimeclock.ui.theme.AmberWarning
import com.randallengineering.jokarztimeclock.ui.theme.EmeraldSuccess
import com.randallengineering.jokarztimeclock.ui.theme.PurplePrimary
import com.randallengineering.jokarztimeclock.ui.theme.RoseError
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.FactCheck,
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
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Track overtime to enter into payroll",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (allOtSessions.isNotEmpty()) {
                    IconButton(
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
                            imageVector = Icons.Filled.ContentCopy,
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
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (totalPendingOtHours > 0) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.HourglassTop,
                                    contentDescription = null,
                                    tint = if (totalPendingOtHours > 0) RoseError else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "PENDING INPUT",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (totalPendingOtHours > 0) RoseError else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${String.format(Locale.US, "%.2f", totalPendingOtHours)} hrs",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (totalPendingOtHours > 0) RoseError else MaterialTheme.colorScheme.onSurface
                            )
                            if (!state.settings.hideMoneyAmounts && totalPendingOtHours > 0) {
                                Text(
                                    text = PayrollEngine.formatMoney(pendingOtEarnings),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Submitted Card
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = EmeraldSuccess,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "ENTERED / DONE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldSuccess
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${String.format(Locale.US, "%.2f", totalSubmittedOtHours)} hrs",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${submittedOtSessions.size} shifts",
                                fontSize = 11.sp,
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
                        FilterChip(
                            selected = currentFilter == OtFilter.ALL,
                            onClick = { currentFilter = OtFilter.ALL },
                            label = { Text("All (${allOtSessions.size})", fontSize = 11.sp) },
                            shape = RoundedCornerShape(12.dp)
                        )
                        FilterChip(
                            selected = currentFilter == OtFilter.PENDING,
                            onClick = { currentFilter = OtFilter.PENDING },
                            label = { Text("Pending (${pendingOtSessions.size})", fontSize = 11.sp) },
                            shape = RoundedCornerShape(12.dp)
                        )
                        FilterChip(
                            selected = currentFilter == OtFilter.SUBMITTED,
                            onClick = { currentFilter = OtFilter.SUBMITTED },
                            label = { Text("Done (${submittedOtSessions.size})", fontSize = 11.sp) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                if (pendingOtSessions.isNotEmpty() && (currentFilter == OtFilter.ALL || currentFilter == OtFilter.PENDING)) {
                    Spacer(modifier = Modifier.height(6.dp))
                    FilledTonalButton(
                        onClick = {
                            val indicesToMark = pendingOtSessions.map { it.first }
                            onMarkAllPutInSystem(indicesToMark, true)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Mark All Pending as Input (${pendingOtSessions.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldSuccess.copy(alpha = 0.6f),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (currentFilter == OtFilter.PENDING && allOtSessions.isNotEmpty()) "All overtime shifts have been put into system!" else "No overtime shifts found",
                                fontSize = 12.sp,
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
                                shape = RoundedCornerShape(16.dp),
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
                                            checkedColor = EmeraldSuccess,
                                            checkmarkColor = Color.White
                                        )
                                    )

                                    Spacer(modifier = Modifier.width(6.dp))

                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { onEditSession(origIndex, session) }
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = sdfDay.format(Date(session.start)),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (session.isPutInSystem) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                                            )
                                            // Overtime Badge
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (session.isPutInSystem) EmeraldSuccess.copy(alpha = 0.2f) else RoseError.copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    text = "+${String.format(Locale.US, "%.2f", otHours)}h OT",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = if (session.isPutInSystem) EmeraldSuccess else RoseError,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))

                                        Text(
                                            text = "${sdfTime.format(Date(session.start))} → ${sdfTime.format(Date(session.end))} ($durStr shift)",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Spacer(modifier = Modifier.height(3.dp))

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.surface
                                            ) {
                                                Text(
                                                    text = if (isMonThu) "Weekday Shift (>12.5h Cliff)" else "Weekend Overtime",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }

                                            if (!state.settings.hideMoneyAmounts) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = PayrollEngine.formatMoney(otPay),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = EmeraldSuccess
                                                )
                                            }
                                        }

                                        if (session.note.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.Notes,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(11.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = session.note,
                                                    fontSize = 10.sp,
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
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
