const { execSync } = require('child_process');
const https = require('https');
const fs = require('fs');

const cred = execSync('git credential fill', { input: 'protocol=https\nhost=github.com\n\n' }).toString();
let token = '';
cred.split('\n').forEach(line => {
    if (line.startsWith('password=')) token = line.substring(9).trim();
});

const releaseData = JSON.stringify({
    tag_name: 'v2.6.1',
    target_commitish: 'main',
    name: 'v2.6.1 - Live Status Bar Chronometer Chip & Dynamic Island Fix',
    body: `## 🚀 Jokarz Timeclock v2.6.1

### 📦 Download Latest APK
- **[Jokarz-Timeclock.apk](https://github.com/Flexingg/Jokarz-Timeclock/releases/download/v2.6.1/Jokarz-Timeclock.apk)** (Native Android App)

### ✨ What's Fixed & Improved in v2.6.1

#### ⏱️ True Status Bar Chronometer Chip & Aqua Dynamics Pill (Oppo Find X9 Pro / ColorOS / Android 16)
- **Eliminated 1-Second Notification Re-Posting Spam**: Previous versions called \`notify()\` every 1000ms to update raw elapsed string text, which repeatedly invalidated the notification and prevented Android & ColorOS from promoting it into a persistent status bar chip / Dynamic Island pill.
- **Hardware Native Chronometer**: The notification is now posted once (with state observation on break/resume/clock) and relies on Android's native Chronometer widget (\`setUsesChronometer(true)\`, \`setWhen(startMs)\`, \`setChronometerCountDown(false)\`) so the system UI's status bar punch-hole / capsule renders smooth live ticking seconds without any notify spam.
- **Clean Monochrome Vector Icon**: Added dedicated \`ic_stat_stopwatch\` vector drawable for crisp rendering inside the status bar chip.
- **Stable Titles & High Priority Channel**: Configured channel \`jokarz_live_shift_chip_v4\` with \`IMPORTANCE_HIGH\`, static title \`"⏱️ Shift Active"\` / \`"⏸️ Shift Paused (Lunch)"\`, and full ColorOS Aqua Dynamics (\`oplus.isLiveAlert\`, \`oplus.capsule.enable\`) and Android 16 Rich Ongoing Notification extras.
`
});

const createReq = https.request({
    hostname: 'api.github.com',
    path: '/repos/Flexingg/Jokarz-Timeclock/releases',
    method: 'POST',
    headers: {
        'User-Agent': 'NodeJS-Release-Uploader',
        'Authorization': 'token ' + token,
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(releaseData)
    }
}, res => {
    let body = '';
    res.on('data', chunk => body += chunk);
    res.on('end', () => {
        console.log('Release create status:', res.statusCode);
        const parsed = JSON.parse(body);
        if (!parsed.upload_url) {
            console.error('No upload URL in response:', body);
            process.exit(1);
        }

        const uploadUrl = parsed.upload_url.split('{')[0] + '?name=Jokarz-Timeclock.apk';
        console.log('Uploading APK to:', uploadUrl);

        const apkPath = 'C:/RandallEngineering/Jokarz-Timeclock/Jokarz-Timeclock.apk';
        const apkData = fs.readFileSync(apkPath);

        const uploadReq = https.request(uploadUrl, {
            method: 'POST',
            headers: {
                'User-Agent': 'NodeJS-Release-Uploader',
                'Authorization': 'token ' + token,
                'Content-Type': 'application/vnd.android.package-archive',
                'Content-Length': apkData.length
            }
        }, uploadRes => {
            let uploadBody = '';
            uploadRes.on('data', c => uploadBody += c);
            uploadRes.on('end', () => {
                console.log('APK Upload status:', uploadRes.statusCode);
                console.log('v2.6.1 APK Upload complete!');
            });
        });

        uploadReq.on('error', e => console.error('Upload error:', e));
        uploadReq.write(apkData);
        uploadReq.end();
    });
});

createReq.on('error', e => console.error('Create error:', e));
createReq.write(releaseData);
createReq.end();
