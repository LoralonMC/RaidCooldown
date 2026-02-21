# RaidCooldown

Adds configurable cooldowns to raids with persistent storage.

## Features

- Configurable cooldown duration between raids per player
- Persistent cooldown storage that survives server restarts
- Synchronized daily reset option (all cooldowns expire at a fixed time)
- Bypass permission for exempt players
- Automatic cleanup of expired cooldowns
- Full MiniMessage support for all messages
- PlaceholderAPI integration

## Requirements

- Paper 1.21.10+
- Java 21+
- Optional: PlaceholderAPI

## Installation

1. Place the JAR in your `plugins/` folder
2. Restart the server
3. Edit `plugins/RaidCooldown/config.yml` to your liking
4. Use `/raidcooldown reload` to apply changes

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/raidcooldown` | Check your own cooldown | None |
| `/raidcooldown check <player>` | Check another player's cooldown | `raidcooldown.check` |
| `/raidcooldown reset <player>` | Reset a player's cooldown | `raidcooldown.reset` |
| `/raidcooldown reload` | Reload configuration | `raidcooldown.reload` |
| `/raidcooldown info` | Show plugin information | `raidcooldown.info` |

Aliases: `rc`, `raidcd`

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `raidcooldown.*` | All RaidCooldown permissions | op |
| `raidcooldown.check` | Check other players' cooldowns | op |
| `raidcooldown.reset` | Reset players' cooldowns | op |
| `raidcooldown.reload` | Reload configuration | op |
| `raidcooldown.info` | View plugin information | op |
| `raidcooldown.bypass` | Bypass raid cooldowns | false |

## Configuration

Key settings in `config.yml`:

- `raid-cooldown-seconds` — Cooldown duration in seconds (default: 86400 = 24 hours)
- `synchronized-reset.enabled` — When enabled, all cooldowns reset at a fixed daily time instead of being relative per player
- `synchronized-reset.reset-time` — Time of day for synchronized reset (24-hour format, e.g. `"00:00"`)
- `settings.log-cooldown-actions` — Log cooldown events to console

All messages are customizable using MiniMessage format. See the config file for full details.

## Placeholders

Requires [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/).

| Placeholder | Description | Example |
|-------------|-------------|---------|
| `%raidcooldown_time%` | Formatted time remaining or ready message | `2h 30m 15s` / `Ready` |
| `%raidcooldown_ready%` | Whether player can start a raid | `true` / `false` |
| `%raidcooldown_seconds%` | Raw seconds remaining | `9015` / `0` |
