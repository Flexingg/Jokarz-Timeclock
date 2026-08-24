/**
 * Jokarz Timeclock - Comprehensive UI Controller & Modal System
 */
const UIController = {
    confirmCallback: null,

    init() {
        this.applyTheme(StateManager.state.settings?.theme || 'dark');
        this.bindEvents();
        this.updateModeUI();
        this.updateTotals();

        if (StateManager.state.isClockedIn) {
            this.setClockUIActive();
            TimerEngine.start();
        } else {
            this.setClockUIInactive();
        }
    },

    bindEvents() {
        const modeGrossBtn = document.getElementById('modeGrossBtn');
        const modeNetBtn = document.getElementById('modeNetBtn');
        const hourlyRateInput = document.getElementById('hourlyRate');
        const clockBtn = document.getElementById('clockBtn');
        const breakBtn = document.getElementById('breakBtn');
        const confirmActionBtn = document.getElementById('confirmActionBtn');

        if (modeGrossBtn) modeGrossBtn.addEventListener('click', () => {
            AudioHaptics.playButtonClick();
            this.setMode('gross');
        });
        if (modeNetBtn) modeNetBtn.addEventListener('click', () => {
            AudioHaptics.playButtonClick();
            this.setMode('net');
        });

        if (hourlyRateInput) {
            hourlyRateInput.addEventListener('change', (e) => {
                const val = parseFloat(e.target.value) || 0;
                StateManager.setRate(StateManager.state.displayMode, val);
                this.updateTotals();
            });
        }

        if (clockBtn) {
            clockBtn.addEventListener('click', () => this.toggleClock());
        }

        if (breakBtn) {
            breakBtn.addEventListener('click', () => {
                AudioHaptics.playButtonClick();
                StateManager.toggleBreak();
                TimerEngine.tick();
            });
        }

        if (confirmActionBtn) {
            confirmActionBtn.addEventListener('click', () => {
                AudioHaptics.playButtonClick();
                if (this.confirmCallback) this.confirmCallback();
                this.closeConfirm();
            });
        }
    },

    applyTheme(theme) {
        document.body.classList.remove('theme-dark', 'theme-amoled', 'theme-light', 'theme-emerald', 'theme-amber', 'theme-purple');
        document.body.classList.add('theme-' + theme);
        if (theme === 'amoled') {
            document.body.style.backgroundColor = '#000000';
        } else if (theme === 'light') {
            document.body.style.backgroundColor = '#f1f5f9';
            document.body.classList.remove('text-gray-100');
            document.body.classList.add('text-gray-900');
        } else {
            document.body.style.backgroundColor = '#0f172a';
            document.body.classList.remove('text-gray-900');
            document.body.classList.add('text-gray-100');
        }
    },

    setMode(mode) {
        StateManager.setMode(mode);
        this.updateModeUI();
        this.updateTotals();
        if (StateManager.state.isClockedIn) {
            TimerEngine.tick();
        }
    },

    updateModeUI() {
        const modeGrossBtn = document.getElementById('modeGrossBtn');
        const modeNetBtn = document.getElementById('modeNetBtn');
        const rateLabel = document.getElementById('rateLabel');
        const hourlyRateInput = document.getElementById('hourlyRate');

        if (!modeGrossBtn || !modeNetBtn || !rateLabel || !hourlyRateInput) return;

        if (StateManager.state.displayMode === 'gross') {
            modeGrossBtn.classList.replace('text-slate-400', 'text-white');
            modeGrossBtn.classList.add('bg-purple-600', 'shadow-md');
            modeNetBtn.classList.remove('bg-purple-600', 'text-white', 'shadow-md');
            modeNetBtn.classList.add('text-slate-400');
            rateLabel.innerText = "Gross";
            hourlyRateInput.value = StateManager.state.grossRate;
        } else {
            modeNetBtn.classList.replace('text-slate-400', 'text-white');
            modeNetBtn.classList.add('bg-purple-600', 'shadow-md');
            modeGrossBtn.classList.remove('bg-purple-600', 'text-white', 'shadow-md');
            modeGrossBtn.classList.add('text-slate-400');
            rateLabel.innerText = "Take Home";
            hourlyRateInput.value = StateManager.state.netRate;
        }
    },

    toggleClock() {
        if (StateManager.state.isClockedIn) {
            AudioHaptics.playClockOutSound();
            StateManager.clockOut();
            this.setClockUIInactive();
            TimerEngine.stop();
            TaskerBridge.sendEvent("Clocked Out");
            this.showUndoToast("Clocked out successfully.");
        } else {
            AudioHaptics.playClockInSound();
            NotificationEngine.requestPermission();
            StateManager.clockIn();
            this.setClockUIActive();
            TimerEngine.start();
            TaskerBridge.sendEvent("Clocked In");
        }
        this.updateTotals();
    },

    setClockUIActive() {
        const clockBtn = document.getElementById('clockBtn');
        const clockBtnText = document.getElementById('clockBtnText');
        const clockIcon = document.getElementById('clockIcon');
        const statusText = document.getElementById('statusText');
        const liveStats = document.getElementById('liveStats');

        if (clockBtn) {
            clockBtn.classList.remove('bg-purple-600', 'shadow-purple-600/40');
            clockBtn.classList.add('bg-rose-600', 'shadow-rose-600/50', 'animate-pulse-ring');
        }
        if (clockBtnText) clockBtnText.innerText = "CLOCK OUT";
        if (clockIcon) clockIcon.innerText = "stop";
        if (statusText) {
            statusText.innerText = "Working...";
            statusText.classList.remove('text-slate-400');
            statusText.classList.add('text-emerald-400');
        }
        if (liveStats) {
            liveStats.classList.remove('opacity-0', 'h-0', 'pointer-events-none');
            liveStats.classList.add('opacity-100', 'h-auto', 'py-2');
        }
    },

    setClockUIInactive() {
        const clockBtn = document.getElementById('clockBtn');
        const clockBtnText = document.getElementById('clockBtnText');
        const clockIcon = document.getElementById('clockIcon');
        const statusText = document.getElementById('statusText');
        const liveStats = document.getElementById('liveStats');

        if (clockBtn) {
            clockBtn.classList.remove('bg-rose-600', 'shadow-rose-600/50', 'animate-pulse-ring');
            clockBtn.classList.add('bg-purple-600', 'shadow-purple-600/40');
        }
        if (clockBtnText) clockBtnText.innerText = "CLOCK IN";
        if (clockIcon) clockIcon.innerText = "play_arrow";
        if (statusText) {
            statusText.innerText = "Ready to Work";
            statusText.classList.remove('text-emerald-400');
            statusText.classList.add('text-slate-400');
        }
        if (liveStats) {
            liveStats.classList.add('opacity-0', 'h-0', 'pointer-events-none');
            liveStats.classList.remove('opacity-100', 'h-auto', 'py-2');
        }
    },

    updateTotals() {
        const totals = PayrollEngine.calculatePeriodTotals(StateManager.state);
        const { todayStats, totalClockedMsPeriod, totalClockedHoursPeriod, totalPayableHoursPeriod, totalOtHoursPeriod, totalPtoHoursPeriod, rate } = totals;

        // Today UI
        if (todayStats) {
            const todayPayableType = document.getElementById('todayPayableType');
            const todayActualEarnings = document.getElementById('todayActualEarnings');
            const todayBonusEarnings = document.getElementById('todayBonusEarnings');
            const todayActualHours = document.getElementById('todayActualHours');
            const todayTechDetails = document.getElementById('todayTechDetails');

            if (todayPayableType) todayPayableType.innerText = todayStats.type;
            if (todayActualEarnings) todayActualEarnings.innerText = PayrollEngine.formatMoney(totals.todayEarnings);

            if (todayBonusEarnings) {
                todayBonusEarnings.innerText = totals.todayOtEarnings > 0 ? `+${PayrollEngine.formatMoney(totals.todayOtEarnings)} OT` : '';
            }
            if (todayActualHours) todayActualHours.innerText = `${todayStats.payableHours.toFixed(1)}h Paid`;
            if (todayTechDetails) todayTechDetails.innerText = `${PayrollEngine.formatDurationShort(todayStats.clockedMs)}`;
        }

        // Period UI
        const periodActualEarnings = document.getElementById('periodActualEarnings');
        const periodBonusEarnings = document.getElementById('periodBonusEarnings');
        const periodActualHours = document.getElementById('periodActualHours');
        const periodTechDetails = document.getElementById('periodTechDetails');
        const periodLabel = document.getElementById('periodLabel');

        if (periodActualEarnings) periodActualEarnings.innerText = PayrollEngine.formatMoney(totals.periodEarnings);
        const otMult = StateManager.state.settings?.otMultiplier || 1.0;
        const periodBonus = totalOtHoursPeriod * rate * otMult;
        if (periodBonusEarnings) {
            periodBonusEarnings.innerText = periodBonus > 0 ? `+${PayrollEngine.formatMoney(periodBonus)} OT` : '';
        }
        if (periodActualHours) {
            const ptoStr = totalPtoHoursPeriod > 0 ? ` (${totalPtoHoursPeriod.toFixed(1)}h PTO)` : '';
            periodActualHours.innerText = `${totalPayableHoursPeriod.toFixed(1)}h Paid${ptoStr}`;
        }
        if (periodTechDetails) periodTechDetails.innerText = `${PayrollEngine.formatDurationShort(totalClockedMsPeriod)}`;

        if (periodLabel) {
            const dStart = new Date(totals.startOfPeriod);
            const dEnd = new Date(totals.endOfPeriod);
            periodLabel.innerText = `${dStart.getMonth()+1}/${dStart.getDate()} - ${dEnd.getMonth()+1}/${dEnd.getDate()}`;
        }

        this.renderWeeklySwiper();
        this.renderSessionLog();
        this.renderPtoList();
        TaskerBridge.pushData(todayStats, totalClockedHoursPeriod, totalPayableHoursPeriod, StateManager.state);
    },

    renderWeeklySwiper() {
        const weeklySwiperContainer = document.getElementById('weeklySwiperContainer');
        if (!weeklySwiperContainer) return;

        const weeks = new Set();
        const currentWeekStart = PayrollEngine.getStartOfWeekDate(new Date()).getTime();
        weeks.add(currentWeekStart);

        StateManager.state.sessions.forEach(sess => {
            weeks.add(PayrollEngine.getStartOfWeekDate(new Date(sess.start)).getTime());
        });

        const sortedWeeks = Array.from(weeks).sort((a, b) => b - a);
        let html = '';

        sortedWeeks.forEach(weekStartMs => {
            let weekBanked = 0;
            let weekSystemInput = 0;
            let weekClockedMs = 0;

            let d = new Date(weekStartMs);
            for (let i = 0; i < 7; i++) {
                const ms = d.getTime();
                if (ms <= PayrollEngine.getStartOfDay()) {
                    const stats = PayrollEngine.calculateDayStats(ms, false, StateManager.state);
                    weekBanked += stats.bankedHours;
                    weekSystemInput += stats.systemInput;
                    weekClockedMs += stats.clockedMs;
                }
                d.setDate(d.getDate() + 1);
            }

            const dStart = new Date(weekStartMs);
            const dEnd = new Date(weekStartMs + (6 * 86400000));
            const formatOpts = { month: 'short', day: 'numeric' };
            let label = `${dStart.toLocaleDateString(undefined, formatOpts)} - ${dEnd.toLocaleDateString(undefined, formatOpts)}`;
            if (weekStartMs === currentWeekStart) label = "Current Week";

            const sysColor = weekSystemInput > 0 ? "text-purple-400" : "text-slate-400";

            let bankHtml = '<div></div>';
            if (Math.abs(weekBanked) > 0.05) {
                const bankColor = weekBanked > 0 ? "text-amber-400" : "text-rose-400";
                const bankSign = weekBanked > 0 ? "+" : "";
                bankHtml = `
                    <div class="flex flex-col text-right">
                        <span class="text-[10px] font-bold text-slate-400 uppercase tracking-wide">Net Banked</span>
                        <span class="text-lg font-bold ${bankColor}">${bankSign}${weekBanked.toFixed(1)}h</span>
                    </div>
                `;
            }

            html += `
                <div class="snap-slide shrink-0 w-[270px] md-card rounded-2xl p-4 flex flex-col">
                    <div class="text-xs font-bold text-slate-300 uppercase mb-3 flex justify-between">
                        <span>${label}</span>
                        <span class="text-[10px] text-slate-400 font-mono">${PayrollEngine.formatDurationShort(weekClockedMs)} Tech</span>
                    </div>
                    <div class="flex justify-between items-end mb-1">
                        <div class="flex flex-col">
                            <span class="text-[10px] font-bold text-slate-400 uppercase tracking-wide">System Input (OT)</span>
                            <span class="text-xl font-bold ${sysColor}">${weekSystemInput.toFixed(1)}h</span>
                        </div>
                        ${bankHtml}
                    </div>
                </div>
            `;
        });

        weeklySwiperContainer.innerHTML = html;
    },

    renderSessionLog() {
        const sessionLogContainer = document.getElementById('sessionLogContainer');
        if (!sessionLogContainer) return;

        const displaySessions = StateManager.state.sessions
            .map((sess, idx) => ({ ...sess, originalIndex: idx }))
            .sort((a, b) => b.start - a.start)
            .slice(0, 50);

        if (displaySessions.length === 0) {
            sessionLogContainer.innerHTML = `
                <div class="flex flex-col items-center justify-center p-8 text-slate-500 border border-slate-800 rounded-2xl border-dashed">
                    <span class="material-icons text-3xl mb-2 opacity-50">history</span>
                    <span class="text-xs uppercase tracking-wider font-semibold">No completed sessions</span>
                </div>
            `;
            return;
        }

        let html = '';
        displaySessions.forEach(sess => {
            const dStart = new Date(sess.start);
            const dEnd = new Date(sess.end);
            const days = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
            const dayStr = days[dStart.getDay()] + ', ' + dStart.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
            const startStr = dStart.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
            const endStr = dEnd.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
            const durationMs = sess.end - sess.start;
            const durStr = PayrollEngine.formatDurationShort(durationMs);
            const noteStr = sess.note ? `<div class="text-[11px] text-slate-400 italic mt-1 truncate max-w-[200px]"><span class="material-icons text-[11px] mr-0.5 align-middle">notes</span>${sess.note}</div>` : '';

            html += `
                <div onclick="UIController.openEditModal(${sess.originalIndex})" class="flex justify-between items-center bg-slate-800/80 hover:bg-slate-750 p-3.5 rounded-xl border border-slate-700/60 cursor-pointer active:scale-98 transition-all md-card">
                    <div>
                        <div class="text-sm font-bold text-slate-100 mb-0.5">${dayStr}</div>
                        <div class="text-xs text-slate-400 font-medium">${startStr} &rarr; ${endStr}</div>
                        ${noteStr}
                    </div>
                    <div class="text-right">
                        <div class="text-sm font-bold text-purple-400 mb-0.5 font-mono">${durStr} Tech</div>
                        <div class="text-[10px] text-slate-400 uppercase flex items-center justify-end font-semibold">
                            <span class="material-icons text-[13px] mr-1 text-purple-400">edit</span> Edit
                        </div>
                    </div>
                </div>
            `;
        });

        sessionLogContainer.innerHTML = html;
    },

    renderPtoList() {
        const ptoListContainer = document.getElementById('ptoListContainer');
        if (!ptoListContainer) return;

        const entries = StateManager.state.ptoEntries || [];
        if (entries.length === 0) {
            ptoListContainer.innerHTML = '<div class="text-xs text-slate-500 italic p-3 text-center">No PTO or holiday hours logged yet.</div>';
            return;
        }

        let html = '';
        entries.slice().reverse().forEach(p => {
            const d = new Date(p.date);
            const dateStr = d.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
            html += `
                <div class="flex justify-between items-center bg-slate-900/80 p-2.5 rounded-xl border border-slate-700/50 mb-2">
                    <div>
                        <div class="text-xs font-bold text-slate-200">${p.type}: ${p.hours}h</div>
                        <div class="text-[10px] text-slate-400">${dateStr} ${p.note ? '• ' + p.note : ''}</div>
                    </div>
                    <button onclick="UIController.deletePto('${p.id}')" class="text-rose-400 hover:text-rose-300 p-1.5 rounded-lg transition-colors">
                        <span class="material-icons text-sm">delete</span>
                    </button>
                </div>
            `;
        });
        ptoListContainer.innerHTML = html;
    },

    deletePto(id) {
        StateManager.deletePtoEntry(id);
        this.updateTotals();
    },

    // --- Modals ---
    openActiveTimerModal() {
        if (!StateManager.state.isClockedIn || !StateManager.state.currentSessionStart) return;
        const editActiveStartTime = document.getElementById('editActiveStartTime');
        const editActiveModal = document.getElementById('editActiveModal');
        const editActiveModalContent = document.getElementById('editActiveModalContent');

        editActiveStartTime.value = PayrollEngine.timestampToDatetimeLocal(StateManager.state.currentSessionStart);
        editActiveModal.classList.remove('hidden');
        setTimeout(() => {
            editActiveModal.classList.remove('opacity-0');
            editActiveModalContent.classList.remove('scale-95');
        }, 10);
    },

    closeActiveTimerModal() {
        const editActiveModal = document.getElementById('editActiveModal');
        const editActiveModalContent = document.getElementById('editActiveModalContent');
        editActiveModal.classList.add('opacity-0');
        editActiveModalContent.classList.add('scale-95');
        setTimeout(() => { editActiveModal.classList.add('hidden'); }, 250);
    },

    saveActiveTimer() {
        const editActiveStartTime = document.getElementById('editActiveStartTime');
        const newStart = new Date(editActiveStartTime.value).getTime();

        if (!newStart || isNaN(newStart)) {
            this.customAlert("Please enter a valid date and time.");
            return;
        }
        if (newStart > Date.now()) {
            this.customAlert("Start time cannot be in the future.");
            return;
        }

        StateManager.updateActiveStartTime(newStart);
        TimerEngine.tick();
        this.updateTotals();
        this.closeActiveTimerModal();
    },

    openEditModal(index) {
        const session = StateManager.state.sessions[index];
        if (!session) return;

        const editSessionIndex = document.getElementById('editSessionIndex');
        const editStartTime = document.getElementById('editStartTime');
        const editEndTime = document.getElementById('editEndTime');
        const editSessionNote = document.getElementById('editSessionNote');
        const editModal = document.getElementById('editModal');
        const editModalContent = document.getElementById('editModalContent');

        editSessionIndex.value = index;
        editStartTime.value = PayrollEngine.timestampToDatetimeLocal(session.start);
        editEndTime.value = PayrollEngine.timestampToDatetimeLocal(session.end);
        if (editSessionNote) editSessionNote.value = session.note || "";

        editModal.classList.remove('hidden');
        setTimeout(() => {
            editModal.classList.remove('opacity-0');
            editModalContent.classList.remove('scale-95');
        }, 10);
    },

    closeEditModal() {
        const editModal = document.getElementById('editModal');
        const editModalContent = document.getElementById('editModalContent');
        editModal.classList.add('opacity-0');
        editModalContent.classList.add('scale-95');
        setTimeout(() => { editModal.classList.add('hidden'); }, 250);
    },

    saveSession() {
        const editSessionIndex = document.getElementById('editSessionIndex');
        const editStartTime = document.getElementById('editStartTime');
        const editEndTime = document.getElementById('editEndTime');
        const editSessionNote = document.getElementById('editSessionNote');

        const idx = parseInt(editSessionIndex.value);
        const newStart = new Date(editStartTime.value).getTime();
        const newEnd = new Date(editEndTime.value).getTime();
        const note = editSessionNote ? editSessionNote.value.trim() : "";

        if (!newStart || !newEnd || isNaN(newStart) || isNaN(newEnd)) {
            this.customAlert("Please enter valid dates and times.");
            return;
        }
        if (newStart >= newEnd) {
            this.customAlert("Start time must be before end time.");
            return;
        }

        StateManager.updateSession(idx, newStart, newEnd, note);
        this.updateTotals();
        this.closeEditModal();
        this.showUndoToast("Session updated successfully.");
    },

    deleteSession() {
        const editSessionIndex = document.getElementById('editSessionIndex');
        const idx = parseInt(editSessionIndex.value);

        this.customConfirm(
            "Delete Session?",
            "This action cannot be undone. Are you sure you want to permanently delete this work session?",
            "Delete",
            "bg-rose-600 hover:bg-rose-500",
            () => {
                StateManager.deleteSession(idx);
                this.updateTotals();
                this.closeEditModal();
                this.showUndoToast("Session deleted.");
            }
        );
    },

    openAddModal() {
        const addModal = document.getElementById('addModal');
        const addModalContent = document.getElementById('addModalContent');
        const addStartTime = document.getElementById('addStartTime');
        const addEndTime = document.getElementById('addEndTime');

        const now = new Date();
        const start = new Date(now.getTime() - (8 * 3600000));
        addStartTime.value = PayrollEngine.timestampToDatetimeLocal(start.getTime());
        addEndTime.value = PayrollEngine.timestampToDatetimeLocal(now.getTime());

        addModal.classList.remove('hidden');
        setTimeout(() => {
            addModal.classList.remove('opacity-0');
            addModalContent.classList.remove('scale-95');
        }, 10);
    },

    closeAddModal() {
        const addModal = document.getElementById('addModal');
        const addModalContent = document.getElementById('addModalContent');
        addModal.classList.add('opacity-0');
        addModalContent.classList.add('scale-95');
        setTimeout(() => { addModal.classList.add('hidden'); }, 250);
    },

    saveManualSession() {
        const addStartTime = document.getElementById('addStartTime');
        const addEndTime = document.getElementById('addEndTime');
        const addNote = document.getElementById('addSessionNote');

        const newStart = new Date(addStartTime.value).getTime();
        const newEnd = new Date(addEndTime.value).getTime();
        const note = addNote ? addNote.value.trim() : "";

        if (!newStart || !newEnd || isNaN(newStart) || isNaN(newEnd)) {
            this.customAlert("Please enter valid start and end dates/times.");
            return;
        }
        if (newStart >= newEnd) {
            this.customAlert("Start time must be before end time.");
            return;
        }

        StateManager.addManualSession(newStart, newEnd, note);
        this.updateTotals();
        this.closeAddModal();
        this.showUndoToast("Manual shift added.");
    },

    // --- PTO Modal ---
    openPtoModal() {
        const ptoModal = document.getElementById('ptoModal');
        const ptoModalContent = document.getElementById('ptoModalContent');
        const ptoDate = document.getElementById('ptoDate');
        ptoDate.value = new Date().toISOString().split('T')[0];

        ptoModal.classList.remove('hidden');
        setTimeout(() => {
            ptoModal.classList.remove('opacity-0');
            ptoModalContent.classList.remove('scale-95');
        }, 10);
    },

    closePtoModal() {
        const ptoModal = document.getElementById('ptoModal');
        const ptoModalContent = document.getElementById('ptoModalContent');
        ptoModal.classList.add('opacity-0');
        ptoModalContent.classList.add('scale-95');
        setTimeout(() => { ptoModal.classList.add('hidden'); }, 250);
    },

    savePto() {
        const ptoDate = document.getElementById('ptoDate');
        const ptoHours = document.getElementById('ptoHours');
        const ptoType = document.getElementById('ptoType');
        const ptoNote = document.getElementById('ptoNote');

        const dateMs = new Date(ptoDate.value + 'T00:00:00').getTime();
        const hours = parseFloat(ptoHours.value) || 0;
        const type = ptoType.value;
        const note = ptoNote ? ptoNote.value.trim() : '';

        if (!dateMs || isNaN(dateMs) || hours <= 0) {
            this.customAlert("Please enter valid date and hours (> 0).");
            return;
        }

        StateManager.addPtoEntry(dateMs, hours, type, note);
        this.updateTotals();
        this.closePtoModal();
    },

    // --- Settings Modal ---
    openSettingsModal() {
        const settingsModal = document.getElementById('settingsModal');
        const settingsModalContent = document.getElementById('settingsModalContent');
        const s = StateManager.state.settings || {};

        document.getElementById('settingSchedule').value = s.paySchedule || 'semimonthly';
        document.getElementById('settingStandardHours').value = s.standardShiftHours ?? 10.0;
        document.getElementById('settingCliffHours').value = s.cliffHours ?? 12.5;
        document.getElementById('settingOtMultiplier').value = s.otMultiplier ?? 1.0;
        document.getElementById('settingTheme').value = s.theme || 'dark';
        document.getElementById('settingSound').checked = s.soundEnabled !== false;
        document.getElementById('settingHaptic').checked = s.hapticEnabled !== false;
        document.getElementById('settingNotifications').checked = s.notificationsEnabled !== false;
        document.getElementById('settingAutoBreak').checked = s.autoBreakDeduction !== false;

        settingsModal.classList.remove('hidden');
        setTimeout(() => {
            settingsModal.classList.remove('opacity-0');
            settingsModalContent.classList.remove('scale-95');
        }, 10);
    },

    closeSettingsModal() {
        const settingsModal = document.getElementById('settingsModal');
        const settingsModalContent = document.getElementById('settingsModalContent');
        settingsModal.classList.add('opacity-0');
        settingsModalContent.classList.add('scale-95');
        setTimeout(() => { settingsModal.classList.add('hidden'); }, 250);
    },

    saveSettings() {
        const newSettings = {
            paySchedule: document.getElementById('settingSchedule').value,
            standardShiftHours: parseFloat(document.getElementById('settingStandardHours').value) || 10.0,
            cliffHours: parseFloat(document.getElementById('settingCliffHours').value) || 12.5,
            otMultiplier: parseFloat(document.getElementById('settingOtMultiplier').value) || 1.0,
            theme: document.getElementById('settingTheme').value,
            soundEnabled: document.getElementById('settingSound').checked,
            hapticEnabled: document.getElementById('settingHaptic').checked,
            notificationsEnabled: document.getElementById('settingNotifications').checked,
            autoBreakDeduction: document.getElementById('settingAutoBreak').checked
        };

        StateManager.updateSettings(newSettings);
        this.applyTheme(newSettings.theme);
        this.updateTotals();
        this.closeSettingsModal();
        this.showUndoToast("Settings saved successfully.");
    },

    // --- Analytics / Chart Modal ---
    openAnalyticsModal() {
        const analyticsModal = document.getElementById('analyticsModal');
        const analyticsModalContent = document.getElementById('analyticsModalContent');

        analyticsModal.classList.remove('hidden');
        setTimeout(() => {
            analyticsModal.classList.remove('opacity-0');
            analyticsModalContent.classList.remove('scale-95');
            ChartEngine.renderWeeklyBreakdown('weeklyChartContainer');
        }, 10);
    },

    closeAnalyticsModal() {
        const analyticsModal = document.getElementById('analyticsModal');
        const analyticsModalContent = document.getElementById('analyticsModalContent');
        analyticsModal.classList.add('opacity-0');
        analyticsModalContent.classList.add('scale-95');
        setTimeout(() => { analyticsModal.classList.add('hidden'); }, 250);
    },

    // --- Report / Print Modal ---
    openReportModal() {
        const reportModal = document.getElementById('reportModal');
        const reportModalContent = document.getElementById('reportModalContent');
        const reportBody = document.getElementById('reportBody');

        const totals = PayrollEngine.calculatePeriodTotals(StateManager.state);
        const sessions = StateManager.state.sessions;

        let rowsHtml = '';
        sessions.slice().reverse().forEach(s => {
            const dStart = new Date(s.start);
            const dEnd = new Date(s.end);
            const dur = PayrollEngine.formatDurationShort(s.end - s.start);
            rowsHtml += `
                <tr class="border-b border-slate-700/50">
                    <td class="py-2 text-xs text-slate-200">${dStart.toLocaleDateString()}</td>
                    <td class="py-2 text-xs text-slate-300">${dStart.toLocaleTimeString([], {hour:'2-digit', minute:'2-digit'})} - ${dEnd.toLocaleTimeString([], {hour:'2-digit', minute:'2-digit'})}</td>
                    <td class="py-2 text-xs font-bold text-slate-100">${dur}</td>
                    <td class="py-2 text-xs text-slate-400">${s.note || '-'}</td>
                </tr>
            `;
        });

        reportBody.innerHTML = `
            <div class="mb-4 p-4 bg-slate-900 rounded-xl border border-slate-700">
                <div class="flex justify-between mb-2">
                    <span class="text-xs text-slate-400">Total Period Payable:</span>
                    <span class="text-sm font-bold text-emerald-400">${totals.totalPayableHoursPeriod.toFixed(1)} hrs</span>
                </div>
                <div class="flex justify-between mb-2">
                    <span class="text-xs text-slate-400">Total Overtime:</span>
                    <span class="text-sm font-bold text-purple-400">${totals.totalOtHoursPeriod.toFixed(1)} hrs</span>
                </div>
                <div class="flex justify-between">
                    <span class="text-xs text-slate-400">Estimated Total (${StateManager.state.displayMode.toUpperCase()}):</span>
                    <span class="text-base font-bold text-emerald-400">${PayrollEngine.formatMoney(totals.periodEarnings)}</span>
                </div>
            </div>
            <table class="w-full text-left">
                <thead>
                    <tr class="text-[11px] font-bold text-slate-400 uppercase border-b border-slate-700">
                        <th class="pb-2">Date</th>
                        <th class="pb-2">Shift</th>
                        <th class="pb-2">Duration</th>
                        <th class="pb-2">Notes</th>
                    </tr>
                </thead>
                <tbody>
                    ${rowsHtml || '<tr><td colspan="4" class="text-center py-4 text-xs text-slate-500">No session records</td></tr>'}
                </tbody>
            </table>
        `;

        reportModal.classList.remove('hidden');
        setTimeout(() => {
            reportModal.classList.remove('opacity-0');
            reportModalContent.classList.remove('scale-95');
        }, 10);
    },

    closeReportModal() {
        const reportModal = document.getElementById('reportModal');
        const reportModalContent = document.getElementById('reportModalContent');
        reportModal.classList.add('opacity-0');
        reportModalContent.classList.add('scale-95');
        setTimeout(() => { reportModal.classList.add('hidden'); }, 250);
    },

    printReport() {
        window.print();
    },

    // --- Undo Toast ---
    showUndoToast(message) {
        let toast = document.getElementById('undoToast');
        if (!toast) {
            toast = document.createElement('div');
            toast.id = 'undoToast';
            toast.className = 'fixed bottom-4 left-1/2 -translate-x-1/2 bg-slate-800 border border-purple-500/50 shadow-2xl text-slate-100 px-4 py-2.5 rounded-full flex items-center space-x-3 z-50 transition-all duration-300 opacity-0 transform translate-y-4';
            document.body.appendChild(toast);
        }

        toast.innerHTML = `
            <span class="text-xs font-semibold">${message}</span>
            <button onclick="UIController.handleUndo()" class="text-xs font-bold text-purple-400 hover:text-purple-300 uppercase underline ml-2">Undo</button>
        `;

        toast.classList.remove('opacity-0', 'translate-y-4');
        setTimeout(() => {
            toast.classList.add('opacity-0', 'translate-y-4');
        }, 4000);
    },

    handleUndo() {
        const success = StateManager.undo();
        if (success) {
            this.updateTotals();
            const toast = document.getElementById('undoToast');
            if (toast) toast.classList.add('opacity-0', 'translate-y-4');
        }
    },

    customAlert(msg) {
        document.getElementById('alertMessage').innerText = msg;
        const alertModal = document.getElementById('alertModal');
        const alertContent = document.getElementById('alertModalContent');
        alertModal.classList.remove('hidden');
        setTimeout(() => {
            alertModal.classList.remove('opacity-0');
            alertContent.classList.remove('scale-95');
        }, 10);
    },

    closeAlert() {
        const alertModal = document.getElementById('alertModal');
        const alertContent = document.getElementById('alertModalContent');
        alertModal.classList.add('opacity-0');
        alertContent.classList.add('scale-95');
        setTimeout(() => { alertModal.classList.add('hidden'); }, 250);
    },

    customConfirm(title, message, btnText, btnColorClass, callback) {
        document.getElementById('confirmTitle').innerText = title;
        document.getElementById('confirmMessage').innerText = message;

        const actionBtn = document.getElementById('confirmActionBtn');
        actionBtn.innerText = btnText;
        actionBtn.className = `${btnColorClass} text-white rounded-xl px-5 py-2.5 font-bold text-sm shadow transition-colors`;

        this.confirmCallback = callback;

        const confirmModal = document.getElementById('confirmModal');
        const confirmContent = document.getElementById('confirmModalContent');
        confirmModal.classList.remove('hidden');
        setTimeout(() => {
            confirmModal.classList.remove('opacity-0');
            confirmContent.classList.remove('scale-95');
        }, 10);
    },

    closeConfirm() {
        const confirmModal = document.getElementById('confirmModal');
        const confirmContent = document.getElementById('confirmModalContent');
        confirmModal.classList.add('opacity-0');
        confirmContent.classList.add('scale-95');
        setTimeout(() => {
            confirmModal.classList.add('hidden');
            this.confirmCallback = null;
        }, 250);
    },

    clearData() {
        this.customConfirm(
            "Reset All Data?",
            "Are you sure you want to reset all tracked time? This will wipe your history permanently.",
            "Reset Data",
            "bg-rose-600 hover:bg-rose-500",
            () => {
                StateManager.clearAllData();
                this.setClockUIInactive();
                TimerEngine.stop();
                this.updateTotals();
            }
        );
    },

    exportCSV() {
        const sessions = StateManager.state.sessions;
        if (!sessions || sessions.length === 0) {
            this.customAlert("No session records found to export.");
            return;
        }

        let csv = "Date,Day,Start Time,End Time,Break (Mins),Tech Duration (Hours),Tech Duration (Formatted),Notes\n";
        sessions.forEach(s => {
            const dStart = new Date(s.start);
            const dEnd = new Date(s.end);
            const dateStr = dStart.toISOString().split('T')[0];
            const dayStr = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'][dStart.getDay()];
            const startStr = dStart.toLocaleTimeString();
            const endStr = dEnd.toLocaleTimeString();
            const breakMins = Math.round((s.breakMs || 0) / 60000);
            const durHrs = ((s.end - s.start) / 3600000).toFixed(2);
            const durFmt = PayrollEngine.formatDurationShort(s.end - s.start);
            const noteClean = (s.note || '').replace(/"/g, '""');

            csv += `"${dateStr}","${dayStr}","${startStr}","${endStr}",${breakMins},${durHrs},"${durFmt}","${noteClean}"\n`;
        });

        const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `Jokarz_Timeclock_Timesheet_${new Date().toISOString().split('T')[0]}.csv`;
        a.click();
        URL.revokeObjectURL(url);
    },

    exportJSONBackup() {
        const json = StateManager.exportJSON();
        const blob = new Blob([json], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `Jokarz_Timeclock_Backup_${new Date().toISOString().split('T')[0]}.json`;
        a.click();
        URL.revokeObjectURL(url);
    },

    triggerJSONImport() {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = '.json,application/json';
        input.onchange = (e) => {
            const file = e.target.files[0];
            if (!file) return;
            const reader = new FileReader();
            reader.onload = (event) => {
                const ok = StateManager.importJSON(event.target.result);
                if (ok) {
                    this.applyTheme(StateManager.state.settings?.theme || 'dark');
                    this.updateModeUI();
                    this.updateTotals();
                    if (StateManager.state.isClockedIn) {
                        this.setClockUIActive();
                        TimerEngine.start();
                    } else {
                        this.setClockUIInactive();
                    }
                    this.customAlert("Data backup restored successfully!");
                } else {
                    this.customAlert("Failed to import backup. Invalid JSON file format.");
                }
            };
            reader.readAsText(file);
        };
        input.click();
    }
};

window.UIController = UIController;
