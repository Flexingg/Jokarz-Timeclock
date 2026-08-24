package com.randallengineering.jokarztimeclock

import android.content.Intent
import android.os.Bundle
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.randallengineering.jokarztimeclock.ui.components.ClockButton
import com.randallengineering.jokarztimeclock.ui.components.LiveStatsDrawer
import com.randallengineering.jokarztimeclock.ui.components.SessionLogList
import com.randallengineering.jokarztimeclock.ui.components.SummaryCards
import com.randallengineering.jokarztimeclock.ui.components.WeeklySwiper
import com.randallengineering.jokarztimeclock.ui.dialogs.AddManualShiftDialog
import com.randallengineering.jokarztimeclock.ui.dialogs.AnalyticsDialog
import com.randallengineering.jokarztimeclock.ui.dialogs.EditActiveTimerDialog
import com.randallengineering.jokarztimeclock.ui.dialogs.EditSessionDialog
import com.randallengineering.jokarztimeclock.ui.dialogs.PtoManagementDialog
import com.randallengineering.jokarztimeclock.ui.dialogs.SettingsDialog
import com.randallengineering.jokarztimeclock.ui.dialogs.TimesheetReportDialog
import com.randallengineering.jokarztimeclock.ui.theme.AmberWarning
import com.randallengineering.jokarztimeclock.ui.theme.EmeraldSuccess
import com.randallengineering.jokarztimeclock.ui.theme.JokarzTimeclockTheme
import com.randallengineering.jokarztimeclock.ui.theme.PurplePrimary
import com.randallengineering.jokarztimeclock.ui.viewmodel.TimeclockViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: TimeclockViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        setContent {
            val state by viewModel.state.collectAsState()
            val totals by viewModel.totals.collectAsState()

            JokarzTimeclockTheme(themeMode = state.settings.theme) {
                TimeclockAppScreen(
                    viewModel = viewModel,
                    state = state,
                    totals = totals
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
fun TimeclockAppScreen(
    viewModel: TimeclockViewModel,
    state: com.randallengineering.jokarztimeclock.data.models.TimeclockState,
    totals: com.randallengineering.jokarztimeclock.data.models.PeriodTotals
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
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = PurplePrimary.copy(alpha = 0.2f),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Timer,
                                    contentDescription = null,
                                    tint = PurplePrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Jokarz Timeclock",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Randall Engineering",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.audioHaptic.playClickSound(state.settings.soundEnabled)
                        showAnalyticsDialog = true
                    }) {
                        Icon(Icons.Filled.BarChart, contentDescription = "Analytics", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = {
                        viewModel.audioHaptic.playClickSound(state.settings.soundEnabled)
                        showSettingsDialog = true
                    }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // Gross / Take Home Toggle
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Row(modifier = Modifier.padding(2.dp)) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (state.displayMode == PayMode.GROSS) PurplePrimary else Color.Transparent,
                                modifier = Modifier.clickable { viewModel.setMode(PayMode.GROSS) }
                            ) {
                                Text(
                                    text = "Gross",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (state.displayMode == PayMode.GROSS) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (state.displayMode == PayMode.NET) PurplePrimary else Color.Transparent,
                                modifier = Modifier.clickable { viewModel.setMode(PayMode.NET) }
                            ) {
                                Text(
                                    text = "Take Home",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (state.displayMode == PayMode.NET) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 14.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Quick Actions Bar + Rate Input
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable {
                            viewModel.audioHaptic.playClickSound(state.settings.soundEnabled)
                            showAddShiftDialog = true
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Filled.AddCircle, contentDescription = null, tint = PurplePrimary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Shift", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable {
                            viewModel.audioHaptic.playClickSound(state.settings.soundEnabled)
                            showPtoDialog = true
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Filled.EventAvailable, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PTO / Hol", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable {
                            viewModel.audioHaptic.playClickSound(state.settings.soundEnabled)
                            showReportDialog = true
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Filled.Summarize, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Report", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Rate Input field
                val currentRate = if (state.displayMode == PayMode.GROSS) state.grossRate else state.netRate
                var rateText by remember(currentRate) { mutableStateOf<String>(String.format(Locale.US, "%.1f", currentRate)) }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (state.displayMode == PayMode.GROSS) "Gross " else "Net ",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(Icons.Filled.AttachMoney, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                        BasicTextField(
                            value = rateText,
                            onValueChange = { newText: String ->
                                rateText = newText
                                newText.toDoubleOrNull()?.let { r: Double ->
                                    viewModel.setRate(state.displayMode, r)
                                }
                            },
                            textStyle = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            cursorBrush = SolidColor(PurplePrimary),
                            modifier = Modifier.width(42.dp)
                        )
                        Text("/hr", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Main Clock In / Out Card
            ElevatedCard(
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp, horizontal = 16.dp)
                ) {
                    ClockButton(
                        isClockedIn = state.isClockedIn,
                        onClick = {
                            viewModel.toggleClock()
                            if (!state.isClockedIn) {
                                showUndoSnackbar("Clocked In successfully.")
                            } else {
                                showUndoSnackbar("Clocked Out successfully.")
                            }
                        }
                    )

                    LiveStatsDrawer(
                        state = state,
                        onEditStartClick = { showEditActiveDialog = true },
                        onBreakToggle = { viewModel.toggleBreak() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Summary Cards (Today & Pay Period)
            SummaryCards(totals = totals)

            Spacer(modifier = Modifier.height(14.dp))

            // Weekly Swiper
            WeeklySwiper(state = state)

            Spacer(modifier = Modifier.height(14.dp))

            // Session History Log
            SessionLogList(
                sessions = state.sessions,
                onSessionClick = { idx, s ->
                    selectedSessionForEdit = Pair(idx, s)
                },
                modifier = Modifier.height(280.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
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
            onSave = { start, end, note ->
                viewModel.updateSession(idx, start, end, note)
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
            onSave = { start, end, note ->
                viewModel.addManualSession(start, end, note)
                showAddShiftDialog = false
                showUndoSnackbar("Manual shift added.")
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
