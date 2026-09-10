# KoHs-Core documentation

[Back to overview](../README.md)

## Guides

- [Feature guide](FEATURES.md)
- [Integration API](INTEGRATION.md)
- [Performance research](research/PERFORMANCE_RESEARCH_2026-09.md)
- [Animation preview](media/animations-preview.gif)

## Root build

| Setting | Value |
| --- | --- |
| Minecraft | `26.1.2` |
| Mod version | `1.2.0` |
| Loader | Fabric `0.19.3` |
| Loom | `1.17.17` |
| JDK | 25 |

Official Minecraft names for the 26.x root target.
The source of truth is [gradle.properties](../gradle.properties) and
[build.gradle](../build.gradle); each additional target declares its own dependencies.

## Working folders

Run build commands from the repository root unless a target's instructions say
otherwise. `build/`, `.gradle/` and `run/` hold local build or game state and are
excluded from Git. Preserve saves, configuration and logs when organizing files.
Audits describe the version and checks recorded at the time; they do not imply
that every later change has been tested in a running game.
