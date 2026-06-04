# Futa Manager - ZenithProxy Plugin Collection

> **[📖 中文文档 (Chinese Documentation)](README_zh.md)**

A powerful Minecraft ZenithProxy plugin collection offering automated pearl management, enchanting, trading, crafting assistance, and more.

## 📋 Table of Contents

- [Project Overview](#project-overview)
- [Features](#features)
- [Installation](#installation)
- [Configuration Guide](#configuration-guide)
- [Commands](#commands)
- [Module Details](#module-details)
- [Development Guide](#development-guide)
- [Important Notes](#important-notes)

---

## 🎯 Project Overview

**Futa Manager** is a Minecraft client plugin built on the ZenithProxy platform, designed for automating game operations. It provides pearl management, automatic item sorting, enchanting, villager trading, auto-crafting, and other repetitive tasks.

I originally wrote this in 2024 for personal use on a server. As the features grew and became more refined, I decided to open-source it.

There are many hardcoded values in the codebase. Feel free to open an issue if you find any problems.

Most feature modules are disabled by default and won't affect normal behavior — feel free to use them.

Contributions are welcome! Please submit PRs.

### Key Features

- ✅ **Highly Automated**: Intelligent state machine-driven, handles complex workflows automatically
- ✅ **Modular Design**: 25+ independent modules, enable only what you need
- ✅ **Flexible Configuration**: Detailed configuration files for each module
- ✅ **Multi-Version Support**: Supports Minecraft 1.21.4 and above
- ✅ **Event-Driven**: Efficient event handling mechanism

---

## ⚡ Features

### 📦 Item Management

#### 1. **Auto Chest Manager (AutoChestManager)**
- Automatically extract items from multiple chests
- Intelligently sort and store items into shulker boxes
- Auto-discard junk items
- Supports round delays and timeout protection

#### 2. **Item Sorter (ItemSorter)**
- Intelligent item sorting system
- Supports custom sorting rules
- Automatic chest content caching
- One-click sorting for multiple chests
- Supports regular chests, barrels, and shulker boxes

#### 3. **Enchantment Book Sorter (EnchantBookSorter)**
- Auto-organize enchantment books
- Sort by enchantment type
- Smart enchantment level recognition
- Optimize chest space utilization

### 🔮 Enchanting System

#### 4. **Auto Enchant (AutoEnchant)**
- Fully automatic diamond equipment enchanting
- Smart XP collection (mob grinders/farms)
- Supports merging multiple enchantment books
- Special handling for swords (up to 7 books)
- Enchantment progress tracking and caching
- Auto-replenish materials and books

### 💰 Trading System

#### 5. **Villager Trader (VillagerTrader)**
- Auto-trade with villagers
- Smart restocking system
- Auto-craft emeralds
- Trade cooldown management
- Supports multiple villagers

#### 6. **Shop Advertisement (Shop)**
- Automated shop advertisement operations

### ⚔️ Combat Assistance

#### 7. **Auto Crystal (AutoCrystal)**
- Auto-place End Crystals

#### 8. **Pearl Plus (PearlPlus)**
- Auto-load and use Ender Pearls
- Remote control via private message commands (send "pull" request)
- Smart search nearby signs to find player pearl locations
- Auto-detect distance and return status info
- Supports configuring multiple preset pearl points
- Auto-return to starting position and disconnect after use

#### 9. **Death Logger (DeathLogger)**
- Record all player death information
- JSON format detailed logs
- Includes timestamps, coordinates, weapons, etc.
- Optional console output and file saving

### 🌾 Farm Automation

#### 10. **Nether Wart Farm (NetherWartFarm)**
- Auto-plant and harvest nether wart
- Smart growth detection
- Auto-replanting

#### 11. **Auto Wither (AutoWither)**
- Auto-spawn Withers at designated locations
- Auto-replenish soul sand
- Round counting and management
- Maximum wither limit
- Detailed usage docs: [autowither_usage.md](autowither_usage.md)

### 🎒 Other Utilities

#### 12. **Auto Follow (AutoFollow)**
- Auto-follow designated players
- Smart pathfinding
- Combat state detection
- Auto-send coordinates after respawn
- Bed click support

#### 13. **Auto Login (AutoLogin)**
- Auto server login
- Session reconnect support

#### 14. **Auto Vault Opener (AutoVaultOpener)**
- Batch open vaults
- Quick item extraction

#### 15. **Chat Log (ChatLog)**
- Chat history saving
- Keyword filtering

#### 16. **Visual Range Logger (VisualRangeLogger)**
- Record players entering visual range
- Distance alerts

#### 17. **Fixed Angle View (FixedAngleView)**
- Lock viewing angle
- Prevent angle drift
- Mainly for enderman farms

#### 18. **End Gateway (EndGateway)**
- Auto-enter End Gateways
- Quick teleportation

#### 19. **Auto Drop (AutoDrop)**
- Auto-drop specified items
- Inventory space management

#### 21. **Anti Stuck (AntiStuck)**
- Detect stuck state
- Auto-escape

#### 22. **Auto Brewer (AutoBrewer)**
- Auto-brew potions

#### 23. **Post Respawn (PostRespawn)**
- Auto-execute actions after respawn

#### 24. **Elytra Fly (ElytraFly)**
- Automatic elytra flight control
- Pitch oscillation flight between upper and lower bounds (no fireworks needed)
- Smart boundary reset based on player position
- Target coordinate navigation with auto-disconnect
- Low Y-axis auto-disconnect safety feature
- Speed: 30-40 blocks/second without elytra durability loss

---

## 📥 Installation

### Prerequisites

- **ZenithProxy**: Java version (Linux version not supported)
- **Java**: JDK 25 or higher
- **Minecraft**: 1.21.4 or compatible version

### Installation Steps

1. **Download the Plugin**
   - Download the latest `.jar` from [Releases](../../releases)

2. **Install the Plugin**
   - Copy the `.jar` file to ZenithProxy's `plugins` folder
   - Restart ZenithProxy to load the plugin

3. **Verify Installation**
   - Start ZenithProxy
   - Check console output for successful plugin loading
   - Test with related commands

---

## ⚙️ Configuration Guide

### Configuration File Location

Plugin configuration file is located at `config/futa.json` in the ZenithProxy root directory.

### Configuration Examples

See each module's documentation:
- [Auto Wither Usage Guide](autowither_usage.md)
- [Block Replace Usage Guide](block_replace_usage.md)

---

## 💻 Commands

### Item Management
```bash
/autochest <on|off>              # Enable/disable Auto Chest Manager
/autochest addChest <x> <y> <z>  # Add chest position
/autochest list                  # List all chests

/itemsorter <on|off>             # Enable/disable Item Sorter
/itemsorter addChest <x> <y> <z> # Add chest position
/itemsorter classify             # Manually trigger sorting

/enchantbooksorter <on|off>      # Enable/disable Enchantment Book Sorter
```

### Enchanting System
```bash
/autoenchant <on|off>            # Enable/disable Auto Enchant
/autoenchant setXpFarm <x> <y> <z>  # Set XP farm position
/autoenchant status              # View enchanting status
```

### Trading System
```bash
/villagertrader <on|off>         # Enable/disable Villager Trader
/villagertrader setRestockChest <x> <y> <z>  # Set restock chest
/villagertrader restock          # Manually restock

/shop <buy|sell> <item> <amount> # Shop operations
```

### Combat Assistance
```bash
/autocrystal <on|off>            # Enable/disable Auto Crystal
/pp <on|off>                     # Enable/disable Pearl Plus (PM remote control)

/chatlog <on|off>                # Enable/disable Chat Log
/chatlog search <keyword>        # Search chat history
```

### Farm Automation
```bash
/netherwartfarm <on|off>         # Enable/disable Nether Wart Farm

/autowither <on|off>             # Enable/disable Auto Wither
/autowither addPosition <x> <y> <z>     # Add spawn position
/autowither soulSandChest <x> <y> <z>   # Set soul sand chest
/autowither maxWithers <number>         # Set maximum wither count
/autowither listPositions        # View all positions
/autowither resetRound           # Reset round counter
```

### Other Features
```bash
/autofollow <on|off>             # Enable/disable Auto Follow
/autofollow addTarget <player>   # Add follow target
/autofollow removeTarget <player># Remove follow target

/autologin <on|off>              # Enable/disable Auto Login

/autovault <on|off>              # Enable/disable Auto Vault Opener

/visualrange <on|off>            # Enable/disable Visual Range Logger

/fixedangle <on|off>             # Enable/disable Fixed Angle View
/fixedangle set <pitch> <yaw>    # Set viewing angle

/endgateway <on|off>             # Enable/disable End Gateway

/autodrop <on|off>               # Enable/disable Auto Drop
/autodrop addItem <item>         # Add item to auto-drop list

/wander <on|off>                 # Enable/disable Random Wandering

/antistuck <on|off>              # Enable/disable Anti Stuck

/showentity                      # Show nearby entity info

/pp                              # Pearl

/loginonce                       # One-time login command

/elytrafly <on|off>              # Enable/disable Elytra Fly
/elytrafly upper <height>        # Set upper flight bound
/elytrafly lower <height>        # Set lower flight bound
/elytrafly speed <degrees>       # Set pitch rotation speed
/elytrafly gap <blocks>          # Set boundary gap
/elytrafly goto <x> <z>          # Navigate to target coordinates
/elytrafly disconnect on|off     # Auto-disconnect at target
/elytrafly disconnectDistance <blocks>  # Set disconnect distance
/elytrafly lowY <height>         # Auto-disconnect below Y level
/elytrafly debug on|off          # Enable debug logging
/elytrafly debugPeriod <seconds> # Set debug log interval
```

---

## 📚 Module Details

### Pearl Plus (PearlPlus)

**Overview:**
PearlPlus is an intelligent Ender Pearl management system that supports auto-loading and remote control via private messages.

**Core Features:**

1. **Auto Pearl Loading**
   - Automatically travel to designated locations based on preset IDs
   - Optionally return to starting position and disconnect after loading
   - Ideal for scenarios requiring regular pearl usage

2. **Private Message Remote Control**
   - Other players can send "pull" via PM to request pearl loading assistance
   - Automatically searches nearby signs (64-block range) for player pearl locations
   - Smart distance detection (rejects requests beyond 120 blocks)
   - Auto-reply with status info (en route, too far, pearl not found, etc.)

3. **Smart Location Management**
   - Auto-identify signs with player names (requires trapdoors)
   - Save found pearl locations to configuration
   - Supports multiple player pearl location storage

4. **Status Feedback**
   - Discord and in-game notifications
   - Detailed distance calculation and pathfinding
   - Error handling and friendly prompts

**Configuration:**
```json
{
   "pearlPlus": {
      "enabled": true,        // Enable module
      "auto": false,          // Auto-execute (auto-resets to false after trigger)
      "autoId": "",           // Auto-load pearl ID
      "server": ""            // Server name
   }
}
```

**Important Notes:**
- Ensure interactive blocks (trapdoors, levers, etc.) are near pearl locations
- Signs need player names for auto-identification
- Distance limit is 120 blocks (Manhattan distance)
- Sufficient Ender Pearl inventory required

---

### Elytra Fly (ElytraFly)

**Overview:**
ElytraFly is an automatic elytra flight control module that enables pitch oscillation flight without fireworks or elytra durability loss. The bot automatically adjusts pitch between -40° (climb) and +40° (dive) to maintain flight within configured height bounds.

**Core Features:**

1. **Automatic Pitch Control**
   - Oscillates pitch between -40° and +40° for sustained flight
   - Configurable rotation speed (degrees per tick)
   - Automatic boundary reset based on player position

2. **Height Management**
   - Upper and lower bounds define flight altitude range
   - Auto-reset boundaries when player drops below threshold
   - Smart detection of highest point for boundary adjustment

3. **Target Navigation**
   - Set target X/Z coordinates to fly towards
   - Automatic yaw calculation to face target
   - Configurable disconnect distance for arrival detection

4. **Safety Features**
   - Low Y-axis auto-disconnect (configurable threshold)
   - Unloaded chunk detection to prevent crashes
   - Debug logging with configurable interval

**Configuration:**
```json
{
   "elytraFly": {
      "enabled": false,
      "pitch40LowerBounds": 80,
      "pitch40UpperBounds": 120,
      "pitch40RotationSpeed": 4,
      "boundGap": 60,
      "targetX": 0,
      "targetZ": 0,
      "disconnectOnReach": false,
      "disconnectDistance": 5,
      "disconnectOnLowY": 0,
      "debug": false,
      "debugLogPeriod": 2
   }
}
```

**Usage:**
1. Wear elytra and manually take off
2. Enable the module: `.elytrafly on`
3. (Optional) Set target: `.elytrafly goto 1000 2000`
4. (Optional) Enable auto-disconnect: `.elytrafly disconnect on`
5. Module will automatically control pitch and navigate

**Performance:**
- Speed: 30-40 blocks/second
- No fireworks required
- No elytra durability loss
- Recommended to enable ElytraUnbreak module

---

### Architecture

The plugin uses a modular architecture where each feature is an independent Module or Command:

```
FutaPlugin (Main Plugin)
├── Modules (25 modules)
│   ├── AutoChestManagerModule
│   ├── ItemSorterModule
│   ├── AutoEnchantModule
│   ├── VillagerTrader
│   ├── PearlPlusModule
│   └── ... (other modules)
└── Commands (22 commands)
    ├── AutoChestManagerCommand
    ├── ItemSorterCommand
    ├── AutoEnchantCommand
    ├── PPCommand
    └── ... (other commands)
```

### State Machine

Most modules implement complex logic using the state machine pattern:

```java
enum ProcessingState {
   IDLE,
   OPENING_CHEST,
   WITHDRAWING_FROM_CHEST,
   CLOSING_CHEST,
   // ... more states
}
```

### Event-Driven

Based on ZenithProxy's event system:

- `ClientBotTick`: Client tick event
- `SystemChatEvent`: System chat event
- `ClientDeathEvent`: Death event
- `ServerPlayerInVisualRangeEvent`: Player enters visual range event

---

## 🔧 Development Guide

### Tech Stack

- **Language**: Java 25+
- **Build Tool**: Gradle 8.x
- **Framework**: ZenithProxy Plugin API
- **Dependencies**:
   - Hutool (utility library)
   - Gson (JSON processing)
   - ProtocolLib (protocol handling)

### Project Structure

```
ZenithProxy-futa/
├── src/main/java/com/github/futa/
│   ├── FutaPlugin.java          # Main plugin class
│   ├── BaseModule.java          # Base module class
│   ├── module/                  # Module implementations
│   │   ├── AutoChestManagerModule.java
│   │   ├── ItemSorterModule.java
│   │   └── ...
│   ├── command/                 # Command implementations
│   │   ├── AutoChestManagerCommand.java
│   │   ├── ItemSorterCommand.java
│   │   └── ...
│   ├── config/                  # Configuration classes
│   │   ├── FutaConfig.java
│   │   ├── AutoChestManagerConfig.java
│   │   └── ...
│   ├── dto/                     # Data transfer objects
│   └── util/                    # Utility classes
├── src/main/resources/
│   ├── itemtag/                 # Item tags
│   ├── mcdata/                  # Minecraft data
│   └── recipes/                 # Recipe data
├── doc/                         # Documentation
├── build.gradle.kts             # Gradle configuration
└── README.md                    # This file
```

### Creating a New Module

1. **Create Module Class**
```java
public class MyModule extends Module {
   @Override
   public List<EventConsumer<?>> registerEvents() {
      return List.of(
              of(ClientBotTick.class, this::onTick)
      );
   }

   @Override
   public boolean enabledSetting() {
      return PLUGIN_CONFIG.myModule.enabled;
   }

   private void onTick(ClientBotTick event) {
      // Module logic
   }
}
```

2. **Create Configuration Class**
```java
public class MyModuleConfig {
   public boolean enabled = false;
   // Other config items
}
```

3. **Register Module**
```java
// In FutaPlugin.onLoad()
pluginAPI.registerModule(new MyModule());
```

### Creating a New Command

```java
@CommandInfo(
        name = "mycommand",
        description = "My command"
)
public class MyCommand extends Command {
   @Override
   public void execute(CommandContext context) {
      // Command logic
   }
}
```

### Build and Test

```bash
# Build the plugin
./gradlew build

# Local testing (requires ZenithProxy configuration)
./gradlew run
```

---

## ⚠️ Important Notes

### Usage Recommendations

1. **Backup Config**: Backup important data and configuration files before use
2. **Enable Gradually**: Test single modules first, then enable others
3. **Monitor Logs**: Regularly check console logs and error messages
4. **Reasonable Configuration**: Adjust module parameters according to server rules to avoid detection

### Performance Optimization

- Adjust `updateInterval` to reduce tick frequency
- Set appropriate delays (`delayBetweenActions`)
- Avoid enabling too many modules simultaneously
- Clean log files periodically

### Compatibility

- Only supports ZenithProxy Java version
- Requires Minecraft 1.21.4 or compatible version
- Some modules may require specific server environments

### Security Notice

⚠️ **Important Disclaimer**:
- This plugin is for learning and research purposes only
- Using automation scripts on multiplayer servers may violate server rules
- Please comply with each server's terms of use
- Developers are not responsible for any consequences arising from the use of this plugin

---

## 📖 Documentation

- [Auto Wither Usage Guide](autowither_usage.md)
- [Block Replace Usage Guide](block_replace_usage.md)
- [Item Classification Complete Guide](doc/ItemClassification_Complete.md)
- [Item Sorter Default Classification](doc/ItemSorter_DefaultClassification_README.md)
- [Item Sorter One Item One Chest](doc/ItemSorter_OneItemOneChest_README.md)
- [Death Logger Documentation](doc/README-DeathLogger.md)

---

## 🤝 Contributing

Issues and Pull Requests are welcome!

1. Fork this repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details

---

## 👥 Authors

- **futa** - Initial development and maintenance

---

## 🙏 Acknowledgments

- [ZenithProxy](https://github.com/rfresh2/ZenithProxy) - Powerful Minecraft proxy platform
- All contributors and users

---

**⭐ If this project helps you, please give it a Star!**
