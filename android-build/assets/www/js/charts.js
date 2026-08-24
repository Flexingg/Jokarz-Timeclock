/**
 * Jokarz Timeclock - Visual Analytics & SVG Charting
 */
const ChartEngine = {
    renderWeeklyBreakdown(containerId, weekStartMs = null) {
        const container = document.getElementById(containerId);
        if (!container) return;

        const startOfWeek = weekStartMs ? new Date(weekStartMs) : new Date(PayrollEngine.getStartOfWeekDate());
        const days = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
        const dayData = [];

        let maxHours = 14;
        let cur = new Date(startOfWeek);

        for (let i = 0; i < 7; i++) {
            const ms = cur.getTime();
            const stats = PayrollEngine.calculateDayStats(ms, false, StateManager.state);
            const rawHours = stats.clockedHours;
            const otHours = stats.otHours;
            if (rawHours > maxHours) maxHours = Math.ceil(rawHours + 1);

            dayData.push({
                day: days[i],
                dateStr: (cur.getMonth() + 1) + '/' + cur.getDate(),
                rawHours,
                workedHours: stats.workedHours,
                baseHours: stats.baseHours,
                otHours,
                bankedHours: stats.bankedHours
            });
            cur.setDate(cur.getDate() + 1);
        }

        const width = 360;
        const height = 180;
        const barWidth = 30;
        const gap = 18;
        const startX = 18;
        const chartHeight = 120;

        let barsSvg = '';
        dayData.forEach((d, idx) => {
            const x = startX + idx * (barWidth + gap);
            const totalH = (d.rawHours / maxHours) * chartHeight;
            const y = chartHeight - totalH + 20;

            const otH = (d.otHours / maxHours) * chartHeight;

            let barFill = 'url(#regGrad)';
            if (d.otHours > 0) barFill = 'url(#otGrad)';
            else if (d.rawHours === 0) barFill = '#334155';

            barsSvg += '<g class= chart-bar>';
            barsSvg += '<title>' + d.day + ' (' + d.dateStr + '): ' + d.rawHours.toFixed(1) + 'h (' + d.otHours.toFixed(1) + 'h OT)</title>';
            barsSvg += '<rect x= + x +  y= + y +  width= + barWidth +  height= + totalH +  rx=6 fill= + barFill +  />';
            if (d.otHours > 0) {
                barsSvg += '<rect x= + x +  y= + y +  width= + barWidth +  height= + otH +  rx=6 fill=#c084fc />';
            }
            barsSvg += '<text x= + (x + barWidth / 2) +  y= + (y - 4) +  text-anchor=middle fill=#e2e8f0 font-size=10 font-weight=bold>' + (d.rawHours > 0 ? d.rawHours.toFixed(1) + 'h' : '') + '</text>';
            barsSvg += '<text x= + (x + barWidth / 2) +  y= + (chartHeight + 35) +  text-anchor=middle fill=#94a3b8 font-size=11 font-weight=600>' + d.day + '</text>';
            barsSvg += '</g>';
        });

        const svg = '<svg viewBox=0 0  + width +   + height +   class= w-full h-auto xmlns=http://www.w3.org/2000/svg>' +
            '<defs>' +
            '<linearGradient id=regGrad x1=0% y1=0% x2=0% y2=100%>' +
            '<stop offset=0% stop-color=#8b5cf6 />' +
            '<stop offset=100% stop-color=#6d28d9 />' +
            '</linearGradient>' +
            '<linearGradient id=otGrad x1=0% y1=0% x2=0% y2=100%>' +
            '<stop offset=0% stop-color=#f43f5e />' +
            '<stop offset=100% stop-color=#be123c />' +
            '</linearGradient>' +
            '</defs>' +
            '<line x1=10 y1=20 x2= + (width - 10) +  y2=20 stroke=#334155 stroke-dasharray=3 3 />' +
            '<line x1=10 y1= + (chartHeight / 2 + 20) +  x2= + (width - 10) +  y2= + (chartHeight / 2 + 20) +  stroke=#334155 stroke-dasharray=3 3 />' +
            '<line x1=10 y1= + (chartHeight + 20) +  x2= + (width - 10) +  y2= + (chartHeight + 20) +  stroke=#475569 stroke-width=1.5 />' +
            barsSvg +
            '</svg>';

        container.innerHTML = svg;
    }
};

window.ChartEngine = ChartEngine;