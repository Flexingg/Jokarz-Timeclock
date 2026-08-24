package com.randallengineering.jokarztimeclock.ui.dialogs

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Location
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.location.LocationServices
import com.randallengineering.jokarztimeclock.data.models.AppSettings
import com.randallengineering.jokarztimeclock.data.models.PaySchedule
import com.randallengineering.jokarztimeclock.data.models.PeriodTotals
import com.randallengineering.jokarztimeclock.data.models.PtoEntry
import com.randallengineering.jokarztimeclock.data.models.PtoType
import com.randallengineering.jokarztimeclock.data.models.Session
import com.randallengineering.jokarztimeclock.data.models.ThemeMode
import com.randallengineering.jokarztimeclock.data.models.TimeclockState
import com.randallengineering.jokarztimeclock.engine.PayrollEngine
import com.randallengineering.jokarztimeclock.ui.components.WeeklyChart
import com.randallengineering.jokarztimeclock.ui.theme.EmeraldSuccess
import com.randallengineering.jokarztimeclock.ui.theme.PurplePrimary
import com.randallengineering.jokarztimeclock.ui.theme.RoseError
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialTimePickerDialog(
    title: String = "Select Time",
    initialHour: Int = 8,
    initialMinute: Int = 0,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    val timeState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = false
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                TimePicker(state = timeState)
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(timeState.hour, timeState.minute)
            }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialDatePickerDialog(
    initialDateMs: Long = System.currentTimeMillis(),
    onDismiss: () -> Unit,
    onConfirm: (selectedDateMs: Long) -> Unit
) {
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = initialDateMs
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                dateState.selectedDateMillis?.let { onConfirm(it) }
            }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        DatePicker(state = dateState)
    }
}

@Composable
fun EditActiveTimerDialog(
    currentStartMs: Long,
    onDismiss: () -> Unit,
    onSave: (newStartMs: Long) -> Unit
) {
    val cal = Calendar.getInstance().apply { timeInMillis = currentStartMs }
    var selectedHour by remember { mutableStateOf(cal.get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember { mutableStateOf(cal.get(Calendar.MINUTE)) }
    var showTimePicker by remember { mutableStateOf(false) }

    val sdfTime = SimpleDateFormat("h:mm a", Locale.US)
    val timeDisplay = remember(selectedHour, selectedMinute) {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, selectedHour)
            set(Calendar.MINUTE, selectedMinute)
        }
        sdfTime.format(c.time)
    }

    if (showTimePicker) {
        MaterialTimePickerDialog(
            title = "Select Shift Start Time",
            initialHour = selectedHour,
            initialMinute = selectedMinute,
            onDismiss = { showTimePicker = false },
            onConfirm = { h, m ->
                selectedHour = h
                selectedMinute = m
                showTimePicker = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Active Shift Start", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "Tap below to adjust today's clock-in time using the native clock:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTimePicker = true }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccessTime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = timeDisplay,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newCal = Calendar.getInstance().apply {
                        timeInMillis = currentStartMs
                        set(Calendar.HOUR_OF_DAY, selectedHour)
                        set(Calendar.MINUTE, selectedMinute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onSave(newCal.timeInMillis)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun EditSessionDialog(
    session: Session,
    onDismiss: () -> Unit,
    onSave: (startMs: Long, endMs: Long, note: String) -> Unit,
    onDelete: () -> Unit
) {
    val calStart = Calendar.getInstance().apply { timeInMillis = session.start }
    val calEnd = Calendar.getInstance().apply { timeInMillis = session.end }

    var startMs by remember { mutableLongStateOf(session.start) }
    var endMs by remember { mutableLongStateOf(session.end) }
    var noteText by remember { mutableStateOf(session.note) }

    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    val sdf = SimpleDateFormat("EEE, MMM d • h:mm a", Locale.US)

    if (showStartTimePicker) {
        val c = Calendar.getInstance().apply { timeInMillis = startMs }
        MaterialTimePickerDialog(
            title = "Select Shift Start Time",
            initialHour = c.get(Calendar.HOUR_OF_DAY),
            initialMinute = c.get(Calendar.MINUTE),
            onDismiss = { showStartTimePicker = false },
            onConfirm = { h, m ->
                c.set(Calendar.HOUR_OF_DAY, h)
                c.set(Calendar.MINUTE, m)
                startMs = c.timeInMillis
                showStartTimePicker = false
            }
        )
    }

    if (showEndTimePicker) {
        val c = Calendar.getInstance().apply { timeInMillis = endMs }
        MaterialTimePickerDialog(
            title = "Select Shift End Time",
            initialHour = c.get(Calendar.HOUR_OF_DAY),
            initialMinute = c.get(Calendar.MINUTE),
            onDismiss = { showEndTimePicker = false },
            onConfirm = { h, m ->
                c.set(Calendar.HOUR_OF_DAY, h)
                c.set(Calendar.MINUTE, m)
                endMs = c.timeInMillis
                showEndTimePicker = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Completed Shift", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("START TIME", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth().clickable { showStartTimePicker = true }
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(sdf.format(Date(startMs)), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("END TIME", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth().clickable { showEndTimePicker = true }
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(sdf.format(Date(endMs)), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(startMs, endMs, noteText.trim())
                }
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

@Composable
fun AddManualShiftDialog(
    onDismiss: () -> Unit,
    onSave: (startMs: Long, endMs: Long, note: String) -> Unit
) {
    val now = System.currentTimeMillis()
    val defaultStart = now - (8L * 3600000L)

    var startMs by remember { mutableLongStateOf(defaultStart) }
    var endMs by remember { mutableLongStateOf(now) }
    var noteText by remember { mutableStateOf("") }

    var showDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    val sdfDate = SimpleDateFormat("EEE, MMM d, yyyy", Locale.US)
    val sdfTime = SimpleDateFormat("h:mm a", Locale.US)

    if (showDatePicker) {
        MaterialDatePickerDialog(
            initialDateMs = startMs,
            onDismiss = { showDatePicker = false },
            onConfirm = { dateMillis ->
                val calOrig = Calendar.getInstance().apply { timeInMillis = startMs }
                val calNew = Calendar.getInstance().apply {
                    timeInMillis = dateMillis
                    set(Calendar.HOUR_OF_DAY, calOrig.get(Calendar.HOUR_OF_DAY))
                    set(Calendar.MINUTE, calOrig.get(Calendar.MINUTE))
                }
                val diff = endMs - startMs
                startMs = calNew.timeInMillis
                endMs = startMs + diff
                showDatePicker = false
            }
        )
    }

    if (showStartTimePicker) {
        val c = Calendar.getInstance().apply { timeInMillis = startMs }
        MaterialTimePickerDialog(
            title = "Select Shift Start Time",
            initialHour = c.get(Calendar.HOUR_OF_DAY),
            initialMinute = c.get(Calendar.MINUTE),
            onDismiss = { showStartTimePicker = false },
            onConfirm = { h, m ->
                c.set(Calendar.HOUR_OF_DAY, h)
                c.set(Calendar.MINUTE, m)
                startMs = c.timeInMillis
                showStartTimePicker = false
            }
        )
    }

    if (showEndTimePicker) {
        val c = Calendar.getInstance().apply { timeInMillis = endMs }
        MaterialTimePickerDialog(
            title = "Select Shift End Time",
            initialHour = c.get(Calendar.HOUR_OF_DAY),
            initialMinute = c.get(Calendar.MINUTE),
            onDismiss = { showEndTimePicker = false },
            onConfirm = { h, m ->
                c.set(Calendar.HOUR_OF_DAY, h)
                c.set(Calendar.MINUTE, m)
                endMs = c.timeInMillis
                showEndTimePicker = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Past Shift", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("SHIFT DATE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(sdfDate.format(Date(startMs)), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("START TIME", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth().clickable { showStartTimePicker = true }
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(sdfTime.format(Date(startMs)), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("END TIME", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth().clickable { showEndTimePicker = true }
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(sdfTime.format(Date(endMs)), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Notes / Job Code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(startMs, endMs, noteText.trim())
                }
            ) {
                Text("Add Shift")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun PtoManagementDialog(
    ptoEntries: List<PtoEntry>,
    onDismiss: () -> Unit,
    onAddPto: (dateMs: Long, hours: Double, type: PtoType, note: String) -> Unit,
    onDeletePto: (id: String) -> Unit
) {
    var dateMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var hoursText by remember { mutableStateOf("10.0") }
    var selectedType by remember { mutableStateOf(PtoType.PTO) }
    var noteText by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }

    val sdfDate = SimpleDateFormat("EEE, MMM d, yyyy", Locale.US)

    if (showDatePicker) {
        MaterialDatePickerDialog(
            initialDateMs = dateMs,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                dateMs = it
                showDatePicker = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log PTO / Holiday Hours", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("DATE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(sdfDate.format(Date(dateMs)), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = hoursText,
                    onValueChange = { hoursText = it },
                    label = { Text("Hours") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PtoType.values().forEach { type ->
                        val isSelected = selectedType == type
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) PurplePrimary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedType = type }
                        ) {
                            Text(
                                text = type.name,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Notes (e.g. Labor Day)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        val hours = hoursText.toDoubleOrNull() ?: 0.0
                        if (hours > 0.0) {
                            onAddPto(dateMs, hours, selectedType, noteText.trim())
                            noteText = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add Entry")
                }

                if (ptoEntries.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("LOGGED PTO / HOLIDAYS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                    ptoEntries.reversed().forEach { entry ->
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column {
                                Text("${entry.type}: ${entry.hours}h", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("${sdfDate.format(Date(entry.date))} ${if (entry.note.isNotBlank()) "• " + entry.note else ""}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { onDeletePto(entry.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = RoseError)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@SuppressLint("MissingPermission")
@Composable
fun SettingsDialog(
    currentSettings: AppSettings,
    onDismiss: () -> Unit,
    onSave: (AppSettings) -> Unit
) {
    val context = LocalContext.current
    var schedule by remember { mutableStateOf(currentSettings.paySchedule) }
    var standardHours by remember { mutableDoubleStateOf(currentSettings.standardShiftHours) }
    var cliffHours by remember { mutableDoubleStateOf(currentSettings.cliffHours) }
    var otMultiplier by remember { mutableDoubleStateOf(currentSettings.otMultiplier) }
    var theme by remember { mutableStateOf(currentSettings.theme) }
    var soundEnabled by remember { mutableStateOf(currentSettings.soundEnabled) }
    var hapticEnabled by remember { mutableStateOf(currentSettings.hapticEnabled) }
    var notificationsEnabled by remember { mutableStateOf(currentSettings.notificationsEnabled) }
    var liveNotificationEnabled by remember { mutableStateOf(currentSettings.liveNotificationEnabled) }
    var autoBreak by remember { mutableStateOf(currentSettings.autoBreakDeduction) }

    // Geofencing Settings
    var geofenceEnabled by remember { mutableStateOf(currentSettings.geofenceEnabled) }
    var workLat by remember { mutableDoubleStateOf(currentSettings.workLatitude) }
    var workLng by remember { mutableDoubleStateOf(currentSettings.workLongitude) }
    var radiusMeters by remember { mutableFloatStateOf(currentSettings.geofenceRadiusMeters) }
    var addressName by remember { mutableStateOf(currentSettings.workAddressName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Tune, contentDescription = null, tint = PurplePrimary, modifier = Modifier.padding(end = 8.dp))
                Text("App Settings & Geofence", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // GOOGLE MAPS GEOFENCE SECTION
                Text("GOOGLE MAPS AUTO-CLOCK GEOFENCE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Auto Clock In/Out with Geofence", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Checkbox(checked = geofenceEnabled, onCheckedChange = { geofenceEnabled = it })
                }

                if (geofenceEnabled) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = addressName,
                        onValueChange = { addressName = it },
                        label = { Text("Work Site Name / Address") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = if (workLat != 0.0) String.format(Locale.US, "%.6f", workLat) else "",
                            onValueChange = { workLat = it.toDoubleOrNull() ?: workLat },
                            label = { Text("Latitude") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = if (workLng != 0.0) String.format(Locale.US, "%.6f", workLng) else "",
                            onValueChange = { workLng = it.toDoubleOrNull() ?: workLng },
                            label = { Text("Longitude") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    FilledTonalButton(
                        onClick = {
                            try {
                                val fused = LocationServices.getFusedLocationProviderClient(context)
                                fused.lastLocation.addOnSuccessListener { loc: Location? ->
                                    if (loc != null) {
                                        workLat = loc.latitude
                                        workLng = loc.longitude
                                        if (addressName.isBlank()) addressName = "Randall Engineering Work Site"
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Set to Current GPS Location", fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Geofence Radius: ${radiusMeters.toInt()} meters", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = radiusMeters,
                        onValueChange = { radiusMeters = it },
                        valueRange = 50f..500f,
                        steps = 8
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))

                Text("PAY SCHEDULE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                PaySchedule.values().forEach { s ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { schedule = s }
                            .padding(vertical = 4.dp)
                    ) {
                        androidx.compose.material3.RadioButton(selected = schedule == s, onClick = { schedule = s })
                        Text(s.label, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = standardHours.toString(),
                        onValueChange = { standardHours = it.toDoubleOrNull() ?: standardHours },
                        label = { Text("Standard Shift") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = cliffHours.toString(),
                        onValueChange = { cliffHours = it.toDoubleOrNull() ?: cliffHours },
                        label = { Text("OT Cliff Target") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("OVERTIME MULTIPLIER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    listOf(1.0 to "1.0x", 1.5 to "1.5x", 2.0 to "2.0x").forEach { (mult, label) ->
                        val isSelected = otMultiplier == mult
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) PurplePrimary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { otMultiplier = mult }
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("THEME & VISUALS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ThemeMode.values().forEach { t ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { theme = t }
                            .padding(vertical = 2.dp)
                    ) {
                        androidx.compose.material3.RadioButton(selected = theme == t, onClick = { theme = t })
                        Text(t.label, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Live Ongoing Notification (Android Bar)", fontSize = 12.sp)
                    Checkbox(checked = liveNotificationEnabled, onCheckedChange = { liveNotificationEnabled = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Sound Chimes & Clicks", fontSize = 12.sp)
                    Checkbox(checked = soundEnabled, onCheckedChange = { soundEnabled = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Haptic Vibration Feedback", fontSize = 12.sp)
                    Checkbox(checked = hapticEnabled, onCheckedChange = { hapticEnabled = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Shift Milestone Notifications", fontSize = 12.sp)
                    Checkbox(checked = notificationsEnabled, onCheckedChange = { notificationsEnabled = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Auto-Deduct 30m Meal After 4h", fontSize = 12.sp)
                    Checkbox(checked = autoBreak, onCheckedChange = { autoBreak = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        currentSettings.copy(
                            paySchedule = schedule,
                            standardShiftHours = standardHours,
                            cliffHours = cliffHours,
                            otMultiplier = otMultiplier,
                            theme = theme,
                            soundEnabled = soundEnabled,
                            hapticEnabled = hapticEnabled,
                            notificationsEnabled = notificationsEnabled,
                            liveNotificationEnabled = liveNotificationEnabled,
                            autoBreakDeduction = autoBreak,
                            geofenceEnabled = geofenceEnabled,
                            workLatitude = workLat,
                            workLongitude = workLng,
                            geofenceRadiusMeters = radiusMeters,
                            workAddressName = addressName
                        )
                    )
                }
            ) {
                Text("Save Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AnalyticsDialog(
    state: TimeclockState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.BarChart, contentDescription = null, tint = PurplePrimary, modifier = Modifier.padding(end = 8.dp))
                Text("Weekly Analytics", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                WeeklyChart(state = state)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
fun TimesheetReportDialog(
    state: TimeclockState,
    totals: PeriodTotals,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val sdfTime = SimpleDateFormat("h:mm a", Locale.US)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Description, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.padding(end = 8.dp))
                    Text("Timesheet Summary", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                IconButton(onClick = {
                    shareCsvTimesheet(context, state)
                }) {
                    Icon(Icons.Filled.Share, contentDescription = "Share CSV")
                }
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Total Period Payable:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${String.format("%.1f", totals.totalPayableHoursPeriod)} hrs", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = EmeraldSuccess)
                        }
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            Text("Total Overtime:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${String.format("%.1f", totals.totalOtHoursPeriod)} hrs", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = RoseError)
                        }
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            Text("Estimated Total (${state.displayMode.name}):", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(PayrollEngine.formatMoney(totals.periodEarnings), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = EmeraldSuccess)
                        }
                    }
                }

                Text("SHIFTS LOG", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                state.sessions.reversed().forEach { s ->
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column {
                            Text(sdfDate.format(Date(s.start)), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("${sdfTime.format(Date(s.start))} - ${sdfTime.format(Date(s.end))}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(PayrollEngine.formatDurationShort(s.end - s.start), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Close") }
        }
    )
}

fun shareCsvTimesheet(context: Context, state: TimeclockState) {
    try {
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val sdfTime = SimpleDateFormat("HH:mm:ss", Locale.US)
        val sdfDay = SimpleDateFormat("EEE", Locale.US)

        val csv = StringBuilder("Date,Day,Start Time,End Time,Break (Mins),Tech Duration (Hours),Tech Duration (Formatted),Notes\n")
        state.sessions.forEach { s ->
            val dStart = Date(s.start)
            val dEnd = Date(s.end)
            val breakMins = s.breakMs / 60000L
            val durHours = (s.end - s.start) / 3600000.0
            val durFmt = PayrollEngine.formatDurationShort(s.end - s.start)
            csv.append("\"${sdfDate.format(dStart)}\",\"${sdfDay.format(dStart)}\",\"${sdfTime.format(dStart)}\",\"${sdfTime.format(dEnd)}\",$breakMins,${String.format(Locale.US, "%.2f", durHours)},\"$durFmt\",\"${s.note.replace("\"", "\"\"")}\"\n")
        }

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, csv.toString())
            type = "text/csv"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share Timesheet CSV")
        context.startActivity(shareIntent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
