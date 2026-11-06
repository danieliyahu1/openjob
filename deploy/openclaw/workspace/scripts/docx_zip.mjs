// Minimal DOCX (ZIP) reader/writer for this workspace — python-docx is unavailable
// and PowerShell script execution is blocked by the exec policy.
// Usage: node scripts/docx_zip.mjs extract <docx> <outDir>
//        node scripts/docx_zip.mjs pack <dir> <outDocx>
import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";

function crc32(buf) {
  if (typeof zlib.crc32 === "function") return zlib.crc32(buf) >>> 0;
  let c;
  const table = [];
  for (let n = 0; n < 256; n++) {
    c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    table[n] = c >>> 0;
  }
  let crc = 0xffffffff;
  for (let i = 0; i < buf.length; i++) crc = table[(crc ^ buf[i]) & 0xff] ^ (crc >>> 8);
  return (crc ^ 0xffffffff) >>> 0;
}

function listEntries(buf) {
  const sig = 0x06054b50;
  let eocd = -1;
  for (let i = buf.length - 22; i >= 0 && i > buf.length - 22 - 65536; i--) {
    if (buf.readUInt32LE(i) === sig) { eocd = i; break; }
  }
  if (eocd < 0) throw new Error("not a zip: no EOCD");
  const total = buf.readUInt16LE(eocd + 10);
  const cdOffset = buf.readUInt32LE(eocd + 16);
  const entries = [];
  let p = cdOffset;
  for (let n = 0; n < total; n++) {
    if (buf.readUInt32LE(p) !== 0x02014b50) throw new Error("bad central header at " + p);
    const flags = buf.readUInt16LE(p + 8);
    const method = buf.readUInt16LE(p + 10);
    const crc = buf.readUInt32LE(p + 16);
    const compSize = buf.readUInt32LE(p + 20);
    const uncompSize = buf.readUInt32LE(p + 24);
    const nameLen = buf.readUInt16LE(p + 28);
    const extraLen = buf.readUInt16LE(p + 30);
    const commentLen = buf.readUInt16LE(p + 32);
    const localOffset = buf.readUInt32LE(p + 42);
    const name = buf.subarray(p + 46, p + 46 + nameLen).toString("utf8");
    entries.push({ name, flags, method, crc, compSize, uncompSize, localOffset });
    p += 46 + nameLen + extraLen + commentLen;
  }
  return entries;
}

function readEntry(buf, e) {
  const lh = e.localOffset;
  if (buf.readUInt32LE(lh) !== 0x04034b50) throw new Error("bad local header for " + e.name);
  const nameLen = buf.readUInt16LE(lh + 26);
  const extraLen = buf.readUInt16LE(lh + 28);
  const start = lh + 30 + nameLen + extraLen;
  const data = buf.subarray(start, start + e.compSize);
  if (e.method === 0) return Buffer.from(data);
  if (e.method === 8) return zlib.inflateRawSync(data);
  throw new Error("unsupported compression " + e.method + " for " + e.name);
}

const mode = process.argv[2];

if (mode === "extract") {
  const buf = fs.readFileSync(process.argv[3]);
  const outDir = process.argv[4];
  for (const e of listEntries(buf)) {
    if (e.name.endsWith("/")) continue;
    const dest = path.join(outDir, e.name);
    fs.mkdirSync(path.dirname(dest), { recursive: true });
    fs.writeFileSync(dest, readEntry(buf, e));
  }
  console.log("EXTRACTED " + outDir);
} else if (mode === "pack") {
  const root = path.resolve(process.argv[3]);
  const out = path.resolve(process.argv[4]);
  const files = [];
  (function walk(dir) {
    for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, ent.name);
      if (ent.isDirectory()) walk(full);
      else files.push(full);
    }
  })(root);
  const rels = files.map((f) => path.relative(root, f).split(path.sep).join("/"));
  // [Content_Types].xml must come first in an OPC package
  const ordered = rels.filter((r) => r === "[Content_Types].xml").concat(rels.filter((r) => r !== "[Content_Types].xml"));
  const chunks = [];
  const central = [];
  let offset = 0;
  for (const rel of ordered) {
    const data = fs.readFileSync(path.join(root, rel));
    const comp = zlib.deflateRawSync(data, { level: 9 });
    const nameBuf = Buffer.from(rel, "utf8");
    const crc = crc32(data);
    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0);
    local.writeUInt16LE(20, 4);
    local.writeUInt16LE(0, 6);
    local.writeUInt16LE(8, 8);
    local.writeUInt16LE(0, 10);
    local.writeUInt16LE(0x21, 12);
    local.writeUInt32LE(crc, 14);
    local.writeUInt32LE(comp.length, 18);
    local.writeUInt32LE(data.length, 22);
    local.writeUInt16LE(nameBuf.length, 26);
    local.writeUInt16LE(0, 28);
    chunks.push(local, nameBuf, comp);
    const cen = Buffer.alloc(46);
    cen.writeUInt32LE(0x02014b50, 0);
    cen.writeUInt16LE(20, 4);
    cen.writeUInt16LE(20, 6);
    cen.writeUInt16LE(0, 8);
    cen.writeUInt16LE(8, 10);
    cen.writeUInt16LE(0, 12);
    cen.writeUInt16LE(0x21, 14);
    cen.writeUInt32LE(crc, 16);
    cen.writeUInt32LE(comp.length, 20);
    cen.writeUInt32LE(data.length, 24);
    cen.writeUInt16LE(nameBuf.length, 28);
    cen.writeUInt16LE(0, 30);
    cen.writeUInt16LE(0, 32);
    cen.writeUInt16LE(0, 34);
    cen.writeUInt16LE(0, 36);
    cen.writeUInt32LE(0, 38);
    cen.writeUInt32LE(offset, 42);
    central.push(cen, nameBuf);
    offset += local.length + nameBuf.length + comp.length;
  }
  const cd = Buffer.concat(central);
  const eocd = Buffer.alloc(22);
  eocd.writeUInt32LE(0x06054b50, 0);
  eocd.writeUInt16LE(0, 4);
  eocd.writeUInt16LE(0, 6);
  eocd.writeUInt16LE(ordered.length, 8);
  eocd.writeUInt16LE(ordered.length, 10);
  eocd.writeUInt32LE(cd.length, 12);
  eocd.writeUInt32LE(offset, 16);
  eocd.writeUInt16LE(0, 20);
  fs.mkdirSync(path.dirname(out), { recursive: true });
  fs.writeFileSync(out, Buffer.concat([...chunks, cd, eocd]));
  console.log("PACKED " + out + " entries=" + ordered.length);
} else {
  console.error("usage: extract <docx> <outDir> | pack <dir> <outDocx>");
  process.exit(2);
}
