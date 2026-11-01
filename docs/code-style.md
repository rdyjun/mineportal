# Java code style

Server, headless Windows agent, and shared protocol Java sources use the standard Google Java
Format style. Spotless pins both the plugin and formatter versions so local formatting and CI are
deterministic.

Apply formatting before committing:

```text
server/gradlew spotlessApply
desktop-client/gradlew spotlessApply
```

Verify without modifying files:

```text
server/gradlew spotlessCheck
desktop-client/gradlew spotlessCheck
```

The Gradle `check` lifecycle includes `spotlessCheck`. Pull-request CI runs `check`, so Java code
that does not match the formatter is rejected with a diff and can be fixed by `spotlessApply`.
