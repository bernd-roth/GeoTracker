'use strict';

// Match the native RecordingFlags geometry, colors, and pole-tip anchors.
// Opposing flags remain distinguishable when the endpoints coincide.
function createRecordingFlagMarker(checkered, eventNumber = 1) {
    const number = Number.isSafeInteger(eventNumber) && eventNumber > 0 ? eventNumber : 1;
    const label = String(number);
    const labelX = checkered ? 21 : 19;
    const labelWidth = Math.min(26, 8 + label.length * 7);
    const element = document.createElement('div');
    element.style.cssText = 'width:40px;height:48px;cursor:pointer';
    element.title = `Event ${number} - ${checkered ? 'Recording finish' : 'Recording start'}`;
    element.setAttribute('aria-label', element.title);

    let squares = '';
    if (checkered) {
        for (let row = 0; row < 4; row++) {
            for (let column = 0; column < 5; column++) {
                if ((row + column) % 2 === 0) {
                    squares += `<rect x="${6 + column * 6}" y="${4 + row * 5.5}" width="6" height="5.5" fill="black"/>`;
                }
            }
        }
    }
    element.innerHTML = `<svg xmlns="http://www.w3.org/2000/svg" width="40" height="48" viewBox="0 0 40 48" aria-hidden="true" style="display:block">
        <g${checkered ? '' : ' transform="translate(40 0) scale(-1 1)"'}>
            <path d="M5 3V48" stroke="white" stroke-width="5"/>
            <rect x="5" y="3" width="32" height="24" fill="white"/>
            <rect x="6" y="4" width="30" height="22" fill="${checkered ? 'white' : '#009141'}"/>
            ${squares}
            <path d="M5 3V48" stroke="#444444" stroke-width="2"/>
        </g>
        <rect x="${labelX - labelWidth / 2}" y="7" width="${labelWidth}" height="16" rx="3" fill="white"/>
        <text x="${labelX}" y="19" text-anchor="middle" font-family="sans-serif" font-size="${label.length > 3 ? 9 : 12}" font-weight="bold" fill="#222222"${label.length > 3 ? ' textLength="22" lengthAdjust="spacingAndGlyphs"' : ''}>${label}</text>
    </svg>`;

    return new maplibregl.Marker({
        element,
        anchor: 'bottom',
        offset: [checkered ? 15 : -15, 0]
    });
}
