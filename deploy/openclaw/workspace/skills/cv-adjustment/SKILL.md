---
name: CV Adjustment
description: Use when tailoring a CV/résumé to a specific job posting. Reorders, rewords, splits, and trims the EXISTING CV — the single source of truth — to emphasize the experience most relevant to the role, never inventing anything, while preserving the original document's formatting. Produces a per-application copy and reports honest gaps.
---

# CV Adjustment

Tailor an existing CV to one specific job posting. The CV is the **single source
of truth**: reorder, reword, split, and trim what is already there to surface the
most relevant experience. Never invent roles, tools, metrics, employers, dates,
or activities that are not in the CV.

## When to use

- "Adjust / tailor / customize my CV (or résumé) for this job."
- Any time a job-specific copy of the CV is needed, especially before applying.

## Core rules

1. **The CV is the primary source of truth.** Prefer reordering, rewording,
   emphasizing, splitting, or deleting existing content.
2. **Never fabricate.** Add only what the CV states or the user's own GitHub
   verifies (rule 9) — never an employer, date, metric, tool, or activity that
   neither shows.
3. **Emphasize relevance.** Lead with the sections/roles/bullets most relevant to
   the posting; retitle or reorder sections to highlight fit.
4. **Leave protected sections verbatim** (e.g. `LANGUAGES`) unless explicitly asked.
5. **Surface honest gaps.** List requirements the CV does not meet; do not paper
   over them.
6. **Preserve formatting.** Keep the original fonts, styles, headers, hyperlinks,
   and section layout (including multi-column header sections and section breaks).
7. **When the language is unspecified, use English.** If the user hasn't stated a
   language, tailor the English master. If they ask for a specific language, use
   exactly that — don't assume English is always wanted. Do not switch language
   just because the posting itself is written in another one.
8. **When the format is unspecified, deliver one.** If the user hasn't stated a
   format, pick whichever fits best and produce a single file. If they ask for
   specific format(s), give them exactly that — don't assume one is always wanted.
9. **Mine GitHub for technical gaps.** When the posting wants a skill the CV
   doesn't show, check the user's public repos
   (`https://api.github.com/users/<user>/repos`, then
   `/repos/<user>/<repo>/readme` with `Accept: application/vnd.github.raw`)
   before reporting it as a gap. Use only what a README or the code proves.
   Project work goes in a `SELECTED PROJECTS` section, never inside an employer's
   bullets; a fork counts only as a contribution.

## Where things live (defaults — confirm if not found)

- Master CV: the newest English `Daniel Eliyahu resume.docx` under the project CV
  folder `/home/node/.openclaw/workspace-openjob/cv/original cv`
  (currently `/home/node/.openclaw/workspace-openjob/cv/original cv/java israel/`).
  Confirm it is the newest / intended master. A non-English master (e.g. the
  Hebrew CV under `original cv/עברית/`) is used **only** when the user asks for that language.
- Output: a per-application subfolder under the dedicated output folder
  `/home/node/.openclaw/workspace-openjob/cv/cv-adjustment/<job-slug>/`, where
  `<job-slug>` is the adjustment name (e.g.
  `twine-software-engineer-customer-solutions`). The generated file keeps the
  **same name as the original/master file** — never rename it per company or
  role. Produce exactly one output file. All newly generated CVs go here, not
  into `cv/`.
- Tooling: the CV scripts ship with this skill in
  `skills/cv-adjustment/scripts/` (`read_docx.py`, `tailor_docx.py`,
  `docx_to_pdf.ps1`). Run every command below from the **project root**, and always
  call them by that full path — a bare `scripts/...` is the project's other folder.
  **This host has no Python** (the WindowsApps `python.exe` alias is dead and no
  interpreter is installed). Use the drop-in Node port instead, same arguments:
  `node skills/cv-adjustment/scripts/tailor_docx.cjs --src … --out … --spec …`
  (reads the same spec JSON, keeps the master's styles/formatting). Verify the
  result with `node skills/cv-adjustment/scripts/dump_docx.cjs "<output file>"`. PDF rendering needs Word automation, which this automation
  context cannot approve — skip step 7's visual check and rely on the text dump.
- GitHub: the profile linked in the CV contact line (`LINK: https://github.com/...`),
  falling back to `danieliyahu1`.

## Workflow

1. **Read the master CV.**
   `python skills/cv-adjustment/scripts/read_docx.py "<master.docx>"`
   Note paragraph indices, styles, runs (bold/italic), and external `LINK:` targets.
2. **Get the job description.** Try `web_fetch` first (it returns readable
   markdown). If the page is JS-rendered (you see template placeholders like
   `{{position.name}}` or empty content), open it with
   `browser` `{action:"open", url:"<posting url>"}` and read the page with
   `browser` `{action:"text"}` or `{action:"snapshot"}` instead.
   Capture must-haves, nice-to-haves, responsibilities, and the posting's vocabulary.
3. **Map requirements → CV facts.** Build two lists: (a) what the job wants,
   (b) which existing CV lines genuinely support each want. Mark unmet
   requirements as gaps — then check GitHub for the *technical* ones before
   moving on.
4. **Plan the rewrite.** Decide the headline/subtitle, the section order (lead
   with the most relevant domain), the role order, and which bullets to keep,
   split, merge, or drop. Prefer leading with the target-relevant section when the
   posting is outside the CV's primary discipline.
5. **Write a spec JSON** describing the rewrite into the per-application folder
   (schema in the header of
   `skills/cv-adjustment/scripts/tailor_docx.py`).
   Each content segment is `[text, bold, italic]`, where `null` means "inherit
   from the template paragraph".
6. **Build the tailored copy.**
   `python skills/cv-adjustment/scripts/tailor_docx.py --src "<master file>" --out "cv-adjustment/<job-slug>/<same name as master>" --spec spec.json`
7. **Verify.** Convert and review:
   `powershell -File skills/cv-adjustment/scripts/docx_to_pdf.ps1 -In "<output file>" -Out "$env:TEMP\<master base name>.pdf"`
   To view it visually, serve the folder over localhost and screenshot it with
   the browser (`file://` is blocked, so use `python -m http.server`): open
   `http://localhost:<port>/<file>.pdf` with `browser` `{action:"open", url:…}`,
   then `{action:"screenshot", fullPage:true}`.
8. **Report** a change map — every change and the CV line it came from — plus the
   honest gaps, and deliver the requested output (a single format unless the user
   asked for more).

## Structure tips (general)

- Keep reverse-chronological truth *within* each section; moving a whole section
  up for emphasis is fine.
- Split a compound bullet into two when one half is more relevant.
- Adopt the posting's vocabulary only where the CV genuinely supports it.
- Retitle the headline/subtitle using roles the CV already shows
  (e.g. "Software Engineer & Coding Instructor"), not a role the CV never held.

## Do / Don't

| Do | Don't |
| --- | --- |
| Reorder, reword, split, trim, retitle using existing facts | Add tools, activities, or metrics not in the CV |
| Close technical gaps with verified repo evidence | Paste README claims you can't verify, or pass off a fork as your own |
| Keep formatting, hyperlinks, and protected sections | Inflate years, change dates, or rename roles you never held |
| Flag gaps explicitly | Quietly imply experience the CV lacks |
| When unspecified, default to English and one output format | Switch language or add formats unless asked |

## Optional cover letter

If asked, draft a short cover letter using **only** CV facts or verified GitHub
projects (rule 9).
