package com.github.futa.command;

import com.github.futa.FutaPlugin;
import com.github.futa.config.VisualRangeLoggerConfig;
import com.github.futa.module.VisualRangeLogger;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;

import java.io.File;
import java.util.List;

import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class VisualRangeLoggerCommand extends Command {

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
                .name("visualrangelogger")
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
                        "status",
                        "clear",
                        "view"
                )
                .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return literal("visualrangelogger")
                .then(argument("toggle", toggle()).executes(c -> {
                    FutaPlugin.PLUGIN_CONFIG.visualRangeLogger.enabled = getToggle(c, "toggle");
                    MODULE.get(VisualRangeLogger.class).syncEnabledFromConfig();
                    c.getSource().getEmbed()
                            .title("VisualRangeLogger " + toggleStrCaps(FutaPlugin.PLUGIN_CONFIG.visualRangeLogger.enabled))
                            .primaryColor();
                }))
                .then(literal("status").executes(c -> {
                    VisualRangeLoggerConfig config = FutaPlugin.PLUGIN_CONFIG.visualRangeLogger;
                    StringBuilder status = new StringBuilder();
                    status.append("VisualRangeLogger Status:\n");
                    status.append("  Enabled: ").append(config.enabled).append("\n");
                    status.append("  Log Player Enter: ").append(config.logPlayerEnter).append("\n");
                    status.append("  Log Player Leave: ").append(config.logPlayerLeave).append("\n");
                    status.append("  Log Player Logout: ").append(config.logPlayerLogout).append("\n");
                    status.append("  Log Coordinates: ").append(config.logCoordinates).append("\n");
                    status.append("  Log Distance: ").append(config.logDistance).append("\n");
                    status.append("  Ignore Friends: ").append(config.ignoreFriends).append("\n");
                    status.append("  Log File: ").append(config.logFilePath).append("\n");

                    // Check if log file exists and show size
                    File logFile = new File(config.logFilePath);
                    if (logFile.exists()) {
                        long fileSizeKB = logFile.length() / 1024;
                        status.append("  Log File Size: ").append(fileSizeKB).append(" KB");
                    } else {
                        status.append("  Log File: Not created yet");
                    }

                    c.getSource().getEmbed()
                            .title(status.toString())
                            .primaryColor();
                }))

                .then(literal("clear").executes(c -> {
                    VisualRangeLoggerConfig config = FutaPlugin.PLUGIN_CONFIG.visualRangeLogger;
                    File logFile = new File(config.logFilePath);
                    if (logFile.exists()) {
                        if (logFile.delete()) {
                        } else {
                        }
                    } else {
                    }
                }))
                .then(literal("view").executes(c -> {
                    VisualRangeLoggerConfig config = FutaPlugin.PLUGIN_CONFIG.visualRangeLogger;
                    File logFile = new File(config.logFilePath);
                    if (logFile.exists()) {
                        try {
                            List<String> lines = java.nio.file.Files.readAllLines(logFile.toPath());
                            int linesToShow = Math.min(20, lines.size());
                            if (linesToShow > 0) {
                                StringBuilder output = new StringBuilder("Last ").append(linesToShow).append(" log entries:\n");
                                for (int i = lines.size() - linesToShow; i < lines.size(); i++) {
                                    output.append(lines.get(i)).append("\n");
                                }


                                c.getSource().getEmbed()
                                        .title(output.toString())
                                        .primaryColor();
                            } else {
                            }
                        } catch (Exception e) {
                        }
                    } else {
                    }
                }));
    }
}
