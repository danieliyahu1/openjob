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

Walking skeleton: a Spring Boot 3.5 / Java 21 app with one form and one endpoint.
`POST /message` forwards the text to the local OpenClaw Gateway
(`POST /v1/chat/completions`, agent `openclaw/main`) and returns the agent's reply.

Run with `mvn spring-boot:run`, then open http://localhost:8080. Copy
`.env.example` to `.env` and set `OPENCLAW_TOKEN` to the gateway token from
`~/.openclaw/openclaw.json`; the Gateway also needs
`gateway.http.endpoints.chatCompletions.enabled = true`.

## Docker

`compose.yaml` runs both pieces: OpenClaw `2026.10.10` (one `openjob` agent,
config baked in from `deploy/openclaw/openclaw.json`) and this app talking to it
at `http://openclaw:18789`. Put `OPENCODE_API_KEY` and `OPENCLAW_GATEWAY_TOKEN`
in `.env`, then `docker compose up --build`. Only port 8080 is published.

## Plane

- Workspace: `akatsukiIL`
- Project: `OpenJob` / `OPENJOB`
- Project id: `f024354d-4a6a-4c59-9a89-f0b7896293ec`
