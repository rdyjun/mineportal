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

`.github/workflows/java-style.yml` runs `spotlessCheck`, `checkstyleMain`, and
`checkstyleTest` for pull requests that change those trees or `config/checkstyle/`.
`desktop-package.yml` runs `spotlessCheck` again while packaging. Either Checkstyle task
fails the build on the first violation: `ignoreFailures` is false, and both `maxWarnings`
and `maxErrors` are 0. Warnings are not allowed to pass.

`shared/` is compiled through the `server` and `desktop-client` source sets, so both
Gradle builds check it. Checkstyle `10.24.0` (the version Gradle 9.1 runs by default) reads
`config/checkstyle/checkstyle.xml`. The Checkstyle DTD is vendored next to that file.
`enableExternalDtdLoad` is on so the local DOCTYPE can load; the build does not fetch a DTD.

Check the same gates locally:

```bash
(cd server && ./gradlew spotlessCheck checkstyleMain checkstyleTest --no-daemon)
(cd desktop-client && ./gradlew spotlessCheck checkstyleMain checkstyleTest --no-daemon)
```

`demo-server/` is its own Gradle build. Spotless and Checkstyle cover only
`demo-server/src/**/*.java`. That build does not compile or format `shared/`.

```bash
(cd demo-server && chmod +x gradlew && ./gradlew spotlessCheck checkstyleMain checkstyleTest --no-daemon)
```

### Checkstyle rules that are on

File:

- `NewlineAtEndOfFile` (LF)
- `FileTabCharacter`
- trailing whitespace (`RegexpSingleline`)

Structure and imports:

- `OuterTypeFilename`, `OneTopLevelClass`, `PackageDeclaration`
- `AvoidStarImport` (type and static wildcards)
- `UnusedImports`, `RedundantImport`, `RedundantModifier`

Control flow and statements:

- `NeedBraces` (`do`, `else`, `for`, `if`, `while`)
- `EmptyBlock` (a comment counts as content), `EmptyCatchBlock` (the exception variable may be named `ignored` or `expected`)
- `EmptyStatement`, `OneStatementPerLine`, `MultipleVariableDeclarations`, `ArrayTypeStyle`
- `MissingSwitchDefault`, `FallThrough`, `DefaultComesLast`
- `EqualsHashCode`, `CovariantEquals`, `EqualsAvoidNull`, `StringLiteralEquality`
- `SimplifyBooleanExpression`, `SimplifyBooleanReturn`
- `UpperEll`, `ModifierOrder`, `NoFinalizer`, `SuperClone`, `SuperFinalize`
- `HideUtilityClassConstructor`, `InterfaceIsType`, `MutableException`, `AvoidNestedBlocks`
- `UnnecessarySemicolonInTryWithResources`, `UnnecessarySemicolonAfterTypeMemberDeclaration`, `UnnecessarySemicolonAfterOuterTypeDeclaration`
- `UnusedLocalVariable`, `InvalidJavadocPosition`

Names:

- `PackageName`, `TypeName`, `MethodName`, `MemberName`, `ConstantName`, `StaticVariableName`
- `ParameterName`, `LambdaParameterName`, `CatchParameterName`, `LocalVariableName`, `PatternVariableName`, `RecordComponentName` (a single letter is allowed)
- `ClassTypeParameterName`, `MethodTypeParameterName`, `InterfaceTypeParameterName`, `RecordTypeParameterName`
- `AbbreviationAsWordInName` (abbreviations of 4 or more letters must be listed; the allow-list is `DNS`, `FML`, `HTTP`, `HTTPS`, `ID`, `IO`, `IP`, `JSON`, `SHA`, `SRV`, `TTL`, `URI`, `URL`, `UUID`)

One suppression: `HideUtilityClassConstructor` on `MineportalServerApplication`. Spring Boot
instantiates that class, so a private constructor would stop the process from starting.

### Checkstyle rules that are off

Layout stays with Spotless and google-java-format `1.28.0`. These modules are omitted
because they re-check indentation, wrapping, whitespace, brace placement, import order, or
Javadoc wrapping and disagree with that formatter:

`Indentation`, `LineLength`, `WhitespaceAround`, `WhitespaceAfter`, `NoWhitespaceBefore`,
`NoWhitespaceAfter`, `GenericWhitespace`, `MethodParamPad`, `ParenPad`, `OperatorWrap`,
`SeparatorWrap`, `EmptyLineSeparator`, `LeftCurly`, `RightCurly`, `CommentsIndentation`,
`NoLineWrap`, `AnnotationLocation`, `CustomImportOrder`, `ImportOrder`, `JavadocParagraph`,
`AtclauseOrder`, `SummaryJavadoc`, `JavadocTagContinuationIndentation`, `SingleLineJavadoc`,
`RequireEmptyLineBeforeBlockTagGroup`.

Javadoc presence (`JavadocMethod`, `MissingJavadocMethod`, `MissingJavadocType`,
`JavadocVariable`, `NonEmptyAtclauseDescription`) is off. This tree has no project-wide
Javadoc policy, and requiring it would be a comment-only edit across the Java sources.

`VisibilityModifier` is off. Session and value types expose `public` `final` and
`volatile` fields (`Account`, `ServerConfig`, `AccountState`). Hiding them behind getters
would change the API.

`DesignForExtension` and `FinalClass` are off. Spring beans are non-final on purpose.

`MagicNumber` and `ParameterNumber` are off. Protocol and controller signatures already use
numeric constants and longer parameter lists; wrapping them would be a refactor.

`MethodLength`, `JavaNCSS`, `CyclomaticComplexity`, `NPathComplexity`, `NestedIfDepth`,
`ReturnCount`, and `BooleanExpressionComplexity` are off. Splitting the longer protocol
methods to satisfy a size cap can change behavior.

`HiddenField` is off. Constructors assign `this.x = x`.

`IllegalCatch` and `IllegalThrows` are off. IO and third-party protocol code catch
`Exception` on purpose.

`DeclarationOrder`, `OverloadMethodsDeclarationOrder`, `ConstructorsDeclarationGrouping`,
`InnerTypeLast`, and `VariableDeclarationUsageDistance` are off. Reordering members is a
wide diff and does not change what the code does.

`FinalLocalVariable`, `FinalParameters`, and `RequireThis` are off. google-java-format does
not require them, and applying them would touch most method bodies.

`AvoidStaticImport` is off. Explicit static imports (JUnit, Mockito) stay; only `*` imports
are rejected.

`TodoComment` and `MultipleStringLiterals` are off. Existing notes and repeated protocol
strings would fail the build without a behavior change.

`EmptyBlock` does not include `METHOD_DEF`, so an empty interface method such as
`afterHandshake` is not a violation.

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
