/**
 * Jokarz Timeclock - Comprehensive Payroll & Calculations Engine
 */
const PayrollEngine = {
    getStartOfDay(date = new Date()) {
        const d = new Date(date);
        d.setHours(0, 0, 0, 0);
        return d.getTime();
    },

    getStartOfWeekDate(date = new Date()) {
        const d = new Date(date);
        d.setHours(0, 0, 0, 0);
        const day = d.getDay();
        const diff = d.getDate() - day + (day === 0 ? -6 : 1);
        d.setDate(diff);
        return d;
    },

    getStartOfPayPeriod(date = new Date(), state = StateManager.state) {
        const d = new Date(date);
        d.setHours(0, 0, 0, 0);
        const schedule = state.settings ? (state.settings.paySchedule || 'semimonthly') : 'semimonthly';

        if (schedule === 'semimonthly') {
            if (d.getDate() <= 15) {
                d.setDate(1);
            } else {
                d.setDate(16);
            }
            return d.getTime();
        } else if (schedule === 'weekly') {
            return this.getStartOfWeekDate(date).getTime();
        } else if (schedule === 'monthly') {
            d.setDate(1);
            return d.getTime();
        } else if (schedule === 'biweekly') {
            const anchor = new Date((state.settings && state.settings.biweeklyAnchorDate) || '2026-01-05');
            anchor.setHours(0, 0, 0, 0);
            const diffDays = Math.floor((d.getTime() - anchor.getTime()) / 86400000);
            const periodIndex = Math.floor(diffDays / 14);
            const periodStart = new Date(anchor.getTime() + (periodIndex * 14 * 86400000));
            return periodStart.getTime();
        }
        return d.getTime();
    },

    getEndOfPayPeriod(date = new Date(), state = StateManager.state) {
        const d = new Date(date);
        const schedule = state.settings ? (state.settings.paySchedule || 'semimonthly') : 'semimonthly';

        if (schedule === 'semimonthly') {
            if (d.getDate() <= 15) {
                d.setDate(15);
                d.setHours(23, 59, 59, 999);
            } else {
                const lastDay = new Date(d.getFullYear(), d.getMonth() + 1, 0);
                lastDay.setHours(23, 59, 59, 999);
                return lastDay.getTime();
            }
            return d.getTime();
        } else if (schedule === 'weekly') {
            const mon = this.getStartOfWeekDate(date);
            const sun = new Date(mon.getTime() + (6 * 86400000));
            sun.setHours(23, 59, 59, 999);
            return sun.getTime();
        } else if (schedule === 'monthly') {
            const lastDay = new Date(d.getFullYear(), d.getMonth() + 1, 0);
            lastDay.setHours(23, 59, 59, 999);
            return lastDay.getTime();
        } else if (schedule === 'biweekly') {
            const start = this.getStartOfPayPeriod(date, state);
            const end = new Date(start + (13 * 86400000));
            end.setHours(23, 59, 59, 999);
            return end.getTime();
        }
        return d.getTime();
    },

    calculateDayStats(dayStartMs, excludeActive = false, state = StateManager.state) {
        let clockedMs = 0;
        let totalBreakMs = 0;
        const dayEndMs = dayStartMs + 86400000;

        (state.sessions || []).forEach(sess => {
            const d = new Date(sess.start);
            if (d.getTime() >= dayStartMs && d.getTime() < dayEndMs) {
                clockedMs += (sess.end - sess.start);
                totalBreakMs += (sess.breakMs || 0);
            }
        });

        if (!excludeActive && state.isClockedIn && state.currentSessionStart >= dayStartMs && state.currentSessionStart < dayEndMs) {
            clockedMs += (Date.now() - state.currentSessionStart);
            let curBreak = state.accumulatedBreakMs || 0;
            if (state.isOnBreak && state.breakStartTime) {
                curBreak += (Date.now() - state.breakStartTime);
            }
            totalBreakMs += curBreak;
        }

        const settings = state.settings || {};
        const standardSalaryHours = settings.standardShiftHours !== undefined ? settings.standardShiftHours : 10.0;
        const unpaidMealThreshold = settings.unpaidMealThreshold !== undefined ? settings.unpaidMealThreshold : 4.0;
        const unpaidMealDuration = settings.unpaidMealDuration !== undefined ? settings.unpaidMealDuration : 0.5;
        const cliffHours = settings.cliffHours !== undefined ? settings.cliffHours : 12.5;
        const otMultiplier = settings.otMultiplier !== undefined ? settings.otMultiplier : 1.0;

        const clockedHours = clockedMs / 3600000;
        
        let breakHours = totalBreakMs / 3600000;
        if (breakHours === 0 && settings.autoBreakDeduction !== false && clockedHours > unpaidMealThreshold) {
            breakHours = unpaidMealDuration;
        }

        const workedHours = Math.max(0, clockedHours - breakHours);
        const dayOfWeek = new Date(dayStartMs).getDay();

        let baseHours = 0;
        let otHours = 0;
        let bankedHours = 0;
        let type = "";

        let ptoHours = 0;
        (state.ptoEntries || []).forEach(p => {
            const pDate = new Date(p.date);
            pDate.setHours(0, 0, 0, 0);
            if (pDate.getTime() === dayStartMs) {
                ptoHours += (p.hours || 0);
            }
        });

        if (dayOfWeek >= 1 && dayOfWeek <= 4) {
            baseHours = standardSalaryHours;
            const targetStandardShift = standardSalaryHours + unpaidMealDuration;

            if (clockedHours >= cliffHours) {
                otHours = clockedHours - targetStandardShift;
                bankedHours = 0;
                type = "Base + OT";
            } else if (clockedHours > 0.1) {
                otHours = 0;
                bankedHours = workedHours - standardSalaryHours;
                type = "Salary Base";
            } else {
                otHours = 0;
                bankedHours = 0;
                type = ptoHours > 0 ? "PTO / Holiday" : "Salary Base";
            }
        } else {
            baseHours = 0;
            otHours = workedHours;
            bankedHours = 0;
            type = clockedHours > 0 ? "Overtime" : (ptoHours > 0 ? "PTO / Holiday" : "Off");
        }

        const payableHours = baseHours + otHours + ptoHours;
        const systemInput = otHours;

        return {
            dayStartMs,
            clockedMs,
            clockedHours,
            workedHours,
            breakHours,
            baseHours,
            otHours,
            ptoHours,
            bankedHours,
            payableHours,
            systemInput,
            type,
            otMultiplier
        };
    },

    getPreviousBankedHoursForCurrentWeek(targetTimeMs, state = StateManager.state) {
        const targetDate = new Date(targetTimeMs);
        const dayOfWeek = targetDate.getDay();
        const daysSinceMonday = dayOfWeek === 0 ? 6 : dayOfWeek - 1;

        const startOfWeek = new Date(targetDate);
        startOfWeek.setDate(targetDate.getDate() - daysSinceMonday);
        startOfWeek.setHours(0, 0, 0, 0);

        const targetStartOfDay = new Date(targetDate);
        targetStartOfDay.setHours(0, 0, 0, 0);

        let totalPrevBanked = 0;
        let d = new Date(startOfWeek);

        while (d.getTime() < targetStartOfDay.getTime()) {
            const stats = this.calculateDayStats(d.getTime(), true, state);
            totalPrevBanked += stats.bankedHours;
            d.setDate(d.getDate() + 1);
        }
        return totalPrevBanked;
    },

    calculatePeriodTotals(state = StateManager.state) {
        const startOfDay = this.getStartOfDay();
        const startOfPeriod = this.getStartOfPayPeriod(new Date(), state);
        const endOfPeriod = this.getEndOfPayPeriod(new Date(), state);

        let totalClockedMsPeriod = 0;
        let totalClockedHoursPeriod = 0;
        let totalPayableHoursPeriod = 0;
        let totalOtHoursPeriod = 0;
        let totalPtoHoursPeriod = 0;
        let todayStats = null;

        let d = new Date(startOfPeriod);
        while (d.getTime() <= Math.min(startOfDay, endOfPeriod)) {
            const ms = d.getTime();
            const stats = this.calculateDayStats(ms, false, state);

            totalClockedMsPeriod += stats.clockedMs;
            totalClockedHoursPeriod += stats.clockedHours;
            totalPayableHoursPeriod += stats.payableHours;
            totalOtHoursPeriod += stats.otHours;
            totalPtoHoursPeriod += stats.ptoHours;

            if (ms === startOfDay) {
                todayStats = stats;
            }
            d.setDate(d.getDate() + 1);
        }

        const rate = state.displayMode === 'gross' ? state.grossRate : state.netRate;
        const otMult = (state.settings && state.settings.otMultiplier) ? state.settings.otMultiplier : 1.0;

        const regularHours = Math.max(0, totalPayableHoursPeriod - totalOtHoursPeriod);
        const periodEarnings = (regularHours * rate) + (totalOtHoursPeriod * rate * otMult);
        const periodGrossEarnings = (regularHours * state.grossRate) + (totalOtHoursPeriod * state.grossRate * otMult);
        const periodNetEarnings = (regularHours * state.netRate) + (totalOtHoursPeriod * state.netRate * otMult);

        const todayOtEarnings = todayStats ? (todayStats.otHours * rate * otMult) : 0;
        const todayEarnings = todayStats ? ((todayStats.payableHours - todayStats.otHours) * rate + todayOtEarnings) : 0;

        return {
            todayStats,
            totalClockedMsPeriod,
            totalClockedHoursPeriod,
            totalPayableHoursPeriod,
            totalOtHoursPeriod,
            totalPtoHoursPeriod,
            rate,
            todayEarnings,
            todayOtEarnings,
            periodGrossEarnings,
            periodNetEarnings,
            periodEarnings,
            startOfPeriod,
            endOfPeriod
        };
    },

    formatDuration(ms) {
        let totalSeconds = Math.floor(Math.max(0, ms) / 1000);
        let hours = Math.floor(totalSeconds / 3600);
        let minutes = Math.floor((totalSeconds % 3600) / 60);
        let seconds = totalSeconds % 60;
        return String(hours).padStart(2, '0') + ':' + String(minutes).padStart(2, '0') + ':' + String(seconds).padStart(2, '0');
    },

    formatDurationShort(ms) {
        let totalMinutes = Math.floor(Math.max(0, ms) / (1000 * 60));
        let hours = Math.floor(totalMinutes / 60);
        let minutes = totalMinutes % 60;
        return hours + 'h ' + minutes + 'm';
    },

    formatMoney(amount) {
        return '$' + Math.max(0, amount).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    },

    timestampToDatetimeLocal(ts) {
        const d = new Date(ts);
        const tzOffset = d.getTimezoneOffset() * 60000;
        return new Date(d.getTime() - tzOffset).toISOString().slice(0, 16);
    }
};

window.PayrollEngine = PayrollEngine;