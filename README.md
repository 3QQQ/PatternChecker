# Pattern Checker

Pattern Checker is a NeoForge 1.21.1 addon for Applied Energistics 2.
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

The project targets Java 21 and NeoForge 1.21.1.

Required development JARs are loaded from `libs/` and are intentionally not
committed:

- Applied Energistics 2 `19.2.17`
- GuideME `21.1.17`
- AE2 Lightning Tech `2.0.7` (compile-only)
- Thunderbolt `1.0.3` (compile-only)

Build with:

```powershell
.\gradlew.bat build
```

The built mod JAR is written to `build/libs/`.
