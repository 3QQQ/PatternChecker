# Pattern Checker

Pattern Checker is a Forge 1.20.1 addon for Applied Energistics 2.
It scans patterns on an ME network and reports invalid recipes, incompatible
machines, unavailable inputs, and duplicate patterns.

## Features

- AE2-styled draggable checker module inside pattern encoding terminals.
- Standalone checker interface when the tool is used directly.
- Optional Curios and Accessories support.
- Pattern highlighting, extraction, upload, editing, and safe in-place writing.
- Processing-machine compatibility checks.
- Toggleable input-supply and duplicate-pattern checks.
- Crafting, processing, smithing, and stonecutting pattern writing.

## Development

This branch targets Java 17, Minecraft 1.20.1, and Forge 47.4.0.

Required development JARs are loaded from `libs/` and are intentionally not
committed:

- Applied Energistics 2 `15.4.9`
- GuideME `20.1.13`

Build with:

```powershell
.\gradlew.bat build
```

The built mod JAR is written to `build/libs/`.
