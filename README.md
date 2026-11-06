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

Walking skeleton: a Spring Boot 3.5 / Java 21 app with one form and one endpoint
(`POST /message` → `your message is: <message>`). Run with `mvn spring-boot:run`,
then open http://localhost:8080.

## Plane

- Workspace: `akatsukiIL`
- Project: `OpenJob` / `OPENJOB`
- Project id: `f024354d-4a6a-4c59-9a89-f0b7896293ec`
