const fs = require('fs');
const path = 'd:/ASUS/Documents/jiedan/waibao/network-monitor-frontend/src/components/world.js';
let content = fs.readFileSync(path, 'utf8');
const startMarker = 'const worldData = ';
const startIdx = content.indexOf(startMarker) + startMarker.length;
let endIdx = content.lastIndexOf('}');
while (endIdx > startIdx) {
    try {
        const testJson = content.substring(startIdx, endIdx + 1);
        JSON.parse(testJson);
        console.log('Found valid JSON at', endIdx);
        const newContent = 'import * as echarts from "echarts";\n\nconst worldData = ' + testJson + ';\n\necharts.registerMap("world", worldData);\n';
        fs.writeFileSync(path, newContent);
        process.exit(0);
    } catch (e) {
        endIdx = content.lastIndexOf('}', endIdx - 1);
    }
}
console.error('No valid JSON found');
process.exit(1);
