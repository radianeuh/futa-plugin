package com.github.futa.command;

import com.github.futa.FutaPlugin;
import com.github.futa.config.AutoCrystalConfig;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;

import static com.mojang.brigadier.arguments.BoolArgumentType.bool;
import static com.mojang.brigadier.arguments.BoolArgumentType.getBool;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class AutoCrystalCommand extends Command {

    AutoCrystalConfig crystalConfig = FutaPlugin.PLUGIN_CONFIG.autoCrystal;

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
                .name("autocrystal")
                .category(CommandCategory.MODULE)
                .description("""
                        Automatically places and explodes crystals for PVP combat.
                        
                        Features:
                        🔸 Automatic crystal placement and explosion
                        🔸 Configurable target selection and range
                        🔸 Smart crystal management with cooldown tracking
                        🔸 Safe placement with obstacle detection
                        🔸 Health and armor based target prioritization
                        """)
                .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return literal("autocrystal")
                .then(literal("toggle")
                        .then(argument("enabled", toggle())
                                .executes(c -> {
                                    crystalConfig.enabled = getToggle(c, "enabled");
                                    return OK;
                                })))
                .then(literal("targetRange")
                        .then(argument("range", integer(1, 100))
                                .executes(c -> {
                                    crystalConfig.targetRange = getInteger(c, "range");
                                    return OK;
                                })))
                .then(literal("placeRange")
                        .then(argument("range", integer(1, 20))
                                .executes(c -> {
                                    crystalConfig.placeRange = getInteger(c, "range");
                                    return OK;
                                })))
                .then(literal("minHealth")
                        .then(argument("health", integer(1, 20))
                                .executes(c -> {
                                    crystalConfig.minHealth = getInteger(c, "minHealth");
                                    return OK;
                                })))
                .then(literal("maxHealth")
                        .then(argument("health", integer(1, 20))
                                .executes(c -> {
                                    crystalConfig.maxHealth = getInteger(c, "maxHealth");
                                    return OK;
                                })))
                .then(literal("delay")
                        .then(argument("ticks", integer(1, 100))
                                .executes(c -> {
                                    crystalConfig.delayTicks = getInteger(c, "ticks");
                                    return OK;
                                })))
                .then(literal("safeMode")
                        .then(argument("enabled", bool())
                                .executes(c -> {
                                    crystalConfig.safeMode = getBool(c, "enabled");
                                    return OK;
                                })))
                .then(literal("debug")
                        .then(argument("enabled", bool())
                                .executes(c -> {
                                    crystalConfig.debugMode = getBool(c, "enabled");
                                    return OK;
                                })));
    }

    public void postPopulate(Embed builder) {
        builder.addField("AutoCrystal", crystalConfig.enabled ? "Enabled" : "Disabled", false)
               .addField("Target Range", crystalConfig.targetRange + " blocks", true)
               .addField("Place Range", crystalConfig.placeRange + " blocks", true)
               .addField("Health Range", crystalConfig.minHealth + "-" + crystalConfig.maxHealth + " hearts", true)
               .addField("Delay", crystalConfig.delayTicks + " ticks", true)
               .addField("Safe Mode", crystalConfig.safeMode ? "Enabled" : "Disabled", true)
               .addField("Debug Mode", crystalConfig.debugMode ? "Enabled" : "Disabled", true);
    }
}
