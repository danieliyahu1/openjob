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
endpoint. `POST /message` forwards the text to the OpenClaw Gateway
(`POST /v1/chat/completions`) and returns the agent's reply. Each signed-in
account gets its own agent and workspace, created on demand: the app stores
`email -> agent slug` in `user_agents`, routes to `openclaw/<slug>`, and a
provisioner sidecar in the agent pod creates the agent and workspace when they
are missing. Workspaces live on an emptyDir, so they are recreated after any
agent pod restart. The conversation id is still the email.

Applications are stored in SQLite (`openjob.db`, table `applications`) and managed
through `GET/POST /api/applications` and `PATCH /api/applications/{id}`, guarded by
the `X-OpenJob-Token` and `X-OpenJob-Agent` headers; rows are scoped to the
account the agent slug belongs to. Statuses are
`found`, `applied`, `blocked`. Per-user match criteria live in the `criteria` table
(`GET/POST /api/criteria`). The agent's scripts read their slug from
`.openjob-id` in their workspace.

Signing in is required for the page and `POST /message`. The first visit goes to
`login.html`; email and password create an account (open signup) or sign in.
Accounts live in the `users` table with a BCrypt hash. Sessions last 30 idle
minutes and the cookie is `SameSite=Lax` and `Secure` in `deploy/`; they are
held in memory, so a restart signs everyone out. The agent's token API and the
anonymous feedback endpoint stay outside the sign-in.

Run with `mvn spring-boot:run`, then open http://localhost:8080. Copy
`.env.example` to `.env` and set `OPENCLAW_TOKEN` to the gateway token from
`~/.openclaw/openclaw.json`; the Gateway also needs
`gateway.http.endpoints.chatCompletions.enabled = true`. Chat also needs the
provisioner on `PROVISIONER_URL` (default `http://127.0.0.1:18790`); without it
`POST /message` answers 503.

## Docker

`compose.yaml` runs OpenClaw `2026.10.10` (config baked in from
`deploy/openclaw/openclaw.json`), the provisioner sidecar that creates per-user
agents, and this app talking to them at `http://openclaw:18789`. The workspace
template ships in the repo: `deploy/openclaw/workspace/{skills,scripts}` is
copied into each user's workspace, and `PLANE_API_KEY` is passed through for
`scripts/plane-jobs.mjs`. Put
`OPENCODE_API_KEY` and `OPENCLAW_GATEWAY_TOKEN` in `.env`, then
`docker compose up --build`. Only port 8080 is published.

## Production deployment

Push to `main`. GitHub Actions runs the build, publishes
`ghcr.io/danieliyahu1/openjob` and `ghcr.io/danieliyahu1/openjob-agent` for
`linux/arm64`, and commits the exact published image into `deploy/`. Argo CD
then reconciles the `openjob` namespace on the OCI VM — `deploy/` is the only
deployment source, and the image is pinned by commit SHA.

- URL: https://openjob.danieliyahu.com (public)
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

## Feedback

The page has a Feedback button. `POST /api/feedback {"message": "..."}` accepts
up to 1500 characters, rate-limits 5 submissions per 10 minutes per client, and
forwards the text to a private Telegram chat in the same request. Delivery is
best-effort: nothing is stored, so if Telegram fails the page asks the visitor to
try again. The bot token and chat id come from the `openjob-feedback` Secret
(`openjob_telegram_feedback_bot_token`, `openjob_telegram_feedback_chat_id` in
Vault); until those exist the endpoint answers 503 and the dashboard shows the
`disabled` outcome.

Known limits: the CV workflow still needs Windows and Word and has no CV volume
on the VM; a long agent run can outlive Cloudflare's 100 s origin timeout; and a
LinkedIn challenge requires a human, which a headless agent cannot provide.

## Plane

- Workspace: `akatsukiIL`
- Project: `OpenJob` / `OPENJOB`
- Project id: `f024354d-4a6a-4c59-9a89-f0b7896293ec`
