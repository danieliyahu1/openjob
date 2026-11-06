// Node port of tailor_docx.py (python-docx unavailable on this machine).
// Clones styled paragraphs from the master document.xml and replaces only text,
// preserving styles/fonts/numbering/hyperlinks/section layout.
// Usage: node scripts/build_tailored_docx.mjs <extracted-docx-dir> <spec.json>
// spec.json: {
//   "subtitle": {"index": 2, "text": "..."},
//   "keep_until": 4,
//   "templates": {"sec": 5, "sum": 7, "h2": 8, "h3": 9, "li": 10, "body": 19},
//   "keep_with_next": ["h2", "h3", "sec"],
//   "content": [["sec", [["SUMMARY", null, null]]], ...]
// }
import fs from "node:fs";
import path from "node:path";

const dir = process.argv[2];
// Strip a UTF-8 BOM: PowerShell's Set-Content -Encoding utf8 writes one, and JSON.parse rejects it.
const spec = JSON.parse(fs.readFileSync(process.argv[3], "utf8").replace(/^\uFEFF/, ""));
const docPath = path.join(dir, "word", "document.xml");
let xml = fs.readFileSync(docPath, "utf8");

const esc = (s) =>
  s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");

// Locate top-level w:p elements (no nested w:p in this document).
const pRe = /<w:p(\s[^>]*)?>[\s\S]*?<\/w:p>|<w:p[^>]*\/>/g;
const paras = [];
let m;
while ((m = pRe.exec(xml)) !== null) {
  paras.push({ start: m.index, end: m.index + m[0].length, xml: m[0] });
}

// --- subtitle edit (in place) ---
if (spec.subtitle) {
  const p = paras[spec.subtitle.index];
  let px = p.xml;
  const runRe = /<w:r(\s[^>]*)?>[\s\S]*?<\/w:r>/g;
  const runs = [];
  let rm;
  while ((rm = runRe.exec(px)) !== null) runs.push({ start: rm.index, end: rm.index + rm[0].length, xml: rm[0] });
  if (runs.length > 0) {
    // rebuild: first run keeps rPr, gets new text; drop the rest
    let first = runs[0].xml;
    first = first.replace(/<w:t[^>]*>[\s\S]*?<\/w:t>/g, "");
    first = first.replace(/<\/w:r>$/, `<w:t xml:space="preserve">${esc(spec.subtitle.text)}</w:t></w:r>`);
    let out = "";
    let cursor = 0;
    for (const r of runs) {
      out += px.slice(cursor, r.start) + (r === runs[0] ? first : "");
      cursor = r.end;
    }
    out += px.slice(cursor);
    px = out;
  } else {
    px = px.replace(/<\/w:p>$/, `<w:r><w:t xml:space="preserve">${esc(spec.subtitle.text)}</w:t></w:r></w:p>`);
  }
  xml = xml.slice(0, p.start) + px + xml.slice(p.end);
  // recompute paragraph offsets after the edit
  paras.length = 0;
  pRe.lastIndex = 0;
  while ((m = pRe.exec(xml)) !== null) paras.push({ start: m.index, end: m.index + m[0].length, xml: m[0] });
}

const keepUntil = spec.keep_until ?? 0;

// --- build new paragraphs from templates ---
const RPR_RE = /<w:rPr[\s\S]*?<\/w:rPr>|<w:rPr[^>]*\/>/;

function insertIntoRpr(rpr, flagXml) {
  if (/<w:rPr[^>]*\/>/.test(rpr)) {
    return rpr.replace(/<w:rPr([^>]*)\/>/, `<w:rPr$1>${flagXml}</w:rPr>`);
  }
  return rpr.replace(/<w:rPr([^>]*)>/, `<w:rPr$1>${flagXml}>`);
}

function setFlag(rpr, tag, value) {
  if (value === null || value === undefined) return rpr;
  const t = tag.replace(/^w:/, "");
  const re = new RegExp(`<w:${t}(\\s[^>]*)?/>|<w:${t}(\\s[^>]*)?>[\\s\\S]*?<\\/w:${t}>`);
  if (value === false) {
    if (re.test(rpr)) return rpr.replace(re, `<w:${t} w:val="0"/>`);
    return insertIntoRpr(rpr, `<w:${t} w:val="0"/>`);
  }
  // value true: normalize existing flag or add a bare one
  if (re.test(rpr)) return rpr.replace(re, `<w:${t}/>`);
  return insertIntoRpr(rpr, `<w:${t}/>`);
}

function buildRun(baseRun, text, bold, italic) {
  let run = baseRun.replace(/<w:t[^>]*>[\s\S]*?<\/w:t>/g, "");
  let rpr = null;
  const m = run.match(RPR_RE);
  if (m) {
    rpr = m[0];
  } else {
    rpr = "<w:rPr/>";
    run = run.replace(/^<w:r[^>]*>/, (mm) => mm + rpr);
  }
  rpr = setFlag(rpr, "w:b", bold);
  rpr = setFlag(rpr, "w:i", italic);
  run = run.replace(RPR_RE, () => rpr);
  return run.replace(/<\/w:r>$/, `<w:t xml:space="preserve">${esc(text)}</w:t></w:r>`);
}

function makePara(kind, segments) {
  const tmplXml = paras[spec.templates[kind]].xml;
  // direct-child runs of the paragraph: take first w:r as base (skip those inside pPr — none exist)
  const runRe = /<w:r(\s[^>]*)?>[\s\S]*?<\/w:r>/g;
  const rm = runRe.exec(tmplXml);
  const baseRun = rm ? rm[0] : null;
  // paragraph = pPr (everything before first run/hyperlink/bookmark) + new runs
  const cutMatch = tmplXml.search(/<w:r[\s>]|<w:hyperlink[\s>]|<w:bookmarkStart|<w:bookmarkEnd/);
  let head = cutMatch >= 0 ? tmplXml.slice(0, cutMatch) : tmplXml.replace(/<\/w:p>$/, "");
  // keep_with_next
  if (spec.keep_with_next?.includes(kind) && !/<w:keepNext/.test(head)) {
    if (/<w:pPr[^>]*\/>/.test(head)) {
      head = head.replace(/<w:pPr([^>]*)\/>/, `<w:pPr$1><w:keepNext/></w:pPr>`);
    } else if (/<w:pPr[^>]*>/.test(head)) {
      head = head.replace(/<w:pPr([^>]*)>/, `<w:pPr$1><w:keepNext/>`);
    } else {
      head = head.replace(/(<w:p(\s[^>]*)?>)/, `$1<w:pPr><w:keepNext/></w:pPr>`);
    }
  }
  let runsXml = "";
  for (const seg of segments) {
    const [text, bold, italic] = [seg[0], seg[1] ?? null, seg[2] ?? null];
    runsXml += buildRun(baseRun ?? "<w:r></w:r>", text, bold, italic);
  }
  return head + runsXml + "</w:p>";
}

let newParas = "";
for (const [kind, segments] of spec.content) newParas += makePara(kind, segments);

// --- reassemble: head = up to end of keep_until para; tail = after it, minus removed w:p ---
const anchorEnd = paras[keepUntil].end;
const head = xml.slice(0, anchorEnd);
let tail = xml.slice(anchorEnd);
// remove all w:p from the tail (they are the deleted paragraphs), keep sectPr etc.
// NOTE: the self-closing alternative must require whitespace after "w:p" so that
// sibling elements like <w:pgSz/> or <w:pgMar/> are NOT matched.
tail = tail.replace(/<w:p(\s[^>]*)?>[\s\S]*?<\/w:p>|<w:p\s[^>]*\/>/g, "");
const result = head + newParas + tail;

fs.writeFileSync(docPath, result, "utf8");
console.log("REWROTE", docPath, "paras:", paras.length, "new content paras:", spec.content.length);
