# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a ZenithProxy plugin called "ZenithDie" that provides death message logging and anti-stuck functionality for Minecraft 1.21.4. The project is built using Kotlin DSL for Gradle and targets Java 21.

## Build & Development Commands

### Building
- Build plugin: `./gradlew build` or `./gradlew shadowJar`
- Run tests: `./gradlew test`
- Clean build: `./gradlew clean`

### Development Setup
- The project uses the ZenithProxy plugin development SDK
- Dependencies are shaded into the final JAR using Shadow plugin
- Main dependencies: fastjson2, hutool-core

## Architecture

### Core Components

**PluginDie.java** - Main plugin entry point
- Implements `ZenithProxyPlugin` interface
- Registers configuration and modules
- Central access point for plugin configuration and logging

**DieConfig.java** - Configuration POJO
- Uses ZenithProxy's JSON configuration system
- Main config for death logging and anti-stuck features
- Nested configuration for AntiStuck module

**Modules System** - Plugin functionality organized into modules

### DeathLogger Module (`src/main/java/org/example/module/DeathLogger.java`)
- **Purpose**: Records player death messages with detailed information
- **Key Features**:
  - Listens to `DeathMessageChatEvent` and `SystemChatEvent`
  - Extracts structured data from death messages using custom parsing
  - Outputs to console and/or JSON file in `deaths/` directory
  - Configurable formatting and data inclusion options
- **Data Structure**: `DeathLogEntry` contains timestamp, victim, killer, weapon, coordinates, message details
- **File Format**: JSON array with incremental updates (not full file rewrite)

### AntiStuck Module (`src/main/java/org/example/module/AntiStuck.java`)
- **Purpose**: Prevents client from getting stuck due to excessive teleportation packets
- **Mechanism**: Monitors `ServerboundAcceptTeleportationPacket` frequency
- **Trigger**: Activates when 10+ teleport confirmations occur within 5 seconds
- **Action**: Uses Baritone integration to path to nearby safe position

### DieMessageParser (`src/main/java/org/example/DieMessageParser.java`)
- **Purpose**: Parses Minecraft death message components into structured data
- **Key Logic**: Extracts victim, killer, weapon info from Adventure TextComponents
- **Data Sources**: Uses hover events and translatable component arguments
- **Output**: `DeathMessageParseResult` with victim, Optional<Killer>, Optional<weapon>

### Event Handling
- Uses ZenithProxy's event system with `EventConsumer`
- Modules register event consumers in `registerEvents()` method
- Events include death messages, system chat, client ticks, and packet handling

### Configuration
- Configuration managed through ZenithProxy's JSON system
- Hot-reloadable without plugin restart
- Boolean toggles for enabling/disabling features
- Customizable output formatting and file options

## Important Notes

### Dependencies
- Uses ZenithProxy plugin SDK: `com.zenith:ZenithProxy:1.21.4-SNAPSHOT`
- Shaded dependencies: fastjson2 (JSON processing), hutool-core (utilities)
- Adventure Text Component API for message formatting

### File Structure
- Main code: `src/main/java/org/example/`
- Configuration auto-generated and saved as JSON
- Death logs saved to `deaths/` directory
- Templates in `templates/` for build-time code generation

### Integration Points
- Integrates with ZenithProxy's event bus (`EVENT_BUS`)
- Uses global caches (`CACHE`) for player/world state
- Baritone integration for movement in AntiStuck module
- Component serialization for message processing