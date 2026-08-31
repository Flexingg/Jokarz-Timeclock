package com.randallengineering.jokarztimeclock

import android.content.Intent
import android.os.Bundle
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.randallengineering.jokarztimeclock.data.models.PayMode
import com.randallengineering.jokarztimeclock.data.models.Session
import com.randallengineering.jokarztimeclock.ui.components.GoogleClockHero
import com.randallengineering.jokarztimeclock.ui.components.GoogleSessionLogList
import com.randallengineering.jokarztimeclock.ui.components.GoogleSummaryCards
import com.randallengineering.jokarztimeclock.ui.components.GoogleWeeklySwiper
import com.randallengineering.jokarztimeclock.ui.dialogs.AddManualShiftDialog
import com.randallengineering.jokarztimeclock.ui.dialogs.AnalyticsDialog
import com.randallengineering.jokarztimeclock.ui.dialogs.EditActiveTimerDialog
import com.randallengineering.jokarztimeclock.ui.dialogs.EditSessionDialog
import com.randallengineering.jokarztimeclock.ui.dialogs.PtoManagementDialog
import com.randallengineering.jokarztimeclock.ui.dialogs.SettingsDialog
import com.randallengineering.jokarztimeclock.ui.dialogs.TimesheetReportDialog
import com.randallengineering.jokarztimeclock.ui.theme.EmeraldSuccess
import com.randallengineering.jokarztimeclock.ui.theme.JokarzTimeclockTheme
import com.randallengineering.jokarztimeclock.ui.theme.PurplePrimary
import com.randallengineering.jokarztimeclock.ui.viewmodel.TimeclockViewModel
import kotlinx.coroutines.launch

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val viewModel: TimeclockViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        setContent {
            val state by viewModel.state.collectAsState()
            val totals by viewModel.totals.collectAsState()
            val currentTick by viewModel.tick.collectAsState()

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { /* Permissions handled */ }

            LaunchedEffect(Unit) {
                val permissions = mutableListOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                permissionLauncher.launch(permissions.toTypedArray())
            }

            JokarzTimeclockTheme(themeMode = state.settings.theme) {
                GoogleTimeclockScreen(
                    viewModel = viewModel,
                    state = state,
                    totals = totals,
                    currentTick = currentTick
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null || intent.data == null) return
        val action = intent.data?.getQueryParameter("action")
        when (action) {
            "clock_in" -> if (!viewModel.state.value.isClockedIn) viewModel.toggleClock()
            "clock_out" -> if (viewModel.state.value.isClockedIn) viewModel.toggleClock()
            "toggle" -> viewModel.toggleClock()
            "break" -> viewModel.toggleBreak()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleTimeclockScreen(
    viewModel: TimeclockViewModel,
    state: com.randallengineering.jokarztimeclock.data.models.TimeclockState,
    totals: com.randallengineering.jokarztimeclock.data.models.PeriodTotals,
    currentTick: Long
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Dialog visibility states
    var showEditActiveDialog by remember { mutableStateOf(false) }
    var showAddShiftDialog by remember { mutableStateOf(false) }
    var showPtoDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showAnalyticsDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showOvertimeDialog by remember { mutableStateOf(false) }

    val pendingOtCount = remember(state.sessions, state.settings) {
        com.randallengineering.jokarztimeclock.engine.PayrollEngine.getOtSessions(state).count { !it.second.isPutInSystem }
    }

    var selectedSessionForEdit by remember { mutableStateOf<Pair<Int, Session>?>(null) }

    fun showUndoSnackbar(message: String) {
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "UNDO",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undo()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Timer,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Timeclock",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Randall Engineering",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    // Overtime System Input button with Badge
                    BadgedBox(
                        badge = {
                            if (pendingOtCount > 0) {
                                Badge(
                                    containerColor = com.randallengineering.jokarztimeclock.ui.theme.RoseError,
                                    contentColor = Color.White
                                ) {
                                    Text("$pendingOtCount", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                viewModel.audioHaptic.playClickSound(state.settings.soundEnabled)
                                showOvertimeDialog = true
                            },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (pendingOtCount > 0) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FactCheck,
                                contentDescription = "Overtime System Input",
                                tint = if (pendingOtCount > 0) com.randallengineering.jokarztimeclock.ui.theme.RoseError else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    FilledTonalIconButton(
                        onClick = {
                            val newHide = !state.settings.hideMoneyAmounts
                            viewModel.updateSettings(state.settings.copy(hideMoneyAmounts = newHide))
                            viewModel.audioHaptic.playClickSound(state.settings.soundEnabled)
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = if (state.settings.hideMoneyAmounts) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = "Toggle Money Visibility",
                            tint = if (state.settings.hideMoneyAmounts) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    FilledTonalIconButton(
                        onClick = {
                            viewModel.audioHaptic.playClickSound(state.settings.soundEnabled)
                            showAnalyticsDialog = true
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BarChart,
                            contentDescription = "Analytics",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    FilledTonalIconButton(
                        onClick = {
                            viewModel.audioHaptic.playClickSound(state.settings.soundEnabled)
                            showSettingsDialog = true
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            // Google Material 3 Segmented Rate Switcher & Input Capsule
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    // Gross vs Take Home Switcher
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        Row(modifier = Modifier.padding(3.dp)) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (state.displayMode == PayMode.GROSS) MaterialTheme.colorScheme.primary else Color.Transparent,
                                modifier = Modifier.clickable { viewModel.setMode(PayMode.GROSS) }
                            ) {
                                Text(
                                    text = "Gross",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (state.displayMode == PayMode.GROSS) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (state.displayMode == PayMode.NET) MaterialTheme.colorScheme.primary else Color.Transparent,
                                modifier = Modifier.clickable { viewModel.setMode(PayMode.NET) }
                            ) {
                                Text(
                                    text = "Take Home",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (state.displayMode == PayMode.NET) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Editable Rate
                    val currentRate = if (state.displayMode == PayMode.GROSS) state.grossRate else state.netRate
                    var rateText by remember(currentRate) { mutableStateOf<String>(String.format(Locale.US, "%.1f", currentRate)) }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AttachMoney,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        BasicTextField(
                            value = rateText,
                            onValueChange = { newText: String ->
                                rateText = newText
                                newText.toDoubleOrNull()?.let { r: Double ->
                                    viewModel.setRate(state.displayMode, r)
                                }
                            },
                            textStyle = TextStyle(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.width(48.dp)
                        )
                        Text(
                            text = "/hr",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Google Clock Hero Widget with Live Real-Time Ticking
            GoogleClockHero(
                state = state,
                currentTickMs = currentTick,
                onClockToggle = {
                    viewModel.toggleClock()
                    if (!state.isClockedIn) {
                        showUndoSnackbar("Clocked In successfully.")
                    } else {
                        showUndoSnackbar("Clocked Out successfully.")
                    }
                },
                onBreakToggle = { viewModel.toggleBreak() },
                onEditStartClick = { showEditActiveDialog = true }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Google Assist Action Chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                AssistChip(
                    onClick = {
                        viewModel.audioHaptic.playClickSound(state.settings.soundEnabled)
                        showAddShiftDialog = true
                    },
                    label = { Text("Add Shift", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    border = null,
                    modifier = Modifier.weight(1f)
                )

                AssistChip(
                    onClick = {
                        viewModel.audioHaptic.playClickSound(state.settings.soundEnabled)
                        showOvertimeDialog = true
                    },
                    label = {
                        Text(
                            text = if (pendingOtCount > 0) "OT ($pendingOtCount)" else "OT Input",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (pendingOtCount > 0) com.randallengineering.jokarztimeclock.ui.theme.RoseError else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.FactCheck,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = if (pendingOtCount > 0) com.randallengineering.jokarztimeclock.ui.theme.RoseError else MaterialTheme.colorScheme.primary
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (pendingOtCount > 0) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    border = null,
                    modifier = Modifier.weight(1f)
                )

                AssistChip(
                    onClick = {
                        viewModel.audioHaptic.playClickSound(state.settings.soundEnabled)
                        showPtoDialog = true
                    },
                    label = { Text("PTO/Hol", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = EmeraldSuccess
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    border = null,
                    modifier = Modifier.weight(1f)
                )

                AssistChip(
                    onClick = {
                        viewModel.audioHaptic.playClickSound(state.settings.soundEnabled)
                        showReportDialog = true
                    },
                    label = { Text("Report", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Description,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    border = null,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Google-styled Today & Pay Period Summary Cards
            GoogleSummaryCards(totals = totals, hideMoney = state.settings.hideMoneyAmounts)

            Spacer(modifier = Modifier.height(14.dp))

            // Weekly Swiper
            GoogleWeeklySwiper(state = state)

            Spacer(modifier = Modifier.height(14.dp))

            // Session History Log
            GoogleSessionLogList(
                sessions = state.sessions,
                onSessionClick = { idx, s ->
                    selectedSessionForEdit = Pair(idx, s)
                },
                modifier = Modifier.height(280.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Dialogs
    if (showEditActiveDialog && state.currentSessionStart != null) {
        EditActiveTimerDialog(
            currentStartMs = state.currentSessionStart,
            onDismiss = { showEditActiveDialog = false },
            onSave = { newStart ->
                viewModel.updateActiveStartTime(newStart)
                showEditActiveDialog = false
                showUndoSnackbar("Start time updated.")
            }
        )
    }

    selectedSessionForEdit?.let { (idx, session) ->
        EditSessionDialog(
            session = session,
            onDismiss = { selectedSessionForEdit = null },
            onSave = { start, end, note, isPutIn ->
                viewModel.updateSession(idx, start, end, note, isPutIn)
                selectedSessionForEdit = null
                showUndoSnackbar("Session updated.")
            },
            onDelete = {
                viewModel.deleteSession(idx)
                selectedSessionForEdit = null
                showUndoSnackbar("Session deleted.")
            }
        )
    }

    if (showAddShiftDialog) {
        AddManualShiftDialog(
            onDismiss = { showAddShiftDialog = false },
            onSave = { start, end, note, isPutIn ->
                viewModel.addManualSession(start, end, note, isPutIn)
                showAddShiftDialog = false
                showUndoSnackbar("Manual shift added.")
            }
        )
    }

    if (showOvertimeDialog) {
        com.randallengineering.jokarztimeclock.ui.dialogs.OvertimeSystemInputDialog(
            state = state,
            onDismiss = { showOvertimeDialog = false },
            onTogglePutInSystem = { origIndex, isPutIn ->
                viewModel.setSessionPutInSystem(origIndex, isPutIn)
            },
            onMarkAllPutInSystem = { indices, isPutIn ->
                viewModel.markAllOtPutInSystem(indices, isPutIn)
                showUndoSnackbar("Marked ${indices.size} shifts as input.")
            },
            onEditSession = { origIndex, session ->
                selectedSessionForEdit = Pair(origIndex, session)
            }
        )
    }

    if (showPtoDialog) {
        PtoManagementDialog(
            ptoEntries = state.ptoEntries,
            onDismiss = { showPtoDialog = false },
            onAddPto = { date, hours, type, note ->
                viewModel.addPto(date, hours, type, note)
            },
            onDeletePto = { id ->
                viewModel.deletePto(id)
            }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            currentSettings = state.settings,
            onDismiss = { showSettingsDialog = false },
            onSave = { newSettings ->
                viewModel.updateSettings(newSettings)
                showSettingsDialog = false
            }
        )
    }

    if (showAnalyticsDialog) {
        AnalyticsDialog(
            state = state,
            onDismiss = { showAnalyticsDialog = false }
        )
    }

    if (showReportDialog) {
        TimesheetReportDialog(
            state = state,
            totals = totals,
            onDismiss = { showReportDialog = false }
        )
    }
}
