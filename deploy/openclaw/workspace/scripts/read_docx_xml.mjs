// Parse an extracted docx word/document.xml into a paragraph map.
// Usage: node scripts/read_docx_xml.mjs <folder-extracted-docx>
// Prints: index | style | text   plus run flags and hyperlink LINK targets.
import fs from "node:fs";
import path from "node:path";

const root = process.argv[2];
const xml = fs.readFileSync(path.join(root, "word", "document.xml"), "utf8");

// hyperlinks rels
let rels = {};
const relsPath = path.join(root, "word", "_rels", "document.xml.rels");
if (fs.existsSync(relsPath)) {
  const relsXml = fs.readFileSync(relsPath, "utf8");
  for (const m of relsXml.matchAll(/<Relationship[^>]*Id="([^"]+)"[^>]*Target="([^"]+)"([^>]*)>/g)) {
    const external = m[3].includes('TargetMode="External"');
    rels[m[1]] = { target: m[2], external };
  }
}

// namespace-agnostic tag matcher
function tag(name) {
  return new RegExp(`<w:${name}(\\s[^>]*)?(/)?>|<w:${name}[^>]*/>`, "g");
}

// split body into paragraphs (top-level w:p only — w:p inside tables also count for content)
const paras = [];
const pRe = /<w:p(\s[^>]*)?>[\s\S]*?<\/w:p>|<w:p[^>]*\/>/g;
let m;
while ((m = pRe.exec(xml)) !== null) {
  const pXml = m[0];
  const styleM = pXml.match(/<w:pStyle[^>]*w:val="([^"]+)"/);
  const style = styleM ? styleM[1] : "";
  // runs
  const runs = [];
  const runRe = /<w:r(\s[^>]*)?>[\s\S]*?<\/w:r>|<w:r[^>]*\/>/g;
  let rm;
  while ((rm = runRe.exec(pXml)) !== null) {
    const rXml = rm[0];
    const bold = /<w:b\b/.test(rXml) && !/<w:b\s+[^>]*w:val="0"/.test(rXml);
    const italic = /<w:i\b/.test(rXml) && !/<w:i\s+[^>]*w:val="0"/.test(rXml);
    const texts = [...rXml.matchAll(/<w:t[^>]*>([\s\S]*?)<\/w:t>/g)].map((t) => t[1]).join("");
    runs.push({ text: texts, bold, italic });
  }
  // hyperlinks inside paragraph
  const links = [];
  const hRe = /<w:hyperlink[^>]*r:id="([^"]+)"[\s\S]*?<\/w:hyperlink>/g;
  let hm;
  while ((hm = hRe.exec(pXml)) !== null) {
    const linkText = [...hm[0].matchAll(/<w:t[^>]*>([\s\S]*?)<\/w:t>/g)].map((t) => t[1]).join("");
    links.push({ id: hm[1], text: linkText, target: rels[hm[1]]?.target });
  }
  const text = runs.map((r) => r.text).join("");
  paras.push({ style, text, runs, links });
}

const esc = (s) => s.replace(/&amp;/g, "&").replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&quot;/g, '"').replace(/&apos;/g, "'");
paras.forEach((p, i) => {
  const flags = p.runs.map((r) => (r.bold ? "B" : "") + (r.italic ? "I" : "")).join(",");
  const linkStr = p.links.map((l) => ` LINK:${l.target}("${esc(l.text)}")`).join("");
  console.log(`${i} | style=${p.style || "-"} | runs:${p.runs.length} ${flags} | ${esc(p.text)}${linkStr}`);
});
console.log("TOTAL:", paras.length);
