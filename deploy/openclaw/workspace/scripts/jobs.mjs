#!/usr/bin/env node
// Job board client for the OpenJob API. Replaces scripts/plane-jobs.mjs.
import { readFileSync } from "node:fs";
import process from "node:process";

const BASE = (process.env.OPENJOB_API_URL || "http://openjob:8080").replace(/\/+$/, "");
const TOKEN = process.env.OPENJOB_API_TOKEN;
const STATUSES = ["found", "applied", "blocked"];

const fail = (message, code = 1) => {
  console.error(`ERROR: ${message}`);
  process.exit(code);
};

const parseArgs = (argv) => {
  const args = {};
  for (let i = 0; i < argv.length; i += 1) {
    const token = argv[i];
    if (!token.startsWith("--")) fail(`unexpected argument: ${token}`);
    const key = token.slice(2);
    const value = argv[i + 1];
    if (value === undefined || value.startsWith("--")) fail(`missing value for ${token}`);
    args[key] = value;
    i += 1;
  }
  return args;
};

// The workspace this script runs in is the user's. Its id authorizes the call.
const readWorkspaceId = () => {
  try {
    return readFileSync(".openjob-id", "utf8").trim();
  } catch {
    return fail("no agent id: run this from your workspace (.openjob-id is missing)");
  }
};

const call = async (path, init = {}) => {
  if (!TOKEN) fail("OPENJOB_API_TOKEN is not set");
  let response;
  try {
    response = await fetch(BASE + path, {
      ...init,
      headers: { "X-OpenJob-Token": TOKEN, "X-OpenJob-Agent": AGENT, ...(init.headers || {}) },
    });
  } catch (error) {
    fail(`cannot reach ${BASE}: ${error.message}`);
  }
  const text = await response.text();
  if (!response.ok) fail(`HTTP ${response.status}: ${text.trim()}`);
  return text;
};

const normalize = (url) => (url || "").trim().replace(/\/+$/, "");

const find = async (url) => {
  const rows = JSON.parse(await call("/api/applications"));
  return rows.find((row) => normalize(row.url) === normalize(url)) || null;
};

const commands = {
  async add(args) {
    if (!args.company) fail("--company is required");
    if (!args.title) fail("--title is required");
    if (args.status && !STATUSES.includes(args.status)) {
      fail(`--status must be one of ${STATUSES.join(", ")}`);
    }
    if (args.url) {
      const existing = await find(args.url);
      if (existing) {
        console.log(`DUPLICATE id=${existing.id} status=${existing.status} url=${existing.url}`);
        process.exit(2);
      }
    }
    const body = new URLSearchParams({ company: args.company, title: args.title });
    if (args.url) body.set("url", args.url);
    const id = (await call("/api/applications", { method: "POST", body })).trim();
    if (args.status && args.status !== "found") {
      await call(`/api/applications/${id}?status=${args.status}`, { method: "PATCH" });
    }
    console.log(`ADDED id=${id}`);
  },

  async check(args) {
    if (!args.url) fail("--url is required");
    const existing = await find(args.url);
    if (existing) {
      console.log(`DUPLICATE id=${existing.id} status=${existing.status} url=${existing.url}`);
      process.exit(2);
    }
    console.log("UNIQUE");
  },

  async list(args) {
    if (args.status && !STATUSES.includes(args.status)) {
      fail(`--status must be one of ${STATUSES.join(", ")}`);
    }
    const query = args.status ? `?status=${args.status}` : "";
    console.log(await call(`/api/applications${query}`));
  },

  async mark(args) {
    if (!args.id) fail("--id is required");
    if (!STATUSES.includes(args.status)) {
      fail(`--status must be one of ${STATUSES.join(", ")}`);
    }
    await call(`/api/applications/${args.id}?status=${args.status}`, { method: "PATCH" });
    console.log(`MARKED id=${args.id} status=${args.status}`);
  },
};

const [command, ...rest] = process.argv.slice(2);
if (!command || !commands[command]) {
  fail(`usage: jobs.mjs <${Object.keys(commands).join("|")}> [--agent <id>] [options]`);
}

const args = parseArgs(rest);
const AGENT = args.agent || readWorkspaceId();

try {
  await commands[command](args);
} catch (error) {
  fail(error.message);
}
