/**
 * Jokarz Timeclock - Audio & Haptic Feedback Engine
 * Procedural Web Audio API sound synthesis & Vibration API haptics
 */
const AudioHaptics = {
    audioCtx: null,

    getAudioContext() {
        if (!this.audioCtx) {
            const AudioContext = window.AudioContext || window.webkitAudioContext;
            if (AudioContext) {
                this.audioCtx = new AudioContext();
            }
        }
        if (this.audioCtx && this.audioCtx.state === 'suspended') {
            this.audioCtx.resume();
        }
        return this.audioCtx;
    },

    vibrate(pattern = 50) {
        if (StateManager.state.settings?.hapticEnabled !== false && 'vibrate' in navigator) {
            try {
                navigator.vibrate(pattern);
            } catch (e) {}
        }
    },

    playClockInSound() {
        if (StateManager.state.settings?.soundEnabled === false) return;
        const ctx = this.getAudioContext();
        if (!ctx) return;

        const now = ctx.currentTime;
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();

        osc.type = 'sine';
        osc.frequency.setValueAtTime(440, now); // A4
        osc.frequency.exponentialRampToValueAtTime(880, now + 0.15); // A5

        gain.gain.setValueAtTime(0.01, now);
        gain.gain.linearRampToValueAtTime(0.18, now + 0.05);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.35);

        osc.connect(gain);
        gain.connect(ctx.destination);

        osc.start(now);
        osc.stop(now + 0.35);
        this.vibrate([40, 60, 40]);
    },

    playClockOutSound() {
        if (StateManager.state.settings?.soundEnabled === false) return;
        const ctx = this.getAudioContext();
        if (!ctx) return;

        const now = ctx.currentTime;
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();

        osc.type = 'sine';
        osc.frequency.setValueAtTime(660, now); // E5
        osc.frequency.exponentialRampToValueAtTime(330, now + 0.2); // E4

        gain.gain.setValueAtTime(0.01, now);
        gain.gain.linearRampToValueAtTime(0.18, now + 0.05);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.35);

        osc.connect(gain);
        gain.connect(ctx.destination);

        osc.start(now);
        osc.stop(now + 0.35);
        this.vibrate([60, 40, 20]);
    },

    playChimeAlert() {
        if (StateManager.state.settings?.soundEnabled === false) return;
        const ctx = this.getAudioContext();
        if (!ctx) return;

        const now = ctx.currentTime;
        [523.25, 659.25, 783.99, 1046.50].forEach((freq, i) => {
            const osc = ctx.createOscillator();
            const gain = ctx.createGain();
            osc.type = 'triangle';
            osc.frequency.setValueAtTime(freq, now + (i * 0.08));

            gain.gain.setValueAtTime(0.001, now + (i * 0.08));
            gain.gain.linearRampToValueAtTime(0.15, now + (i * 0.08) + 0.02);
            gain.gain.exponentialRampToValueAtTime(0.001, now + (i * 0.08) + 0.4);

            osc.connect(gain);
            gain.connect(ctx.destination);
            osc.start(now + (i * 0.08));
            osc.stop(now + (i * 0.08) + 0.4);
        });
        this.vibrate([100, 50, 100, 50, 150]);
    },

    playButtonClick() {
        if (StateManager.state.settings?.soundEnabled === false) return;
        const ctx = this.getAudioContext();
        if (!ctx) return;

        const now = ctx.currentTime;
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();

        osc.type = 'triangle';
        osc.frequency.setValueAtTime(800, now);
        osc.frequency.exponentialRampToValueAtTime(200, now + 0.04);

        gain.gain.setValueAtTime(0.08, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.04);

        osc.connect(gain);
        gain.connect(ctx.destination);

        osc.start(now);
        osc.stop(now + 0.04);
        this.vibrate(20);
    }
};

window.AudioHaptics = AudioHaptics;
