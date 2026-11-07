# Adapter: freehire

`source` = `freehire` · public JSON API, no login, no browser.

Only the parts you cannot infer from the API itself are here. This is a reference
for the endpoints; choosing which filters fit a given user is yours to decide
from the criteria (`node scripts/criteria.mjs`).

## 1. Access

None needed. `https://freehire.me/api/v1` is public and cross-origin for
personal, non-commercial use. No key, no account. Never open `freehire.me` in a
browser for this platform — everything needed is in the API.

Rate limit is **not documented**, only declared per response:
`x-ratelimit-limit: 120` / `x-ratelimit-reset: 60` — i.e. 120 requests per
minute. Read the headers rather than assuming.

## 2. Response envelope

```json
{ "data": { ... } }                                  // single
{ "data": [ ... ], "meta": { "total": 4898466, "limit": 20, "offset": 0 } }
{ "error": "message" }
```

`meta.total` is the real total for the current filters, so paging is exact.

## 3. Endpoints

| Endpoint | Use it for |
| --- | --- |
| `GET /jobs/search` | filtered search — the one you want |
| `GET /agent/jobs/search` | same filters, plus full `description` *(documented, unverified)* |
| `GET /jobs/facets` | counts per facet value — narrow a search without paging |
| `GET /jobs` | newest first, **no filters at all** |
| `GET /jobs/{public_slug}` | one job by slug; serves closed jobs too *(unverified)* |
| `GET /jobs/{slug}/apply-form` | the captured ATS form *(unverified)* |
| `GET /jobs/{slug}/copies` | per-city duplicates of one role *(unverified)* |

## 4. Search — `GET /jobs/search`

Text: `q`, `q_fields` (`title,company,description,location`).
Paging: `limit` (1–100, default 20), `offset`.
Ordering: `sort` (`created_at`, `posted_at`, `view_count`, `salary_min`,
`salary_max`), `order` (`asc`/`desc`). Without `q` results are newest first; with
`q` they are relevance-ordered.

Facet params (any combination):

| Param | Examples |
| --- | --- |
| `category` | `software_engineering`, `backend`, `frontend`, `devops`, `sre`, `data_engineering`, `ml_ai`, `qa`, `security`, `product`, `design`, `sales`, `marketing`, `support`, `finance`, `hr`, `legal`, `healthcare`, `logistics`, `education`, `skilled_trades`, `manufacturing`, `retail`, `hospitality`, `operations`, `other` |
| `seniority` | `intern`, `junior`, `middle`, `senior`, `lead`, `staff`, `principal`, `c_level` |
| `work_mode` | `remote`, `hybrid`, `onsite` |
| `regions` | `global`, `north_america`, `latam`, `eu`, `uk`, `mena`, `africa`, `apac`, `cis`, `none` |
| `countries` | ISO alpha-2 — open vocabulary, see `/jobs/facets` |
| `cities`, `metros` | open vocabulary |
| `employment_type` | `full_time`, `part_time`, `contract`, `internship`, `fellowship` |
| `company_type` | `product`, `startup`, `outsource`, `outstaff`, `agency`, `inhouse`, `government` |
| `company_slug` | open vocabulary |
| `skills` | open vocabulary — `kubernetes`, `terraform`, … |
| `domains` | `fintech`, `crypto`, `ecommerce`, `gamedev`, `healthcare`, `devtools`, `cybersecurity`, `ai`, … |
| `salary_min` / `salary_max` | integer, pair with `salary_currency` (`USD`, `EUR`, `GBP`, `RUB`) |
| `experience_years_min` / `_max` | integer |
| `posted_within_days` / `open_within_days` | integer |
| `visa_sponsorship`, `requires_clearance` | `true` / `false` |
| `english_level` | `a1`…`c2`, `native`, `none` |
| `source_type` | `ats`, `company`, `aggregator`, `social` |

Facet modifiers, on every facet:

- Repeat or comma-separate to OR: `skills=go,rust`
- `<param>_mode=and` to require all: `skills=go,rust&skills_mode=and`
- `<param>_exclude=<value>` to drop: `company_type_exclude=outstaff`

## 5. Browse — `GET /jobs`

- Takes `limit` and `offset` **only**. Every other param is ignored.
- Ignored params are reported honestly in `meta.ignored_params`, sometimes with
  `did_you_mean`. **Always check it** — a dropped filter returns the whole
  catalogue and looks like a broad result.
- Sending a filter here is a bug: use `/jobs/search`.

## 6. Identity

- ID = `public_slug`, stable and used by every other endpoint.
- Normalized URL = `url` with the `?utm_source=freehire.me` query string
  stripped. It is the employer's own ATS/board link, not a freehire page.
- Dedupe on `public_slug`. The same role across boards is already collapsed, and
  `copies` holds per-city duplicates under one slug — do not save those twice.

## 7. Field mapping

| Flag | Source |
| --- | --- |
| `--title` | `title` |
| `--company` | `company` (fall back to `company_slug`, else `Not disclosed`) |
| `--url` | normalized `url` from §6 |

## 8. Data notes

- Verified 2026-11-08: 4,898,466 open jobs, split `tech` 919,240 /
  `non_tech` 3,596,341. This is **not** an IT-only board despite its tagline —
  `category` covers healthcare, retail, hospitality, logistics and trades too.
- `enrichment` holds the derived fields: `seniority`, `category`,
  `employment_type`, `salary_min`/`max`/`currency`, `visa_sponsorship`,
  `experience_years_min`, `english_level`, `domains`. Prefer these over guessing
  from the description.
- `reality.class` is `fresh` / `stale` / `likely-evergreen` — a ghost-job signal.
- `closed_at` non-null means closed; lists and search already exclude closed.
- `is_tech` is `tech` or `non_tech`.
- Many filters are dictionary-derived, so an absent value means "not detected",
  not "not offered" — e.g. `equity=not_stated`, `role_type` empty.

## 9. Failure modes

- `400 pagination too deep` — `offset + limit` may not exceed 10,000. Narrow with
  filters; do not page deeper.
- `400` invalid facet value → fix the value from `/jobs/facets`; do not retry.
- `429` → STOP and report. Back off rather than retrying in a loop.
- `503 search temporarily unavailable` → STOP and report.
- Non-200, timeout, connection error, malformed JSON, unexpected envelope →
  STOP and report. No retries, no fallback source.
- The public endpoints are licensed for personal, non-commercial use; surfacing
  them inside a product for others needs their API plan. That is a product
  decision, not something to work around.
