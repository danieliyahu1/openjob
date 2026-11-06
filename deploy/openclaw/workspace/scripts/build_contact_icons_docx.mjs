// Build a copy of the master CV where the contact line uses icons instead of
// the words "Personal website" / "LinkedIn" / "GitHub".
//
// Only the contact paragraph of the source document is rewritten; everything
// else (styles, layout, hyperlink targets, fonts) is copied through untouched.
// The icon PNGs live next to this script in ./contact-icons/.
//
// Usage: node scripts/build_contact_icons_docx.mjs <src.docx> <out.docx> <iconsDir>
import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";

const [, , srcDocx, outDocx, iconsDir] = process.argv;
if (!srcDocx || !outDocx || !iconsDir) {
  console.error("usage: node build_icons_docx.mjs <src.docx> <out.docx> <iconsDir>");
  process.exit(1);
}

const workspace = process.cwd();
const work = path.join(workspace, ".tmp", "build");
fs.rmSync(work, { recursive: true, force: true });
fs.mkdirSync(work, { recursive: true });

const unpacked = path.join(work, "unpacked");
fs.mkdirSync(unpacked, { recursive: true });

// unzip via PowerShell (Expand-Archive needs .zip extension)
const zipCopy = path.join(work, "src.zip");
fs.copyFileSync(srcDocx, zipCopy);
execFileSync("powershell", [
  "-NoProfile",
  "-Command",
  `Expand-Archive -LiteralPath '${zipCopy}' -DestinationPath '${unpacked}' -Force`,
]);

// ---------------------------------------------------------------- icons
const ICON_PT = 8.5;                     // rendered icon height in points
const EMU = 12700;                       // EMU per point
const extent = Math.round(ICON_PT * EMU); // 114300

const icons = [
  { file: "icon-pin.png", key: "pin", name: "Location icon", descr: "Location" },
  { file: "icon-phone.png", key: "phone", name: "Phone icon", descr: "Phone" },
  { file: "icon-globe.png", key: "globe", name: "Website icon", descr: "Personal website" },
  { file: "icon-linkedin.png", key: "linkedin", name: "LinkedIn icon", descr: "LinkedIn" },
  { file: "icon-github.png", key: "github", name: "GitHub icon", descr: "GitHub" },
  { file: "icon-mail.png", key: "mail", name: "Email icon", descr: "Email" },
];

fs.mkdirSync(path.join(unpacked, "word", "media"), { recursive: true });
let nextRid = 20;
let nextDocPr = 200;
const rid = {};
for (const icon of icons) {
  fs.copyFileSync(path.join(iconsDir, icon.file), path.join(unpacked, "word", "media", icon.file));
  rid[icon.key] = `rId${nextRid++}`;
  icon.docPr = nextDocPr++;
}

// vertical nudge (half-points, negative = lower) so icons optically centre on the text
const NUDGE = -2;

function iconRun(key) {
  const icon = icons.find((i) => i.key === key);
  return (
    `<w:r><w:rPr><w:position w:val="${NUDGE}"/></w:rPr><w:drawing>` +
    `<wp:inline distT="0" distB="0" distL="0" distR="0">` +
    `<wp:extent cx="${extent}" cy="${extent}"/>` +
    `<wp:effectExtent l="0" t="0" r="0" b="0"/>` +
    `<wp:docPr id="${icon.docPr}" name="${icon.name}" descr="${icon.descr}"/>` +
    `<wp:cNvGraphicFramePr><a:graphicFrameLocks xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" noChangeAspect="1"/></wp:cNvGraphicFramePr>` +
    `<a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">` +
    `<a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">` +
    `<pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">` +
    `<pic:nvPicPr><pic:cNvPr id="${icon.docPr}" name="${icon.file}" descr="${icon.descr}"/><pic:cNvPicPr/></pic:nvPicPr>` +
    `<pic:blipFill><a:blip r:embed="${rid[key]}"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>` +
    `<pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="${extent}" cy="${extent}"/></a:xfrm>` +
    `<a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr>` +
    `</pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r>`
  );
}

const ESC = { "&": "&amp;", "<": "&lt;", ">": "&gt;" };
const esc = (s) => s.replace(/[&<>]/g, (c) => ESC[c]);

function textRun(text, spacing) {
  const rPr = spacing ? `<w:rPr><w:spacing w:val="${spacing}"/></w:rPr>` : "";
  return `<w:r>${rPr}<w:t xml:space="preserve">${esc(text)}</w:t></w:r>`;
}

function link(rId, inner) {
  return `<w:hyperlink r:id="${rId}" w:history="1">${inner}</w:hyperlink>`;
}

// ---------------------------------------------------------------- paragraph
const SEP = textRun(" | ");

const newPara =
  `<w:p w14:paraId="2354CEA0" w14:textId="43D55BDA" w:rsidR="000A248E" w:rsidRDefault="00C66061" w:rsidP="00D143A3">` +
  `<w:pPr><w:pStyle w:val="BodyText"/><w:spacing w:before="39" w:line="276" w:lineRule="auto"/><w:ind w:left="0" w:firstLine="0"/><w:jc w:val="center"/></w:pPr>` +
  iconRun("pin") +
  textRun(" ") +
  textRun("Israel") +
  SEP +
  iconRun("phone") +
  textRun(" ") +
  textRun("(+972)050-4084647") +
  SEP +
  link("rId5", iconRun("globe")) +
  textRun("  ") +
  link("rId6", iconRun("linkedin")) +
  textRun("  ") +
  link("rId7", iconRun("github")) +
  SEP +
  link("rId8", iconRun("mail")) +
  textRun("  ") +
  link("rId8", textRun("danieliyahu1@gmail.com")) +
  `</w:p>`;

// ---------------------------------------------------------------- document.xml
const docPath = path.join(unpacked, "word", "document.xml");
let doc = fs.readFileSync(docPath, "utf8");

const paras = [...doc.matchAll(/<w:p(?:\s[^>]*)?>[\s\S]*?<\/w:p>|<w:p[^>]*\/>/g)];
const target = paras.find((m) => m[0].includes("(+972)") && m[0].includes("LinkedIn"));
if (!target) throw new Error("contact paragraph not found");
doc = doc.slice(0, target.index) + newPara + doc.slice(target.index + target[0].length);
fs.writeFileSync(docPath, doc, "utf8");

// ---------------------------------------------------------------- rels
const relsPath = path.join(unpacked, "word", "_rels", "document.xml.rels");
let rels = fs.readFileSync(relsPath, "utf8");
const newRels = icons
  .map(
    (i) =>
      `<Relationship Id="${rid[i.key]}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/${i.file}"/>`
  )
  .join("");
rels = rels.replace("</Relationships>", newRels + "</Relationships>");
fs.writeFileSync(relsPath, rels, "utf8");

// ---------------------------------------------------------------- content types
const ctPath = path.join(unpacked, "[Content_Types].xml");
let ct = fs.readFileSync(ctPath, "utf8");
if (!/Extension="png"/.test(ct)) {
  ct = ct.replace(
    "</Types>",
    `<Default Extension="png" ContentType="image/png"/></Types>`
  );
  fs.writeFileSync(ctPath, ct, "utf8");
}

console.log("document.xml patched; icons:", icons.map((i) => `${i.key}=${rid[i.key]}`).join(", "));

// ---------------------------------------------------------------- pack
execFileSync(
  "powershell",
  ["-NoProfile", "-File", path.join(workspace, "scripts", "pack_docx.ps1"), "-SourceDir", unpacked, "-OutDocx", outDocx],
  { stdio: "inherit" }
);
console.log("OK:", outDocx);
