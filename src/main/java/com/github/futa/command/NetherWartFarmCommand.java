package com.github.futa.command;

import com.github.futa.FutaPlugin;
import com.github.futa.config.NetherWartFarmConfig;
import com.github.futa.module.NetherWartFarmModule;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.zenith.Globals.CONFIG;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.BlockPosArgument.blockPos;
import static com.zenith.command.brigadier.BlockPosArgument.getBlockPos;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class NetherWartFarmCommand extends Command {

    NetherWartFarmConfig farmConfig = FutaPlugin.PLUGIN_CONFIG.netherWartFarm;

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
                .name("netherwartfarm")
                .category(CommandCategory.MODULE)
                .description("""
                        Automatically farms nether warts within a specified range.

                        Searches for soul sand within 32 blocks horizontally and 2 blocks vertically.
                        Plants nether warts on empty soul sand and harvests mature ones.
                        Automatically switches to hoes for harvesting or uses hands if no hoe available.

                        The module will continuously search, plant, harvest, and store nether warts.
                        """)
                .usageLines(
                        "on/off",
                        "storageChest <x> <y> <z>",
                        "searchRadius <radius>",
                        "searchYRange <range>",
                        "actionDelay <ticks>",
                        "restDuration <ticks>",
                        "avoidOther <true/false>",
                        "preferredPlanting <true/false>",
                        "debug <true/false>"
                )
                .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("netherwartfarm")
                .then(argument("toggle", toggle()).executes(c -> {
                    farmConfig.enabled = getToggle(c, "toggle");
                    MODULE.get(NetherWartFarmModule.class).syncEnabledFromConfig();
                    c.getSource().getEmbed()
                            .title("Nether Wart Farm " + toggleStrCaps(farmConfig.enabled))
                            .primaryColor();
                    return OK;
                }))
                .then(literal("storageChest").then(argument("pos", blockPos()).executes(c -> {
                    farmConfig.storageChest = getBlockPos(c, "pos");
                    c.getSource().getEmbed()
                            .title("Storage Chest Set")
                            .description("Storage chest position updated");
                    return OK;
                })))
                .then(literal("searchRadius").then(argument("radius", integer(1, 64)).executes(c -> {
                    farmConfig.searchRadius = getInteger(c, "radius");
                    c.getSource().getEmbed()
                            .title("Search Radius Set")
                            .description("Search radius set to " + farmConfig.searchRadius + " blocks");
                    return OK;
                })))
                .then(literal("searchYRange").then(argument("range", integer(1, 10)).executes(c -> {
                    farmConfig.searchYRange = getInteger(c, "range");
                    c.getSource().getEmbed()
                            .title("Y Range Set")
                            .description("Vertical search range set to " + farmConfig.searchYRange + " blocks");
                    return OK;
                })))
                .then(literal("actionDelay").then(argument("ticks", integer(1, 20)).executes(c -> {
                    farmConfig.actionDelay = getInteger(c, "ticks");
                    c.getSource().getEmbed()
                            .title("Action Delay Set")
                            .description("Action delay set to " + farmConfig.actionDelay + " ticks");
                    return OK;
                })))
                .then(literal("restDuration").then(argument("ticks", integer(0, 600)).executes(c -> {
                    farmConfig.restDuration = getInteger(c, "ticks");
                    c.getSource().getEmbed()
                            .title("Rest Duration Set")
                            .description("Rest duration set to " + farmConfig.restDuration + " ticks");
                    return OK;
                })))
                .then(literal("debug").then(argument("toggle", toggle()).executes(c -> {
                    farmConfig.debugMode = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                            .title("Debug Mode " + (farmConfig.debugMode ? "Enabled" : "Disabled"));
                    return OK;
                })))
                .then(literal("avoidOther").then(argument("toggle", toggle()).executes(c -> {
                    farmConfig.avoidOther = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                            .title("avoidOther Mode " + (farmConfig.avoidOther ? "Enabled" : "Disabled"));
                    return OK;
                })))
                .then(literal("preferredPlanting").then(argument("toggle", toggle()).executes(c -> {
                    farmConfig.preferredPlanting = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                            .title("Preferred Planting " + (farmConfig.preferredPlanting ? "Enabled" : "Disabled"));
                    return OK;
                })));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        embed.title("Nether Wart Farm Configuration")
                .addField("Enabled", toggleStr(farmConfig.enabled))
                .addField("Storage Chest", "||" + (CONFIG.discord.reportCoords ? farmConfig.storageChest : "Coords disabled") + "||")
                .addField("Search Radius", farmConfig.searchRadius + " blocks")
                .addField("Search Y Range", farmConfig.searchYRange + " blocks")
                .addField("Action Delay", farmConfig.actionDelay + " ticks")
                .addField("Rest Duration", farmConfig.restDuration + " ticks")
                .addField("Avoid Other Players", toggleStr(farmConfig.avoidOther))
                .addField("Preferred Planting", toggleStr(farmConfig.preferredPlanting))
                .addField("Debug Mode", toggleStr(farmConfig.debugMode))
                .primaryColor();
    }
}
