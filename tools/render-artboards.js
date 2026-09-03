// Turn .dc.html artboards into plain standalone HTML for headless screenshotting.
const fs = require('fs');
const path = require('path');

const SRC = process.argv[2];
const OUT = process.argv[3];
fs.mkdirSync(OUT, { recursive: true });

const LEVELS = [
  { fil: 'Bukong-bukong', en: 'Ankle' },
  { fil: 'Tuhod', en: 'Knee' },
  { fil: 'Baywang', en: 'Waist' },
  { fil: 'Dibdib', en: 'Chest' },
];
const PICKED = 2; // waist

function chips() {
  return LEVELS.map((l, i) => {
    const sel = i === PICKED;
    const bg = sel ? '#14171A' : '#FFFFFF';
    const fg = sel ? '#FFFFFF' : '#14171A';
    const sub = sel ? '#A8B0BA' : '#5C666F';
    const border = sel ? '#14171A' : '#D8DEE3';
    return `      <div style="height: 76px; background: ${bg}; border: 1.5px solid ${border}; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px; box-sizing: border-box;">
        <span style="font-size: 14px; font-weight: 600; color: ${fg}; text-align: center; line-height: 1.15;">${l.fil}</span>
        <span style="font-size: 12px; color: ${sub};">${l.en}</span>
      </div>`;
  }).join('\n');
}

const SUBS = {
  'Report-Normal': (h) => h
    .replace(/<sc-for[^>]*>[\s\S]*?<\/sc-for>/, chips())
    .replace(/\{\{waterY\}\}/g, '108')
    .replace(/\{\{waterH\}\}/g, '88')
    .replace(/\{\{sevColor\}\}/g, '#C42B2B')
    .replace(/\{\{sevCode\}\}/g, 'S3')
    .replace(/\{\{sevText\}\}/g, 'Hindi madaanan')
    .replace(/\{\{sevEn\}\}/g, 'Impassable for all')
    .replace(/\{\{sevTextColor\}\}/g, '#FFFFFF'),
};

function convert(name) {
  let h = fs.readFileSync(path.join(SRC, name + '.dc.html'), 'utf8');
  h = h.replace(/<script src="\.\/support\.js"><\/script>/g, '');
  h = h.replace(/<script data-dc-script>[\s\S]*?<\/script>/g, '');
  if (SUBS[name]) h = SUBS[name](h);
  h = h.replace(/<\/?x-dc>/g, '').replace(/<\/?helmet>/g, '');
  h = h.replace(/<body>/, '<body style="margin:0">');
  const left = (h.match(/\{\{/g) || []).length;
  fs.writeFileSync(path.join(OUT, name + '.html'), h);
  console.log(`${name}: ${left ? 'WARNING ' + left + ' unresolved bindings' : 'clean'}`);
}

process.argv.slice(4).forEach(convert);
