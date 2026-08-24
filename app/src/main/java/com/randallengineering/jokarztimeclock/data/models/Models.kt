package com.randallengineering.jokarztimeclock.data.models

data class Session(
    val id: String = "sess_" + System.currentTimeMillis() + "_" + (1000..9999).random(),
    val start: Long,
    val end: Long,
    val breakMs: Long = 0L,
    val note: String = "",
    val jobCode: String = ""
)

data class PtoEntry(
    val id: String = "pto_" + System.currentTimeMillis() + "_" + (1000..9999).random(),
    val date: Long,
    val hours: Double,
    val type: PtoType = PtoType.PTO,
    val note: String = ""
)

data class AppSettings(
    val theme: ThemeMode = ThemeMode.DARK,
    val paySchedule: PaySchedule = PaySchedule.SEMI_MONTHLY,
    val biweeklyAnchorDate: String = "2026-01-05",
    val standardShiftHours: Double = 10.0,
    val unpaidMealThreshold: Double = 4.0,
    val unpaidMealDuration: Double = 0.5,
    val cliffHours: Double = 12.5,
    val otMultiplier: Double = 1.0,
    val soundEnabled: Boolean = true,
    val hapticEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val liveNotificationEnabled: Boolean = true,
    val hideMoneyAmounts: Boolean = false,
    val autoBreakDeduction: Boolean = true,
    val geofenceEnabled: Boolean = false,
    val workLatitude: Double = 0.0,
    val workLongitude: Double = 0.0,
    val geofenceRadiusMeters: Float = 150f,
    val workAddressName: String = ""
)

data class AuditEntry(
    val action: String,
    val timestamp: Long = System.currentTimeMillis(),
    val payloadJson: String = ""
)

data class UndoAction(
    val type: String,
    val session: Session? = null,
    val index: Int = -1,
    val previousSession: Session? = null
)

data class DayStats(
    val dayStartMs: Long,
    val clockedMs: Long,
    val clockedHours: Double,
    val workedHours: Double,
    val breakHours: Double,
    val baseHours: Double,
    val otHours: Double,
    val ptoHours: Double,
    val bankedHours: Double,
    val payableHours: Double,
    val systemInput: Double,
    val type: String,
    val otMultiplier: Double
)

data class PeriodTotals(
    val todayStats: DayStats?,
    val totalClockedMsPeriod: Long,
    val totalClockedHoursPeriod: Double,
    val totalPayableHoursPeriod: Double,
    val totalOtHoursPeriod: Double,
    val totalPtoHoursPeriod: Double,
    val rate: Double,
    val todayEarnings: Double,
    val todayOtEarnings: Double,
    val periodGrossEarnings: Double,
    val periodNetEarnings: Double,
    val periodEarnings: Double,
    val startOfPeriod: Long,
    val endOfPeriod: Long
)

data class TimeclockState(
    val isClockedIn: Boolean = false,
    val currentSessionStart: Long? = null,
    val isOnBreak: Boolean = false,
    val breakStartTime: Long? = null,
    val accumulatedBreakMs: Long = 0L,
    val grossRate: Double = 62.0,
    val netRate: Double = 30.0,
    val displayMode: PayMode = PayMode.GROSS,
    val sessions: List<Session> = emptyList(),
    val ptoEntries: List<PtoEntry> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val auditLog: List<AuditEntry> = emptyList()
)
