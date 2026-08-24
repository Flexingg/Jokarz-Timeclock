/**
 * Jokarz Timeclock - Real-Time Live Clock Engine
 */
const TimerEngine = {
    intervalId: null,

    start() {
        if (this.intervalId) clearInterval(this.intervalId);
        this.tick();
        this.intervalId = setInterval(() => this.tick(), 1000);
    },

    stop() {
        if (this.intervalId) {
            clearInterval(this.intervalId);
            this.intervalId = null;
        }
        if (typeof NotificationEngine !== 'undefined') {
            NotificationEngine.resetMilestones();
        }
    },

    tick() {
        const state = StateManager.state;
        if (!state.isClockedIn || !state.currentSessionStart) return;

        const elapsedMs = Date.now() - state.currentSessionStart;
        const liveTimerEl = document.getElementById('liveTimer');
        const liveStatusBox = document.getElementById('liveStatusBox');
        const breakBtn = document.getElementById('breakBtn');

        if (liveTimerEl) {
            liveTimerEl.innerText = PayrollEngine.formatDuration(elapsedMs);
        }

        const rate = StateManager.getCurrentRate();
        const d = new Date(state.currentSessionStart);
        const dayOfWeek = d.getDay();

        const settings = state.settings || {};
        const standardSalaryHours = settings.standardShiftHours !== undefined ? settings.standardShiftHours : 10.0;
        const unpaidMealDuration = settings.unpaidMealDuration !== undefined ? settings.unpaidMealDuration : 0.5;
        const cliffHours = settings.cliffHours !== undefined ? settings.cliffHours : 12.5;
        const otMultiplier = settings.otMultiplier !== undefined ? settings.otMultiplier : 1.0;

        let statusHtml = '';

        if (state.isOnBreak) {
            const breakElapsed = (state.accumulatedBreakMs || 0) + (Date.now() - state.breakStartTime);
            statusHtml = '<span class= text-amber-400 text-xs font-semibold uppercase tracking-wider mr-2>On Break / Lunch</span>' +
                         '<span class=text-amber-300 font-bold text-lg font-mono>' + PayrollEngine.formatDuration(breakElapsed) + '</span>';
            if (breakBtn) {
                breakBtn.classList.replace('bg-amber-600/30', 'bg-amber-500');
                breakBtn.classList.replace('text-amber-400', 'text-slate-900');
                breakBtn.innerHTML = '<span class=material-icons text-sm mr-1>play_arrow</span> Resume Shift';
            }
        } else {
            if (breakBtn) {
                breakBtn.classList.replace('bg-amber-500', 'bg-amber-600/30');
                breakBtn.classList.replace('text-slate-900', 'text-amber-400');
                breakBtn.innerHTML = '<span class=material-icons text-sm mr-1>pause</span> Break / Lunch';
            }

            if (dayOfWeek >= 1 && dayOfWeek <= 4) {
                const prevBanked = PayrollEngine.getPreviousBankedHoursForCurrentWeek(state.currentSessionStart, state);
                const targetStandardClocked = (standardSalaryHours + unpaidMealDuration) - prevBanked;
                const standardMs = targetStandardClocked * 3600000;
                const cliffMs = cliffHours * 3600000;

                if (typeof NotificationEngine !== 'undefined') {
                    NotificationEngine.checkMilestones(elapsedMs, standardMs, cliffMs);
                }

                if (elapsedMs < standardMs) {
                    const remainingMs = standardMs - elapsedMs;
                    let bankNote = '';
                    if (Math.abs(prevBanked) > 0.05) {
                        const sign = prevBanked > 0 ? '+' : '';
                        const color = prevBanked > 0 ? 'text-amber-400' : 'text-rose-400';
                        bankNote = '<span class= + color +  ml-1>(' + sign + prevBanked.toFixed(1) + 'h bank)</span>';
                    }

                    statusHtml = '<span class=text-slate-400 text-xs font-semibold uppercase tracking-wider mr-2>Remaining ' + bankNote + '</span>' +
                                 '<span class=text-emerald-400 font-bold text-lg font-mono>' + PayrollEngine.formatDuration(remainingMs) + '</span>';
                } else if (elapsedMs < cliffMs) {
                    const bankingMs = elapsedMs - standardMs;
                    const bankingHrs = bankingMs / 3600000;
                    statusHtml = '<span class=text-amber-400 text-xs font-semibold uppercase tracking-wider mr-2>Banking Unpaid</span>' +
                                 '<span class=text-amber-400 font-bold text-lg font-mono>+' + bankingHrs.toFixed(2) + 'h</span>';
                } else {
                    const otMs = elapsedMs - ((standardSalaryHours + unpaidMealDuration) * 3600000);
                    const otHours = otMs / 3600000;
                    const otPay = otHours * rate * otMultiplier;
                    statusHtml = '<span class=text-purple-400 text-xs font-semibold uppercase tracking-wider mr-2>Live OT (' + otMultiplier + 'x)</span>' +
                                 '<span class=text-purple-400 font-bold text-lg font-mono>' + otHours.toFixed(2) + 'h | ' + PayrollEngine.formatMoney(otPay) + '</span>';
                }
            } else {
                const elapsedHours = elapsedMs / 3600000;
                const payableHours = elapsedHours > 4 ? Math.max(0, elapsedHours - 0.5) : elapsedHours;
                const pay = Math.max(0, payableHours * rate * otMultiplier);

                statusHtml = '<span class=text-purple-400 text-xs font-semibold uppercase tracking-wider mr-2>Weekend OT</span>' +
                             '<span class=text-purple-400 font-bold text-lg font-mono>' + payableHours.toFixed(2) + 'h | ' + PayrollEngine.formatMoney(pay) + '</span>';
            }
        }

        if (liveStatusBox) {
            liveStatusBox.innerHTML = statusHtml;
        }

        const statusText = document.getElementById('statusText');
        if (typeof NotificationEngine !== 'undefined') {
            NotificationEngine.updateMediaSession(true, PayrollEngine.formatDuration(elapsedMs), statusText ? statusText.innerText : 'Working');
        }

        if (Math.floor(elapsedMs / 1000) % 60 === 0) {
            UIController.updateTotals();
        }
    }
};

window.TimerEngine = TimerEngine;