package com.github.futa.command;

import com.github.futa.module.FixedAngleView;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;

import static com.github.futa.FutaPlugin.PLUGIN_CONFIG;
import static com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg;
import static com.mojang.brigadier.arguments.DoubleArgumentType.getDouble;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class FixedAngleViewCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
                .name("fixedangle")
                .aliases("fa")
                .category(CommandCategory.MODULE)
                .description("""
                        Fixed angle view module that periodically sets camera to specific angles.

                        Automatically rotates camera view to specified yaw and pitch angles at configurable intervals.
                        Useful for maintaining consistent viewing direction for surveillance, recording, or other purposes.
                        Such as ender man look.

                        `enabled` - whether the module is enabled
                        `yaw` - horizontal rotation angle (-180 to 180 degrees)
                        `pitch` - vertical rotation angle (-90 to 90 degrees)
                        `intervalTicks` - how often to update camera position (in ticks)
                        """)
                .usageLines(
                        "on/off",
                        "yaw <degrees>",
                        "pitch <degrees>",
                        "interval <ticks>"
                )
                .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("fixedangle")
                .then(argument("toggle", toggle()).executes(c -> {
                    PLUGIN_CONFIG.fixedAngleView.enabled = getToggle(c, "toggle");
                    MODULE.get(FixedAngleView.class).syncEnabledFromConfig();
                    c.getSource().getEmbed()
                            .title("Fixed Angle View " + toggleStrCaps(PLUGIN_CONFIG.fixedAngleView.enabled))
                            .primaryColor();
                    return OK;
                }))
                .then(literal("yaw")
                        .then(argument("degrees", doubleArg(-180.0, 180.0)).executes(c -> {
                            double yaw = getDouble(c, "degrees");
                            PLUGIN_CONFIG.fixedAngleView.yaw = (float) yaw;
                            c.getSource().getEmbed()
                                    .title("Yaw Set")
                                    .description("Set yaw to: " + yaw + "°");
                            return OK;
                        })))
                .then(literal("pitch")
                        .then(argument("degrees", doubleArg(-90.0, 90.0)).executes(c -> {
                            double pitch = getDouble(c, "degrees");
                            PLUGIN_CONFIG.fixedAngleView.pitch = (float) pitch;
                            c.getSource().getEmbed()
                                    .title("Pitch Set")
                                    .description("Set pitch to: " + pitch + "°");
                            return OK;
                        })))
                .then(literal("interval")
                        .then(argument("ticks", integer(1, 1000)).executes(c -> {
                            int ticks = getInteger(c, "ticks");
                            PLUGIN_CONFIG.fixedAngleView.intervalTicks = ticks;
                            c.getSource().getEmbed()
                                    .title("Interval Set")
                                    .description("Set interval to: " + ticks + " ticks (" + (ticks / 20.0) + " seconds)");
                            return OK;
                        })));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        embed
                .addField("Fixed Angle View", toggleStr(PLUGIN_CONFIG.fixedAngleView.enabled))
                .addField("Yaw", PLUGIN_CONFIG.fixedAngleView.yaw + "°")
                .addField("Pitch", PLUGIN_CONFIG.fixedAngleView.pitch + "°")
                .addField("Interval", PLUGIN_CONFIG.fixedAngleView.intervalTicks + " ticks (" + (PLUGIN_CONFIG.fixedAngleView.intervalTicks / 20.0) + "s)")
                .primaryColor();
    }
}
