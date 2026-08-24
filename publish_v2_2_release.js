const { execSync } = require('child_process');
const https = require('https');
const fs = require('fs');

const cred = execSync('git credential fill', { input: 'protocol=https\nhost=github.com\n\n' }).toString();
let token = '';
cred.split('\n').forEach(line => {
    if (line.startsWith('password=')) token = line.substring(9).trim();
});

const releaseData = JSON.stringify({
    tag_name: 'v2.2.0',
    target_commitish: 'main',
    name: 'v2.2.0 - Live Notifications, Google Maps Geofencing & Native TimePicker',
    body: '## 🚀 Jokarz Timeclock v2.2.0\n\n### 📦 Download Latest APK\n- **[Jokarz-Timeclock.apk](https://github.com/Flexingg/Jokarz-Timeclock/releases/download/v2.2.0/Jokarz-Timeclock.apk)** (Native Android App)\n\n### ✨ What\'s New in v2.2.0\n- **Live Ongoing Android Notification**: Displays continuous time at work (`HH:MM:SS`) and remaining shift duration in the Android notification shade with 1-tap `Lunch/Pause` and `Clock Out` action buttons.\n- **Google Maps Geofencing for Auto Clock-In/Out**: Configure work location (latitude, longitude, or 1-tap GPS capture) with radius (50m-500m) to automatically clock in when arriving at work and clock out when leaving.\n- **Native Material 3 Clock-Face TimePicker & DatePicker**: Replaced raw text inputs with Google Material 3 native dial clock face and calendar pickers for active shift start time and past shift logs.\n'
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
        console.log('Uploading Native APK to:', uploadUrl);

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
                console.log('v2.2.0 Native APK Upload complete!');
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
