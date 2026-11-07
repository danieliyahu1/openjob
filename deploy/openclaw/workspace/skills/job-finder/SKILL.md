---
name: job-finder
description: Use when the user asks to find or search for jobs on HireMe, LinkedIn, or another job board, or to save a matching posting to the board.
---

# Job finder

Find matching jobs and save them to the OpenJob board. This file is the shared
procedure for every board; boards we already worked out have notes in
`platforms/<platform>.md`, and boards that don't are still fine to use.

## The board

Job state lives in the OpenJob app. `scripts/jobs.mjs` is the only writer; it
talks to the app's API using `OPENJOB_API_URL` and `OPENJOB_API_TOKEN`, which are
already in the environment. Never call the API yourself and never write job data
to local files.

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

Every user message belongs to the account that owns this workspace. The scripts
read the owner from `.openjob-id` themselves; never pass a user or agent id. If
`.openjob-id` is missing, STOP and report it — never invent one.

## Criteria

The match criteria are the user's own input, stored per account — never in the
repo and never in local files. Read them fresh at the start of every run:

```bash
node scripts/criteria.mjs
```

- exit 0 → use the printed markdown as the criteria for this run
- exit 2 → nothing saved yet: ask the user for their criteria (roles, seniority,
  location, must-haves, dealbreakers), then save the answer and continue:
  ```bash
  node scripts/criteria.mjs --set "<their answer>"
  ```
- any other exit code → STOP and report the error

Never invent criteria and never edit them without the user asking. If the user
dictates their criteria, save them with `--set` and confirm what was saved.

## Known adapters (optional)

Any job board is fair game. These two are just written-up notes for boards we
already solved:

| Platform | Adapter | Access |
| --- | --- | --- |
| HireMe (`hiremetech.com`) | `platforms/hiremetech.md` | public JSON API, no login |
| LinkedIn (`linkedin.com`) | `platforms/linkedin.md` | browser session, needs login |

- Use the board the user names. If they name none, prefer HireMe (cheapest: API
  only, no login) — but any board that fits the criteria is fine.
- If the board has an adapter, **read it before discovery**
  (`platforms/<platform>.md`) — supporting files are not loaded automatically,
  only this `SKILL.md` is.
- No adapter for that board? Search it anyway: work out the points below
  yourself, then leave notes behind — see *"Write down a board you had to figure
  out"*.

## What an adapter must define

Each adapter supplies exactly the parts that differ per platform:

1. **Access** — API or browser, and how to authenticate (never print credentials).
2. **Discovery** — how to turn the criteria into platform queries and iterate results.
3. **Identity** — the normalized posting URL used for dedupe.
4. **Field mapping** — how to fill `--company`, `--title` and `--url`.
5. **Failure modes** — what "stop and report" looks like on that platform.

Write only what cannot be inferred from the platform itself. Browsing steps an
agent already knows are noise; identity rules, board vocabulary and hard stops
are not.

You must be able to derive a **normalized URL** for dedupe — if a board gives you
no usable posting URL, skip it rather than saving cards you cannot dedupe.

### Write down a board you had to figure out

If you searched a board that has no notes, leave notes behind so the next run
doesn't re-derive them:

1. Write `platforms/<board>.md` using the same five sections as the existing
   files — Access, Discovery, Identity, Field mapping, Failure modes.
2. Record only what you actually verified this run (endpoints, URL shape, labels,
   the stop that really happened). Mark anything you did not confirm as
   *unverified* instead of guessing.
3. Include credentials only if the user supplied them for that board.
4. Add one row to the known-adapters table above.
5. Keep it short: identity rules, board vocabulary, hard stops. Not a tutorial.

Write it in the same run that used the board, and mention in the report that you
added the notes.

## Flow

1. **Read the criteria** for this user:
   ```bash
   node scripts/criteria.mjs
   ```
   Skip this step when the user is only asking to save a specific posting they
   already picked — criteria are for searching.
2. **Pick the board and get its mechanics.** Read `platforms/<platform>.md` if it
   exists; otherwise derive access, discovery and field mapping yourself.
3. **Loop, up to 25 attempts.** For each candidate:
   - **3a. Evaluate against the criteria.** Use the fields your board or notes
     name; fetch the posting detail when the list data is not enough.
     - criteria not met → skip to the next attempt
     - criteria met → continue to 3b
   - **3b. Verify it is unique.** From your workspace:
     ```bash
     node scripts/jobs.mjs check --url "<normalized url>"
     ```
     - exit 0 (`UNIQUE`) → continue to 3c
     - exit 2 (`DUPLICATE id=… status=…`) → skip to the next attempt
     - any other exit code → STOP and report the error
   - **3c. Save it.** From your workspace:
     ```bash
     node scripts/jobs.mjs add \
       --company "<company>" \
       --title "<title>" \
       --url "<normalized url>"
     ```
     - prints `ADDED id=<id>`; the row is status `found`
     - exit 2 (`DUPLICATE`) → skip to the next attempt
     - any other exit code → STOP and report the error
   - The script owns the board: it handles dedupe, the API and the token. Job
     state lives in the app only — never write job data to local files, and never
     call the API yourself.
4. **Finish.** If a job was saved, report it in chat:

   **<Title>** — <Company or "Not disclosed">
   Posted <posted date>
   <URL>
   Why it matches:
   - <reason from the criteria>
   - <reason from the criteria>

   Then **DONE** — stop searching. If the 25 attempts are exhausted without
   saving, say so and stop.

## Rules

- **One job per run maximum.**
- No duplicate job URLs.
- **`scripts/jobs.mjs` is the only writer.** All job state lives in the app —
  never in local files — and you never call the API yourself.
- Status values: `found` (saved, not applied), `applied`, `blocked` (attempted,
  not completed). Finding only ever writes `found`.
- **Never apply** to a job from this skill — that is `job-application`. A board
  adapter may sign in to *search* (LinkedIn does); that permission ends at
  discovery. Applying happens on the employer/ATS page and is never
  authenticated.
- Never message anyone, send connection requests, or create accounts.
- Never modify the user's criteria unless the user asks you to.
- Never print credentials in chat.
- One board per run: pick it up front and don't switch boards mid-run when one
  fails or comes up empty.

## Error handling

- Any failure the board's notes mark as fatal, or that clearly means "stop"
  (rate limit, CAPTCHA, verification, API error, unexpected schema) → STOP and
  report. No fallback, no retry loop unless the notes allow one.
- `scripts/jobs.mjs` exits non-zero with `ERROR: …` → STOP and report that
  message verbatim.
- Never swap to a different board mid-run to "still deliver a result".
