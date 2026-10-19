package com.randallengineering.jokarztimeclock

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import com.randallengineering.jokarztimeclock.ui.components.FluidCard
import com.randallengineering.jokarztimeclock.ui.components.FluidIconButton
import com.randallengineering.jokarztimeclock.ui.components.FluidIsland
import com.randallengineering.jokarztimeclock.ui.components.FluidSegmented
import com.randallengineering.jokarztimeclock.ui.components.IslandAction
import com.randallengineering.jokarztimeclock.ui.components.fluidAmbientLight
import com.randallengineering.jokarztimeclock.ui.components.fluidContour
import com.randallengineering.jokarztimeclock.ui.components.fluidPageColor
import com.randallengineering.jokarztimeclock.ui.components.fluidGlassFill
import androidx.compose.material3.Snackbar
import com.randallengineering.jokarztimeclock.ui.theme.FluidGlass
import com.randallengineering.jokarztimeclock.ui.theme.LocalFluid
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import android.os.Bundle
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.randallengineering.jokarztimeclock.engine.LiveChipStatusReader
import com.randallengineering.jokarztimeclock.engine.PermissionHelper
import com.randallengineering.jokarztimeclock.ui.components.ConfirmationBurst
import com.randallengineering.jokarztimeclock.ui.components.GoogleClockHero
import com.randallengineering.jokarztimeclock.ui.components.GoogleSessionLogList
import com.randallengineering.jokarztimeclock.ui.components.GoogleSummaryCards
import com.randallengineering.jokarztimeclock.ui.components.GoogleWeeklySwiper
import com.randallengineering.jokarztimeclock.ui.components.LaunchedChangeEffect
import com.randallengineering.jokarztimeclock.ui.components.LiveChipHealthCard
import com.randallengineering.jokarztimeclock.ui.components.rememberReducedMotion
import com.randallengineering.jokarztimeclock.ui.dialogs.AddManualShiftDialog
import com.randallengineering.jokarztimeclock.ui.dialogs.AnalyticsDialog
import com.randallengineering.jokarztimeclock.ui.dialogs.EditActiveTimerDialog
import com.randallengineering.jokarztimeclock.ui.dialogs.EditSessionDialog
import com.randallengineering.jokarztimeclock.ui.dialogs.PtoManagementDialog
import com.randallengineering.jokarztimeclock.ui.dialogs.SettingsDialog
import com.randallengineering.jokarztimeclock.ui.dialogs.TimesheetReportDialog
import com.randallengineering.jokarztimeclock.ui.theme.JokarzTimeclockTheme
import com.randallengineering.jokarztimeclock.ui.viewmodel.TimeclockViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import com.randallengineering.jokarztimeclock.ui.components.ScaledFilledTonalIconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import com.randallengineering.jokarztimeclock.engine.InputValidation
import com.randallengineering.jokarztimeclock.ui.components.ConnectedToggleGroup
import com.randallengineering.jokarztimeclock.ui.theme.ExpressiveButtonSize
import com.randallengineering.jokarztimeclock.ui.theme.PillShape
import com.randallengineering.jokarztimeclock.ui.components.ScaledAssistChip
import com.randallengineering.jokarztimeclock.ui.components.expressiveClickable

/** Short tick on the clock in/out tap, through the existing AudioHapticEngine. */
private const val HAPTIC_TAP_MS = 20L

/** Second, firmer beat once the new shift state is confirmed. */
private const val HAPTIC_CONFIRM_MS = 40L
private const val HAPTIC_CONFIRM_DELAY_MS = 140L

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
                // POST_NOTIFICATIONS is requested separately, with an explanation, from the
                // live-chip health card (see LiveChipHealthCard) so the owner knows why.
                permissionLauncher.launch(permissions.toTypedArray())
            }

            JokarzTimeclockTheme(themeMode = state.settings.theme, reduceTransparency = state.settings.reduceTransparency) {
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

    // ---- Live-chip health (notifications + ColorOS battery management) ----------------------
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? android.app.Activity
    var notificationsGranted by remember { mutableStateOf(PermissionHelper.hasNotificationPermission(context)) }
    var batteryExempt by remember { mutableStateOf(PermissionHelper.isIgnoringBatteryOptimizations(context)) }
    var chipSnapshot by remember { mutableStateOf(LiveChipStatusReader.read(context)) }

    // Re-read the Live Update verdict when the live notification's inputs change. One-shot, not a
    // loop: the short wait only lets the service post (or cancel) before the notification is read back.
    LaunchedEffect(
        state.isClockedIn, state.isOnBreak, state.currentSessionStart, state.settings.liveNotificationEnabled
    ) {
        delay(1500)
        chipSnapshot = LiveChipStatusReader.read(context)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsGranted = granted
        // Degraded mode: the app is fully usable without it — only the live chip is lost.
        if (!granted) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    "Notifications stay off — clocking in/out still works, the live status bar timer will not show."
                )
            }
        }
    }

    // Re-check whenever the screen comes back (the owner may have changed settings meanwhile).
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                notificationsGranted = PermissionHelper.hasNotificationPermission(context)
                batteryExempt = PermissionHelper.isIgnoringBatteryOptimizations(context)
                chipSnapshot = LiveChipStatusReader.read(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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

    // Bumped on each successful import so the confirmation badge plays for it too.
    var importConfirmations by remember { mutableIntStateOf(0) }
    val reducedMotion = rememberReducedMotion()

    // Confirmation haptic once the shift state has actually changed (from the button, a geofence or
    // Tasker). One-shot, just far enough after the tap haptic to be felt as a second beat.
    LaunchedChangeEffect(state.isClockedIn to state.isOnBreak) {
        if (state.settings.hapticEnabled) {
            delay(HAPTIC_CONFIRM_DELAY_MS)
            viewModel.audioHaptic.vibrate(HAPTIC_CONFIRM_MS)
        }
    }

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

    // The Living Flow shell: content runs edge to edge as the blur source, under a frosted top bar
    // and above a floating island; the page carries a soft primary-coloured wash.
    val hazeState = remember { HazeState() }
    CompositionLocalProvider(LocalFluid provides LocalFluid.current.copy(hazeState = hazeState)) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .fluidAmbientLight()
                    .haze(hazeState)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(modifier = Modifier.height(TOP_BAR_HEIGHT))

                    // Always visible, straight from BuildConfig, so the owner can see which build is installed.
                    Text(
                        text = AppVersion.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Gross / Take Home + hourly rate capsule
                    FluidCard(shape = PillShape, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                        ) {
                            // Gross vs Take Home: a segmented pill whose indicator glides between options.
                            FluidSegmented(
                                options = listOf(PayMode.GROSS, PayMode.NET),
                                selected = state.displayMode,
                                onSelect = { viewModel.setMode(it) },
                                label = { if (it == PayMode.GROSS) "Gross" else "Take Home" },
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // Editable Rate
                            val currentRate = if (state.displayMode == PayMode.GROSS) state.grossRate else state.netRate
                            var rateText by remember(currentRate) { mutableStateOf<String>(String.format(Locale.US, "%.1f", currentRate)) }
                            // Only a valid rate is applied; anything else stays on screen in the error colour.
                            val rateCheck = InputValidation.rate(rateText)

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AttachMoney,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                BasicTextField(
                                    value = rateText,
                                    onValueChange = { newText: String ->
                                        rateText = newText
                                        InputValidation.rate(newText).value?.let { r: Double ->
                                            viewModel.setRate(state.displayMode, r)
                                        }
                                    },
                                    textStyle = MaterialTheme.typography.titleSmall.copy(
                                        color = if (rateCheck.ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                                    ),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier
                                        .width(52.dp)
                                        .semantics { if (!rateCheck.ok) error(rateCheck.error ?: "Invalid rate") }
                                )
                                Text(
                                    text = "/hr",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // The hero stage with Live Real-Time Ticking, plus the "done" badge that pops
                    // in its top-end corner (away from the timer) after a clock in/out or an import.
                    Box(contentAlignment = Alignment.TopEnd) {
                        GoogleClockHero(
                            state = state,
                            currentTickMs = currentTick,
                            onClockToggle = {
                                // Fire-and-forget: Vibrator.vibrate() returns immediately, so the state
                                // change below is never held up by the haptic.
                                if (state.settings.hapticEnabled) viewModel.audioHaptic.vibrate(HAPTIC_TAP_MS)
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
                        ConfirmationBurst(
                            trigger = state.isClockedIn to importConfirmations,
                            reducedMotion = reducedMotion,
                            modifier = Modifier.padding(top = 20.dp, end = 14.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ColourOS / battery / notification reality check — renders only when something is wrong.
                    LiveChipHealthCard(
                        isClockedIn = state.isClockedIn,
                        liveNotificationEnabled = state.settings.liveNotificationEnabled,
                        notificationsPermissionGranted = notificationsGranted,
                        batteryExempt = batteryExempt,
                        chipSnapshot = chipSnapshot,
                        versionLabel = AppVersion.label,
                        onAllowNotifications = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                activity?.let { PermissionHelper.openNotificationSettings(it) }
                            }
                        },
                        onRequestBatteryExemption = {
                            val launched = activity?.let { PermissionHelper.requestIgnoreBatteryOptimizations(it) } ?: false
                            if (launched != true) {
                                activity?.let { PermissionHelper.openAutostartSettings(it) }
                            }
                        },
                        onOpenAutostart = {
                            activity?.let { PermissionHelper.openAutostartSettings(it) }
                        },
                        onOpenNotificationSettings = {
                            activity?.let { PermissionHelper.openNotificationSettings(it) }
                        },
                        onOpenPromotionSettings = {
                            activity?.let { LiveChipStatusReader.openPromotionSettings(it) }
                        },
                        onRecheckChip = { chipSnapshot = LiveChipStatusReader.read(context) }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Today & Pay Period summary cards
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
                        // PeriodTotals' end is the last millisecond of the period; the filter wants [start, end).
                        payPeriod = totals.startOfPeriod to totals.endOfPeriod + 1L,
                        onAddShift = { showAddShiftDialog = true }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Room to scroll the last card clear of the floating island.
                    Spacer(modifier = Modifier.height(112.dp))
                }
            }

            FluidTopBar(
                pendingOtCount = pendingOtCount,
                moneyHidden = state.settings.hideMoneyAmounts,
                onOvertime = {
                    viewModel.audioHaptic.playClickSound(state.settings.soundEnabled)
                    showOvertimeDialog = true
                },
                onToggleMoney = {
                    viewModel.updateSettings(state.settings.copy(hideMoneyAmounts = !state.settings.hideMoneyAmounts))
                    viewModel.audioHaptic.playClickSound(state.settings.soundEnabled)
                },
                onAnalytics = {
                    viewModel.audioHaptic.playClickSound(state.settings.soundEnabled)
                    showAnalyticsDialog = true
                },
                onSettings = {
                    viewModel.audioHaptic.playClickSound(state.settings.soundEnabled)
                    showSettingsDialog = true
                },
                modifier = Modifier.align(Alignment.TopCenter)
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // Toasts float as glass pills above the island.
                SnackbarHost(snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        shape = PillShape,
                        containerColor = fluidGlassFill(FluidGlass.regular).copy(alpha = 1f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        actionColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(bottom = 10.dp)
                            .border(fluidContour(), PillShape)
                    )
                }
                FluidIsland(
                    primary = IslandAction("Add Shift", Icons.Rounded.Add, onClick = {
                        viewModel.audioHaptic.playClickSound(state.settings.soundEnabled)
                        showAddShiftDialog = true
                    }),
                    actions = listOf(
                        IslandAction(
                            label = "OT Input",
                            icon = Icons.AutoMirrored.Rounded.FactCheck,
                            onClick = {
                                viewModel.audioHaptic.playClickSound(state.settings.soundEnabled)
                                showOvertimeDialog = true
                            },
                            badge = pendingOtCount,
                            alert = pendingOtCount > 0
                        ),
                        IslandAction("PTO / Hol", Icons.Rounded.CalendarMonth, onClick = {
                            viewModel.audioHaptic.playClickSound(state.settings.soundEnabled)
                            showPtoDialog = true
                        }),
                        IslandAction("Report", Icons.Rounded.Description, onClick = {
                            viewModel.audioHaptic.playClickSound(state.settings.soundEnabled)
                            showReportDialog = true
                        })
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
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
            standardShiftHours = state.settings.standardShiftHours,
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
            standardShiftHours = state.settings.standardShiftHours,
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
            },
            onExportBackup = { viewModel.exportBackup() },
            onPlanImport = { incoming, mode -> viewModel.planImport(incoming, mode) },
            onConfirmImport = { incoming, mode ->
                val applied = viewModel.importBackup(incoming, mode)
                // Close Settings either way: its fields were seeded from the pre-import settings, and
                // saving them afterwards would silently undo a REPLACE.
                showSettingsDialog = false
                if (applied != null) importConfirmations++
                scope.launch {
                    snackbarHostState.showSnackbar(applied?.let { "Imported. " + it.summary } ?: "Import failed — your data was not changed.")
                }
            },
            onPlanCsvImport = { parsed, mode -> viewModel.planCsvImport(parsed, mode) },
            onConfirmCsvImport = { parsed, mode ->
                val applied = viewModel.importCsv(parsed, mode)
                // Closed for the same reason as a backup import, and so the new shifts are in view.
                showSettingsDialog = false
                if (applied != null) importConfirmations++
                val n = parsed.entries.size
                android.widget.Toast.makeText(
                    context,
                    if (applied != null) "Imported $n ${if (n == 1) "shift" else "shifts"} from the CSV."
                    else "CSV import refused — it could not be saved, so your data was not changed.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
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


private val TOP_BAR_HEIGHT = 72.dp

/**
 * The frosted top bar: the brand on the left, circular glass actions on the right. Content scrolls
 * beneath it and is blurred progressively (strongest at the top edge, none at its bottom), so there
 * is never a hard-edged band.
 */
@Composable
private fun FluidTopBar(
    pendingOtCount: Int,
    moneyHidden: Boolean,
    onOvertime: () -> Unit,
    onToggleMoney: () -> Unit,
    onAnalytics: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val env = LocalFluid.current
    val page = fluidPageColor()
    val backdrop = if (env.glass && env.hazeState != null) {
        Modifier.hazeChild(env.hazeState, HazeStyle(page, listOf(HazeTint(page.copy(alpha = 0.55f))), FluidGlass.regular.blur, 0f, HazeTint(page))) {
            progressive = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)
        }
    } else {
        Modifier.background(Brush.verticalGradient(listOf(page, page, page.copy(alpha = 0f))))
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(TOP_BAR_HEIGHT)
            .then(backdrop)
            .padding(horizontal = 16.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .background(
                    Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)),
                    CircleShape
                )
                .border(fluidContour(), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Rounded.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Timeclock",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = "Randall Engineering",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FluidIconButton(
                icon = Icons.AutoMirrored.Rounded.FactCheck,
                contentDescription = "Overtime System Input",
                onClick = onOvertime,
                badge = pendingOtCount
            )
            FluidIconButton(
                icon = if (moneyHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                contentDescription = "Toggle Money Visibility",
                onClick = onToggleMoney,
                active = moneyHidden
            )
            FluidIconButton(icon = Icons.Rounded.BarChart, contentDescription = "Analytics", onClick = onAnalytics)
            FluidIconButton(icon = Icons.Rounded.Settings, contentDescription = "Settings", onClick = onSettings)
        }
    }
}
