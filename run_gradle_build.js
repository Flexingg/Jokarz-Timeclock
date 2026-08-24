const { spawn } = require('child_process');
const path = require('path');

const rootDir = 'C:/RandallEngineering/Jokarz-Timeclock';
const gradleBat = path.join(rootDir, 'gradle-dist/gradle-8.12/bin/gradle.bat');

const env = {
    ...process.env,
    JAVA_HOME: 'C:/Program Files/Java/jdk-21',
    PATH: 'C:\\Program Files\\Java\\jdk-21\\bin;' + process.env.PATH
};

console.log('Starting Gradle assembleRelease...');
const child = spawn(gradleBat, ['assembleRelease', '--stacktrace'], {
    cwd: rootDir,
    env: env,
    shell: true,
    stdio: 'inherit'
});

child.on('close', code => {
    console.log(`Gradle build finished with code ${code}`);
    process.exit(code);
});
