const fs = require('fs');
const path = require('path');

const srcIcon = 'C:/RandallEngineering/Jokarz-Timeclock/icons/icon-192.png';
const resDir = 'C:/RandallEngineering/Jokarz-Timeclock/app/src/main/res';
const mipmaps = ['mipmap-mdpi', 'mipmap-hdpi', 'mipmap-xhdpi', 'mipmap-xxhdpi', 'mipmap-xxxhdpi'];

mipmaps.forEach(m => {
    const dir = path.join(resDir, m);
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
    fs.copyFileSync(srcIcon, path.join(dir, 'ic_launcher.png'));
    fs.copyFileSync(srcIcon, path.join(dir, 'ic_launcher_round.png'));
});
console.log('Mipmap icons copied.');
