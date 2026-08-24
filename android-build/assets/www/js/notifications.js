/**
 * Jokarz Timeclock - Notifications & Media Session Engine
 */
const NotificationEngine = {
    standardNotified: false,
    cliffNotified: false,
    audioElement: null,

    async requestPermission() {
        if ('Notification' in window && Notification.permission === 'default') {
            try {
                await Notification.requestPermission();
            } catch (e) {}
        }
    },

    notify(title, body) {
        if ('Notification' in window && Notification.permission === 'granted') {
            try {
                new Notification(title, {
                    body: body,
                    icon: 'icons/icon-192.png',
                    badge: 'icons/icon-192.png',
                    vibrate: [200, 100, 200]
                });
            } catch (e) {
                console.warn(Notification trigger failed:, e);
            }
        }
    },

    checkMilestones(elapsedMs, standardMs, cliffMs) {
        if (!StateManager.state.isClockedIn) {
            this.standardNotified = false;
            this.cliffNotified = false;
            return;
        }

        // 1. Standard shift reached
        if (elapsedMs >= standardMs && !this.standardNotified) {
            this.standardNotified = true;
            this.notify(Standard Shift Complete!, You have fulfilled today's required shift hours. Entering banking buffer.);
            AudioHaptics.playChimeAlert();
        }

        // 2. Overtime Cliff reached
        if (elapsedMs >= cliffMs && !this.cliffNotified) {
            this.cliffNotified = true;
            this.notify(Overtime Unlocked! 🔥, You have crossed the 12.5h threshold! Overtime pay is now actively accruing.);
            AudioHaptics.playChimeAlert();
        }
    },

    resetMilestones() {
        this.standardNotified = false;
        this.cliffNotified = false;
        this.updateMediaSession(false);
    },

    updateMediaSession(isActive, durationStr = ", statusStr = ) {
 if (!('mediaSession' in navigator)) return;

 if (isActive) {
 navigator.mediaSession.metadata = new MediaMetadata({
 title: Active Shift: ,
 artist: statusStr || Jokarz Timeclock,
 album: Randall Engineering,
 artwork: [{ src: 'icons/icon-192.png', sizes: '192x192', type: 'image/png' }]
 });

 navigator.mediaSession.setActionHandler('pause', () => {
 UIController.toggleClock();
 });
 navigator.mediaSession.setActionHandler('play', () => {
 UIController.toggleClock();
 });
 } else {
 navigator.mediaSession.metadata = null;
 }
 }
};

window.NotificationEngine = NotificationEngine;
