# Changelog

## 1.0.5 - 2026-10-01

Minecraft 1.21.1 / NeoForge.

### Added

- Added machine-aware recipe checks for Mystical Automation infusion and
  awakening, including the central ingredient, counted essences and input side.
- Recognized Mekanism Magic processing machines and non-pattern targets.
  Context-dependent recipes are shown as unverified instead of being reported
  as missing.
- Expanded processing recipe handling for Ars Nouveau, Immersive Engineering,
  Mystical Agriculture and other supported machines, including reusable
  ingredients and dynamic outputs.
- Added virtual-completion pattern statistics without bypassing decoding,
  input availability or dispatch-target checks.

### Fixed

- Prevented false invalid-pattern reports for fluid substitution and shapeless
  crafting patterns.
- Restored the selected pattern near its previous position when reopening the
  checker, and cleared stale tool state when opening another terminal.
- Distinguished same-name, same-coordinate pattern providers in different
  dimensions during selection restoration.
- Corrected provider-world lookup, loose-container counting, target validation
  and extra-output allocation.

## 1.0.4 - 2026-09-19

Minecraft 1.20.1 / Forge and Minecraft 1.21.1 / NeoForge.

### Added

- The collapsed checker icon can now be dragged to reposition it. A click
  expands the panel on release; dragging preserves the collapsed state and
  saves the new position.

### Fixed

- Fixed the collapsed checker consuming inventory mouse-release events,
  preventing picked-up items from being placed back into slots.
- Fixed narrow or invisible action buttons after reopening a terminal with
  the checker collapsed and then expanding it.
- Fixed mouse capture cleanup when switching between expanded and collapsed
  states.
- Fixed the Minecraft 1.20.1 Edit button not following panel layout changes.

## 1.0.3 - 2026-08-20

### Added

- Added direct editing for item-only processing patterns, including duplicate
  patterns, from both checker interfaces.
- Added container names and coordinates to pattern list entries.
- Added mouse dragging for the checker list scrollbar.

### Changed

- Preserved the floating checker position across terminal sessions and
  expanded/collapsed states.
- Kept the checker toggle compact and pinned to the module's top-right corner.

### Fixed

- Fixed terminal checker rendering with invalid or transient widget sizes.
- Fixed position resets when reopening a pattern terminal.
- Fixed edge-position expansion crashes caused by invalid button dimensions.

## 1.0.2 - 2026-08-18

### Added

- Added an in-terminal checker module toggle.
- When disabled, only the compact toggle remains visible and the checker no
  longer intercepts clicks, dragging, or scrolling in the rest of the terminal.

### Changed

- Kept all existing Minecraft 1.21.1 NeoForge machine, addon pattern provider,
  wireless provider, and packaged-pattern compatibility.

## 1.0.1 - 2026-08-15

### Added

- Added precise Powah 6.2.10 Energizing Orb recipe validation by binding
  `powah:energizing_orb` to the `powah:energizing` recipe type.
- Invalid Powah energizing patterns can now be distinguished without treating
  unrelated or unknown machines as compatible.

### Changed

- Lowered the minimum supported NeoForge version from 21.1.244 to 21.1.200
  while retaining Minecraft 1.21.1 compatibility.

## 1.0.0 - 2026-08-14

- Initial public release.
