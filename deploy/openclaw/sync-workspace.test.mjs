import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

const script = fileURLToPath(new URL("./sync-workspace.mjs", import.meta.url));
const slug = "u-0123456789ab";

function fixture() {
  const root = mkdtempSync(join(tmpdir(), "openjob-sync-"));
  const template = join(root, "template");
  const workspaces = join(root, "workspaces");
  const workspace = join(workspaces, slug);

  mkdirSync(join(template, "skills", "job-finder", "platforms"), { recursive: true });
  mkdirSync(join(template, "scripts"), { recursive: true });
  writeFileSync(join(template, "AGENTS.md"), "v1");
  writeFileSync(join(template, "skills", "job-finder", "SKILL.md"), "skill v1");
  writeFileSync(join(template, "skills", "job-finder", "platforms", "linkedin.md"), "adapter v1");
  writeFileSync(join(template, "scripts", "jobs.mjs"), "board client v1");

  mkdirSync(join(workspace, "scripts"), { recursive: true });
  mkdirSync(join(workspace, "skills", "job-finder", "platforms"), { recursive: true });
  writeFileSync(join(workspace, ".openjob-id"), `${slug}\n`);
  writeFileSync(join(workspace, "scripts", "jobs.mjs"), "stale");
  writeFileSync(join(workspace, "skills", "job-finder", "platforms", "acme.md"), "learned");
  mkdirSync(join(workspace, "cv"), { recursive: true });
  writeFileSync(join(workspace, "cv", "resume.docx"), "user data");

  return { root, template, workspaces, workspace };
}

function run(template, workspaces) {
  return spawnSync(process.execPath, [script], {
    encoding: "utf8",
    env: { ...process.env, WORKSPACES_ROOT: workspaces, TEMPLATE_WORKSPACE: template },
  });
}

test("copies provided files, restores the scripts, and keeps user data", () => {
  const { root, template, workspaces, workspace } = fixture();
  try {
    const result = run(template, workspaces);
    assert.equal(result.status, 0, result.stderr);

    assert.equal(readFileSync(join(workspace, "AGENTS.md"), "utf8"), "v1");
    assert.equal(readFileSync(join(workspace, "skills", "job-finder", "SKILL.md"), "utf8"), "skill v1");
    assert.equal(readFileSync(join(workspace, ".openjob-id"), "utf8"), `${slug}\n`);
    assert.equal(readFileSync(join(workspace, "cv", "resume.docx"), "utf8"), "user data");
    assert.equal(
        readFileSync(join(workspace, "skills", "job-finder", "platforms", "acme.md"), "utf8"),
        "learned",
        "a board the agent wrote down must survive");
    assert.equal(
        readFileSync(join(workspace, "scripts", "jobs.mjs"), "utf8"),
        "board client v1",
        "an edited board client is replaced by the provided one");
    assert.match(readFileSync(join(workspace, ".openjob-template"), "utf8"), /^[0-9a-f]{64}\n$/);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test("an unchanged template is not copied over the workspace again", () => {
  const { root, template, workspaces, workspace } = fixture();
  try {
    assert.equal(run(template, workspaces).status, 0);
    writeFileSync(join(workspace, "skills", "job-finder", "SKILL.md"), "edited locally");

    assert.equal(run(template, workspaces).status, 0);
    assert.equal(readFileSync(join(workspace, "skills", "job-finder", "SKILL.md"), "utf8"),
        "edited locally");
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test("a changed template updates existing workspaces", () => {
  const { root, template, workspaces, workspace } = fixture();
  try {
    assert.equal(run(template, workspaces).status, 0);
    writeFileSync(join(template, "skills", "job-finder", "SKILL.md"), "skill v2");

    assert.equal(run(template, workspaces).status, 0);
    assert.equal(readFileSync(join(workspace, "skills", "job-finder", "SKILL.md"), "utf8"), "skill v2");
    assert.equal(
        readFileSync(join(workspace, "skills", "job-finder", "platforms", "acme.md"), "utf8"),
        "learned");
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test("a missing template stops the sync instead of half-copying", () => {
  const { root, workspaces } = fixture();
  try {
    const result = run(join(root, "does-not-exist"), workspaces);
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /provided path is missing from the image/);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
