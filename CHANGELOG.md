# Changelog

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
