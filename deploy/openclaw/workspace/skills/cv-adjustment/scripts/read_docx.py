#!/usr/bin/env python3
"""Dump the structure of a .docx so you can plan a tailored rewrite.

Usage:
    python read_docx.py <file.docx>

Prints, for each paragraph:
    <index> | <style name> | <text>

and, when a paragraph mixes formatting or has several non-empty runs, the run
list with bold/italic flags. Finally lists the external hyperlink targets found
in the document (useful for contact links: website, LinkedIn, GitHub, mailto).
"""
import re
import sys
import zipfile

import docx


def main(path: str) -> None:
    document = docx.Document(path)
    for i, para in enumerate(document.paragraphs):
        print(f"{i} | {para.style.name} | {para.text!r}")
        runs = [(r.text, r.bold, r.italic) for r in para.runs]
        non_empty = [r for r in runs if r[0].strip()]
        varied = any(b or it for _, b, it in runs) or len(non_empty) > 1
        if varied:
            print("     runs:", runs)

    print("--- links ---")
    try:
        with zipfile.ZipFile(path) as archive:
            rels = archive.read("word/_rels/document.xml.rels").decode("utf-8", "ignore")
        for target in re.findall(r'Target="([^"]+)"[^>]*TargetMode="External"', rels):
            print("LINK:", target)
    except Exception as exc:  # noqa: BLE001 - best-effort diagnostics
        print("LINK: (could not read rels:", exc, ")")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit("usage: read_docx.py <file.docx>")
    main(sys.argv[1])
