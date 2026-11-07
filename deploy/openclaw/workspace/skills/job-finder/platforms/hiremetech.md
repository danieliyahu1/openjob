# Adapter: HireMe

`source` = `hiremetech` · public JSON API, no login, no browser.

## 1. Access

None needed. `https://hiremetech.com/api/jobs/*` is public. Never open the site in
a browser for this platform.

## 2. Discovery

Search: `GET https://hiremetech.com/api/jobs/search`

- Params: `israeli`, `title`, `company`, `location`, `country`, `state`, `remote`,
  `job_level`, `employment_type`, `department`, `tech`, `ai_category`,
  `posted_days`, `sort_by`, `sort_order`, `limit`, `page`
- Response: `{ jobs: [...], page, size, pagination: { page, limit, has_more,
  total_count, total_pages }, filters_applied, performance }`
- Detail: `GET https://hiremetech.com/api/jobs/<id>` → `{ job: {...} }`
- Valid filter values: `GET https://hiremetech.com/api/jobs/filter-options`
  (`remote`, `job_level`, `employment_type`, `ai_category`, `tech`, `locations`, …)

Building the query from the criteria (`node /opt/openjob/agent-scripts/criteria.mjs`):

- Role keywords → `title`; location → `country` / `location` / `state` / `israeli`;
  work mode → `remote`; seniority → `job_level`; stack → `tech`; AI focus → `ai_category`.
- Always `sort_by=posted_date`, `sort_order=desc`; `limit=20`; page with `pagination.has_more`.
- Map criteria words to real param values with `filter-options` (values are
  free-text and inconsistent).
- A hard criterion that has no param is applied in the core evaluation step, not dropped.

Useful fields per result: `id`, `job_url`, `title`, `company_name`,
`location.basic.city` / `.country`, `location.work_model.display_tag`,
`job_level`, `posted_date`, `experience`, `tech_stack`, `description`, `requirements`.

## 3. Identity

- ID = the numeric job id (`<id>` in `/api/jobs/<id>`, or the trailing segment of
  `https://hiremetech.com/job/<id>`).
- Normalized URL = `https://hiremetech.com/job/<id>`. Locale paths such as
  `/he-il/job/<id>?f=1` are the same posting — dedupe by **id**, and store the
  canonical URL.
- Never invent an id; if a posting has none (rare, e.g. a redirect to another
  board), use the normalized URL as the id and keep the real apply URL in `--url`.

## 4. Field mapping

| Flag | Source |
| --- | --- |
| `--title` | `title` |
| `--company` | `company_name`, else `company.name`, else `Not disclosed` when the employer is masked (`employer_masked` / empty) |
| `--url` | `https://hiremetech.com/job/<id>` |

## 5. Apply destination (verified 2026-11-06)

The detail payload already says where the application goes — **no account or token
is needed for any of these fields**:

- `apply_url` — direct ATS/employer URL. Non-empty means *that* is the target.
- `application_email` — present when the employer accepts a mail application.
- `ats_type` — `external` / `internal` / ATS name.
- `apply_source` + `is_linkedin_apply` — the real destination (e.g. `linkedin`).
- `requires_login` — `true` means the platform's own apply flow is authenticated.
- `is_auto_apply_supported` — whether HireMe can submit on your behalf.
- `is_platform_internal` — `true` only when the job is applied to inside HireMe.

Decision order:

1. `apply_url` set → open it. These are almost always Greenhouse / Lever / Comeet /
   company career pages → treat per `job-application` rule 8 and submit if
   non-authenticated.
2. `application_email` set (and no `apply_url`) → an email application; this skill
   does not send mail, so record `!` and report it to the user.
3. `is_linkedin_apply: true` or `requires_login: true` with no `apply_url` → the
   only route is authenticated → record `!`.
4. **Before recording `!`, mine `description` for a destination URL.** Postings
   frequently end with an employer careers line (e.g. "apply at
   blackedge.co.il/careers"). Try it if it is a non-authenticated form.

**Verified 2026-11-06 (job `153004666`, Fullpath by Cox Automotive):** `ats_type:
comeet` with `apply_url: null` still has a working route. Open the employer's own
careers page, read the position links with one `act kind:"evaluate"` over the
`<a>` elements, and take the `…/careers/co/<city>/<pos-uid>/<slug>/all/` URL. Its
Comeet apply iframe is non-authenticated (name, email, phone, resume, website,
optional cover letter/portfolio/note).

Uploading into that iframe fails (`not interactable` / `input[type=file]` not
found), so open the iframe's own `https://www.comeet.co/jobs/<company-uid>/<pos-uid>/apply?…`
src as a **top-level tab** and fill + upload there; the form submits fine
("Your application has been submitted"). The `upload` action only accepts paths
inside `/home/node/.openclaw/media/inbound`, so copy the tailored CV there
first.

Worked example — job `152717478` (Lupa, Petah Tikva): employer masked in the API,
`apply_url: null`, `application_email: null`, `ats_type: external`, `requires_login:
true`, no URL in the description. The description text did name the company, and the
employer's own page `https://www.lupa.co.il/positions/` carries the posting — its only
application route is email (`jobs@lupa.co.il`, Cloudflare-obfuscated as a `data-cfemail`
attribute, decoded client-side), so it was recorded `!`. Hiring pages that end in "send
your CV to …" are email applications; do not treat them as forms.

Worked example — job `152902476` (BlackEdge Labs): `apply_url: null`,
`apply_source: "linkedin"`, `is_linkedin_apply: true`, `requires_login: true`,
`ats_type: "external"`, and the description names `blackedge.co.il/careers`. The
board blocks us, but step 4 still had a legitimate non-authenticated route.

**Verified 2026-11-06 — clicking Apply usually opens the employer site in a NEW
TAB.** Three live examples: `151946559` (Rafael) → `career.rafael.co.il/job/13229/`
(404, posting removed), `152036518` (Centrical) → `centrical.com/job/ai-platform-engineer/`
(email-only: `mailto:jobs@centrical.com`), `151941774` (NGsoft) →
`ngsoft.com/who-we-are/careers#job-6651` (board page, deep link does not resolve).
So: click Apply, then run `browser` `{action:"tabs"}` and follow the new tab before
concluding anything — a missing `apply_url` does **not** mean the posting is
gated. Only the LinkedIn-sourced ones (`is_linkedin_apply: true`) hit the sign-in
modal.

Worked example — job `153000865` (COMBLACK, "Backend Developer", junior, .NET + MongoDB,
Open Banking): `apply_url: null`, `apply_source: "linkedin"`, `is_linkedin_apply: true`,
`requires_login: true`, no URL in the description; description names COMBLACK.
Verified 2026-11-06: `comblack.co.il` runs a WordPress job board under `/careers/position-<n>/`,
and every position page there **does** carry a non-authenticated form (first name, phone,
email, resume file, terms checkbox). But that board has no posting for this role
(`/categories/back-end/`, site search for the title/"Open Banking"/"MongoDB" — no match),
so the only route to this posting is LinkedIn's authenticated apply → recorded `!`.
Rule of thumb for recruiters' own boards: a role that exists only as a LinkedIn job gets no
form; check the board's profession category (`/categories/back-end/` etc.) before giving up.

**Verified 2026-11-06 (job `152719486`, TMF Group — Forward Deployed AI Engineer, Katowice):**
`apply_url: null`, `apply_source: external`, `requires_login: true`, employer masked but named in the
description. Employer route: `www.tmf-group.com/en/careers` → "Join us now" →
`https://tmf.avature.net/careersmarketplace` (Avature). Keyword search returns
`JobDetail/<slug>/<jobId>`; Apply is `ApplicationMethods?jobId=<jobId>`, then "From Device"
reveals a hidden `input#resumeFile` (the `upload` action needs that revealed control, and the
file must be copied into `/home/node/.openclaw/media/inbound` first). The next step is
`Register?jobId=…`, which has **required `Password *` + `Password confirmation *` fields** —
TMF's apply flow creates a candidate account, so it is authenticated. There is no guest/skip
control. Record `!`.

**Verified 2026-11-06 (job `152717612`, BlackEdge Labs — Backend & DevOps Engineer – AI/LLM):** `apply_url: null`, `apply_source: external`, `requires_login: true`, no `is_linkedin_apply`. The employer's own board carries the exact role at
`https://blackedge.co.il/careers/jobs/<slug>/` (index at `/careers`; read the role links with one `act kind:"evaluate"` over `<a>`). Each role page has a **non-authenticated** form on the page itself (no iframe): first name, last name, email, phone, LinkedIn (all required), optional website, education level, institution, years of experience, notice-period radio, source, CV file (PDF/DOCX up to 10MB), plus a honeypot `bc_x9f3k` that must stay empty. `upload` needs the file copied into `/home/node/.openclaw/media/inbound` first. Success shows "Application received … Reference: BE-…"; `bc-formerr` / `bc-senderr` stay `display:none` when it worked. So a LinkedIn-only posting can still have a fully non-authenticated employer route — check the employer's own board before recording `!`.

**Verified 2026-11-06 (job `152908102`, Sapiens — AI Engineer, Israel, junior):** `apply_url: null`,
`apply_source: external`, `requires_login: true`, employer masked but the description names
"Sapiens Accelerate". `sapiens.com/careers` 403s to plain fetches and redirects to the HiBob
careers site `https://sapiens.careers.hibob.com/` (SPA). Its list page has no job anchors: search
with the `Search jobs` box, click the matching card (`div.bvsl-item-container`), then the
`Apply` button loads `/jobs/<uuid>/apply`. That form is **non-authenticated** — first/last name,
email, phone, LinkedIn, Country (custom dropdown: type to filter, then ArrowDown+Enter to commit,
the value resets if the page re-renders), optional date/salary, CV, one required consent checkbox.
The CV picker is Uppy: `upload` against the `Add file` button times out (it opens a modal, not a
file chooser); upload against the modal's `upload from computer` button instead, then click `Done`.
Success shows "Application submitted".

**Verified 2026-11-06 (job `152999144`, Backend Developer — masked employer, description only `"Backend Developer\nSubsidiary: Neema"`):** thin/empty descriptions are common on fresh scrapes, and clicking Apply still works: the button opened a new tab at `https://employer.dueto.io/position/<posId>/<tenant>#…` (Dueto ATS, employer-branded careers page). Dueto apply is **non-authenticated** and multi-step: step 1 uploads the resume (`upload` needs the file copied into `/home/node/.openclaw/media/inbound` first), then `Next` parses the CV and prefills first/last name, email and phone. Fix the LinkedIn field — the parser writes just `https://www.linkedin.com` — add salary only if known (leave the spinbox blank rather than `0`), tick the required privacy consent, then `Submit Application` → "We got your application!". The employer name is only visible on the Dueto page, so read it there before filling `--company`. Also note the API's `job_level` can disagree with the posting (this one was tagged `Junior` but asks 5+ years) — always read the real requirements on the employer page before applying.

**Verified 2026-11-06 (job `152759221`, Guidde — AI Software Engineer, Tel Aviv, mid):** `apply_url: null`, `apply_source: external`, `li=false`, `requires_login: true` — Apply opened the employer's **Greenhouse** form in a new tab, at the EU host `job-boards.eu.greenhouse.io/guidde/jobs/5025881101` (EU boards live on `job-boards.eu.greenhouse.io`, not `job-boards.greenhouse.io`; the boards API equivalent for EU is unverified). The form is **non-authenticated** and short: First/Last/Preferred name, Email, Country, Phone, Location (City), Resume, optional Cover Letter, LinkedIn, Website. Two gotchas: Country and Location are react-select inputs (not `<select>`) — `act kind:"select"` fails with "Element is not a <select>"; type the value, then `ArrowDown`+`Enter`, and confirm by reading `.select__single-value` (they read e.g. `+972` and `Tel Aviv, Israel` while `#country`/`#candidate-location` inputs stay empty and `aria-invalid` stays `true` — that is normal, not a failed selection). Resume: `upload` against the first `Attach` trigger works — afterwards the page text contains the uploaded filename and `#resume` disappears while `#cover_letter` remains the only `input[type=file]`. Submitted → "Thank you for applying. Your application has been received." Also note a Poland-scoped role is a real eligibility screen: GitLab's Greenhouse form asked "Are you currently located in Poland?" — check the posting's country before saving (job `152048215` was saved then marked `!`).

**Verified 2026-11-06 (job `152860926`, Claroty — LLM Engineer, Tel Aviv):** `apply_url: null`, `apply_source: external`, `is_linkedin_apply: false`, `requires_login: true` — but the Apply button opened `comeet.com/jobs/Claroty/F2.004/llm-engineer/86.473` in a new tab and the form is **non-authenticated** (first/last name, email, phone, resume, personal website, optional cover letter/portfolio/note). Clicking `APPLY FOR THIS JOB` there mounts the form as an iframe titled `Job Application form`; upload into that iframe fails (`not interactable`), so read the frame's `src` with `act kind:"evaluate", fn:"() => Array.from(document.querySelectorAll('iframe')).map(f => f.src)"` and open that `comeet.co/jobs/<company-uid>/<pos-uid>/apply?token=…` URL as a top-level tab with `embedded=false` — the label triggers (`Attach Resume`) accept the upload there and submit returns "Thank you Daniel! Your application has been submitted."

**Verified 2026-11-06 (job `152617045`, NVIDIA — Software Engineer, AI Infrastructure, Tel Aviv, 2+ yrs,
mid):** `apply_url: null`, `apply_source: external`, `ats_type: external`, `is_linkedin_apply: false`,
`requires_login: true`. Apply opens a new tab at `nvidia.wd5.myworkdayjobs.com/
nvidiaexternalcareersite/job/israel-tel-aviv/software-engineer--ai-infrastructure_jr2028826`,
which renders Workday's **"The page you are looking for doesn't exist."** — the req was pulled from
the employer side while HireMe still lists it as active. Even when the req is live, NVIDIA's apply
runs through Workday, which requires creating a candidate account (authenticated) → record `!`.
Workday-hosted employers (`*.wdN.myworkdayjobs.com`) are therefore never submittable here.

**Verified 2026-11-06 (job `152489294`, Elad Software Systems — Backend Node.js, Lod):** the job page names the employer ("Elad Software Systems") even when the API masks it. Apply opens a new tab at `careers.eladsoft.com/jobs/<positionNo>/` — Elad's own careers site with an **inline non-authenticated** form: full name, email, mobile, CV file, one consent checkbox, submit. The consent checkbox ignores ref clicks (`act kind:"click"` leaves it unchecked) — click it with `act kind:"evaluate", fn:"() => { const c=document.querySelector('input[type=checkbox]'); c.click(); return c.checked; }"`. Success page: "קורות החיים אצלנו. מכאן זה כבר עלינו". Upload needs the file copied into `/home/node/.openclaw/media/inbound` first.

**Verified 2026-11-06 (job `152712408`, Zafran — Backend Engineer, Discover Team):** Ashby-sourced posting; the job page links straight to `jobs.ashbyhq.com/<company-slug>/<uuid>/application`, which renders the full description — use that link to evaluate thin/masked postings before saving.

## 6. Failure modes

- Non-200, timeout, connection error, empty body, malformed JSON, unexpected
  shape → STOP and report. No retries and no fallback source.
- Rate limiting (HTTP 429) → STOP and report; do not hammer the API.
- `requires_login: true` on a posting is normal board metadata, not an access
  problem — the API is still public.
- **Verified 2026-11-06:** the job page's "הגש מועמדות" (Apply) button opens a
  "התחבר כדי להגיש" sign-in modal (Google / LinkedIn / Microsoft / email / SSO).
  HireMe's own apply flow is therefore an **authenticated** application — record
  the card as `!` and do not sign in. Only an `apply_url` that resolves to an
  external non-authenticated ATS can be submitted.
