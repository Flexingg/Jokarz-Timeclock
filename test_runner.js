const fs = require('fs');

global.window = global;
global.localStorage = {
    _data: {},
    getItem(k) { return this._data[k] || null; },
    setItem(k, v) { this._data[k] = String(v); },
    removeItem(k) { delete this._data[k]; }
};

const stateCode = fs.readFileSync('C:/RandallEngineering/Jokarz-Timeclock/js/state.js', 'utf8');
const payrollCode = fs.readFileSync('C:/RandallEngineering/Jokarz-Timeclock/js/payroll.js', 'utf8');

Function(stateCode)();
Function(payrollCode)();

console.log('--- RUNNING PAYROLL & TIMECLOCK TESTS ---');

// Test 1: Mon-Thu standard shift (10.5h clocked -> 10.0h payable, 0 OT, 0 bank)
StateManager.clearAllData();
const mondayMs = new Date('2026-08-24T00:00:00').getTime();
const shift1Start = new Date('2026-08-24T06:00:00').getTime();
const shift1End = new Date('2026-08-24T16:30:00').getTime(); // 10.5 hrs

StateManager.addManualSession(shift1Start, shift1End);
let stats = PayrollEngine.calculateDayStats(mondayMs, false, StateManager.state);
console.log('Test 1 (10.5h Mon): clocked =', stats.clockedHours, 'payable =', stats.payableHours, 'OT =', stats.otHours, 'bank =', stats.bankedHours);
if (stats.clockedHours === 10.5 && stats.payableHours === 10 && stats.otHours === 0 && stats.bankedHours === 0) {
    console.log(' PASS: Standard shift 10.5h calculation matches.');
} else {
    console.error(' FAIL: Standard shift calculation mismatch!');
    process.exit(1);
}

// Test 2: Mon shift with banking buffer (11.5h clocked -> 10h payable, +1.0h banked)
StateManager.clearAllData();
const shift2End = new Date('2026-08-24T17:30:00').getTime(); // 11.5 hrs
StateManager.addManualSession(shift1Start, shift2End);
stats = PayrollEngine.calculateDayStats(mondayMs, false, StateManager.state);
console.log('Test 2 (11.5h Mon): clocked =', stats.clockedHours, 'payable =', stats.payableHours, 'OT =', stats.otHours, 'bank =', stats.bankedHours);
if (stats.clockedHours === 11.5 && stats.payableHours === 10 && stats.otHours === 0 && stats.bankedHours === 1.0) {
    console.log(' PASS: 11.5h Banking buffer calculation matches.');
} else {
    console.error(' FAIL: Banking calculation mismatch!');
    process.exit(1);
}

// Test 3: Mon shift crossing 12.5h Cliff (13.0h clocked -> OT unlocks back to 10.5h -> 10h base + 2.5h OT = 12.5h payable)
StateManager.clearAllData();
const shift3End = new Date('2026-08-24T19:00:00').getTime(); // 13.0 hrs
StateManager.addManualSession(shift1Start, shift3End);
stats = PayrollEngine.calculateDayStats(mondayMs, false, StateManager.state);
console.log('Test 3 (13.0h Mon Cliff): clocked =', stats.clockedHours, 'payable =', stats.payableHours, 'OT =', stats.otHours, 'bank =', stats.bankedHours);
if (stats.clockedHours === 13.0 && stats.otHours === 2.5 && stats.payableHours === 12.5 && stats.bankedHours === 0) {
    console.log(' PASS: 12.5h Cliff Overtime calculation matches.');
} else {
    console.error(' FAIL: Cliff OT calculation mismatch!');
    process.exit(1);
}

// Test 4: Weekend shift (Saturday 6h clocked -> 5.5h OT)
StateManager.clearAllData();
const satMs = new Date('2026-08-22T00:00:00').getTime(); // Sat within current period
const satStart = new Date('2026-08-22T08:00:00').getTime();
const satEnd = new Date('2026-08-22T14:00:00').getTime(); // 6.0 hrs
StateManager.addManualSession(satStart, satEnd);
stats = PayrollEngine.calculateDayStats(satMs, false, StateManager.state);
console.log('Test 4 (6.0h Sat): clocked =', stats.clockedHours, 'payable =', stats.payableHours, 'OT =', stats.otHours);
if (stats.clockedHours === 6.0 && stats.otHours === 5.5 && stats.payableHours === 5.5) {
    console.log(' PASS: Weekend Overtime calculation matches.');
} else {
    console.error(' FAIL: Weekend OT calculation mismatch!');
    process.exit(1);
}

// Test 5: Bank carry-forward adjustment
StateManager.clearAllData();
const monShortEnd = new Date('2026-08-24T15:30:00').getTime();
StateManager.addManualSession(shift1Start, monShortEnd);
const tuesdayMs = new Date('2026-08-25T00:00:00').getTime();
const prevBank = PayrollEngine.getPreviousBankedHoursForCurrentWeek(tuesdayMs, StateManager.state);
console.log('Test 5 (Bank offset for Tue): prevBank =', prevBank);
if (prevBank === -1.0) {
    console.log(' PASS: Previous bank offset correctly calculated as -1.0h.');
} else {
    console.error(' FAIL: Prev bank offset mismatch!');
    process.exit(1);
}

// Test 6: Overtime Multiplier on Sat shift
StateManager.clearAllData();
StateManager.updateSettings({ otMultiplier: 1.5 });
StateManager.setRate('gross', 60.0);
stats = PayrollEngine.calculateDayStats(satMs, false, StateManager.state);
StateManager.addManualSession(satStart, satEnd);
stats = PayrollEngine.calculateDayStats(satMs, false, StateManager.state);
const satEarnings = stats.otHours * 60.0 * StateManager.state.settings.otMultiplier;
console.log('Test 6 (1.5x OT Multiplier on Sat 5.5h @ $60/hr): earnings = $' + satEarnings);
if (satEarnings === 495) {
    console.log(' PASS: 1.5x OT Multiplier calculated accurately ($495.00).');
} else {
    console.error(' FAIL: OT Multiplier mismatch! Expected 495, got', satEarnings);
    process.exit(1);
}

console.log('\n========================================');
console.log(' ALL UNIT TESTS PASSED SUCCESSFULLY! ');
console.log('========================================\n');
