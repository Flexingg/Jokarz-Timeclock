const { execSync } = require('child_process');
const https = require('https');
const fs = require('fs');

const cred = execSync('git credential fill', { input: 'protocol=https\nhost=github.com\n\n' }).toString();
let token = '';
cred.split('\n').forEach(line => {
    if (line.startsWith('password=')) token = line.substring(9).trim();
});

const releaseData = JSON.stringify({
    tag_name: 'v2.5.0',
    target_commitish: 'main',
    name: 'v2.5.0 - Geofence + Tasker Integration',
    body: `## 🚀 Jokarz Timeclock v2.5.0

### 📦 Download Latest APK
- **[Jokarz-Timeclock.apk](https://github.com/Flexingg/Jokarz-Timeclock/releases/download/v2.5.0/Jokarz-Timeclock.apk)** (Native Android App)

### ✨ What's New in v2.5.0

#### 📍 Geofence & Auto Clock-In/Out
- **Smart Geofence with Permission Checks**: Geofence registration now verifies fine + background location permissions and Google Play Services availability before activating.
- **Exponential Backoff Retry**: Failed geofence registrations automatically retry up to 3 times (2s, 4s, 8s).
- **Tasker Fallback Mode**: New toggle in Settings → "Use Tasker for Clock-In/Out" — bypasses native geofence and delegates to Tasker for trigger-based automation.
- **One-Click Tasker Profile Import**: Tap "Import Tasker Profile" to auto-import the geofence profile into Tasker. Falls back to clipboard copy if Tasker is not installed.
- **Tasker Broadcast Receiver**: App now handles \`ACTION_CLOCK_IN\` / \`ACTION_CLOCK_OUT\` broadcasts from Tasker (or any automation tool).

#### 🔐 Permissions
- **Background Location Permission**: Settings now shows a real-time permission status indicator and a direct button to grant "Allow all the time" via system settings.
- \`ACCESS_BACKGROUND_LOCATION\` added to manifest for Android 10+ geofencing.

#### 🔔 Notifications & Logging
- Improved auto clock-in/out notification messages distinguish between Tasker vs. Geofence triggers.
- Switched all geofence logging from println to \`android.util.Log\` (tagged \`GeofenceManager\`, \`GeofenceReceiver\`).

### 🔧 Technical Improvements
- \`GeofenceManager\`: permission guard, Play Services check, Tasker skip, exponential retry.
- \`GeofenceBroadcastReceiver\`: unified handler for native geofence + Tasker broadcasts.
- \`TaskerHelper\`: Tasker intent import with clipboard fallback.
- \`PermissionHelper\`: fine + background location runtime request flow.
- \`AppSettings\`: new \`useTaskerFallback: Boolean\` field.
- \`SettingsDialog\`: Tasker toggle, import button, permission status UI.
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
                console.log('v2.5.0 APK Upload complete!');
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
