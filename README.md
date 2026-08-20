# Pattern Checker（样板检测工具）

Pattern Checker（中文名：样板检测工具）is an Applied Energistics 2 addon for
Minecraft 1.20.1 Forge and 1.21.1 NeoForge. It scans patterns on an ME network
and reports invalid recipes, incompatible machines, unavailable inputs, and
duplicate patterns.

## Features

- AE2-styled draggable checker module inside pattern encoding terminals.
- Standalone checker interface when the tool is used directly.
- Optional Curios and Accessories support.
- Pattern highlighting, extraction, upload, editing, and safe in-place writing.
- Processing-machine compatibility checks.
- Toggleable input-supply and duplicate-pattern checks.
- In-terminal checker module enable/disable switch that leaves other terminal
  controls unobstructed when disabled.
- Crafting, processing, smithing, and stonecutting pattern writing.

## Development

Supported targets:

| Minecraft | Loader | Java | Branch |
| --- | --- | --- | --- |
| 1.20.1 | Forge 47.4.0 | 17 | `codex/mc-1.20.1` |
| 1.21.1 | NeoForge 21.1.200+ | 21 | `main` |

Each branch has its own loader-specific build configuration and dependency
versions.

Required development JARs are loaded from `libs/` and are intentionally not
committed:

- The exact Applied Energistics 2 and GuideME versions for the selected branch
  are declared in that branch's `gradle.properties`.

Build with:

```powershell
.\gradlew.bat build
```

The built mod JAR is written to `build/libs/`.

## Automated publishing

Pushing a semantic `v*` tag runs the GitHub Actions `mc-publish` workflow.
It publishes the locally verified Minecraft 1.20.1 Forge and 1.21.1 NeoForge
JARs from `release-assets/` to the GitHub Release and CurseForge project
`1653507`. The repository must define the `CURSEFORGE_TOKEN` Actions secret.
