# Block Replace Module Usage Guide

## Overview
The Block Replace module allows you to replace blocks at specific coordinates with other blocks, primarily designed for anti anti-Xray purposes.

## Commands

### Enable/Disable the module
```
blockReplace toggle on
blockReplace toggle off
```

### Add a block replacement
```
blockReplace add <x> <y> <z> <block_name>
```

Example:
```
blockReplace add 100 64 200 diamond_ore
blockReplace add -50 15 300 deepslate_diamond_ore
```

### List all replacements
```
blockReplace list
```

### Clear all replacements
```
blockReplace clear
```

## Available Blocks

### Ore Blocks (main purpose)
- `diamond_ore` - Diamond Ore
- `deepslate_diamond_ore` - Deepslate Diamond Ore
- `gold_ore` - Gold Ore
- `deepslate_gold_ore` - Deepslate Gold Ore
- `iron_ore` - Iron Ore
- `deepslate_iron_ore` - Deepslate Iron Ore
- `coal_ore` - Coal Ore
- `deepslate_coal_ore` - Deepslate Coal Ore
- `copper_ore` - Copper Ore
- `deepslate_copper_ore` - Deepslate Copper Ore
- `lapis_ore` - Lapis Lazuli Ore
- `deepslate_lapis_ore` - Deepslate Lapis Lazuli Ore
- `redstone_ore` - Redstone Ore
- `deepslate_redstone_ore` - Deepslate Redstone Ore
- `emerald_ore` - Emerald Ore
- `deepslate_emerald_ore` - Deepslate Emerald Ore
- `nether_quartz_ore` - Nether Quartz Ore
- `nether_gold_ore` - Nether Gold Ore
- `ancient_debris` - Ancient Debris

### Basic Blocks
- `air` - Air
- `stone` - Stone
- `bedrock` - Bedrock
- `dirt` - Dirt
- `grass_block` - Grass Block
- `cobblestone` - Cobblestone

### Wood Blocks
- `oak_planks` - Oak Planks
- `spruce_planks` - Spruce Planks
- `birch_planks` - Birch Planks
- `jungle_planks` - Jungle Planks
- `acacia_planks` - Acacia Planks
- `dark_oak_planks` - Dark Oak Planks

## Configuration

The module can also be configured directly in the config file:

```json
{
  "client": {
    "extra": {
      "blockReplace": {
        "enabled": true,
        "replacements": [
          {
            "x": 100,
            "y": 64,
            "z": 200,
            "targetBlock": "diamond_ore"
          },
          {
            "x": -50,
            "y": 15,
            "z": 300,
            "targetBlock": "deepslate_diamond_ore"
          }
        ]
      }
    }
  }
}
```

## How It Works

1. When the module is enabled, it intercepts chunk data packets from the server
2. For each chunk, it checks if any of the configured coordinates fall within that chunk
3. If coordinates match, it replaces the block at those coordinates with the specified target block
4. The modified chunk data is then sent to the client

## Use Cases

### Anti Anti-Xray
- Place valuable ores at strategic locations to confuse anti-Xray plugins
- Make ore distribution appear more natural or follow specific patterns
- Create fake ore veins to distract from real mining locations

### Base Decoration
- Replace blocks around your base with more aesthetically pleasing ones
- Create hidden entrances by replacing specific blocks

### Testing
- Test building designs by replacing blocks without modifying the actual world
- Create temporary structures for testing purposes

## Notes

- The module only affects what the client sees, not the actual server world
- Changes are applied to chunk data as it's sent to the client
- The module works on a per-chunk basis for efficiency
- Coordinates are in world coordinates (not chunk-relative)
- Multiple replacements can be configured for the same location (last one wins)