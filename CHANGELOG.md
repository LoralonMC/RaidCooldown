# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [2.0.0] - 2026-02-21

### Changed

- **BREAKING:** Migrate all config keys from camelCase to kebab-case (e.g. `raidCooldownSeconds` → `raid-cooldown-seconds`)
- **BREAKING:** Migrate messages from flat strings to nested `text:`/`display:` structure with display mode support (chat/action_bar)
- **BREAKING:** Replace named MiniMessage colors with hex color palette matching project-wide message design standards
- Rewrite MessageManager to TogglePhantoms pattern (texts/displays maps, send routing, named convenience methods)
- Move cooldown data persistence from ConfigManager to CooldownManager (owns cooldowns.yml directly)
- Change periodic save task to run asynchronously
- Replace BukkitRunnable fields with BukkitTask fields in CooldownManager
- Replace `logger.fine()` with conditional INFO logging gated on `log-cooldown-actions` config
- Rename `command` package to `commands` per project standards
- Move reload logic to `reloadPlugin()` method on main class
- Rewrite README to project standard format
- Rewrite plugin.yml permission descriptions for consistency

### Added

- Create CHANGELOG.md following Keep a Changelog format
- Add action bar display mode for raid-blocked message

### Removed

- Remove `getMessage()` methods from ConfigManager (replaced by MessageManager)
- Remove `isValidConfig()` from ConfigManager (unused)
- Remove `getCooldownConfig()` and `saveCooldownConfig()` from ConfigManager (moved to CooldownManager)

## [1.5.0] - 2026-02-13

### Changed

- Switch from Configurate to Bukkit's FileConfiguration — config comments and formatting are now preserved on save
- Empty messages are now treated as disabled — set any message to `""` to suppress it

### Fixed

- Add try-catch around player selector resolution for safer command execution

### Removed

- Remove Configurate dependency, reducing JAR size
- Remove dead message keys (`noPermissionMessage`, `usage`) left over from Brigadier migration
- Remove dead code (`getAllActiveCooldowns()`, unused message constants)

## [1.4.0] - 2026-02-10

### Added

- Add `paper-plugin.yml` as primary plugin descriptor
- Migrate commands to Brigadier via Paper's LifecycleEventManager

### Changed

- **BREAKING:** Migrate message placeholders from `%placeholder%` to MiniMessage `<placeholder>` tag syntax
- Modernize `build.gradle` (shadow 9.3.1, Paper 1.21.10)
- Strip `plugin.yml` to permissions-only

### Fixed

- Fix `validateConfig()` always returning true — now properly distinguishes fatal errors from warnings
- Fix reload command ignoring failed config reload — now sends error message on failure
- Fix scheduled tasks (cleanup, periodic save) not restarting on config reload
- Fix PlaceholderAPI expansion using hardcoded time format suffixes instead of config values
- Fix `getActiveCooldownCount()` including expired entries in count
- Fix minor race condition in batch save between copy and clear

## [1.3.0] - 2026-01-28

### Added

- Add synchronized reset option — all cooldowns reset at a fixed daily time
- Add PlaceholderAPI integration with three placeholders (`time`, `ready`, `seconds`)
- Add configurable `placeholderapi.readyMessage` option

## [1.2.0] - 2026-01-20

### Added

- Add bStats integration for anonymous usage statistics
- Add config value caching for improved performance
- Add comprehensive config validation on load

### Changed

- **BREAKING:** Change placeholder format from `{key}` to `%key%`
- Refactor package structure from `net.vanillymc` to `dev.oakheart`
- Modernize codebase with Java 21 features

## [1.1.0] - 2026-01-15

### Added

- Implement batch saving system (every 30 seconds) reducing disk I/O
- Implement `cleanupIntervalMinutes`, `logCooldownActions`, and `autoCleanup` config options
- Add console support for all commands

### Fixed

- Fix ClassCastException when console runs `/rc check <player>`
- Fix thread safety issue by changing async cleanup to synchronous execution
- Fix race condition in raid triggering with atomic check-and-set operation
- Fix N+1 save problem on shutdown

## [1.0.0] - 2026-01-10

### Added

- Initial release
