package com.github.futa.command;

import com.github.futa.FutaPlugin;
import com.github.futa.config.EndGatewayConfig;
import com.github.futa.module.EndGateway;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;

import java.util.concurrent.TimeUnit;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.zenith.Globals.CONFIG;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.BlockPosArgument.blockPos;
import static com.zenith.command.brigadier.BlockPosArgument.getBlockPos;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class EndGatewayCommand extends Command {

    EndGatewayConfig gatewayConfig = FutaPlugin.PLUGIN_CONFIG.endGateway;

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
                .name("endgateway")
                .category(CommandCategory.MODULE)
                .description("""
                        Automatically enters end gateway portals.
                        
                        When enabled in the overworld, will automatically pathfind to and enter the configured end gateway portal.
                        
                        `gatewayPosition` -> coordinates of the end gateway portal
                        `detectionRadius` -> radius to search for gateway portal from configured position
                        `autoEnableInOverworld` -> automatically enable when entering overworld
                        `pathfindingTimeout` -> seconds to wait for pathfinding before giving up
                        """)
                .usageLines(
                        "on/off",
                        "gatewayPosition <x> <y> <z>",
                        "detectionRadius <radius>",
                        "autoEnableInOverworld <true/false>",
                        "pathfindingTimeout <seconds>"
                )
                .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("endgateway")
                .then(argument("toggle", toggle()).executes(c -> {
                    gatewayConfig.enabled = getToggle(c, "toggle");
                    MODULE.get(EndGateway.class).syncEnabledFromConfig();
                    c.getSource().getEmbed()
                            .title("End Gateway " + toggleStrCaps(gatewayConfig.enabled))
                            .primaryColor();
                }))
                .then(literal("gatewayPosition").then(argument("pos", blockPos()).executes(c -> {
                    gatewayConfig.gatewayPosition = getBlockPos(c, "pos");
                    c.getSource().getEmbed()
                            .title("Gateway Position Set");
                })))
                .then(literal("detectionRadius").then(argument("radius", integer(1, 200)).executes(c -> {
                    gatewayConfig.detectionRadius = getInteger(c, "radius");
                    c.getSource().getEmbed()
                            .title("Detection Radius Set");
                })))
                .then(literal("autoEnableInOverworld").then(argument("toggle", toggle()).executes(c -> {
                    gatewayConfig.autoEnableInOverworld = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                            .title("Auto Enable In Overworld " + (gatewayConfig.autoEnableInOverworld ? "Enabled" : "Disabled"));
                })))
                .then(literal("pathfindingTimeout").then(argument("seconds", integer(1, (int) TimeUnit.MINUTES.toSeconds(10))).executes(c -> {
                    gatewayConfig.pathfindingTimeoutSeconds = getInteger(c, "seconds");
                    c.getSource().getEmbed()
                            .title("Pathfinding Timeout Set");
                })));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        embed.addField("End Gateway", toggleStr(gatewayConfig.enabled))
                .addField("Gateway Position", "||" + (CONFIG.discord.reportCoords ? gatewayConfig.gatewayPosition : "Coords disabled") + "||")
                .addField("Detection Radius", gatewayConfig.detectionRadius + " blocks")
                .addField("Auto Enable In Overworld", toggleStr(gatewayConfig.autoEnableInOverworld))
                .addField("Pathfinding Timeout", gatewayConfig.pathfindingTimeoutSeconds + "s")
                .primaryColor();
    }
}
