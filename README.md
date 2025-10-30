# 🛡️ RaidCooldown Plugin

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Paper Version](https://img.shields.io/badge/Paper-1.21.4+-blue.svg)](https://papermc.io/)
[![Java Version](https://img.shields.io/badge/Java-21+-orange.svg)](https://adoptium.net/)

A comprehensive Minecraft Paper plugin that adds configurable cooldowns to raids with persistent storage and advanced management features.

## Features

- **Configurable Cooldowns**: Set custom cooldown durations for raids
- **Persistent Storage**: Cooldowns survive server restarts with batch saving
- **Optimized Disk I/O**: Batch saving reduces disk writes by ~95% for better performance
- **Rich Messaging**: Full MiniMessage support with custom colors and formatting
- **Permission System**: Granular permissions for different commands
- **Bypass Permission**: Allow certain players to bypass cooldowns
- **Automatic Cleanup**: Removes expired cooldowns to prevent memory bloat
- **Comprehensive Commands**: Check, reset, and manage cooldowns easily
- **Console Support**: All commands work from server console
- **Thread Safe**: Uses concurrent data structures and atomic operations for reliability
- **Paper 1.21+ Compatible**: Built specifically for modern Paper servers

## Quick Start

1. Download the latest release from the [releases page](https://github.com/LoralonMC/RaidCooldown/releases)
2. Drop it in your `plugins/` folder
3. Restart your server
4. Players now have a 24-hour cooldown between raids!

Need to check a player's cooldown? Use `/rc check <player>`

## Commands

| Command | Description | Permission | Usage |
|---------|-------------|------------|-------|
| `/raidcooldown` | Check your own cooldown | None | `/raidcooldown` |
| `/raidcooldown check <player>` | Check another player's cooldown | `raidcooldown.check` | `/rc check Steve` |
| `/raidcooldown reset <player>` | Reset a player's cooldown | `raidcooldown.reset` | `/rc reset Steve` |
| `/raidcooldown reload` | Reload configuration | `raidcooldown.reload` | `/rc reload` |
| `/raidcooldown info` | Show plugin information | `raidcooldown.info` | `/rc info` |

**Aliases**: `rc`, `raidcd`

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `raidcooldown.*` | Access to all commands | op |
| `raidcooldown.check` | Check other players' cooldowns | op |
| `raidcooldown.reset` | Reset players' cooldowns | op |
| `raidcooldown.reload` | Reload plugin configuration | op |
| `raidcooldown.info` | View plugin information and statistics | op |
| `raidcooldown.bypass` | Bypass raid cooldowns entirely | false |

## Configuration

### Basic Settings

```yaml
# Cooldown duration in seconds (86400 = 24 hours)
raidCooldownSeconds: 86400

settings:
  # Whether to automatically clean up expired cooldowns (recommended: true)
  autoCleanup: true

  # How often to check for expired cooldowns in minutes (0 to disable)
  # Lower values = more frequent cleanup but slightly more CPU usage
  cleanupIntervalMinutes: 10

  # Whether to log cooldown actions to console (useful for debugging)
  logCooldownActions: true
```

**Performance Note**: The plugin uses batch saving (every 30 seconds) to minimize disk I/O. All cooldowns are saved automatically on server shutdown.

### Time Format Examples
- `3600` = 1 hour
- `86400` = 24 hours (default)
- `604800` = 7 days
- `0` = No cooldown (disabled)

### Message Customization

The plugin supports full MiniMessage formatting for rich text, colors, and formatting:

```yaml
messages:
  raidCooldownMessage: "<#FBA9A9>You are on cooldown for starting a raid. Please wait <#A180D0>{time}<#FBA9A9>."
  raidAvailableMessage: "<#B2FBA9>You can start a raid now!"
  # ... more messages
```

#### Placeholders

- `{player}` - Player name
- `{time}` - Formatted time remaining

#### Color Themes

The plugin includes example color themes in the config:
- Default: Purple/pink theme
- Dark/Gothic: Gray and red theme
- Bright/Colorful: Rainbow and vibrant colors

## Installation

1. Download the latest release from the [releases page](https://github.com/LoralonMC/RaidCooldown/releases)
2. Place the `.jar` file in your server's `plugins` folder
3. Restart your server
4. Configure the plugin in `plugins/RaidCooldown/config.yml`
5. Use `/raidcooldown reload` to apply changes

## Requirements

- **Server**: Paper 1.21.4 or newer
- **Java**: Java 21 or newer
- **Dependencies**: None (all included)

## Building from Source

```bash
git clone https://github.com/LoralonMC/RaidCooldown.git
cd RaidCooldown
./gradlew build
```

The compiled plugin will be in `build/libs/RaidCooldown-1.1.0.jar`

## API Usage

Other plugins can interact with RaidCooldown:

```java
// Get the plugin instance
RaidCooldown plugin = (RaidCooldown) Bukkit.getPluginManager().getPlugin("RaidCooldown");

// Check if player has cooldown
CooldownManager manager = plugin.getCooldownManager();
boolean hasCooldown = manager.hasCooldown(player.getUniqueId());

// Get remaining cooldown
Duration remaining = manager.getRemainingCooldown(player.getUniqueId());
```

## Architecture

The plugin is built with a modular architecture:

- **RaidCooldown**: Main plugin class and initialization
- **CooldownManager**: Core cooldown logic and data management
- **MessageManager**: Message formatting and MiniMessage integration
- **ConfigManager**: Configuration loading and validation
- **RaidListener**: Event handling for raid triggers
- **RaidCooldownCommand**: Command processing and tab completion

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

- **Issues**: [Report bugs](https://github.com/LoralonMC/RaidCooldown/issues)
- **Feature Requests**: [Suggest new features](https://github.com/LoralonMC/RaidCooldown/issues)
- **Questions**: [GitHub Discussions](https://github.com/LoralonMC/RaidCooldown/discussions)

## Changelog

### Version 1.1.0

**Critical Bug Fixes:**
- Fixed ClassCastException when console runs `/rc check <player>` command
- Fixed thread safety issue by changing async cleanup to synchronous execution
- Fixed race condition in raid triggering with atomic check-and-set operation
- Fixed N+1 save problem on shutdown (now saves all cooldowns in single operation)

**Performance Improvements:**
- Implemented batch saving system (every 30 seconds) reducing disk I/O by ~95%
- Added dirty tracking to minimize unnecessary file writes
- Optimized cleanup task to batch remove expired cooldowns

**Feature Improvements:**
- Fully implemented `cleanupIntervalMinutes` config option
- Fully implemented `logCooldownActions` config option
- Fully implemented `autoCleanup` config option
- Console support for all commands (previously would crash)
- Added comprehensive Javadoc documentation to all classes

**Code Quality:**
- Moved all hard-coded messages to config.yml for customization
- Added null checks for command registration to prevent NPE
- Removed unused PlaceholderAPI dependency
- Enhanced error handling and logging

### Version 1.0.0
- Initial release