/**
 * Jokarz Timeclock - Extended Reactive State Management
 * Includes support for Upgrades 1-20 (Schedules, Rules, Themes, Multipliers, PTO, Audit Log, Breaks)
 */
const StateManager = {
    KEY: 'workTrackerState_v2',
    LEGACY_KEY: 'workTrackerState',
    
    state: {
        isClockedIn: false,
        currentSessionStart: null,
        isOnBreak: false,
        breakStartTime: null,
        accumulatedBreakMs: 0,
        grossRate: 62.0,
        netRate: 30.0,
        displayMode: 'gross', // 'gross' | 'net'
        sessions: [], // Array of { id: string, start: number, end: number, breakMs?: number, note?: string }
        ptoEntries: [], // Array of { id: string, date: number, hours: number, type: 'PTO'|'Holiday'|'Sick', note?: string }
        settings: {
            theme: 'dark', // 'dark' | 'amoled' | 'light' | 'emerald' | 'amber'
            paySchedule: 'semimonthly', // 'semimonthly' | 'biweekly' | 'weekly' | 'monthly'
            biweeklyAnchorDate: '2026-01-05',
            standardShiftHours: 10.0,
            unpaidMealThreshold: 4.0,
            unpaidMealDuration: 0.5,
            cliffHours: 12.5,
            otMultiplier: 1.0,
            soundEnabled: true,
            hapticEnabled: true,
            notificationsEnabled: true,
            autoBreakDeduction: true
        },
        auditLog: [],
        undoStack: []
    },

    listeners: [],

    subscribe(listener) {
        this.listeners.push(listener);
    },

    notify() {
        this.listeners.forEach(fn => fn(this.state));
    },

    load() {
        let saved = localStorage.getItem(this.KEY);
        if (!saved) {
            saved = localStorage.getItem(this.LEGACY_KEY);
        }

        if (saved) {
            try {
                const parsed = JSON.parse(saved);
                if (parsed.hourlyRate !== undefined && parsed.grossRate === undefined) {
                    parsed.grossRate = parsed.hourlyRate;
                    parsed.netRate = parsed.hourlyRate * 0.75;
                    parsed.displayMode = 'gross';
                    delete parsed.hourlyRate;
                }
                
                this.state = {
                    ...this.state,
                    ...parsed,
                    settings: { ...this.state.settings, ...(parsed.settings || {}) },
                    sessions: parsed.sessions || [],
                    ptoEntries: parsed.ptoEntries || [],
                    auditLog: parsed.auditLog || [],
                    undoStack: []
                };
            } catch (e) {
                console.error("Failed to parse saved state:", e);
            }
        }
        return this.state;
    },

    save(skipNotify = false) {
        localStorage.setItem(this.KEY, JSON.stringify(this.state));
        if (!skipNotify) this.notify();
    },

    setMode(mode) {
        if (mode === 'gross' || mode === 'net') {
            this.state.displayMode = mode;
            this.save();
        }
    },

    setRate(type, rate) {
        const val = Math.max(0, parseFloat(rate) || 0);
        if (type === 'gross') this.state.grossRate = val;
        else if (type === 'net') this.state.netRate = val;
        this.save();
    },

    getCurrentRate() {
        return this.state.displayMode === 'gross' ? this.state.grossRate : this.state.netRate;
    },

    updateSettings(newSettings) {
        this.state.settings = { ...this.state.settings, ...newSettings };
        this.save();
    },

    clockIn() {
        if (this.state.isClockedIn) return;
        this.state.isClockedIn = true;
        this.state.currentSessionStart = Date.now();
        this.state.isOnBreak = false;
        this.state.breakStartTime = null;
        this.state.accumulatedBreakMs = 0;
        this.logAudit('CLOCK_IN', { start: this.state.currentSessionStart });
        this.save();
    },

    clockOut(note = "") {
        if (!this.state.isClockedIn) return;
        let breakMs = this.state.accumulatedBreakMs || 0;
        if (this.state.isOnBreak && this.state.breakStartTime) {
            breakMs += (Date.now() - this.state.breakStartTime);
        }

        const newSession = {
            id: 'sess_' + Date.now() + '_' + Math.random().toString(36).substring(2, 7),
            start: this.state.currentSessionStart,
            end: Date.now(),
            breakMs: breakMs,
            note: note || ""
        };

        this.pushUndo('ADD_SESSION', newSession);
        this.state.sessions.push(newSession);

        this.state.isClockedIn = false;
        this.state.currentSessionStart = null;
        this.state.isOnBreak = false;
        this.state.breakStartTime = null;
        this.state.accumulatedBreakMs = 0;

        this.logAudit('CLOCK_OUT', newSession);
        this.save();
    },

    toggleBreak() {
        if (!this.state.isClockedIn) return;
        if (!this.state.isOnBreak) {
            this.state.isOnBreak = true;
            this.state.breakStartTime = Date.now();
        } else {
            this.state.isOnBreak = false;
            if (this.state.breakStartTime) {
                this.state.accumulatedBreakMs = (this.state.accumulatedBreakMs || 0) + (Date.now() - this.state.breakStartTime);
                this.state.breakStartTime = null;
            }
        }
        this.save();
    },

    updateActiveStartTime(newStartMs) {
        if (!this.state.isClockedIn) return;
        this.state.currentSessionStart = newStartMs;
        this.save();
    },

    updateSession(index, startMs, endMs, note = "", breakMs = 0) {
        if (this.state.sessions[index]) {
            const oldSession = { ...this.state.sessions[index] };
            this.pushUndo('UPDATE_SESSION', { index, oldSession });

            this.state.sessions[index].start = startMs;
            this.state.sessions[index].end = endMs;
            this.state.sessions[index].note = note;
            this.state.sessions[index].breakMs = breakMs;

            this.logAudit('EDIT_SESSION', { index, session: this.state.sessions[index] });
            this.save();
        }
    },

    addManualSession(startMs, endMs, note = "", breakMs = 0) {
        const newSession = {
            id: 'sess_' + Date.now() + '_' + Math.random().toString(36).substring(2, 7),
            start: startMs,
            end: endMs,
            breakMs: breakMs,
            note: note
        };
        this.pushUndo('ADD_SESSION', newSession);
        this.state.sessions.push(newSession);
        this.logAudit('MANUAL_ADD_SESSION', newSession);
        this.save();
    },

    deleteSession(index) {
        if (index >= 0 && index < this.state.sessions.length) {
            const deleted = this.state.sessions[index];
            this.pushUndo('DELETE_SESSION', { index, session: deleted });
            this.state.sessions.splice(index, 1);
            this.logAudit('DELETE_SESSION', deleted);
            this.save();
        }
    },

    addPtoEntry(dateMs, hours, type = 'PTO', note = '') {
        const entry = {
            id: 'pto_' + Date.now() + '_' + Math.random().toString(36).substring(2, 7),
            date: dateMs,
            hours: parseFloat(hours) || 0,
            type: type,
            note: note
        };
        this.state.ptoEntries.push(entry);
        this.logAudit('ADD_PTO', entry);
        this.save();
    },

    deletePtoEntry(id) {
        const idx = this.state.ptoEntries.findIndex(p => p.id === id);
        if (idx !== -1) {
            const deleted = this.state.ptoEntries.splice(idx, 1)[0];
            this.logAudit('DELETE_PTO', deleted);
            this.save();
        }
    },

    logAudit(action, payload) {
        this.state.auditLog.unshift({
            action,
            timestamp: Date.now(),
            payload
        });
        if (this.state.auditLog.length > 50) this.state.auditLog.pop();
    },

    pushUndo(type, data) {
        this.state.undoStack.push({ type, data, timestamp: Date.now() });
        if (this.state.undoStack.length > 20) this.state.undoStack.shift();
    },

    undo() {
        if (!this.state.undoStack.length) return false;
        const lastAction = this.state.undoStack.pop();

        if (lastAction.type === 'ADD_SESSION') {
            const id = lastAction.data.id;
            const idx = this.state.sessions.findIndex(s => s.id === id);
            if (idx !== -1) this.state.sessions.splice(idx, 1);
        } else if (lastAction.type === 'DELETE_SESSION') {
            this.state.sessions.splice(lastAction.data.index, 0, lastAction.data.session);
        } else if (lastAction.type === 'UPDATE_SESSION') {
            this.state.sessions[lastAction.data.index] = lastAction.data.oldSession;
        }

        this.save();
        return true;
    },

    clearAllData() {
        this.pushUndo('CLEAR_ALL', JSON.parse(JSON.stringify(this.state)));
        this.state.sessions = [];
        this.state.ptoEntries = [];
        this.state.isClockedIn = false;
        this.state.currentSessionStart = null;
        this.state.isOnBreak = false;
        this.state.breakStartTime = null;
        this.state.accumulatedBreakMs = 0;
        this.save();
    },

    exportJSON() {
        return JSON.stringify(this.state, null, 2);
    },

    importJSON(jsonString) {
        try {
            const parsed = JSON.parse(jsonString);
            if (Array.isArray(parsed.sessions)) {
                this.state = {
                    ...this.state,
                    ...parsed,
                    settings: { ...this.state.settings, ...(parsed.settings || {}) }
                };
                this.save();
                return true;
            }
        } catch (e) {
            console.error("Invalid JSON import:", e);
        }
        return false;
    }
};

window.StateManager = StateManager;
