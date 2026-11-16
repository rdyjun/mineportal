import { spawnSync } from "node:child_process";
import { glob } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const patterns = [
  "client/**/*.{js,cjs,mjs,html}",
  "toss-client/**/*.{js,cjs,mjs,ts,html}",
  "formal/**/*.{js,cjs,mjs}",
  "eslint.config.mjs",
  "prettier.config.mjs",
  "scripts/**/*.mjs",
];

const files = [];
for (const pattern of patterns) {
  for await (const file of glob(pattern, {
    cwd: root,
    exclude: ["**/node_modules/**", "**/build/**", "**/dist/**"],
  })) {
    files.push(file);
  }
}

if (files.length === 0) {
  console.log("No JavaScript, TypeScript, or HTML files to lint.");
  process.exit(0);
}

const result = spawnSync(
  process.execPath,
  [path.join(root, "node_modules/eslint/bin/eslint.js"), ...process.argv.slice(2), ...files],
  { cwd: root, stdio: "inherit" },
);

process.exit(result.status ?? 1);
