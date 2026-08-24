const { spawn } = require('child_process');
const path = require('path');

const rootDir = 'C:/RandallEngineering/Jokarz-Timeclock';
const gradleBat = path.join(rootDir, 'gradle-dist/gradle-8.12/bin/gradle.bat');

const env = {
    ...process.env,
    JAVA_HOME: 'C:/Program Files/Java/jdk-21',
    PATH: 'C:\\Program Files\\Java\\jdk-21\\bin;' + process.env.PATH
};

console.log('Running Kotlin Unit Tests...');
const child = spawn(gradleBat, ['testReleaseUnitTest'], {
    cwd: rootDir,
    env: env,
    shell: true,
    stdio: 'inherit'
});

child.on('close', code => {
    console.log(`Unit tests finished with code ${code}`);
    process.exit(code);
});
