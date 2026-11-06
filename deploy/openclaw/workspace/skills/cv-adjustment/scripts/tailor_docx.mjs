// Node port of skills/cv-adjustment/scripts/tailor_docx.py (python unavailable on this host)
const fs = require('fs');
const zlib = require('zlib');
const path = require('path');

function readZip(file) {
  const buf = fs.readFileSync(file);
  let eocd = -1;
  for (let i = buf.length - 22; i >= 0; i--) if (buf.readUInt32LE(i) === 0x06054b50) { eocd = i; break; }
  if (eocd < 0) throw new Error('not a zip: ' + file);
  const count = buf.readUInt16LE(eocd + 10);
  let off = buf.readUInt32LE(eocd + 16);
  const entries = [];
  for (let i = 0; i < count; i++) {
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
    const data = method === 0 ? raw : zlib.inflateRawSync(raw);
    entries.push({ name, data });
    off += 46 + nameLen + extraLen + commentLen;
  }
  return entries;
}

const CRC_TABLE = (() => {
  const t = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c;
  }
  return t;
})();
function crc32(buf) {
  let c = 0 ^ -1;
  for (let i = 0; i < buf.length; i++) c = (c >>> 8) ^ CRC_TABLE[(c ^ buf[i]) & 0xff];
  return (c ^ -1) >>> 0;
}

function writeZip(file, entries) {
  const chunks = [];
  const central = [];
  let offset = 0;
  for (const e of entries) {
    const nameBuf = Buffer.from(e.name, 'utf8');
    const comp = zlib.deflateRawSync(e.data);
    const crc = crc32(e.data);
    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0);
    local.writeUInt16LE(20, 4);
    local.writeUInt16LE(0x0800, 6);
    local.writeUInt16LE(8, 8);
    local.writeUInt16LE(0, 10);
    local.writeUInt16LE(0, 12);
    local.writeUInt32LE(crc, 14);
    local.writeUInt32LE(comp.length, 18);
    local.writeUInt32LE(e.data.length, 22);
    local.writeUInt16LE(nameBuf.length, 26);
    local.writeUInt16LE(0, 28);
    chunks.push(local, nameBuf, comp);
    const cd = Buffer.alloc(46);
    cd.writeUInt32LE(0x02014b50, 0);
    cd.writeUInt16LE(20, 4);
    cd.writeUInt16LE(20, 6);
    cd.writeUInt16LE(0x0800, 8);
    cd.writeUInt16LE(8, 10);
    cd.writeUInt16LE(0, 12);
    cd.writeUInt16LE(0, 14);
    cd.writeUInt32LE(crc, 16);
    cd.writeUInt32LE(comp.length, 20);
    cd.writeUInt32LE(e.data.length, 24);
    cd.writeUInt16LE(nameBuf.length, 28);
    cd.writeUInt16LE(0, 30);
    cd.writeUInt16LE(0, 32);
    cd.writeUInt16LE(0, 34);
    cd.writeUInt16LE(0, 36);
    cd.writeUInt32LE(0, 38);
    cd.writeUInt32LE(offset, 42);
    central.push(cd, nameBuf);
    offset += local.length + nameBuf.length + comp.length;
  }
  const cdBuf = Buffer.concat(central);
  const eocd = Buffer.alloc(22);
  eocd.writeUInt32LE(0x06054b50, 0);
  eocd.writeUInt16LE(entries.length, 8);
  eocd.writeUInt16LE(entries.length, 10);
  eocd.writeUInt32LE(cdBuf.length, 12);
  eocd.writeUInt32LE(offset, 16);
  fs.writeFileSync(file, Buffer.concat([...chunks, cdBuf, eocd]));
}

const esc = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

function paraList(xml) {
  const re = /<w:p\b[^>]*\/>|<w:p\b[^>]*>[\s\S]*?<\/w:p>/g;
  const out = [];
  let m;
  while ((m = re.exec(xml))) out.push({ text: m[0], start: m.index, end: m.index + m[0].length });
  return out;
}

function firstRun(p) {
  const m = p.match(/<w:r(?:\s[^>]*)?>[\s\S]*?<\/w:r>/);
  return m ? m[0] : null;
}

function runPr(run) {
  if (!run) return '';
  const m = run.match(/<w:rPr>[\s\S]*?<\/w:rPr>/);
  return m ? m[0] : '';
}

function setFlag(rpr, tag, value) {
  if (value === null || value === undefined) return rpr;
  const re = new RegExp('<' + tag + '(?:\\s+w:val="[^"]*")?\\s*/>');
  let out = rpr.replace(re, '');
  if (value === true) {
    if (out) {
      // insert right after the opening <w:rPr>
      out = out.replace('<w:rPr>', '<w:rPr><' + tag + '/>');
    } else {
      out = '<w:rPr><' + tag + '/></w:rPr>';
    }
  }
  return out;
}

function buildRun(baseRun, text, bold, italic) {
  let rpr = runPr(baseRun);
  if (rpr) {
    rpr = setFlag(rpr, 'w:b', bold);
    rpr = setFlag(rpr, 'w:i', italic);
  } else {
    rpr = '<w:rPr>' + (bold ? '<w:b/>' : '') + (italic ? '<w:i/>' : '') + '</w:rPr>';
  }
  return '<w:r>' + rpr + '<w:t xml:space="preserve">' + esc(text) + '</w:t></w:r>';
}

function makePara(template, baseRun, segments, keepWithNext) {
  let p = template;
  // drop existing runs / hyperlinks / bookmarks, keep pPr
  p = p.replace(/<w:hyperlink\b[\s\S]*?<\/w:hyperlink>/g, '');
  p = p.replace(/<w:r(?:\s[^>]*)?>[\s\S]*?<\/w:r>/g, '');
  p = p.replace(/<w:bookmarkStart\b[^>]*\/>/g, '').replace(/<w:bookmarkEnd\b[^>]*\/>/g, '');
  if (keepWithNext && /<w:pPr>/.test(p)) {
    p = p.replace('<w:pPr>', '<w:pPr><w:keepNext/>');
  } else if (keepWithNext) {
    p = p.replace(/(<w:p\b[^>]*>)/, '$1<w:pPr><w:keepNext/></w:pPr>');
  }
  const runs = segments.map((s) => buildRun(baseRun, s[0], s[1] === undefined ? null : s[1], s[2] === undefined ? null : s[2])).join('');
  return p.replace(/<\/w:p>$/, runs + '</w:p>');
}

function main() {
  const args = process.argv.slice(2);
  const get = (k) => args[args.indexOf(k) + 1];
  const src = get('--src'), out = get('--out'), specPath = get('--spec');
  const spec = JSON.parse(fs.readFileSync(specPath, 'utf8').replace(/^\uFEFF/, ''));

  const entries = readZip(src);
  const docIdx = entries.findIndex((e) => e.name === 'word/document.xml');
  const xml = entries[docIdx].data.toString('utf8');
  const paras = paraList(xml);
  console.log('master paragraphs:', paras.length);

  const keepUntil = spec.keep_until || 0;
  const templates = {};
  const baseRuns = {};
  for (const [kind, idx] of Object.entries(spec.templates)) {
    templates[kind] = paras[idx].text;
    baseRuns[kind] = firstRun(paras[idx].text);
  }

  // subtitle
  let subtitleXml = null;
  if (spec.subtitle) {
    const p0 = paras[spec.subtitle.index].text;
    const pPr = (p0.match(/<w:pPr>[\s\S]*?<\/w:pPr>/) || [''])[0];
    const base = firstRun(p0);
    const body = buildRun(base, spec.subtitle.text, null, null);
    const open = (p0.match(/^<w:p\b[^>]*>/) || ['<w:p>'])[0];
    subtitleXml = open + pPr + body + '</w:p>';
  }

  const head = xml.slice(0, paras[keepUntil].end);
  const tail = xml.slice(paras[paras.length - 1].end);
  const headParts = [];
  for (let i = 0; i <= keepUntil; i++) {
    headParts.push(i === (spec.subtitle ? spec.subtitle.index : -1) ? subtitleXml : paras[i].text);
  }

  const kwn = new Set(spec.keep_with_next || []);
  const contentXml = spec.content
    .map(([kind, segs]) => makePara(templates[kind], baseRuns[kind], segs, kwn.has(kind)))
    .join('');

  const newXml = xml.slice(0, paras[0].start) + headParts.join('') + contentXml + tail;
  entries[docIdx] = { name: 'word/document.xml', data: Buffer.from(newXml, 'utf8') };
  fs.mkdirSync(path.dirname(path.resolve(out)), { recursive: true });
  writeZip(out, entries);
  console.log('SAVED:', out);
}

main();
