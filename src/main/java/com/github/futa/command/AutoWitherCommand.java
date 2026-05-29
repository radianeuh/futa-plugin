package com.github.futa.command;

import com.github.futa.FutaPlugin;
import com.github.futa.config.AutoWitherConfig;
import com.github.futa.module.AutoWitherModule;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;
import com.zenith.mc.block.BlockPos;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.zenith.Globals.CONFIG;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.BlockPosArgument.blockPos;
import static com.zenith.command.brigadier.BlockPosArgument.getBlockPos;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class AutoWitherCommand extends Command {

    AutoWitherConfig witherConfig = FutaPlugin.PLUGIN_CONFIG.autoWither;

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
                .name("autowither")
                .category(CommandCategory.MODULE)
                .description("""
                        Automatically spawns withers at configured positions.

                        The module will automatically place soul sand and wither skeleton skulls
                        in a T-shape pattern to spawn withers. It will check the current number
                        of withers in the world and stop spawning when the maximum limit is reached.

                        Positions are cycled through in order, and the module will wait for withers
                        to despawn before continuing to spawn new ones.
                        """)
                .usageLines(
                        "on/off",
                        "addPosition <x> <y> <z>",
                        "removePosition <index>",
                        "listPositions",
                        "clearPositions",
                        "soulSandChest <x> <y> <z>",
                        "minSoulSand <count>",
                        "maxWithers <count>",
                        "resetRound",
                        "actionDelay <ticks>",
                        "checkInterval <ticks>",
                        "debug <true/false>"
                )
                .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("autowither")
                .then(argument("toggle", toggle()).executes(c -> {
                    witherConfig.enabled = getToggle(c, "toggle");
                    MODULE.get(AutoWitherModule.class).syncEnabledFromConfig();
                    c.getSource().getEmbed()
                            .title("Auto Wither " + toggleStrCaps(witherConfig.enabled))
                            .primaryColor();
                    return OK;
                }))
                .then(literal("addPosition").then(argument("pos", blockPos()).executes(c -> {
                    BlockPos pos = getBlockPos(c, "pos");
                    witherConfig.witherPositions.add(pos);
                    c.getSource().getEmbed()
                            .title("Position Added")
                            .description("Added wither spawn position at " + pos);
                    return OK;
                })))
                .then(literal("removePosition").then(argument("index", integer(0, 100)).executes(c -> {
                    int index = getInteger(c, "index");
                    if (index >= 0 && index < witherConfig.witherPositions.size()) {
                        BlockPos removed = witherConfig.witherPositions.remove(index);
                        c.getSource().getEmbed()
                                .title("Position Removed")
                                .description("Removed position at index " + index + ": " + removed);
                    } else {
                        c.getSource().getEmbed()
                                .title("Invalid Index")
                                .description("Index " + index + " is out of range. Valid range: 0-" + (witherConfig.witherPositions.size() - 1))
                                .errorColor();
                    }
                    return OK;
                })))
                .then(literal("listPositions").executes(c -> {
                    StringBuilder sb = new StringBuilder();
                    if (witherConfig.witherPositions.isEmpty()) {
                        sb.append("No positions configured.");
                    } else {
                        sb.append("Configured positions (").append(witherConfig.witherPositions.size()).append("):\n");
                        for (int i = 0; i < witherConfig.witherPositions.size(); i++) {
                            BlockPos pos = witherConfig.witherPositions.get(i);
                            sb.append(i).append(": ").append(pos.toString());
                            if (i == AutoWitherModule.currentIndex) {
                                sb.append(" (current)");
                            }
                            sb.append("\n");
                        }
                    }
                    c.getSource().getEmbed()
                            .title("Wither Positions")
                            .description(sb.toString());
                    return OK;
                }))
                .then(literal("clearPositions").executes(c -> {
                    int count = witherConfig.witherPositions.size();
                    witherConfig.witherPositions.clear();
                    AutoWitherModule.currentIndex = 0;
                    c.getSource().getEmbed()
                            .title("Positions Cleared")
                            .description("Cleared " + count + " positions");
                    return OK;
                }))
                .then(literal("soulSandChest").then(argument("pos", blockPos()).executes(c -> {
                    BlockPos pos = getBlockPos(c, "pos");
                    witherConfig.soulSandChest = pos;
                    c.getSource().getEmbed()
                            .title("Soul Sand Chest Set")
                            .description("Soul sand chest position set to " + pos);
                    return OK;
                })))
                .then(literal("minSoulSand").then(argument("count", integer(1, 64)).executes(c -> {
                    witherConfig.minSoulSand = getInteger(c, "count");
                    c.getSource().getEmbed()
                            .title("Min Soul Sand Set")
                            .description("Minimum soul sand count set to " + witherConfig.minSoulSand);
                    return OK;
                })))
                .then(literal("resetRound").executes(c -> {
                    int previousRound = AutoWitherModule.currentRound;
                    AutoWitherModule.currentRound = 0;
                    c.getSource().getEmbed()
                            .title("Round Counter Reset")
                            .description("Round counter reset from " + previousRound + " to 0");
                    return OK;
                }))
                .then(literal("maxWithers").then(argument("count", integer(1, 20)).executes(c -> {
                    witherConfig.maxWithers = getInteger(c, "count");
                    c.getSource().getEmbed()
                            .title("Max Withers Set")
                            .description("Maximum withers set to " + witherConfig.maxWithers);
                    return OK;
                })))
                .then(literal("actionDelay").then(argument("ticks", integer(1, 20)).executes(c -> {
                    witherConfig.actionDelay = getInteger(c, "ticks");
                    c.getSource().getEmbed()
                            .title("Action Delay Set")
                            .description("Action delay set to " + witherConfig.actionDelay + " ticks");
                    return OK;
                })))
                .then(literal("checkInterval").then(argument("ticks", integer(1, 100)).executes(c -> {
                    witherConfig.checkInterval = getInteger(c, "ticks");
                    c.getSource().getEmbed()
                            .title("Check Interval Set")
                            .description("Check interval set to " + witherConfig.checkInterval + " ticks");
                    return OK;
                })))
                .then(literal("debug").then(argument("toggle", toggle()).executes(c -> {
                    witherConfig.debugMode = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                            .title("Debug Mode " + (witherConfig.debugMode ? "Enabled" : "Disabled"));
                    return OK;
                })));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        StringBuilder positionsText = new StringBuilder();
        if (witherConfig.witherPositions.isEmpty()) {
            positionsText.append("No positions configured");
        } else {
            positionsText.append(witherConfig.witherPositions.size()).append(" positions configured");
            if (CONFIG.discord.reportCoords) {
                positionsText.append("\nUse `listPositions` command to view details");
            } else {
                positionsText.append("\nCoordinates reporting disabled");
            }
        }

        embed.title("Auto Wither Configuration")
                .addField("Enabled", toggleStr(witherConfig.enabled))
                .addField("Current Index", String.valueOf(AutoWitherModule.currentIndex))
                .addField("Current Round", String.valueOf(AutoWitherModule.currentRound))
                .addField("Positions", positionsText.toString())
                .addField("Soul Sand Chest", "||" + (CONFIG.discord.reportCoords ? witherConfig.soulSandChest : "Coords disabled") + "||")
                .addField("Min Soul Sand", String.valueOf(witherConfig.minSoulSand))
                .addField("Max Withers", String.valueOf(witherConfig.maxWithers))
                .addField("Action Delay", witherConfig.actionDelay + " ticks")
                .addField("Check Interval", witherConfig.checkInterval + " ticks")
                .addField("Debug Mode", toggleStr(witherConfig.debugMode))
                .primaryColor();
    }

}
