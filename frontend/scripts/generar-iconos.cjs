// Genera los íconos de la PWA desde public/logo.svg con Chromium (Playwright). Uso: node scripts/generar-iconos.cjs
const { chromium } = require('playwright');
const fs = require('fs'); const path = require('path');
const pub = path.join(__dirname, '..', 'public');
const svg = fs.readFileSync(path.join(pub, 'logo.svg')).toString('base64');
// [archivo, lado, fondo, fracción del lado que ocupa el logo]
const salidas = [
  ['icons/icon-192.png', 192, null, 1],
  ['icons/icon-512.png', 512, null, 1],
  ['icons/icon-512-maskable.png', 512, '#ffffff', 0.56], // zona segura: círculo del 80 %
  ['icons/apple-touch-icon.png', 180, '#ffffff', 0.78],   // iOS pinta negro lo transparente
  ['icons/favicon-16.png', 16, null, 1],
  ['icons/favicon-32.png', 32, null, 1],
  ['icons/favicon-48.png', 48, null, 1],
];
(async () => {
  const b = await chromium.launch();
  for (const [archivo, lado, fondo, fr] of salidas) {
    const p = await b.newPage({ viewport: { width: lado, height: lado } });
    const s = Math.round(lado * fr);
    await p.setContent(`<html><body style="margin:0;width:${lado}px;height:${lado}px;display:flex;align-items:center;justify-content:center;background:${fondo ?? 'transparent'}"><img src="data:image/svg+xml;base64,${svg}" width="${s}" height="${s}"></body></html>`);
    await p.waitForFunction(() => document.images[0].complete);
    await p.screenshot({ path: path.join(pub, archivo), omitBackground: fondo === null });
    await p.close();
  }
  await b.close();
  // favicon.ico con PNG embebidos (16, 32, 48): cabecera ICONDIR + una entrada por imagen.
  const pngs = [16, 32, 48].map((n) => [n, fs.readFileSync(path.join(pub, `icons/favicon-${n}.png`))]);
  const cab = Buffer.alloc(6 + 16 * pngs.length); cab.writeUInt16LE(0, 0); cab.writeUInt16LE(1, 2); cab.writeUInt16LE(pngs.length, 4);
  let offset = cab.length;
  pngs.forEach(([n, png], i) => {
    const o = 6 + 16 * i; cab.writeUInt8(n, o); cab.writeUInt8(n, o + 1); cab.writeUInt16LE(1, o + 4); cab.writeUInt16LE(32, o + 6);
    cab.writeUInt32LE(png.length, o + 8); cab.writeUInt32LE(offset, o + 12); offset += png.length;
  });
  fs.writeFileSync(path.join(pub, 'favicon.ico'), Buffer.concat([cab, ...pngs.map((x) => x[1])]));
  for (const n of [16, 32, 48]) fs.unlinkSync(path.join(pub, `icons/favicon-${n}.png`));
})();
