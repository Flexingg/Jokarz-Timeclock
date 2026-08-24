const fs = require('fs');
const path = require('path');

const rootDir = 'C:/RandallEngineering/Jokarz-Timeclock';
const distDir = path.join(rootDir, 'gradle-dist/gradle-8.12');
const wrapperDir = path.join(rootDir, 'gradle/wrapper');

if (!fs.existsSync(wrapperDir)) fs.mkdirSync(wrapperDir, { recursive: true });

// Copy gradlew and gradlew.bat
fs.copyFileSync(path.join(distDir, 'bin/gradle.bat'), path.join(rootDir, 'gradlew.bat'));

// Setup gradle-wrapper.properties
const props = `distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=file:///C:/Users/Jonat/.gradle/wrapper/dists/gradle-8.12-all/ejduaidbjup3bmmkhw3rie4zb/gradle-8.12-all.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
`;
fs.writeFileSync(path.join(wrapperDir, 'gradle-wrapper.properties'), props, 'utf8');

console.log('Gradle wrapper configured.');
