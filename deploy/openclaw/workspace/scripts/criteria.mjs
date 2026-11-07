#!/usr/bin/env node
// Reads or writes the job match criteria for one user. Replaces plane-criteria.mjs.
import process from "node:process";

const BASE = (process.env.OPENJOB_API_URL || "http://openjob:8080").replace(/\/+$/, "");
const TOKEN = process.env.OPENJOB_API_TOKEN;

const fail = (message, code = 1) => {
  console.error(`ERROR: ${message}`);
  process.exit(code);
};

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

const userId = args.user || fail("--user is required (the signed-in email)");
if (!TOKEN) fail("OPENJOB_API_TOKEN is not set");

const headers = { "X-OpenJob-Token": TOKEN };

if (args.set !== undefined) {
  if (!args.set.trim()) fail("--set needs the criteria text");
  const body = new URLSearchParams({ user: userId, text: args.set });
  const response = await fetch(`${BASE}/api/criteria`, { method: "POST", headers, body });
  if (!response.ok) fail(`HTTP ${response.status}: ${(await response.text()).trim()}`);
  console.log("SAVED");
} else {
  const response = await fetch(`${BASE}/api/criteria?user=${encodeURIComponent(userId)}`, { headers });
  if (response.status === 404) {
    fail("no criteria saved for this user; ask the user, then save with --set", 2);
  }
  if (!response.ok) fail(`HTTP ${response.status}: ${(await response.text()).trim()}`);
  process.stdout.write(await response.text());
}
