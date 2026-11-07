#!/usr/bin/env node
// Reads or writes the job match criteria for one user. Ships in the workspace
// template; run it from the workspace, never modify it.
import { readFileSync } from "node:fs";
import process from "node:process";

const fail = (message, code = 1) => {
  console.error(`ERROR: ${message}`);
  process.exit(code);
};

const BASE = (process.env.OPENJOB_API_URL || "").replace(/\/+$/, "");
if (!BASE) fail("OPENJOB_API_URL is not set");

const args = {};
const argv = process.argv.slice(2);
for (let i = 0; i < argv.length; i += 1) {
  const token = argv[i];
  if (!token.startsWith("--")) fail(`unexpected argument: ${token}`);
  const key = token.slice(2);
  const value = argv[i + 1];
  if (value === undefined) fail(`missing value for ${token}`);
  args[key] = value;
  i += 1;
}

// The workspace this script runs in is the user's. Its id authorizes the call.
const readWorkspaceId = () => {
  try {
    return readFileSync(".openjob-id", "utf8").trim();
  } catch {
    return fail("no agent id: run this from your workspace (.openjob-id is missing)");
  }
};

const AGENT = readWorkspaceId();

const headers = { "X-OpenJob-Agent": AGENT };

if (args.set !== undefined) {
  if (!args.set.trim()) fail("--set needs the criteria text");
  const body = new URLSearchParams({ text: args.set });
  const response = await fetch(`${BASE}/api/criteria`, { method: "POST", headers, body });
  if (!response.ok) fail(`HTTP ${response.status}: ${(await response.text()).trim()}`);
  console.log("SAVED");
} else {
  const response = await fetch(`${BASE}/api/criteria`, { headers });
  if (response.status === 404) {
    fail("no criteria saved for this user; ask the user, then save with --set", 2);
  }
  if (!response.ok) fail(`HTTP ${response.status}: ${(await response.text()).trim()}`);
  process.stdout.write(await response.text());
}
