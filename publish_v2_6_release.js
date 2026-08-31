const { execSync } = require('child_process');
const https = require('https');
const fs = require('fs');

const cred = execSync('git credential fill', { input: 'protocol=https\nhost=github.com\n\n' }).toString();
let token = '';
cred.split('\n').forEach(line => {
    if (line.startsWith('password=')) token = line.substring(9).trim();
});

const releaseData = JSON.stringify({
    tag_name: 'v2.6.0',
    target_commitish: 'main',
    name: 'v2.6.0 - Dynamic Island Live Alert & Overtime System Input',
    body: `## 🚀 Jokarz Timeclock v2.6.0

### 📦 Download Latest APK
- **[Jokarz-Timeclock.apk](https://github.com/Flexingg/Jokarz-Timeclock/releases/download/v2.6.0/Jokarz-Timeclock.apk)** (Native Android App)

### ✨ What's New in v2.6.0

#### 🏝️ Dynamic Island & Live Alert (Oppo / ColorOS / Android 16)
- **True Live Notification for Dynamic Island**: Upgraded the live shift notification channel to \`IMPORTANCE_DEFAULT\` and configured priority, category (\`CATEGORY_STOPWATCH\`), and system flags so that ColorOS Aqua Dynamics / OnePlus Fluid Cloud and Android 16 promoted ongoing live activities render in the top Dynamic Island capsule.
- **Silent Real-time Chronometer**: Uses native tick chronometer with public lockscreen visibility, silent notifications, and immediate foreground service promotion without intrusive beeps or vibrations.
- **Dynamic Action Controls**: Quick actions to pause/resume lunch breaks and clock out directly from the expanded Dynamic Island and status bar capsule.

#### 📊 Overtime System Input Tracker
- **Dedicated Overtime Screen & Dialog**: Easily accessible from the top bar (with live pending count badge) and main screen action chip \`[ OT Input ]\`.
- **Automatic OT Detection**: Detects all shifts where hours exceeded the 12.5h cliff on weekdays (>2h past expected shift) or any weekend shifts (Fri/Sat/Sun).
- **Interactive Checkboxes**: Boolean check boxes on each OT shift to track whether overtime has been submitted into the company payroll system.
- **Batch Actions & Quick Copy**: "Mark All Pending as Input" button, filter tabs (All, Pending, Submitted), and one-tap "Copy OT Summary" button to copy clean timesheet summaries directly to the clipboard.
- **History & Edit Integration**: Completed shift log and edit dialogs now also show and persist the "Entered into Payroll System" status.
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
                console.log('v2.6.0 APK Upload complete!');
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
