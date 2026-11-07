#!/usr/bin/env node
// Prepares the persisted OpenClaw config before the gateway starts.
//
// The live config lives on the PVC so manual edits and agent registrations
// survive pod restarts. Agent entries still follow the image's template agent:
// every start rebuilds them from `main`, keeping only the slug and its
// workspace. Non-agent sections are left exactly as the operator last wrote
// them, which is the supported place for overrides.
import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from "node:fs";
import { join, posix } from "node:path";

const DATA_ROOT = process.env.OPENCLAW_DATA_ROOT || "/home/node/.openclaw";
const CONFIG = process.env.OPENCLAW_CONFIG_PATH || join(DATA_ROOT, "config/openclaw.json");
const DEFAULT_CONFIG = process.env.OPENCLAW_DEFAULT_CONFIG || "/opt/openjob/openclaw.default.json";
const AGENT_WORKSPACES = process.env.AGENT_WORKSPACES_ROOT || join(DATA_ROOT, "workspaces");
const TEMPLATE_AGENT = process.env.TEMPLATE_AGENT || "main";
const SLUG = /^u-[0-9a-f]{12}$/;

const log = (...parts) => console.log(new Date().toISOString(), ...parts);

function read(path) {
  return JSON.parse(readFileSync(path, "utf8"));
}

function write(path, value) {
  const temporary = `${path}.tmp`;
  writeFileSync(temporary, `${JSON.stringify(value, null, 2)}\n`);
  renameSync(temporary, path);
}

function main() {
  for (const directory of ["workspaces", "state", "agents", "config"]) {
    mkdirSync(join(DATA_ROOT, directory), { recursive: true });
  }

  const defaults = read(DEFAULT_CONFIG);
  const template = defaults.agents?.entries?.[TEMPLATE_AGENT];
  if (!template) {
    throw new Error(`template agent "${TEMPLATE_AGENT}" is missing from ${DEFAULT_CONFIG}`);
  }

  if (!existsSync(CONFIG)) {
    write(CONFIG, defaults);
    log("config_seeded", CONFIG);
  }

  const config = read(CONFIG);
  const entries = config.agents?.entries;
  if (!entries) {
    throw new Error(`agents.entries is missing from ${CONFIG}`);
  }

  let rebuilt = 0;
  for (const slug of Object.keys(entries)) {
    if (!SLUG.test(slug)) continue;
    const entry = { ...template, name: slug, workspace: posix.join(AGENT_WORKSPACES, slug) };
    delete entry.agentDir;
    entries[slug] = entry;
    rebuilt += 1;
  }
  entries[TEMPLATE_AGENT] = { ...template };

  write(CONFIG, config);
  log("config_reconciled", `agents=${rebuilt}`);
}

main();
