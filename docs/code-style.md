# Java code style

Backend, headless Windows agent, and shared protocol Java sources use standard Google Java
Format through a pinned Spotless configuration.

Run `spotlessApply` in `server` and `desktop-client` before opening a pull request. CI runs
`spotlessCheck` for every pull request that changes Java code and rejects formatting violations.
