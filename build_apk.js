const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const SDK_DIR = 'C:/Users/Jonat/AppData/Local/Android/Sdk';
const BUILD_TOOLS_DIR = path.join(SDK_DIR, 'build-tools/35.0.0');
const ANDROID_JAR = path.join(SDK_DIR, 'platforms/android-34/android.jar');
const PROJECT_DIR = 'C:/RandallEngineering/Jokarz-Timeclock';
const ANDROID_DIR = path.join(PROJECT_DIR, 'android-build');

console.log('=== BUILDING FULLY SELF-CONTAINED JOKARZ TIMECLOCK APK ===');

// 1. Prepare directory structure
const dirs = [
    ANDROID_DIR,
    path.join(ANDROID_DIR, 'src/com/randallengineering/jokarztimeclock'),
    path.join(ANDROID_DIR, 'res/values'),
    path.join(ANDROID_DIR, 'res/mipmap-hdpi'),
    path.join(ANDROID_DIR, 'assets/css'),
    path.join(ANDROID_DIR, 'assets/js'),
    path.join(ANDROID_DIR, 'assets/icons'),
    path.join(ANDROID_DIR, 'bin'),
    path.join(ANDROID_DIR, 'dex')
];

dirs.forEach(d => {
    if (!fs.existsSync(d)) fs.mkdirSync(d, { recursive: true });
});

// 2. Copy Web Assets to assets/
const filesToCopy = [
    { src: path.join(PROJECT_DIR, 'index.html'), dest: path.join(ANDROID_DIR, 'assets/index.html') },
    { src: path.join(PROJECT_DIR, 'manifest.json'), dest: path.join(ANDROID_DIR, 'assets/manifest.json') },
    { src: path.join(PROJECT_DIR, 'css/styles.css'), dest: path.join(ANDROID_DIR, 'assets/css/styles.css') },
    { src: path.join(PROJECT_DIR, 'css/tailwind.min.js'), dest: path.join(ANDROID_DIR, 'assets/css/tailwind.min.js') },
    { src: path.join(PROJECT_DIR, 'css/material-icons.woff2'), dest: path.join(ANDROID_DIR, 'assets/css/material-icons.woff2') },
    { src: path.join(PROJECT_DIR, 'icons/icon-192.png'), dest: path.join(ANDROID_DIR, 'assets/icons/icon-192.png') },
    { src: path.join(PROJECT_DIR, 'icons/icon-512.png'), dest: path.join(ANDROID_DIR, 'assets/icons/icon-512.png') }
];

const jsFiles = fs.readdirSync(path.join(PROJECT_DIR, 'js'));
jsFiles.forEach(file => {
    filesToCopy.push({
        src: path.join(PROJECT_DIR, 'js', file),
        dest: path.join(ANDROID_DIR, 'assets/js', file)
    });
});

filesToCopy.forEach(({ src, dest }) => {
    fs.copyFileSync(src, dest);
});

// Copy icon to mipmap
fs.copyFileSync(path.join(PROJECT_DIR, 'icons/icon-192.png'), path.join(ANDROID_DIR, 'res/mipmap-hdpi/ic_launcher.png'));

// 3. Create AndroidManifest.xml
const manifestXml = `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.randallengineering.jokarztimeclock"
    android:versionCode="2"
    android:versionName="1.0.1">

    <uses-sdk android:minSdkVersion="24" android:targetSdkVersion="34" />

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:label="@string/app_name"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher"
        android:theme="@android:style/Theme.NoTitleBar.Fullscreen"
        android:usesCleartextTraffic="true"
        android:allowBackup="true">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:configChanges="orientation|screenSize|keyboardHidden"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="jokarz" android:host="timeclock" />
            </intent-filter>
        </activity>
    </application>
</manifest>`;
fs.writeFileSync(path.join(ANDROID_DIR, 'AndroidManifest.xml'), manifestXml, 'utf8');

// 4. Create res/values/strings.xml
const stringsXml = `<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Jokarz Timeclock</string>
</resources>`;
fs.writeFileSync(path.join(ANDROID_DIR, 'res/values/strings.xml'), stringsXml, 'utf8');

// 5. Create MainActivity.java
const mainActivityJava = `package com.randallengineering.jokarztimeclock;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;

public class MainActivity extends Activity {
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());

        String url = "file:///android_asset/index.html";
        if (getIntent() != null && getIntent().getData() != null) {
            String query = getIntent().getData().getQuery();
            if (query != null && !query.isEmpty()) {
                url += "?" + query;
            }
        }

        webView.loadUrl(url);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
`;
fs.writeFileSync(path.join(ANDROID_DIR, 'src/com/randallengineering/jokarztimeclock/MainActivity.java'), mainActivityJava, 'utf8');

console.log('Project files generated.');

const AAPT2 = `"${path.join(BUILD_TOOLS_DIR, 'aapt2.exe')}"`;
const D8 = `"${path.join(BUILD_TOOLS_DIR, 'd8.bat')}"`;
const ZIPALIGN = `"${path.join(BUILD_TOOLS_DIR, 'zipalign.exe')}"`;
const APKSIGNER = `"${path.join(BUILD_TOOLS_DIR, 'apksigner.bat')}"`;

try {
    // Step A: aapt2 compile
    console.log('Compiling resources...');
    execSync(`${AAPT2} compile --dir "${path.join(ANDROID_DIR, 'res')}" -o "${path.join(ANDROID_DIR, 'compiled_res.zip')}"`, { stdio: 'inherit' });

    // Step B: aapt2 link without -A (we will pack assets directly to ensure clean forward-slash POSIX paths)
    console.log('Linking base APK...');
    execSync(`${AAPT2} link -I "${ANDROID_JAR}" --manifest "${path.join(ANDROID_DIR, 'AndroidManifest.xml')}" -o "${path.join(ANDROID_DIR, 'base.apk')}" "${path.join(ANDROID_DIR, 'compiled_res.zip')}" --auto-add-overlay --java "${path.join(ANDROID_DIR, 'src')}"`, { stdio: 'inherit' });

    // Step C: javac compile
    console.log('Compiling Java classes...');
    const javaFiles = [
        path.join(ANDROID_DIR, 'src/com/randallengineering/jokarztimeclock/MainActivity.java'),
        path.join(ANDROID_DIR, 'src/com/randallengineering/jokarztimeclock/R.java')
    ];
    execSync(`javac --release 17 -cp "${ANDROID_JAR}" -d "${path.join(ANDROID_DIR, 'bin')}" ${javaFiles.map(f => `"${f}"`).join(' ')}`, { stdio: 'inherit' });

    // Step D: d8 dex compilation
    console.log('Compiling DEX bytecode...');
    const binDir = path.join(ANDROID_DIR, 'bin/com/randallengineering/jokarztimeclock');
    const allClasses = fs.readdirSync(binDir).filter(f => f.endsWith('.class')).map(f => `"${path.join(binDir, f)}"`).join(' ');
    execSync(`${D8} --lib "${ANDROID_JAR}" --output "${path.join(ANDROID_DIR, 'dex')}" ${allClasses}`, { stdio: 'inherit' });

    // Step E: Insert classes.dex into base.apk
    console.log('Adding classes.dex and assets to APK...');
    const baseApkPath = path.join(ANDROID_DIR, 'base.apk');
    
    // Add classes.dex
    process.chdir(path.join(ANDROID_DIR, 'dex'));
    execSync(`jar uf "${baseApkPath}" classes.dex`, { stdio: 'inherit' });

    // Add assets/ directory with pure POSIX forward slashes
    process.chdir(ANDROID_DIR);
    execSync(`jar uf "${baseApkPath}" assets/index.html assets/manifest.json assets/css/styles.css assets/css/tailwind.min.js assets/css/material-icons.woff2 assets/icons/icon-192.png assets/icons/icon-512.png`, { stdio: 'inherit' });
    
    // Add all js files
    const jsList = fs.readdirSync(path.join(ANDROID_DIR, 'assets/js')).map(f => `assets/js/${f}`).join(' ');
    execSync(`jar uf "${baseApkPath}" ${jsList}`, { stdio: 'inherit' });

    process.chdir(PROJECT_DIR);

    // Step F: zipalign
    console.log('Aligning APK...');
    const alignedApk = path.join(ANDROID_DIR, 'aligned.apk');
    if (fs.existsSync(alignedApk)) fs.unlinkSync(alignedApk);
    execSync(`${ZIPALIGN} -v -p 4 "${baseApkPath}" "${alignedApk}"`, { stdio: 'inherit' });

    // Step G: Generate keystore if not exists
    const keystore = path.join(ANDROID_DIR, 'debug.keystore');
    if (!fs.existsSync(keystore)) {
        console.log('Generating debug keystore...');
        execSync(`keytool -genkey -v -keystore "${keystore}" -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"`, { stdio: 'inherit' });
    }

    // Step H: Sign APK
    console.log('Signing APK...');
    const finalApk = path.join(PROJECT_DIR, 'Jokarz-Timeclock.apk');
    if (fs.existsSync(finalApk)) fs.unlinkSync(finalApk);
    execSync(`${APKSIGNER} sign --ks "${keystore}" --ks-pass pass:android --out "${finalApk}" "${alignedApk}"`, { stdio: 'inherit' });

    console.log('\n SUCCESS: Self-contained APK build complete!');
    console.log(` Output APK: ${finalApk}`);
    console.log(` File Size: ${(fs.statSync(finalApk).size / 1024).toFixed(1)} KB`);

} catch (err) {
    console.error('Error during APK build:', err);
    process.exit(1);
}
