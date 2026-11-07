---
name: Job Application
description: Use when applying to a job online on the user's behalf.
---

# Job Application

Apply to a job posting online on the user's behalf. Tailor a job-specific copy of
the CV, fill the employer's application form using the CV as the **source of
truth**, attach that tailored copy, review, and submit.

## Job board

Job and application state live in the OpenJob app. Only `scripts/jobs.mjs` talks
to it; never call the API yourself and never write job data to local files.

Every user message belongs to the account that owns this workspace. The scripts
read the owner from `.openjob-id` themselves; never pass a user or agent id. If
`.openjob-id` is missing, STOP and report it.

```bash
node scripts/jobs.mjs check --url "<posting url>"
node scripts/jobs.mjs add   --company "<c>" --title "<t>" --url "<url>"
node scripts/jobs.mjs mark  --id <id> --status applied
node scripts/jobs.mjs list  [--status found]
```

Statuses are exactly `found`, `applied`, `blocked`.

## Browser tool (OpenClaw — not Playwright MCP)

This workspace runs on OpenClaw's `browser` tool, not the Playwright MCP server.
There is no `browser_navigate`, `browser_click`, `browser_snapshot` or
`browser_file_upload` here. Use these calls instead:

| Need | Call |
| --- | --- |
| Open a URL (start here) | `browser` `{action:"open", url:"<url>"}` — capture the returned `targetId` |
| Navigate your tab | `browser` `{action:"navigate", targetId:"<id>", url:"<url>"}` (returns a compact snapshot inline — do not follow with `snapshot`) |
| Structure + element refs | `browser` `{action:"snapshot", targetId:"<id>"}` — add `interactive:true` for controls only, `refs:"aria"` for stable ids |
| Snapshot inside an iframe | `browser` `{action:"snapshot", targetId:"<id>", frame:"<selector>"}` |
| Read page prose | `browser` `{action:"text", targetId:"<id>", selector:"<css>", maxChars:4000}` |
| Click | `browser` `{action:"act", kind:"click", targetId:"<id>", ref:"e12"}` |
| Type into a field | `browser` `{action:"act", kind:"type", targetId:"<id>", ref:"e12", text:"..."}` |
| Set a combobox / select | `browser` `{action:"act", kind:"select", targetId:"<id>", ref:"e12", values:["Israel"]}` |
| Fill several fields at once | `browser` `{action:"act", kind:"fill", targetId:"<id>", fields:[{ref:"e1", value:"..."}, …]}` |
| Press a key | `browser` `{action:"act", kind:"press", targetId:"<id>", key:"Enter"}` |
| Upload a file | `browser` `{action:"upload", targetId:"<id>", ref:"<trigger ref>", paths:["<absolute path>"]}` — or `inputRef:"e5"` to set the file input directly |
| Answer a JS dialog | `browser` `{action:"dialog", targetId:"<id>", accept:true}` |
| See what the page sent | `browser` `{action:"requests", targetId:"<id>"}` / `{action:"console", targetId:"<id>"}` / `{action:"errors", targetId:"<id>"}` |
| Screenshot for review | `browser` `{action:"screenshot", targetId:"<id>", fullPage:true}` |
| Close your tab | `browser` `{action:"close", targetId:"<id>"}` |

Rules that matter:

- **Refs belong to a tab and a document.** Pass `targetId` from the snapshot into
  later `act` calls. After anything that navigates, re-snapshot before using refs.
- **`open` reuses the running browser; the tab it gives you is still yours.**
  Capture its `targetId` and use it from then on.
- **File paths for `upload` must be absolute.** The tailored CV path is absolute,
  so pass it as-is — never a `file://` URL.

## Shared browser

One managed browser is shared by every user's agent, and several agents may run
at the same time. Cookies and logins are shared on purpose; tabs are not.

- Work only in tabs you open. Capture the `targetId` from `open` and pass it to
  every `snapshot`, `act`, `navigate`, `upload` and `close`.
- Never focus, act on, or close a tab you did not open — even if `tabs` lists it.
  Ignore any `targetId` you did not capture yourself.
- Never use `{action:"stop"}`; it would kill the browser other agents are using.
- Keep one working tab. When a page opens a popup or a new tab, capture its id,
  finish the work there, close it, then continue on your working tab.
- Before you finish — success or failure — close every tab you opened, including
  popups.

## Where applying sits in the pipeline

Searching and applying have different rules, and they happen in different places:

- **Search happens on the platforms.** A board may need a signed-in session to
  search (LinkedIn does; HireMe is a public API). That permission is for
  discovery only — see `job-finder` and its adapters.
- **Apply happens wherever the platform sends you.** Clicking Apply usually
  redirects off the board to a company career page or an ATS (Greenhouse, Lever,
  Comeet, SmartRecruiters, Workday). Follow that redirect and fill the form
  **there**; do not treat the board as the application surface.
- **Never sign in to apply.** If the board or the destination form requires an
  account to submit — including LinkedIn "Easy Apply", which always does — do
  not apply: mark the posting `blocked` and stop.
- **When the platform blocks the apply, look for the direct route first.** Before
  recording `blocked`, check the posting for an external apply target: an
  `apply_url` / `job_url` field, an "Apply on company site" control, or the
  employer's own careers page for that exact role. Submit through that
  destination only if it is non-authenticated; if the only route is behind a
  login, record `blocked`.

## When to use

- "Apply to this job" / "apply to these jobs" together with a posting URL.
- Any online application form (Comeet/Spark Hire, Greenhouse, Lever, Workday,
  SmartRecruiters, company career pages, etc.).

## Source of truth

- **The CV.** Pull identity and contact details from it: name, email, phone, and
  the hyperlinks embedded in it (personal website, LinkedIn, GitHub, mailto).
  Read them with the cv-adjustment skill's reader
  (`skills/cv-adjustment/scripts/dump_docx.cjs`), which prints `LINK:` targets.
  The master CV lives in the workspace under `original cv/` (for example
  `original cv/java israel/<name> resume.docx`).
- **If the CV is not in the workspace, STOP** and ask the user to provide it.
  Never invent identity or contact details.
- **Tailoring the CV is required before submitting, not optional.** Run the
  cv-adjustment skill for this posting to produce a job-specific copy under
  `cv-adjustment/<job-slug>/`, and attach **that** file. Do not submit the master
  CV unless the user explicitly says to skip tailoring. If tailoring cannot be
  completed, stop and ask the user.

## Workflow

1. **Get the posting URL(s).** If several, do them one at a time.
2. **Check you haven't already applied.** From your workspace:
   ```bash
   node scripts/jobs.mjs check --url "<posting url>"
   ```
   - exit 0 (`UNIQUE`) → proceed (record it in step 12).
   - exit 2 (`DUPLICATE id=<id> status=<status>`):
     - `found` → proceed, and reuse that id in step 12.
     - `applied` or `blocked` → STOP — do not apply again.
   - any other exit code → STOP, report the error.
3. **Tailor the CV first (mandatory).** Before touching the form, run the
   cv-adjustment skill for this posting to produce a job-specific copy under
   `cv-adjustment/<job-slug>/`. This is a required step, not optional. Keep the
   path of the tailored file — it is what gets attached in step 8. Only skip it
   if the user explicitly says to submit the master CV as-is.
4. **Open the posting** in the browser. Try `web_fetch` first; if it returns
   template placeholders (e.g. `{{position.name}}`) or empty content, the page is
   JS-rendered — open it with `browser` `{action:"open", url:"<posting url>"}`.
5. **Determine the application type, then open the form.** Follow the posting to
   the page where the application is actually submitted — usually a redirect off
   the board to a company career page or an ATS on a different host. Classify that
   destination form as an **authenticated** or **non-authenticated** application
   (Rule 8). Then find and click the Apply control there
   (`browser` `{action:"act", kind:"click", targetId:"<id>", ref:"…"}`); if the
   click opens a new tab, capture its `targetId` from `tabs` and continue there;
   close it before returning to your working tab.
   The form is frequently inside an iframe and served by an ATS — take a snapshot
   (`browser` `{action:"snapshot", targetId:"<id>"}`, or
   `{action:"snapshot", targetId:"<id>", frame:"<iframe selector>"}`
   when the fields live in a child frame) to reveal them.
6. **Enumerate the fields.** Mark required (`*`) vs optional, and note each
   type: textbox, combobox, file, textarea, checkbox. Use
   `browser` `{action:"snapshot", targetId:"<id>", interactive:true}` so only controls come back.
7. **Assemble values from the CV:**
   - First/last name, email, phone. Set the country combobox with
     `act` `kind:"select"` (`values:["Israel"]`), and type the national number the
     way the form expects (often drop the leading `0`; e.g. `50-408-4647` for an
     Israeli mobile).
   - Personal website / LinkedIn / GitHub from the CV's embedded hyperlinks.
   - Location or other fields only if present in the CV; if the location is not
     present, the default is `Israel, Tel Aviv`.
8. **Attach the tailored CV.** Prefer setting the file input directly:
   `browser` `{action:"upload", targetId:"<id>", inputRef:"<ref of input[type=file]>", paths:["<absolute path>"]}`.
   If the control hides behind a button that opens a chooser, pass the trigger ref
   instead: `{action:"upload", targetId:"<id>", ref:"<button ref>", paths:[…]}`. Confirm the page
   shows the filename; re-snapshot if it does not.
9. **Optional fields.** Add a cover letter or personal note only if the user
   wants one, and keep it to CV facts. Leave unknown optional fields blank rather
   than inventing.
10. **Review before submitting.** Take a fresh snapshot and confirm every
    required field is populated and the **tailored** CV is attached.
11. **Submit.** Handle any dialog or validation error. Confirm the success
    message on the page.
12. **Record the outcome.** Keep state in the app only, never in local files:
    - Submitted successfully:
      `node scripts/jobs.mjs mark --id <id> --status applied`
    - Attempted but not completed (login wall, closed posting, broken ATS):
      `node scripts/jobs.mjs mark --id <id> --status blocked`
    - Posting not on the board yet:
      ```bash
      node scripts/jobs.mjs add \
        --company "<company>" \
        --title "<title>" \
        --url "<posting url>" \
        --status applied
      ```
      (use `--status blocked` when the attempt failed)
    - `MARKED id=… status=…` → recorded; non-zero exit → STOP and report.
13. **Report** a table of the values submitted, the tailored CV attached, and
    follow-ups (confirmation email, any remaining gaps).

## Handling different forms

- **Iframes:** snapshot the frame (`{action:"snapshot", targetId:"<id>", frame:"<selector>"}`),
  or act on refs returned from a frame-aware snapshot; upload works across frames.
- **Comboboxes** (country, source, etc.): `{action:"act", kind:"select", targetId:"<id>", ref:"…", values:["…"]}`.
  If it is a custom (non-`<select>`) dropdown, click it, snapshot, then click the option.
- **File inputs:** `{action:"upload", targetId:"<id>", inputRef:"…", paths:["<absolute path>"]}`;
  if a chooser dialog blocks, use the trigger-ref form of `upload` in the same call.
- **Multi-step / paginated forms:** complete a step, re-snapshot, continue.
- **Multiple postings:** repeat the whole workflow per posting and report each.

## Rules

1. **Tailor the CV before every submission.** Run the cv-adjustment skill for the
   posting and attach the job-specific copy it produces (step 3). Never submit
   the master CV unless the user explicitly says to skip tailoring.
2. **Never invent data.** If a required field is not derivable from the CV, ask
   the user.
3. **Do not create accounts or sign in** on the user's behalf.
4. **Review before submitting** — submission is irreversible.
5. **One application per posting**, and report the outcome of each.
6. **Track applications.** Statuses: `found` = saved, not applied (the only value
   you may apply to); `applied` = submitted; `blocked` = attempted but did not
   complete. Apply only to rows marked `found`; never to `applied` or `blocked`.
   Update state only through `scripts/jobs.mjs`.
7. Keep the submitted CV consistent with the cv-adjustment skill: CV = truth.
8. **Authenticated vs non-authenticated application.** We apply only to
   non-authenticated applications, meaning the form at wherever the platform
   redirected us:
   - **Non-authenticated** — can be submitted without signing in or creating an
     account. We fill it in and submit.
   - **Authenticated** — requires a signed-in account to submit. We do not apply;
     record it as `blocked`.

   **Never authenticate.** Signing in to *search* on a platform does not carry
   over: if submission requires sign-in, stop and record the outcome — never
   enter credentials or create an account.
