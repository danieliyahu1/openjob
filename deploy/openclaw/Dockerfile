FROM ghcr.io/openclaw/openclaw:2026.10.10-browser
COPY openclaw.json /home/node/.openclaw/openclaw.json
# The live config is persisted, so the image copy is the startup template the
# merge script compares against.
COPY openclaw.json /opt/openjob/openclaw.default.json
COPY merge-config.mjs /opt/openjob/merge-config.mjs
COPY sync-workspace.mjs /opt/openjob/sync-workspace.mjs
# Legacy approvals document: the image entrypoint runs `openclaw doctor --fix`,
# which imports it into the state database on every fresh container. That is how
# the exec allowlist survives the ephemeral filesystem.
COPY --chown=node:node exec-approvals.json /home/node/.openclaw/exec-approvals.json
COPY --chown=node:node workspace /home/node/.openclaw/workspace
# Provided scripts live outside the workspace and stay root-owned: the exec
# allowlist may only point at files the agent cannot rewrite.
COPY agent-scripts /opt/openjob/agent-scripts
COPY provisioner.mjs /opt/openjob/provisioner.mjs
# Mount points have to be writable by the node user on the first run, both in
# Kubernetes (fsGroup) and in Compose (named volumes inherit image ownership).
USER root
RUN mkdir -p /home/node/.openclaw/config /home/node/.openclaw/state \
        /home/node/.openclaw/agents /home/node/.openclaw/workspaces \
    && chown -R node:node /home/node/.openclaw/config /home/node/.openclaw/state \
        /home/node/.openclaw/agents /home/node/.openclaw/workspaces
USER node
