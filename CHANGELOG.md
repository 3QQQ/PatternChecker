# Changelog

## 1.0.2 - 2026-08-18

### Added

- Added Minecraft 1.20.1 Forge 47.4.0 support for the All the Mods 9 mod set.
- Added precise pattern-container support for AE2, AdvancedAE, ExtendedAE,
  MEGA Cells, and AppliedFlux-enhanced providers.
- Added precise machine mappings for Mekanism, Industrial Foregoing, Powah,
  Productive Bees, Ender IO, Draconic Evolution, PneumaticCraft, Thermal,
  Immersive Engineering, Create, Create Addition, Create Ore Excavation, and
  GTCEu.
- Added an in-terminal checker module toggle.

### Changed

- Reworked networking, item data, menus, recipe access, and events for Forge
  1.20.1 and Java 17.
- When the checker module is disabled, it no longer intercepts terminal clicks,
  dragging, or scrolling outside the compact toggle.

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
