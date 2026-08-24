const { execSync } = require('child_process');
const https = require('https');
const fs = require('fs');

const cred = execSync('git credential fill', { input: 'protocol=https\nhost=github.com\n\n' }).toString();
let token = '';
cred.split('\n').forEach(line => {
    if (line.startsWith('password=')) token = line.substring(9).trim();
});

const releaseData = JSON.stringify({
    tag_name: 'v2.0.0',
    target_commitish: 'main',
    name: 'v2.0.0 - 100% Native Jetpack Compose & Material You Release',
    body: '## 🚀 Jokarz Timeclock v2.0.0 (Native Jetpack Compose & Material You)\n\nComplete ground-up rewrite as a **100% Native Android application** built using **Kotlin, Jetpack Compose, and Material You (Material 3)**.\n\n### 📦 Download Latest Native APK\n- **[Jokarz-Timeclock.apk](https://github.com/Flexingg/Jokarz-Timeclock/releases/download/v2.0.0/Jokarz-Timeclock.apk)** (Direct Download)\n\n### ✨ Highlights in v2.0.0\n- **100% Native Jetpack Compose UI**: Zero WebView, zero HTML/CSS/Tailwind dependencies.\n- **Material You Dynamic Theming**: Adaptive system wallpaper coloring on Android 12+, True AMOLED Black, Slate Dark, Cyber Emerald, Amber Glow, and Material Light.\n- **Kotlin Payroll Engine (`PayrollEngine.kt`)**: Mon-Thu 10h salary base, auto 30m break deduction after 4h, 10.5h-12.5h unpaid bank buffer, strict 12.5h overtime cliff, weekend 100% overtime, and semi-monthly/weekly pay schedules.\n- **Pulsing Clock Button**: Native Compose animated ripple & canvas pulse rings.\n- **Live Stats & Status**: Countdown, bank buffer accumulation, live OT dollar accrual, and 1-tap Lunch/Break pause.\n- **Weekly Swiper & Canvas Analytics**: Native `LazyRow` weekly bank cards and Compose `Canvas` bar chart.\n- **Native Audio & Haptics**: Procedural `AudioTrack` synthesizer and vibration feedback.\n- **Tasker Automation & Deep Links**: Broadcast intents for Tasker variables and intent action routing.\n- **Timesheet Share Intent**: 1-Tap CSV export with Android `Intent.ACTION_SEND`.\n'
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
                console.log('v2.0.0 Native APK Upload complete!');
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
