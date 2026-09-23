package com.randallengineering.jokarztimeclock.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.randallengineering.jokarztimeclock.data.models.Session
import com.randallengineering.jokarztimeclock.engine.ShiftTimeMath
import com.randallengineering.jokarztimeclock.ui.theme.EmeraldSuccess
import com.randallengineering.jokarztimeclock.ui.theme.RoseError
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Shift editing with **full date + time control** on both ends.
 *
 * Both ends are chosen with the real Material 3 `DatePicker` and `TimePicker` (never free text) and
 * the pair is validated with [ShiftTimeMath] before it can be saved: the stop must be strictly after
 * the start, so a shift running 22:00 -> 06:00 the next morning is accepted (and priced as 8h) while
 * an end that precedes the start is refused with an inline error instead of being stored.
 *
 * Dates are handled as absolute instants ([ShiftTimeMath]) — see that file's header for the
 * timezone/DST policy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSessionDialog(
    session: Session,
    onDismiss: () -> Unit,
    onSave: (startMs: Long, endMs: Long, note: String, isPutInSystem: Boolean) -> Unit,
    onDelete: () -> Unit
) {
    var startMs by remember { mutableLongStateOf(session.start) }
    var endMs by remember { mutableLongStateOf(session.end) }
    var noteText by remember { mutableStateOf(session.note) }
    var isPutInSystem by remember { mutableStateOf(session.isPutInSystem) }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    val sdfDate = remember { SimpleDateFormat("EEE, MMM d, yyyy", Locale.US) }
    val sdfTime = remember { SimpleDateFormat("h:mm a", Locale.US) }

    val validation = ShiftTimeMath.validate(startMs, endMs)
    val errorText = ShiftTimeMath.errorMessage(validation)
    val durationMs = ShiftTimeMath.durationMs(startMs, endMs)
    val crossesMidnight = ShiftTimeMath.isOvernight(startMs, endMs)
    val endsOnSameDayAsStart = ShiftTimeMath.startOfDayMs(startMs) == ShiftTimeMath.startOfDayMs(endMs)

    if (showStartDatePicker) {
        MaterialDatePickerDialog(
            initialDateMs = ShiftTimeMath.toPickerDateMs(startMs),
            onDismiss = { showStartDatePicker = false },
            onConfirm = { pickedUtcDate ->
                val movedStart = ShiftTimeMath.applyPickedDate(startMs, pickedUtcDate)
                val keepDuration = endMs - startMs
                startMs = movedStart
                // Moving the start date carries the stop along, so an overnight shift stays
                // 22:00 -> 06:00 on consecutive dates instead of collapsing to a negative range.
                if (keepDuration > 0L) endMs = movedStart + keepDuration
                showStartDatePicker = false
            }
        )
    }

    if (showStartTimePicker) {
        val c = Calendar.getInstance().apply { timeInMillis = startMs }
        MaterialTimePickerDialog(
            title = "Shift Start Time",
            initialHour = c.get(Calendar.HOUR_OF_DAY),
            initialMinute = c.get(Calendar.MINUTE),
            onDismiss = { showStartTimePicker = false },
            onConfirm = { h, m ->
                startMs = ShiftTimeMath.applyPickedTime(startMs, h, m)
                showStartTimePicker = false
            }
        )
    }

    if (showEndDatePicker) {
        MaterialDatePickerDialog(
            initialDateMs = ShiftTimeMath.toPickerDateMs(endMs),
            onDismiss = { showEndDatePicker = false },
            onConfirm = { pickedUtcDate ->
                endMs = ShiftTimeMath.applyPickedDate(endMs, pickedUtcDate)
                showEndDatePicker = false
            }
        )
    }

    if (showEndTimePicker) {
        val c = Calendar.getInstance().apply { timeInMillis = endMs }
        MaterialTimePickerDialog(
            title = "Shift End Time",
            initialHour = c.get(Calendar.HOUR_OF_DAY),
            initialMinute = c.get(Calendar.MINUTE),
            onDismiss = { showEndTimePicker = false },
            onConfirm = { h, m ->
                endMs = ShiftTimeMath.applyPickedTime(endMs, h, m)
                showEndTimePicker = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Shift", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Tap the date or the time of either end. Overnight shifts are fine — the stop date just moves forward.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                ShiftEndField(
                    label = "START",
                    dateText = sdfDate.format(Date(startMs)),
                    timeText = sdfTime.format(Date(startMs)),
                    onPickDate = { showStartDatePicker = true },
                    onPickTime = { showStartTimePicker = true }
                )

                Spacer(modifier = Modifier.height(10.dp))

                ShiftEndField(
                    label = "STOP",
                    dateText = sdfDate.format(Date(endMs)),
                    timeText = sdfTime.format(Date(endMs)),
                    onPickDate = { showEndDatePicker = true },
                    onPickTime = { showEndTimePicker = true }
                )

                if (endsOnSameDayAsStart) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(onClick = { endMs = ShiftTimeMath.nextDaySameTime(endMs) }) {
                        Icon(Icons.Filled.NightsStay, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("This shift ends the next morning", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = if (errorText == null) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        if (errorText == null) {
                            Text(
                                text = "Duration ${ShiftTimeMath.formatDurationShort(durationMs)}" +
                                    if (crossesMidnight) " • crosses midnight" else "",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            Text(
                                text = errorText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Shift Notes / Job Code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth().clickable { isPutInSystem = !isPutInSystem }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Entered into Payroll System", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Check when overtime has been submitted", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Checkbox(checked = isPutInSystem, onCheckedChange = { isPutInSystem = it })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = errorText == null,
                onClick = { onSave(startMs, endMs, noteText.trim(), isPutInSystem) }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = RoseError)
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text("Delete")
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

/**
 * Edits the start of the **running** shift: date *and* time, with a native `DatePicker` /
 * `TimePicker`. A start in the future is refused inline.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditActiveTimerDialog(
    currentStartMs: Long,
    onDismiss: () -> Unit,
    onSave: (newStartMs: Long) -> Unit
) {
    var startMs by remember { mutableLongStateOf(currentStartMs) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val sdfDate = remember { SimpleDateFormat("EEE, MMM d, yyyy", Locale.US) }
    val sdfTime = remember { SimpleDateFormat("h:mm a", Locale.US) }
    val now = System.currentTimeMillis()
    val isFuture = startMs > now
    val errorText = if (isFuture) "The shift cannot start in the future." else null

    if (showDatePicker) {
        MaterialDatePickerDialog(
            initialDateMs = ShiftTimeMath.toPickerDateMs(startMs),
            onDismiss = { showDatePicker = false },
            onConfirm = { pickedUtcDate ->
                startMs = ShiftTimeMath.applyPickedDate(startMs, pickedUtcDate)
                showDatePicker = false
            }
        )
    }

    if (showTimePicker) {
        val c = Calendar.getInstance().apply { timeInMillis = startMs }
        MaterialTimePickerDialog(
            title = "Shift Start Time",
            initialHour = c.get(Calendar.HOUR_OF_DAY),
            initialMinute = c.get(Calendar.MINUTE),
            onDismiss = { showTimePicker = false },
            onConfirm = { h, m ->
                startMs = ShiftTimeMath.applyPickedTime(startMs, h, m)
                showTimePicker = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Running Shift Start", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Clock-in time for the shift that is running right now. The timer recomputes from this instant.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                ShiftEndField(
                    label = "STARTED",
                    dateText = sdfDate.format(Date(startMs)),
                    timeText = sdfTime.format(Date(startMs)),
                    onPickDate = { showDatePicker = true },
                    onPickTime = { showTimePicker = true }
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = errorText ?: "Elapsed ${ShiftTimeMath.formatDurationShort(now - startMs)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (errorText != null) MaterialTheme.colorScheme.error else EmeraldSuccess
                )
            }
        },
        confirmButton = {
            Button(
                enabled = errorText == null,
                onClick = { onSave(startMs) }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/** A labelled row with an independently tappable date chip and time chip. */
@Composable
private fun ShiftEndField(
    label: String,
    dateText: String,
    timeText: String,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.weight(1.4f).clickable(onClick = onPickDate)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.CalendarMonth,
                        contentDescription = "Change $label date",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(dateText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.weight(1f).clickable(onClick = onPickTime)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.AccessTime,
                        contentDescription = "Change $label time",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(timeText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
