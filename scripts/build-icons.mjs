// Builds the icon sprite from lucide-static. Run with: node scripts/build-icons.mjs
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';

const icons = ['layout-dashboard', 'calendar-days', 'shield-alert', 'ticket', 'inbox', 'shield-check',
    'calendar-range', 'users', 'clipboard-check', 'menu', 'sun', 'moon', 'log-out', 'plus', 'chevron-right',
    'chevron-left', 'triangle-alert', 'clock', 'map-pin', 'hard-hat', 'user-round', 'user-x', 'arrow-up',
    'arrow-down', 'minus', 'message-square', 'circle-check', 'circle-x', 'pencil', 'languages', 'bell', 'activity', 'search'];

const symbols = icons.map(name => {
    const svg = readFileSync(`node_modules/lucide-static/icons/${name}.svg`, 'utf8');
    const body = svg.slice(svg.indexOf('>', svg.indexOf('<svg')) + 1, svg.lastIndexOf('</svg>')).replace(/\s*\n\s*/g, '');
    return `<symbol id="${name}" viewBox="0 0 24 24">${body}</symbol>`;
});
mkdirSync('src/main/resources/static/img', { recursive: true });
writeFileSync('src/main/resources/static/img/icons.svg',
    `<svg xmlns="http://www.w3.org/2000/svg">\n${symbols.join('\n')}\n</svg>\n`);
