# Code style

Pull requests fail when formatting checks fail. These checks cover layout and a small set of
correctness rules. They do not replace the server, desktop, or Node tests.

## Editor

`.editorconfig` is the shared editor baseline:

- UTF-8
- LF line endings
- a newline at end of file
- trailing whitespace trimmed (left alone in Markdown)
- spaces, 2 wide by default and 4 wide in `*.gradle`

## Java

Backend, desktop, and shared protocol sources use Google Java Format through Spotless
`8.10.4` (`googleJavaFormat` `1.28.0`) in `server` and `desktop-client`. `shared/` has no
Gradle build of its own; both of those builds format it.

Apply formatting:

```bash
(cd server && chmod +x gradlew && ./gradlew spotlessApply --no-daemon)
(cd desktop-client && chmod +x gradlew && ./gradlew spotlessApply --no-daemon)
```

Check without writing:

```bash
(cd server && ./gradlew spotlessCheck --no-daemon)
(cd desktop-client && ./gradlew spotlessCheck --no-daemon)
```

`.github/workflows/java-style.yml` runs `spotlessCheck` for pull requests that change those
trees. `desktop-package.yml` runs it again while packaging.

Checkstyle is not enabled. Spotless already enforces the Java layout. A second checker
would either repeat that or start failing the build on comments, Javadoc, and naming, which
would take a wide edit across server, desktop, and shared sources.

## JavaScript and TypeScript

ESLint (flat config) and Prettier cover:

- `client/` scripts, including `*.test.cjs` and inline `<script>` in HTML
- `toss-client/` TypeScript and HTML
- `formal/**/*.{js,cjs,mjs}` when those files exist (the TLA+ sources are not JavaScript)

Install once from the repository root with Node.js 22:

```bash
npm ci
```

Then:

```bash
npm run check         # ESLint, then Prettier --check
npm run lint          # ESLint only
npm run lint:fix      # ESLint --fix
npm run format        # Prettier --write
npm run format:check  # Prettier --check
```

Prettier rewrites standalone `.js`, `.cjs`, `.mjs`, and `.ts` files (print width 100, double
quotes, semicolons, trailing commas, LF). It does not reformat HTML, CSS, Markdown, YAML, or
JSON. `client/index.html` is a single file whose tests match slices of the inline script, and
a full HTML reformat would also collide with unrelated edits in that file. Inline scripts are
still linted: `eslint-plugin-html` extracts every `<script>` and, matching the browser, shares
one classic-script scope across the tags in the file.

`client/downloads.js` is a classic script (the page loads it with `<script src>`). `*.cjs`
tests stay CommonJS. `toss-client/**/*.ts` and `formal/**/*.js` are modules. Type-aware
TypeScript rules are off because `toss-client` has no `tsconfig` and its only TypeScript
source is `granite.config.ts`.

Rules adjusted for this tree:

- `no-empty` allows an empty `catch`. `downloads.js` ignores the failure when `localStorage`
  is blocked.
- `no-unused-vars` ignores names that start with `_`.
- `applyDownloadAvailability` is an entry point called from `index.html`, so
  `client/downloads.js` may export it without using it in that file.
- HTML lint treats `IS_DESKTOP`, `applyDownloadAvailability`, and `downloadPlatform` as
  read-only globals defined by `downloads.js`.

`.github/workflows/js-style.yml` runs `npm run check` for pull requests that change these
files.

Git hooks are not installed. Most of the repository is built with Gradle, and a hook would
run only after `npm ci` at the root, which a Java-only change does not need. The pull request
check is what every change goes through.
