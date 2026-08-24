/**
 * Jokarz Timeclock - Android Tasker Integration Bridge
 */
const TaskerBridge = {
    sendEvent(eventText) {
        if (typeof performTask === function) {
            try {
                performTask(Work Tracker Event, 10, eventText, ");
 } catch (e) {
 console.warn(Tasker performTask failed:, e);
 }
 }
 },

 pushData(todayStats, totalTechHrsPeriod, totalActualHrsPeriod, state = StateManager.state) {
 if (typeof setGlobal === function && todayStats) {
 try {
 setGlobal(WorkTechHrsToday, todayStats.clockedHours.toFixed(2));
 setGlobal(WorkActualHrsToday, todayStats.payableHours.toFixed(2));
 setGlobal(WorkActualGrossToday, (todayStats.payableHours * state.grossRate).toFixed(2));
 setGlobal(WorkActualNetToday, (todayStats.payableHours * state.netRate).toFixed(2));

 setGlobal(WorkActualHrsPeriod, totalActualHrsPeriod.toFixed(2));
 setGlobal(WorkActualGrossPeriod, (totalActualHrsPeriod * state.grossRate).toFixed(2));
 setGlobal(WorkActualNetPeriod, (totalActualHrsPeriod * state.netRate).toFixed(2));
 } catch (e) {
 console.warn(Tasker setGlobal failed:, e);
 }
 }
 }
};

window.TaskerBridge = TaskerBridge;
