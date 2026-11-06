# OpenJob

A web control surface for the OpenClaw job-searcher agent.

OpenJob does not implement an agent. OpenClaw remains the runtime that searches
job boards, evaluates postings against the criteria, and writes job state to
Plane. OpenJob adds what the agent does not have: a UI, a run history, explicit
triggering, and a readable board.

```
Browser  ──►  OpenJob (web app)
                 │  spawns a run
                 ▼
           openclaw agent --agent job-searcher
                 │  searches, evaluates, dedupes
                 ▼
           Plane (job state)  ◄──  OpenJob reads for the board
```

## What lives where

| Concern | Owner |
| --- | --- |
| Agent reasoning, skills, browser automation | OpenClaw (`~/.openclaw/workspace-job-searcher`) |
| Job match criteria | Plane page, project `OPENJOB` |
| Job state (`x`/`v`/`p`/`i`/`!`) | Plane work items, project `OPENJOB` |
| UI, run orchestration, progress streaming | This repo |

## Ported from keren-job

Nothing is copied in yet. When it is, these are the assets worth taking:

1. `skills/job-finder` with the `hiremetech` and `linkedin` adapters — stays in
   OpenClaw, not here.
2. `scripts/plane-jobs.mjs` — the dedupe + board writer. The only code worth
   porting, or calling as a subprocess for `list --json`.
3. The per-source stable-ID dedupe rule and the lifecycle model.

Deliberately left behind: `AGENTS.md`/`SOUL.md` boilerplate, `memory/`,
`.openclaw/`, generated CVs.

## Constraints to design around

- The OpenClaw gateway binds loopback only (`127.0.0.1:18789`) and the agent
  drives a single local Chrome profile. OpenJob is therefore a local-first
  dashboard, not a multi-tenant service, unless a bridge is added later.
- One job is saved per agent run, by design. Runs are cheap; the UI should make
  them repeatable rather than parallel.
- Job state is authoritative in Plane. The app must never keep a second copy.

## Status

Walking skeleton: a Spring Boot 3.5 / Java 21 app with a chat page and one
endpoint. `POST /message` forwards the text to the local OpenClaw Gateway
(`POST /v1/chat/completions`, agent `openclaw/main`) and returns the agent's
reply. The conversation id is the servlet session id, so each browser gets its
own thread with the agent; after 30 idle minutes the cookie expires and the next
message starts a new one.

Applications are stored in SQLite (`openjob.db`, table `applications`) and managed
through `GET/POST /api/applications` and `PATCH /api/applications/{id}`, guarded by
the `X-OpenJob-Token` header; rows are scoped by the `user` parameter. Statuses are
`found`, `applied`, `blocked`. Per-user match criteria live in the `criteria` table
(`GET/POST /api/criteria`).

Run with `mvn spring-boot:run`, then open http://localhost:8080. Copy
`.env.example` to `.env` and set `OPENCLAW_TOKEN` to the gateway token from
`~/.openclaw/openclaw.json`; the Gateway also needs
`gateway.http.endpoints.chatCompletions.enabled = true`.

## Docker

`compose.yaml` runs both pieces: OpenClaw `2026.10.10` (one `openjob` agent,
config baked in from `deploy/openclaw/openclaw.json`) and this app talking to it
at `http://openclaw:18789`. The agent workspace ships in the repo too:
`deploy/openclaw/workspace/{skills,scripts}` is copied into the image, and
`PLANE_API_KEY` is passed through for `scripts/plane-jobs.mjs`. Put
`OPENCODE_API_KEY` and `OPENCLAW_GATEWAY_TOKEN` in `.env`, then
`docker compose up --build`. Only port 8080 is published.

## Production deployment

Push to `main`. GitHub Actions runs the build, publishes
`ghcr.io/danieliyahu1/openjob` and `ghcr.io/danieliyahu1/openjob-agent` for
`linux/arm64`, and commits the exact published image into `deploy/`. Argo CD
then reconciles the `openjob` namespace on the OCI VM — `deploy/` is the only
deployment source, and the image is pinned by commit SHA.

- URL: https://openjob.danieliyahu.com (Cloudflare Access)
- `openjob` — the web app, one replica, SQLite on a 1 Gi `local-path` PVC
- `openclaw` — the agent gateway, ClusterIP only, reached at `http://openclaw:18789`

Secrets come from OCI Vault through the `oci-vault` `ClusterSecretStore`; only
the entry names live in Git:

| Vault entry | Used by |
| --- | --- |
| `openjob_gateway_token` | gateway auth, and the agent's `X-OpenJob-Token` |
| `openjob_opencode_api_key` | the agent's model provider |
| `openjob_linkedin_email` / `openjob_linkedin_password` | LinkedIn sign-in |
| `openjob_email_primary_address` / `_password` | Gmail verification codes |
| `openjob_email_secondary_address` / `_password` | Zoho verification codes |

The agent reads those credentials from the environment (`LINKEDIN_EMAIL`,
`EMAIL_PRIMARY_ADDRESS`, …); no credentials live in the repository.

Observability: the app serves Prometheus metrics on the internal port 9090,
scraped through the `openjob-metrics` Service and `VMServiceScrape`. The
`OpenJob / Overview` dashboard covers request rate, errors, latency, agent calls
and their duration, gateway reachability, board size, JVM and container use.

Known limits: the CV workflow still needs Windows and Word and has no CV volume
on the VM; a long agent run can outlive Cloudflare's 100 s origin timeout; and a
LinkedIn challenge requires a human, which a headless agent cannot provide.

## Plane

- Workspace: `akatsukiIL`
- Project: `OpenJob` / `OPENJOB`
- Project id: `f024354d-4a6a-4c59-9a89-f0b7896293ec`
