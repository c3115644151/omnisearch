# Omnisearch

Minecraft client mod — search and browse [mcmod.cn](https://www.mcmod.cn) in-game.

## Status

v2 rewrite in progress. See `docs/` for design documents.

## Tech Stack

- **NeoForge** (ModDevGradle 2.0.141) — MC 1.21.1
- **Stonecutter 0.9.5** — multi-version support from a single codebase
- **Java 21**
- **Kotlin DSL** Gradle scripts

## Building

```bash
./gradlew build
```

## Running

```bash
./gradlew runClient
```

## Version Support

Currently targeting 1.21.1. Stonecutter is configured and ready for additional versions — add them in `settings.gradle.kts`.

## License

MIT
