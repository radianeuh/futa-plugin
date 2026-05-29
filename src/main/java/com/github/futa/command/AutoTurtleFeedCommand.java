package com.github.futa.command;

import com.github.futa.FutaPlugin;
import com.github.futa.config.AutoTurtleFeedConfig;
import com.github.futa.module.AutoTurtleFeedModule;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;

import static com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg;
import static com.mojang.brigadier.arguments.DoubleArgumentType.getDouble;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class AutoTurtleFeedCommand extends Command {

    AutoTurtleFeedConfig config = FutaPlugin.PLUGIN_CONFIG.autoTurtleFeed;

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
                .name("autoturtlefeed")
                .category(CommandCategory.MODULE)
                .description("""
                        Automatically feeds nearby turtles with seagrass.
                        
                        The module will search for unfed turtles within the configured distance,
                        switch to seagrass in your inventory, and right-click to feed them.
                        Each turtle is marked as fed for 10 minutes (configurable) to prevent
                        repeated feeding.
                        
                        Requirements:
                        - Seagrass must be in your inventory
                        - Turtles must be within the configured range
                        - You must have a clear line of sight to the turtle
                        """)
                .usageLines(
                        "on/off",
                        "maxDistance <blocks>",
                        "cooldownMinutes <minutes>",
                        "debug <true/false>"
                )
                .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("autoturtlefeed")
                .then(argument("toggle", toggle()).executes(c -> {
                    config.enabled = getToggle(c, "toggle");
                    MODULE.get(AutoTurtleFeedModule.class).syncEnabledFromConfig();
                    c.getSource().getEmbed()
                            .title("Auto Turtle Feed " + toggleStrCaps(config.enabled))
                            .primaryColor();
                    return OK;
                }))
                .then(literal("maxDistance").then(argument("blocks", doubleArg(1.0, 50.0)).executes(c -> {
                    config.maxDistance = getDouble(c, "blocks");
                    c.getSource().getEmbed()
                            .title("Max Distance Set")
                            .description("Maximum interaction distance set to " + config.maxDistance + " blocks");
                    return OK;
                })))
                .then(literal("cooldownMinutes").then(argument("minutes", integer(1, 60)).executes(c -> {
                    config.feedCooldownMinutes = getInteger(c, "minutes");
                    c.getSource().getEmbed()
                            .title("Cooldown Set")
                            .description("Feed cooldown set to " + config.feedCooldownMinutes + " minutes");
                    return OK;
                })))
                .then(literal("debug").then(argument("toggle", toggle()).executes(c -> {
                    config.debugMode = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                            .title("Debug Mode " + (config.debugMode ? "Enabled" : "Disabled"));
                    return OK;
                })));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        embed.title("Auto Turtle Feed Configuration")
                .addField("Enabled", toggleStr(config.enabled))
                .addField("Max Distance", config.maxDistance + " blocks")
                .addField("Feed Cooldown", config.feedCooldownMinutes + " minutes")
                .addField("Debug Mode", toggleStr(config.debugMode))
                .primaryColor();
    }
}
