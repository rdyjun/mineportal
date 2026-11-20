import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import { collectImages, isExactPin, renderDocument } from "./generate-docker-versions.mjs";

const script = path.join(
  path.dirname(fileURLToPath(import.meta.url)),
  "generate-docker-versions.mjs",
);

test("exact pins require two version components or a digest", () => {
  assert.equal(isExactPin("postgres:18.7-alpine"), true);
  assert.equal(isExactPin("eclipse-temurin:17.0.21_9-jdk"), true);
  assert.equal(isExactPin("eclipse-temurin:17.0.21_9-jre"), true);
  assert.equal(isExactPin("ubuntu:22.04"), true);
  assert.equal(
    isExactPin(
      "postgres:18-alpine@sha256:ff64c9e3bdf8e2120107d3559de8db314e8305f7e38c5105903c6295876738ff",
    ),
    true,
  );
  assert.equal(isExactPin("postgres:18-alpine"), false);
  assert.equal(isExactPin("eclipse-temurin:17-jre"), false);
  assert.equal(isExactPin("postgres:latest"), false);
  assert.equal(isExactPin("postgres"), false);
  assert.equal(isExactPin("not an image"), false);
});

test("collects service images, build stages, and workflow images", async () => {
  const root = await fixture();
  try {
    await writeFixture(root);
    const rows = await collectImages(root);
    assert.deepEqual(
      rows.map((row) => [row.service, row.role, row.image, row.file]),
      [
        ["ci", "workflow", "postgres:18.7-alpine", ".github/workflows/ci.yml"],
        ["demo-mc", "build", "eclipse-temurin:17.0.21_9-jdk", "demo-server/Dockerfile"],
        ["demo-mc", "runtime", "eclipse-temurin:17.0.21_9-jre", "demo-server/Dockerfile"],
        ["db", "service", "postgres:18.7-alpine", "docker-compose.yml"],
        ["app", "builder", "eclipse-temurin:17.0.21_9-jdk", "server/Dockerfile"],
        ["app", "runtime", "eclipse-temurin:17.0.21_9-jre", "server/Dockerfile"],
      ],
    );
    assert.match(renderDocument(rows), /`server\/Dockerfile:1`/);
    assert.doesNotMatch(renderDocument(rows), /actions\/checkout/);
    assert.doesNotMatch(renderDocument(rows), /scratch/);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

test("check fails when the document drifts or a tag floats", async () => {
  const root = await fixture();
  try {
    await writeFixture(root);
    assert.equal(run(["--root", root]).status, 0);
    const document = path.join(root, "docs", "docker-versions.md");
    const written = await readFile(document, "utf8");
    assert.equal(run(["--check", "--root", root]).status, 0);
    await writeFile(document, `${written}\n`);
    const drifted = run(["--check", "--root", root]);
    assert.equal(drifted.status, 3);
    assert.match(drifted.stderr, /out of date/);

    const compose = path.join(root, "docker-compose.yml");
    const composeText = await readFile(compose, "utf8");
    await writeFile(compose, composeText.replace("postgres:18.7-alpine", "postgres:18-alpine"));
    await writeFile(document, written);
    const floating = run(["--root", root]);
    assert.equal(floating.status, 2);
    assert.match(floating.stderr, /docker-compose\.yml:\d+ postgres:18-alpine/);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

test("an empty tree is not treated as a successful document", async () => {
  const root = await fixture();
  try {
    const result = run(["--root", root]);
    assert.equal(result.status, 2);
    assert.match(result.stderr, /No container images found/);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

async function fixture() {
  return mkdtemp(path.join(tmpdir(), "docker-versions-"));
}

async function writeFixture(root) {
  await mkdir(path.join(root, "server"), { recursive: true });
  await mkdir(path.join(root, "demo-server"), { recursive: true });
  await mkdir(path.join(root, ".github", "workflows"), { recursive: true });
  await writeFile(
    path.join(root, "server", "Dockerfile"),
    [
      "FROM eclipse-temurin:17.0.21_9-jdk AS builder",
      "FROM scratch AS tools",
      "FROM builder AS packaged",
      "FROM --platform=linux/arm64 eclipse-temurin:17.0.21_9-jre",
      "",
    ].join("\n"),
  );
  await writeFile(
    path.join(root, "demo-server", "Dockerfile"),
    ["FROM eclipse-temurin:17.0.21_9-jdk AS build", "FROM eclipse-temurin:17.0.21_9-jre", ""].join(
      "\n",
    ),
  );
  await writeFile(
    path.join(root, "docker-compose.yml"),
    [
      "services:",
      "  db:",
      '    image: "postgres:18.7-alpine" # production',
      "  app:",
      "    build:",
      "      context: .",
      "      dockerfile: server/Dockerfile",
      "  demo-mc:",
      "    build:",
      "      context: .",
      "      dockerfile: demo-server/Dockerfile",
      "",
    ].join("\n"),
  );
  await writeFile(
    path.join(root, ".github", "workflows", "ci.yml"),
    [
      "name: Verify",
      "jobs:",
      "  server:",
      "    steps:",
      "      - uses: actions/checkout@fbc6f3992d24b796d5a048ff273f7fcc4a7b6c09 # v5",
      "      - run: |",
      "          docker run \\",
      "            postgres:18.7-alpine",
      "",
    ].join("\n"),
  );
}

function run(args) {
  return spawnSync(process.execPath, [script, ...args], { encoding: "utf8" });
}
