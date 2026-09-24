package com.randallengineering.jokarztimeclock.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.randallengineering.jokarztimeclock.data.models.Session
import com.randallengineering.jokarztimeclock.engine.PayrollEngine
import com.randallengineering.jokarztimeclock.engine.ShiftFilter
import com.randallengineering.jokarztimeclock.engine.ShiftTimeMath
import com.randallengineering.jokarztimeclock.ui.theme.ExpressiveButtonSize
import com.randallengineering.jokarztimeclock.ui.theme.FigureStyle
import com.randallengineering.jokarztimeclock.ui.theme.PillShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Height of the scrolling part of the history (the page around it scrolls too). */
private val HistoryListHeight = 300.dp

/**
 * The shift history: search by note / job code, filter by date range (quick ranges or any custom
 * range from the M3 date-range picker), a running total for what is shown, and a proper empty state.
 * Filtering never renumbers shifts: each row still edits the shift at its original index.
 *
 * [payPeriod] is the current pay period as a half-open `[start, end)` instant range.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleSessionLogList(
    sessions: List<Session>,
    onSessionClick: (index: Int, session: Session) -> Unit,
    payPeriod: Pair<Long, Long>,
    onAddShift: () -> Unit,
    modifier: Modifier = Modifier
) {
    var preset by rememberSaveable { mutableStateOf(ShiftFilter.Preset.ALL) }
    var customFrom by rememberSaveable { mutableStateOf<Long?>(null) }
    var customTo by rememberSaveable { mutableStateOf<Long?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var showRangePicker by remember { mutableStateOf(false) }

    val now = System.currentTimeMillis()
    val range: Pair<Long, Long>? = when (preset) {
        ShiftFilter.Preset.ALL -> null
        ShiftFilter.Preset.THIS_WEEK -> PayrollEngine.getStartOfWeekDate(Date(now)).let { it to ShiftTimeMath.addDays(it, 7) }
        ShiftFilter.Preset.PAY_PERIOD -> payPeriod
        ShiftFilter.Preset.LAST_30_DAYS -> ShiftFilter.lastDays(30, now)
        ShiftFilter.Preset.CUSTOM -> customFrom?.let { from -> customTo?.let { to -> from to to } }
    }
    val criteria = ShiftFilter.Criteria(range?.first, range?.second, query)
    val shown = remember(sessions, criteria) { ShiftFilter.apply(sessions, criteria) }
    val totals = remember(shown) { ShiftFilter.totals(shown) }

    fun clearFilters() {
        preset = ShiftFilter.Preset.ALL
        query = ""
    }

    if (showRangePicker) {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = customFrom?.let { ShiftTimeMath.toPickerDateMs(it) },
            initialSelectedEndDateMillis = customTo?.let { ShiftTimeMath.toPickerDateMs(ShiftTimeMath.addDays(it, -1)) }
        )
        DatePickerDialog(
            onDismissRequest = { showRangePicker = false },
            confirmButton = {
                ScaledButton(
                    enabled = pickerState.selectedStartDateMillis != null,
                    onClick = {
                        pickerState.selectedStartDateMillis?.let { start ->
                            val (from, to) = ShiftFilter.fromPicker(start, pickerState.selectedEndDateMillis)
                            customFrom = from
                            customTo = to
                            preset = ShiftFilter.Preset.CUSTOM
                        }
                        showRangePicker = false
                    }
                ) { Text("Show these days") }
            },
            dismissButton = { DialogBackButton(onClick = { showRangePicker = false }) }
        ) {
            DateRangePicker(state = pickerState, modifier = Modifier.height(480.dp))
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp)
        ) {
            Text(
                text = "SHIFT HISTORY",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f)
            )
            if (sessions.isNotEmpty()) {
                Text(
                    text = "${totals.count} ${if (totals.count == 1) "shift" else "shifts"} · " +
                        PayrollEngine.formatDurationShort(totals.clockedMs),
                    style = FigureStyle,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (sessions.isEmpty()) {
            EmptyHistory(
                icon = Icons.Rounded.History,
                title = "No shifts yet",
                body = "Clock in above to start your first shift, or add one you already worked. " +
                    "Every shift, its hours and any overtime will be listed here.",
                actionLabel = "Add a past shift",
                actionIcon = Icons.Rounded.Add,
                onAction = onAddShift
            )
            return@Column
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Search notes or job code") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    ScaledIconButton(onClick = { query = "" }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                    }
                }
            },
            shape = PillShape,
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            ShiftFilter.Preset.values().forEach { p ->
                val (interaction, pressScale) = rememberPressScaleSource()
                val selected = preset == p
                val label = if (p == ShiftFilter.Preset.CUSTOM && selected && range != null) {
                    rangeLabel(range)
                } else if (p == ShiftFilter.Preset.CUSTOM) {
                    "Custom…"
                } else {
                    p.label
                }
                FilterChip(
                    selected = selected,
                    onClick = {
                        if (p == ShiftFilter.Preset.CUSTOM) showRangePicker = true else preset = p
                    },
                    label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                    leadingIcon = if (p == ShiftFilter.Preset.CUSTOM) {
                        { Icon(Icons.Rounded.DateRange, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else null,
                    shape = PillShape,
                    interactionSource = interaction,
                    modifier = pressScale
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (shown.isEmpty()) {
            EmptyHistory(
                icon = Icons.Rounded.EventBusy,
                title = "No shifts match",
                body = if (query.isNotBlank()) "Nothing in this range mentions “${query.trim()}”." else "There are no shifts in this range.",
                actionLabel = "Clear filters",
                actionIcon = Icons.Rounded.Close,
                onAction = ::clearFilters
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HistoryListHeight)
            ) {
                items(shown, key = { it.value.id + "#" + it.index }) { (originalIndex, session) ->
                    ShiftRow(session = session, onClick = { onSessionClick(originalIndex, session) })
                }
            }
        }
    }
}

@Composable
private fun ShiftRow(session: Session, onClick: () -> Unit) {
    val sdfDay = remember { SimpleDateFormat("EEE, MMM d", Locale.US) }
    val sdfTime = remember { SimpleDateFormat("h:mm a", Locale.US) }
    val dStart = Date(session.start)
    val dEnd = Date(session.end)

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .expressiveClickable(onClick = onClick)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sdfDay.format(dStart),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${sdfTime.format(dStart)} → ${sdfTime.format(dEnd)}" +
                        if (ShiftTimeMath.isOvernight(session.start, session.end)) " (next day)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (session.note.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Notes,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = session.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
                if (session.isPutInSystem) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "✓ Entered into System",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = PayrollEngine.formatDurationShort(session.end - session.start),
                    style = FigureStyle,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "Edit",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/** A friendly empty state: what is (not) here, why, and the one useful next step. */
@Composable
private fun EmptyHistory(
    icon: ImageVector,
    title: String,
    body: String,
    actionLabel: String,
    actionIcon: ImageVector,
    onAction: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp)
        ) {
            Surface(shape = PillShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .padding(14.dp)
                        .size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            ExpressiveButton(
                text = actionLabel,
                onClick = onAction,
                variant = ButtonVariant.Tonal,
                size = ExpressiveButtonSize.Small,
                icon = actionIcon
            )
        }
    }
}

private fun rangeLabel(range: Pair<Long, Long>): String {
    val fmt = SimpleDateFormat("MMM d", Locale.US)
    val lastDay = ShiftTimeMath.addDays(range.second, -1)
    val a = fmt.format(Date(range.first))
    val b = fmt.format(Date(lastDay))
    return if (a == b) a else "$a – $b"
}
