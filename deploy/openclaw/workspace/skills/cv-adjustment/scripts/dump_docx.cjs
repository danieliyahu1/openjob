// Minimal docx (zip) reader: dump paragraphs of word/document.xml
const fs = require('fs');
const zlib = require('zlib');

function readZip(path) {
  const buf = fs.readFileSync(path);
  // locate End of Central Directory
  let eocd = -1;
  for (let i = buf.length - 22; i >= 0; i--) {
    if (buf.readUInt32LE(i) === 0x06054b50) { eocd = i; break; }
  }
  if (eocd < 0) throw new Error('not a zip');
  const count = buf.readUInt16LE(eocd + 10);
  let off = buf.readUInt32LE(eocd + 16);
  const entries = {};
  for (let i = 0; i < count; i++) {
    if (buf.readUInt32LE(off) !== 0x02014b50) throw new Error('bad central dir');
    const method = buf.readUInt16LE(off + 10);
    const compSize = buf.readUInt32LE(off + 20);
    const nameLen = buf.readUInt16LE(off + 28);
    const extraLen = buf.readUInt16LE(off + 30);
    const commentLen = buf.readUInt16LE(off + 32);
    const localOff = buf.readUInt32LE(off + 42);
    const name = buf.slice(off + 46, off + 46 + nameLen).toString('utf8');
    const lNameLen = buf.readUInt16LE(localOff + 26);
    const lExtraLen = buf.readUInt16LE(localOff + 28);
    const dataStart = localOff + 30 + lNameLen + lExtraLen;
    const raw = buf.slice(dataStart, dataStart + compSize);
    entries[name] = { method, data: method === 0 ? raw : zlib.inflateRawSync(raw), compSize };
    off += 46 + nameLen + extraLen + commentLen;
  }
  return entries;
}

function decode(s) {
  return s.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&#(\d+);/g, (m, d) => String.fromCodePoint(+d)).replace(/&amp;/g, '&');
}

const path = process.argv[2];
const e = readZip(path);
const xml = e['word/document.xml'].data.toString('utf8');
const paras = [];
const re = /<w:p\b[^>]*\/>|<w:p\b[^>]*>[\s\S]*?<\/w:p>/g;
let m;
while ((m = re.exec(xml))) paras.push(m[0]);
console.log('paragraphs:', paras.length, 'xml len:', xml.length);
paras.forEach((p, i) => {
  const st = p.match(/<w:pStyle w:val="([^"]*)"/);
  const texts = [...p.matchAll(/<w:t(?:\s[^>]*)?>([\s\S]*?)<\/w:t>/g)].map(x => decode(x[1]));
  const runs = [...p.matchAll(/<w:r(?:\s[^>]*)?>[\s\S]*?<\/w:r>/g)].map(x => {
    const t = [...x[0].matchAll(/<w:t(?:\s[^>]*)?>([\s\S]*?)<\/w:t>/g)].map(y => decode(y[1])).join('');
    const b = /<w:b(?:\s+w:val="(?:1|true|on)")?\s*\/>/.test(x[0]);
    const it = /<w:i(?:\s+w:val="(?:1|true|on)")?\s*\/>/.test(x[0]);
    return [t, b, it];
  });
  const hl = (p.match(/<w:hyperlink/g) || []).length;
  console.log(i + ' | ' + (st ? st[1] : '-') + ' | hl=' + hl + ' | ' + JSON.stringify(texts.join('')));
  if (runs.length > 1) console.log('     runs:', JSON.stringify(runs));
});
const rels = e['word/_rels/document.xml.rels'];
if (rels) {
  const rx = /Target="([^"]+)"[^>]*TargetMode="External"/g;
  let r;
  console.log('--- links ---');
  while ((r = rx.exec(rels.data.toString('utf8')))) console.log('LINK:', r[1]);
}
