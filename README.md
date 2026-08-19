# Pattern Checker（样板检测工具）

Pattern Checker（中文名：样板检测工具）is an Applied Energistics 2 addon for
Minecraft 1.20.1 Forge and 1.21.1 NeoForge. This branch provides the Forge
1.20.1 build and scans patterns on an ME network for invalid recipes,
incompatible machines, unavailable inputs, and duplicate patterns.

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

This branch targets Java 17, Minecraft 1.20.1, and Forge 47.4.0. The matching
NeoForge 1.21.1 build is maintained on the `main` branch.

Required development JARs are loaded from `libs/` and are intentionally not
committed:

- Applied Energistics 2 `15.4.9`
- GuideME `20.1.13`

Build with:

```powershell
.\gradlew.bat build
```

The built mod JAR is written to `build/libs/`.
