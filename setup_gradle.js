const fs = require('fs');
const { execSync } = require('child_process');
const path = require('path');

const gradleZip = 'C:/Users/Jonat/.gradle/wrapper/dists/gradle-8.12-all/ejduaidbjup3bmmkhw3rie4zb/gradle-8.12-all.zip';
const targetDir = 'C:/RandallEngineering/Jokarz-Timeclock/gradle-dist';

if (!fs.existsSync(targetDir)) fs.mkdirSync(targetDir, { recursive: true });
console.log('Extracting Gradle 8.12...');
execSync(`tar -xf "${gradleZip}" -C "${targetDir}"`, { stdio: 'inherit' });
console.log('Gradle extracted successfully.');
