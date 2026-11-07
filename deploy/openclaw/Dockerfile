FROM ghcr.io/openclaw/openclaw:2026.10.10-browser
COPY openclaw.json /home/node/.openclaw/openclaw.json
# Legacy approvals document: the image entrypoint runs `openclaw doctor --fix`,
# which imports it into the state database on every fresh container. That is how
# the exec allowlist (the two workspace scripts and nothing else) survives the
# ephemeral filesystem.
COPY --chown=node:node exec-approvals.json /home/node/.openclaw/exec-approvals.json
COPY --chown=node:node workspace /home/node/.openclaw/workspace-openjob
COPY --chown=node:node provisioner.mjs /opt/openjob/provisioner.mjs
