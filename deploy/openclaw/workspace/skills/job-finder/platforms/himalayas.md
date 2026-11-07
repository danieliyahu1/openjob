# Adapter: Himalayas

`source` = `himalayas` · public JSON API, no login, no browser.

Only the parts you cannot infer from the API itself are here. This is a reference
for the endpoints; choosing which filters fit a given user is yours to decide
from the criteria (`node scripts/criteria.mjs`).

## 1. Access

None needed. `https://himalayas.app/jobs/api*` is public JSON. No key, no account.
Unauthenticated limit is 60 requests/minute. Never open `himalayas.app` in a
browser for this platform — the HTML pages answer scripts with a Cloudflare
`403` "Just a moment…" page, so the API is the only usable route.

## 2. Endpoints

| Endpoint | Use it for |
| --- | --- |
| `GET /jobs/api/search` | filtered search |
| `GET /jobs/api` | the full unfiltered feed, paginated |
| `GET /jobs/api/companies/:company/jobs/:slug` | confirm one job is still open *(unverified)* |

## 3. Search — `GET /jobs/api/search`

Params (all optional, all combinable):

| Param | Accepts |
| --- | --- |
| `q` | free text — title, role, skill |
| `country` | ISO alpha-2 (`IL`, `US`), country name, or slug |
| `worldwide` | `true` — only jobs with no location restriction |
| `exclude_worldwide` | `true` + `country` — drop worldwide roles, keep country-restricted only |
| `seniority` | `Entry-level`, `Mid-level`, `Senior`, `Manager`, `Director`, `Executive` |
| `employment_type` | `Full Time`, `Part Time`, `Contractor`, `Temporary`, `Intern`, `Volunteer`, `Other` |
| `company` | comma-separated company slugs, e.g. `linear,vercel` |
| `timezone` | UTC offset the job accepts: `-5`, `UTC-5`, `UTC+05:30` |
| `sort` | `relevant` (default), `recent`, `salaryAsc`, `salaryDesc`, `nameAToZ`, `nameZToA`, `jobs` |
| `page` | 1-based, 20 jobs per page |

Verified response shape:

```json
{ "comments": "...", "updatedAt": 1794, "offset": 0, "limit": 20,
  "totalCount": 2131, "jobs": [ { ...job fields } ] }
```

Traps, all verified 2026-11-08:

- `country` **includes worldwide roles** open to that country. Add
  `exclude_worldwide=true` when you want country-restricted postings only.
- `company` is comma-separated **slugs**, and one unknown slug fails the entire
  request with `400 {"ok":false,"errors":"Invalid company"}`. Fix the slug — do
  not retry the batch as-is.
- `totalCount` is an estimate up to a day old and can run high. Page until a page
  returns no jobs, and de-duplicate on `guid` — results can shift between pages.
- `page` beyond the last page returns an empty `jobs` array, not an error.

## 4. Browse — `GET /jobs/api`

- Params: `cursor` (preferred), `limit` (max **20**), `offset` (deprecated).
- Paginate by echoing `nextCursor` back as `cursor`. Treat the cursor as opaque —
  never build or parse one.
- **Every filter on this endpoint is silently ignored.** `?country=IL`, `?q=…`,
  `?company=…` all return the unfiltered feed with a normal `200`. If you need
  filtering, you are on the wrong endpoint — use `/jobs/api/search`.
- The feed is roughly reverse-chronological, but not strictly: older outliers
  appear among recent pages, so do not stop paginating on the first old `pubDate`.

## 5. Identity

- ID = `guid`, which is the canonical posting URL
  (`https://himalayas.app/companies/<company>/jobs/<slug>`). Dedupe on `guid`.
- Normalized URL = `applicationLink` with the `?utm_source=api` query string
  stripped. Same posting as `guid`, so either works — store the stripped
  `applicationLink`.
- Never invent a slug; take `companySlug` from a real result.

## 6. Field mapping

| Flag | Source |
| --- | --- |
| `--title` | `title` |
| `--company` | `companyName` — always present on this board, never masked; fall back to `Not disclosed` only if empty |
| `--url` | normalized URL from §5 |

## 7. Data notes

- `locationRestrictions` empty = open worldwide. A long list (100+ countries) is
  effectively worldwide too — read the list before calling it a country match.
- `worldwide=true` returns only jobs with an empty `locationRestrictions`
  (verified: 20/20 on page 1).
- `timezoneRestrictions` empty = no timezone requirement; a job with no
  restriction lists every offset.
- `pubDate` is when the job was **last updated**, not first posted.
- `expiryDate` is a ceiling — jobs can close earlier. Check before saving when it
  matters.
- `minSalary` / `maxSalary` / `currency` are frequently `null`.
- The free API has **no** salary, market or benefit filters. Treat those criteria
  as core-evaluation checks, not query params.

## 8. Failure modes

- `429` → STOP and report; the unauthenticated limit is 60/minute and hammering
  it escalates to a Cloudflare challenge on every endpoint.
- Non-200 other than the `Invalid company` 400, timeout, connection error,
  malformed JSON, or an unexpected response shape → STOP and report. No retries,
  no fallback source.
- A Cloudflare "Just a moment…" HTML body on an API call means you are
  rate-limited → STOP.
- `applicationLink` is the board's own posting page, not an ATS URL. Where the
  application actually goes is `job-application`'s problem — do not assume the
  board page is submittable.
