/**
 * Jokarz Timeclock - Main Application Bootstrapper
 * Handles PWA registration, URL action routing, and theme init
 */
document.addEventListener('DOMContentLoaded', () => {
    // 1. Load state
    StateManager.load();

    // 2. Initialize UI Controller
    UIController.init();

    // 3. Handle URL Actions (Android Quick Tile / Shortcuts / Tasker automation)
    // Examples: ?action=clock_in, ?action=clock_out, ?action=toggle, ?action=break
    const params = new URLSearchParams(window.location.search);
    const action = params.get('action');
    if (action) {
        if (action === 'clock_in' && !StateManager.state.isClockedIn) {
            UIController.toggleClock();
        } else if (action === 'clock_out' && StateManager.state.isClockedIn) {
            UIController.toggleClock();
        } else if (action === 'toggle') {
            UIController.toggleClock();
        } else if (action === 'break' && StateManager.state.isClockedIn) {
            StateManager.toggleBreak();
            TimerEngine.tick();
        }
        // Clean URL without refresh
        window.history.replaceState({}, document.title, window.location.pathname);
    }

    // 4. Register PWA Service Worker
    if ('serviceWorker' in navigator) {
        window.addEventListener('load', () => {
            navigator.serviceWorker.register('./sw.js')
                .then(reg => {
                    console.log('Jokarz Timeclock PWA Service Worker Registered:', reg.scope);
                })
                .catch(err => {
                    console.log('Service Worker registration skipped or failed:', err);
                });
        });
    }
});
