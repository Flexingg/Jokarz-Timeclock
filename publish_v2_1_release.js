const { execSync } = require('child_process');
const https = require('https');
const fs = require('fs');

const cred = execSync('git credential fill', { input: 'protocol=https\nhost=github.com\n\n' }).toString();
let token = '';
cred.split('\n').forEach(line => {
    if (line.startsWith('password=')) token = line.substring(9).trim();
});

const releaseData = JSON.stringify({
    tag_name: 'v2.1.0',
    target_commitish: 'main',
    name: 'v2.1.0 - Google Material You Redesign & Live Ticking',
    body: '## ⏱️ Jokarz Timeclock v2.1.0\n\n### 📦 Download Latest APK\n- **[Jokarz-Timeclock.apk](https://github.com/Flexingg/Jokarz-Timeclock/releases/download/v2.1.0/Jokarz-Timeclock.apk)** (Native Android App)\n\n### ✨ What\'s New in v2.1.0\n- **Google Material You (M3 Expressive) Redesign**: Completely updated visual design inspired by Google Clock, Google Fit, and Pixel system styling.\n- **Real-Time 1-Second Dynamic Live Ticking**: Active timer now ticks live every second without needing to press buttons or navigate.\n- **Google Clock-Style Hero Widget**: Circular animated progress arc tracking progress toward the 10.5h standard shift and 12.5h overtime cliff target.\n- **Material 3 Action Chips & Switcher**: Pill-shaped action chips, tonal segmented rate switcher, and clean metric cards.\n'
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
                console.log('v2.1.0 Native APK Upload complete!');
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
