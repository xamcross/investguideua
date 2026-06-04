// Regenerates the raster favicon set from the single vector source `public/seo/favicon.svg`.
//
// Source of truth is favicon.svg; everything else is derived, so edit the SVG and re-run:
//   node tools/icons/build-favicons.mjs
//
// Rasterization uses the already-present `puppeteer` Chromium (no extra deps): the SVG is drawn
// onto a <canvas> at each target size and read back as PNG. The .ico is then assembled by hand as
// a PNG-in-ICO container (16/32/48), which every modern browser and Windows Vista+ accept.
//
// Outputs (all into public/seo, which angular.json maps to the site root "/"):
//   favicon.ico, apple-touch-icon.png (180), icon-192.png, icon-512.png
import puppeteer from 'puppeteer';
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const outDir = join(here, '..', '..', 'public', 'seo');
const svg = readFileSync(join(outDir, 'favicon.svg'), 'utf8');

const SIZES = [16, 32, 48, 180, 192, 512];

const browser = await puppeteer.launch({
  headless: true,
  args: ['--no-sandbox', '--disable-setuid-sandbox'],
});
try {
  const page = await browser.newPage();
  const encoded = await page.evaluate(async (markup, sizes) => {
    const img = new Image();
    img.src = 'data:image/svg+xml;base64,' + btoa(unescape(encodeURIComponent(markup)));
    await img.decode();
    const result = {};
    for (const s of sizes) {
      const canvas = document.createElement('canvas');
      canvas.width = s;
      canvas.height = s;
      const ctx = canvas.getContext('2d');
      ctx.imageSmoothingEnabled = true;
      ctx.imageSmoothingQuality = 'high';
      ctx.drawImage(img, 0, 0, s, s);
      result[s] = canvas.toDataURL('image/png').split(',')[1];
    }
    return result;
  }, svg, SIZES);

  const png = (size) => Buffer.from(encoded[size], 'base64');

  writeFileSync(join(outDir, 'apple-touch-icon.png'), png(180));
  writeFileSync(join(outDir, 'icon-192.png'), png(192));
  writeFileSync(join(outDir, 'icon-512.png'), png(512));

  // Assemble a multi-resolution PNG-in-ICO (favicon.ico) from the small sizes.
  const icoSizes = [16, 32, 48];
  const header = Buffer.alloc(6);
  header.writeUInt16LE(0, 0); // reserved
  header.writeUInt16LE(1, 2); // type: icon
  header.writeUInt16LE(icoSizes.length, 4); // image count

  const entries = [];
  const blobs = [];
  let offset = 6 + icoSizes.length * 16;
  for (const s of icoSizes) {
    const data = png(s);
    const entry = Buffer.alloc(16);
    entry.writeUInt8(s >= 256 ? 0 : s, 0); // width  (0 means 256)
    entry.writeUInt8(s >= 256 ? 0 : s, 1); // height
    entry.writeUInt8(0, 2); // palette count
    entry.writeUInt8(0, 3); // reserved
    entry.writeUInt16LE(1, 4); // color planes
    entry.writeUInt16LE(32, 6); // bits per pixel
    entry.writeUInt32LE(data.length, 8); // bytes of image data
    entry.writeUInt32LE(offset, 12); // offset of image data
    offset += data.length;
    entries.push(entry);
    blobs.push(data);
  }
  writeFileSync(join(outDir, 'favicon.ico'), Buffer.concat([header, ...entries, ...blobs]));

  console.log('Favicons written to', outDir, '->', ['favicon.ico', 'apple-touch-icon.png', 'icon-192.png', 'icon-512.png'].join(', '));
} finally {
  await browser.close();
}
