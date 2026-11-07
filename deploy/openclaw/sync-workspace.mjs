#!/usr/bin/env node
// Copies the files this repo provides into every existing user workspace before
// the gateway starts. Workspaces live on the PVC, so without this step a skill
// fix would reach new users only.
//
// Rules:
// - Only provided paths are touched (AGENTS.md and skills/). User data and the
//   workspace's .openjob-id are never read or written.
// - Files are overwritten, never deleted — except the legacy scripts/ directory,
//   which moved into the image. An agent may add its own board notes under
//   skills/job-finder/platforms/, and those must survive an update.
// - A content fingerprint of the provided files decides whether to copy, so an
//   unchanged image costs one directory walk.
import { createHash } from "node:crypto";
import { cpSync, existsSync, mkdirSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from "node:fs";
import { join } from "node:path";

const ROOT = process.env.WORKSPACES_ROOT || "/home/node/.openclaw/workspaces";
const TEMPLATE = process.env.TEMPLATE_WORKSPACE || "/home/node/.openclaw/workspace";
const MARKER = ".openjob-template";
const PROVIDED = ["AGENTS.md", "skills"];
const SLUG = /^u-[0-9a-f]{12}$/;

const log = (...parts) => console.log(new Date().toISOString(), ...parts);

function fingerprint() {
  const hash = createHash("sha256");
  const walk = (relative) => {
    const full = join(TEMPLATE, relative);
    if (!existsSync(full)) {
      throw new Error(`provided path is missing from the image: ${full}`);
    }
    if (statSync(full).isDirectory()) {
      for (const name of readdirSync(full).sort()) {
        walk(join(relative, name));
      }
      return;
    }
    hash.update(relative).update("\0").update(readFileSync(full));
  };
  for (const relative of PROVIDED) {
    walk(relative);
  }
  return hash.digest("hex");
}

const version = fingerprint();
mkdirSync(ROOT, { recursive: true });

let updated = 0;
for (const entry of readdirSync(ROOT)) {
  if (!SLUG.test(entry)) continue;
  const workspace = join(ROOT, entry);
  const marker = join(workspace, MARKER);
  if (existsSync(marker) && readFileSync(marker, "utf8").trim() === version) {
    continue;
  }
  for (const relative of PROVIDED) {
    cpSync(join(TEMPLATE, relative), join(workspace, relative), { recursive: true, force: true });
  }
  // scripts/ used to ship inside the workspace; it now lives in the image. Drop
  // the stale copy so nobody runs an outdated, writable board client.
  rmSync(join(workspace, "scripts"), { recursive: true, force: true });
  writeFileSync(marker, `${version}\n`);
  updated += 1;
  log("workspace_synced", entry);
}

log("workspace_sync_done", `updated=${updated}`);
