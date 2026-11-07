# Adapter: LinkedIn

`source` = `linkedin` · browser session required.

Only the parts you cannot infer from the site itself are here. How to type in a
search box, apply a filter, or open a posting is yours to figure out.

## 1. Access

**Search only.** Signing in here is for discovery — finding and reading postings.
It never authorises an application: apply happens on the employer's own page or
ATS (`job-application`, rule 8), and LinkedIn "Easy Apply" is authenticated by
definition, so never submit through it — record the posting as `!` instead.

- Sign in with the credentials in the environment: `LINKEDIN_EMAIL` and
  `LINKEDIN_PASSWORD`. Read them with a shell command and type them into the form;
  never print the values back into the conversation.
- IF a verification challenge appears (CAPTCHA, 2FA, "verify it's you") → STOP and
  ask the user to complete it manually. Never try to solve or bypass it.
- Close the browser when the run finishes, whether or not a job was saved.

## 2. Discovery

Search and filter with the terms in the criteria (`node
scripts/criteria.mjs`) — role keywords, location, experience level, remote /
hybrid / on-site. Read a posting when the card text
is not enough to evaluate it.

UI only: no undocumented endpoints, no `linkedin.com/api/*`, no scraping tools.

## 3. Identity

- ID = the numeric job id in the URL path (10+ digits):
  `.../view/software-engineer-innovid-4440693281/?trackingId=abc` → `4440693281`.
- Normalized URL = the same path without the trailing slash and without any
  query string. Strip everything from `?` onward, including `trackingId`.
- Dedupe by the numeric id, not by URL — the same posting appears under
  localized paths and different slugs.

## 4. Field mapping

| Flag | Source |
| --- | --- |
| `--title` | posting title |
| `--company` | company on the posting; if hidden, `Not disclosed` |
| `--location` | posting location (city, plus country when shown) |
| `--work-mode` | exactly one of `Remote`, `Hybrid`, `On-site` |
| `--url` | normalized URL from §3 |
| `--id` | numeric job id from §3 |

## 5. Failure modes

- Rate-limit / "unusual activity" / security check page → STOP and tell the user
  to retry later. Do not keep clicking.
- Browser automation error → retry once, then STOP and report.
- Never print or echo the credentials, and only ever enter them on LinkedIn's
  own sign-in form.
