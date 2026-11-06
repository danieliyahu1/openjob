#!/usr/bin/env python3
"""Build a tailored .docx from a master .docx WITHOUT changing its styling.

Strategy: keep the master's leading paragraphs (index 0..keep_until — these may
hold the name/contact header, multi-column section breaks, etc.), then rebuild
everything after them by CLONING existing styled paragraphs as templates and
replacing only their text. Styles, fonts, numbering (bullets), hyperlinks in the
kept header, and section layout are all preserved.

Usage:
    python tailor_docx.py --src master.docx --out tailored.docx --spec spec.json

spec.json shape (all keys except templates/content optional):
{
  "keep_until": 6,
  "subtitle": {"index": 2, "text": "New subtitle text"},
  "templates": {"h2": 8, "h3": 9, "li": 10, "body": 28, "sum": 7},
  "keep_with_next": ["h2", "h3"],
  "content": [
    ["h2", [["SECTION TITLE", null, null]]],
    ["h3", [["Role ", null, null], ["Company, 2024 - 2026", null, true]]],
    ["li", [["plain text ", null, null], ["emphasized", true, null], [".", null, null]]]
  ]
}

Notes:
- "templates" maps a logical kind -> paragraph index in the MASTER to clone for
  that kind. Pick indices that already look the way you want (e.g. a section
  heading, a job title, a bullet, a "Label: value" body line).
- Each content segment is [text, bold, italic]; null = inherit from the template.
- "keep_with_next" kinds get paragraph "keep with next" so headings are not
  orphaned at the bottom of a page.
"""
import argparse
import copy
import json
import os

import docx
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.text.paragraph import Paragraph

REMOVABLE = (qn("w:r"), qn("w:hyperlink"), qn("w:bookmarkStart"), qn("w:bookmarkEnd"))


def _set_flag(rpr, tag, value):
    if value is None:
        return
    el = rpr.get_or_add_b() if tag == "w:b" else rpr.get_or_add_i()
    if value is False:
        el.set(qn("w:val"), "0")
    else:
        el.attrib.pop(qn("w:val"), None)


def _make_para(templates, base_runs, kind, segments):
    para = copy.deepcopy(templates[kind])
    for child in list(para):
        if child.tag in REMOVABLE:
            para.remove(child)
    base = base_runs[kind]
    for seg in segments:
        text, bold, italic = (list(seg) + [None, None])[:3]
        run = copy.deepcopy(base) if base is not None else OxmlElement("w:r")
        for child in list(run):
            if child.tag != qn("w:rPr"):
                run.remove(child)
        rpr = run.find(qn("w:rPr"))
        if rpr is None:
            rpr = run.makeelement(qn("w:rPr"), {})
            run.insert(0, rpr)
        _set_flag(rpr, "w:b", bold)
        _set_flag(rpr, "w:i", italic)
        node = OxmlElement("w:t")
        node.set(qn("xml:space"), "preserve")
        node.text = text
        run.append(node)
        para.append(run)
    return para


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--src", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--spec", required=True)
    args = parser.parse_args()

    # utf-8-sig: tolerate the BOM PowerShell's Set-Content -Encoding utf8 adds.
    with open(args.spec, encoding="utf-8-sig") as handle:
        spec = json.load(handle)

    document = docx.Document(args.src)
    paras = document.paragraphs
    keep_until = spec.get("keep_until", 0)

    # Deep-copy templates BEFORE any deletion.
    templates = {kind: copy.deepcopy(paras[idx]._p) for kind, idx in spec["templates"].items()}
    base_runs = {kind: tmpl.find(qn("w:r")) for kind, tmpl in templates.items()}

    subtitle = spec.get("subtitle")
    if subtitle:
        para = paras[subtitle["index"]]
        runs = para.runs
        if runs:
            for run in list(runs)[1:]:
                run._r.getparent().remove(run._r)
            para.runs[0].text = subtitle["text"]
        else:
            para.add_run(subtitle["text"])

    anchor = paras[keep_until]._p
    for para in list(paras[keep_until + 1:]):
        para._p.getparent().remove(para._p)

    keep_with_next = set(spec.get("keep_with_next", []))
    for kind, segments in spec["content"]:
        newp = _make_para(templates, base_runs, kind, segments)
        if kind in keep_with_next:
            Paragraph(newp, None).paragraph_format.keep_with_next = True
        anchor.addnext(newp)
        anchor = newp

    out_dir = os.path.dirname(os.path.abspath(args.out))
    os.makedirs(out_dir, exist_ok=True)
    document.save(args.out)
    print("SAVED:", args.out)


if __name__ == "__main__":
    main()
