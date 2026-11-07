FROM ghcr.io/openclaw/openclaw:2026.10.10-browser
COPY openclaw.json /home/node/.openclaw/openclaw.json
# The live config is persisted, so the image copy is the startup template the
# merge script compares against.
COPY openclaw.json /opt/openjob/openclaw.default.json
COPY merge-config.mjs /opt/openjob/merge-config.mjs
COPY sync-workspace.mjs /opt/openjob/sync-workspace.mjs
# The policy is not a legacy approval file any more: OpenClaw's doctor refuses to
# overwrite approvals it did not write, so shipping this at
# /home/node/.openclaw/exec-approvals.json breaks every restart once the state
# has agents. The provisioner reads it from /opt and seeds each agent via RPC.
COPY exec-approvals.json /opt/openjob/exec-approvals.json
COPY --chown=node:node workspace /home/node/.openclaw/workspace
COPY provisioner.mjs /opt/openjob/provisioner.mjs
# Mount points have to be writable by the node user on the first run, both in
# Kubernetes (fsGroup) and in Compose (named volumes inherit image ownership).
USER root
RUN mkdir -p /home/node/.openclaw/config /home/node/.openclaw/state \
        /home/node/.openclaw/agents /home/node/.openclaw/workspaces \
    && chown -R node:node /home/node/.openclaw/config /home/node/.openclaw/state \
        /home/node/.openclaw/agents /home/node/.openclaw/workspaces
USER node
