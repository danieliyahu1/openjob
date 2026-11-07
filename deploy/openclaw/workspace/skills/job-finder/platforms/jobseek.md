# Adapter: jobseek

`source` = `jobseek` · public REST API, no login, no browser.

Only the parts you cannot infer from the API itself are here. This is a reference
for the endpoints; choosing which filters fit a given user is yours to decide
from the criteria (`node scripts/criteria.mjs`).

## 1. Access

None needed. `https://jseek.co/api/v1` is public, cross-origin, unauthenticated.
OpenAPI 1.4.0 at `https://jseek.co/openapi.json`.

Self-hosting is **not** a one-command deployment — it needs two Postgres
databases, Redis, Typesense and S3-compatible storage. Use the hosted API.

## 2. Endpoints

| Endpoint | Use it for |
| --- | --- |
| `GET /api/v1/search` | filtered search — returns **companies**, not jobs |
| `GET /api/v1/job?id=<uuid>` | one posting in full |
| `GET /api/v1/resolve?type=…&q=…` | turn a name into the slug a filter needs |
| `GET /api/v1/companies?q=…` | company lookup |
| `GET /api/v1/taxonomies` | valid slugs |

## 3. Search — `GET /api/v1/search`

| Param | Accepts |
| --- | --- |
| `q` | free-text keywords |
| `loc` | location **slugs**, comma-separated |
| `occ` | occupation slugs |
| `sen` | seniority slugs |
| `tech` | technology slugs |
| `wm` | work modes, comma-separated |
| `etype` | employment types; use `sen=intern` for internships |
| `sal` | salary range in **EUR**, integer bounds, closed (`min-max`) or one-sided open |
| `exp` | experience range in years, integer bounds |
| `lang` | posting language codes |
| `locale` | UI labels and currency formatting only — **does not filter** |

**Slug params take exact slugs only.** `loc`, `occ`, `sen` and `tech` reject a
plain name with HTTP `400`. Resolve it first:

```
GET /api/v1/resolve?type=location&q=Berlin
```

Unsupported values in `wm` / `etype` also return `400`.

## 4. Response shape — the trap

Search returns **companies with up to a few postings each**, never a flat job
list:

```json
{ "companies": [ { "name": "ALTEN", "slug": "alten", "url": "...",
                   "activeJobs": 34,
                   "topPostings": [ { "id": "<uuid>", "title": "DevOps",
                                      "location": "Boulogne-Billancourt",
                                      "url": "https://jseek.co/en/company/alten?show=<uuid>" } ] } ],
  "totalCompanies": 655,
  "moreAt": "https://jseek.co/en/explore?q=devops" }
```

- `totalCompanies` counts **companies**, not jobs.
- `limit` is not a parameter. Sending it is ignored and you still get the default
  page of companies.
- `moreAt` is a web-UI deep link for the rest — there is no cursor or offset to
  page the API itself.
- For a posting's detail, take `topPostings[].id` and call `/api/v1/job?id=<uuid>`.

## 5. Job detail — `GET /api/v1/job?id=<uuid>`

Returns exactly: `id`, `title`, `company`, `locations`, `seniority`,
`technologies`, `salary`, `experience`, `employmentType`, `url`, `firstSeenAt`.

There is **no description and no employer/ATS URL**. `url` is always a
`jseek.co` page.

## 6. Identity

- ID = the posting `id` (a UUID).
- Normalized URL = the `url` field as returned,
  `https://jseek.co/en/company/<company-slug>?show=<uuid>`.
- Dedupe on the UUID. Keep the query string — the `?show=` value *is* the
  identity; stripping it collapses every posting at that company into one page.

## 7. Field mapping

| Flag | Source |
| --- | --- |
| `--title` | `title` |
| `--company` | `company.name` |
| `--url` | `url` from §6 |

## 8. Data notes

- Jobs come from employer career pages and ATS feeds, so postings are usually
  fresher than reposted boards, and `firstSeenAt` is when jobseek first saw it.
- `salary` is normalised to **EUR**; `seniority`, `experience` and `salary` are
  frequently `null`.
- `locations[].type` is `onsite` / `hybrid` / `remote`.
- Because `url` is a jobseek page, the same job found on another board has a
  different URL — cross-board dedupe will not catch it. Dedupe within jobseek.
- **Licence:** the code is MIT but the **job data is CC BY-NC 4.0** —
  non-commercial, attribution required. Commercial use needs a licence from
  Colophon Group.

## 9. Failure modes

- `400` on `loc` / `occ` / `sen` / `tech` means a value is not a slug → resolve
  it with `/api/v1/resolve`, do not retry the same request.
- `400` on `wm` / `etype` means an unsupported value.
- `404` on `/api/v1/job` means the UUID is unknown or the posting closed.
- Non-200, timeout, connection error, malformed JSON, unexpected shape → STOP and
  report. No retries, no fallback source.
- No rate limit is published. Keep requests modest and cache what you fetch.
