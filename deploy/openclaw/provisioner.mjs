#!/usr/bin/env node
// Creates a user's OpenClaw agent and workspace on demand. Lives in the
// OpenClaw pod: it shares the network namespace with the gateway (localhost)
// and the workspace volume.
//
// Filesystem work happens here; everything else goes through the gateway's
// admin HTTP RPC. Deliberately no `openclaw` CLI spawns: each one is a fresh
// Node process, and several in a row exceed this sidecar's memory limit.
import { cpSync, existsSync, mkdirSync, writeFileSync } from "node:fs";
import { createServer } from "node:http";

const PORT = Number(process.env.PORT || 18790);
const ROOT = process.env.WORKSPACES_ROOT || "/home/node/.openclaw/workspaces";
const TEMPLATE = process.env.TEMPLATE_WORKSPACE || "/home/node/.openclaw/workspace";
const TEMPLATE_AGENT = process.env.TEMPLATE_AGENT || "main";
const DRY_RUN = process.env.PROVISION_DRY_RUN === "1";
const SLUG = /^u-[0-9a-f]{12}$/;

const log = (...parts) => console.log(new Date().toISOString(), ...parts);

/** No defaults for wiring: a missing value must stop the sidecar, not guess. */
const required = (name) => {
  const value = process.env[name];
  if (!value) {
    log(`missing required environment variable ${name}`);
    process.exit(1);
  }
  return value;
};

const GATEWAY = required("OPENCLAW_GATEWAY_URL");
const TOKEN = required("OPENCLAW_GATEWAY_TOKEN");

async function rpc(method, params = {}) {
  const response = await fetch(`${GATEWAY}/api/v1/admin/rpc`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      authorization: `Bearer ${TOKEN}`,
    },
    body: JSON.stringify({ method, params }),
    signal: AbortSignal.timeout(30_000),
  });
  if (!response.ok) {
    throw new Error(`${method}: HTTP ${response.status}`);
  }
  const body = await response.json();
  if (!body.ok) {
    throw new Error(`${method}: ${body.error?.message || "failed"}`);
  }
  return body.payload;
}

async function hasAgent(slug) {
  const { agents = [] } = await rpc("agents.list", {});
  return agents.some((agent) => agent.id === slug);
}

/**
 * Make the entry an exact copy of the template agent, only renamed and
 * pointed at this workspace. Changing the template updates future agents.
 */
async function applyEntry(slug, workspace) {
  const snapshot = await rpc("config.get", {});
  const entries = snapshot.config?.agents?.entries ?? {};
  const template = entries[TEMPLATE_AGENT];
  if (!template) throw new Error(`template agent "${TEMPLATE_AGENT}" is missing`);

  const entry = { ...template, name: slug, workspace };
  delete entry.agentDir;
  await rpc("config.patch", {
    raw: JSON.stringify({ agents: { entries: { [slug]: entry } } }),
    baseHash: snapshot.hash,
  });
}

async function ensureApproval(slug) {
  const current = await rpc("exec.approvals.get", {});
  const document = current?.file ?? current;
  if (!document?.agents) throw new Error("unexpected approvals document");
  if (document.agents[slug]) return;
  const template = document.agents[TEMPLATE_AGENT] || Object.values(document.agents)[0];
  if (!template) throw new Error("no approvals to copy");
  document.agents[slug] = { ...template };
  await rpc("exec.approvals.set", { file: document, baseHash: current.hash });
}

async function provision(slug) {
  const workspace = `${ROOT}/${slug}`;
  const existing = await hasAgent(slug);
  // Fast path: already registered and its workspace is in place.
  if (existing && existsSync(`${workspace}/.openjob-id`)) return;

  mkdirSync(ROOT, { recursive: true });
  if (!existsSync(workspace)) {
    cpSync(TEMPLATE, workspace, { recursive: true });
  }
  writeFileSync(`${workspace}/.openjob-id`, `${slug}\n`);

  if (DRY_RUN) {
    log("[dry-run] provision", slug, workspace);
    return;
  }

  if (!existing) {
    await rpc("agents.create", { name: slug, workspace });
  }
  await applyEntry(slug, workspace);
  await ensureApproval(slug);
  log("provisioned", slug);
}

// One provisioning at a time: the gateway owns shared config state.
let queue = Promise.resolve();
const serialized = (work) => {
  const next = queue.then(work, work);
  queue = next.catch(() => {});
  return next;
};

const respond = (response, status, body) => {
  response.writeHead(status, body ? { "content-type": "application/json" } : {});
  response.end(body ? JSON.stringify(body) : undefined);
};

createServer(async (request, response) => {
  if (request.method === "GET" && request.url === "/healthz") {
    respond(response, 200, { ok: true });
    return;
  }
  if (request.method !== "POST" || request.url !== "/agents") {
    respond(response, 404, { error: "not found" });
    return;
  }
  let body = "";
  for await (const chunk of request) {
    body += chunk;
    if (body.length > 1024) {
      respond(response, 413, { error: "body too large" });
      return;
    }
  }
  let slug;
  try {
    slug = JSON.parse(body || "{}").slug;
  } catch {
    respond(response, 400, { error: "invalid json" });
    return;
  }
  if (typeof slug !== "string" || !SLUG.test(slug)) {
    respond(response, 400, { error: "invalid slug" });
    return;
  }
  try {
    await serialized(() => provision(slug));
    respond(response, 204);
  } catch (error) {
    log("provision_failed", slug, String(error.message || error).trim());
    respond(response, 500, { error: "could not provision the agent" });
  }
}).listen(PORT, "0.0.0.0", () => log(`provisioner listening on ${PORT}`));
