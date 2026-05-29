package com.github.futa.module;

import com.github.futa.BaseModule;
import com.github.futa.FutaPlugin;
import com.github.futa.config.VisualRangeLoggerConfig;
import com.github.rfresh2.EventConsumer;
import com.zenith.cache.data.entity.EntityPlayer;
import com.zenith.event.module.ServerPlayerInVisualRangeEvent;
import com.zenith.event.module.ServerPlayerLeftVisualRangeEvent;
import com.zenith.event.module.ServerPlayerLogoutInVisualRangeEvent;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.PLAYER_LISTS;

public class VisualRangeLogger extends BaseModule {

    private static final VisualRangeLoggerConfig CONFIG = FutaPlugin.PLUGIN_CONFIG.visualRangeLogger;
    private SimpleDateFormat dateFormatter;
    private BufferedWriter logWriter;
    private File logFile;

    @Override
    public boolean enabledSetting() {
        return CONFIG.enabled;
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
                of(ServerPlayerInVisualRangeEvent.class, this::handlePlayerEnter),
                of(ServerPlayerLeftVisualRangeEvent.class, this::handlePlayerLeave),
                of(ServerPlayerLogoutInVisualRangeEvent.class, this::handlePlayerLogout)
        );
    }

    @Override
    public void onEnable() {
        initializeLogger();
    }

    @Override
    public void onDisable() {
        closeLogger();
    }

    private void initializeLogger() {
        try {
            dateFormatter = new SimpleDateFormat(CONFIG.dateFormat);

            // Create log file if it doesn't exist
            logFile = new File(CONFIG.logFilePath);
            if (!logFile.exists()) {
                logFile.createNewFile();
            }

            // Check file size and rotate if necessary
            if (CONFIG.autoRotateLogs && logFile.length() > CONFIG.maxLogFileSizeMB * 1024L * 1024L) {
                rotateLogFile();
            }

            // Open file writer
            FileWriter fileWriter = new FileWriter(logFile, CONFIG.appendToFile);
            logWriter = new BufferedWriter(fileWriter);

            info("VisualRangeLogger initialized, logging to: " + CONFIG.logFilePath);
        } catch (IOException e) {
            error("Failed to initialize VisualRangeLogger: " + e.getMessage());
        }
    }

    private void rotateLogFile() {
        try {
            Path originalPath = Paths.get(CONFIG.logFilePath);
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            Path backupPath = Paths.get(CONFIG.logFilePath + "." + timestamp + ".bak");

            Files.move(originalPath, backupPath);
            info("Rotated log file to: " + backupPath);
        } catch (IOException e) {
            error("Failed to rotate log file: " + e.getMessage());
        }
    }

    private void closeLogger() {
        try {
            if (logWriter != null) {
                logWriter.close();
                logWriter = null;
            }
        } catch (IOException e) {
            error("Failed to close VisualRangeLogger: " + e.getMessage());
        }
    }

    private void logEvent(String eventType, PlayerListEntry playerEntry, EntityPlayer playerEntity) {
        if (logWriter == null) {
            return;
        }

        try {
            StringBuilder logLine = new StringBuilder();

            // Add timestamp if enabled
            if (CONFIG.logTimestamp) {
                logLine.append("[").append(dateFormatter.format(new Date())).append("] ");
            }

            // Add event type
            logLine.append(eventType).append(": ");

            // Add player name
            logLine.append(playerEntry.getName());

            // Add coordinates if enabled
            if (CONFIG.logCoordinates) {
                logLine.append(" [").append(String.format("%.1f", playerEntity.getX()))
                        .append(", ").append(String.format("%.1f", playerEntity.getY()))
                        .append(", ").append(String.format("%.1f", playerEntity.getZ()))
                        .append("]");
            }

            // Add distance if enabled
            if (CONFIG.logDistance) {
                double distance = calculateDistance(playerEntity);
                logLine.append(" (distance: ").append(String.format("%.1f", distance)).append(" blocks)");
            }

            logLine.append("\n");

            logWriter.write(logLine.toString());
            logWriter.flush();

            // Check file size after writing
            if (CONFIG.autoRotateLogs && logFile.length() > CONFIG.maxLogFileSizeMB * 1024L * 1024L) {
                closeLogger();
                rotateLogFile();
                initializeLogger();
            }

        } catch (IOException e) {
            error("Failed to write to log file: " + e.getMessage());
        }
    }

    private double calculateDistance(EntityPlayer playerEntity) {
        var player = CACHE.getPlayerCache().getThePlayer();
        if (player == null) {
            return 0;
        }

        double dx = playerEntity.getX() - player.getX();
        double dy = playerEntity.getY() - player.getY();
        double dz = playerEntity.getZ() - player.getZ();

        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private boolean shouldIgnorePlayer(PlayerListEntry playerEntry) {
        if (CONFIG.ignoreFriends) {
            playerEntry.getProfileId();
            return PLAYER_LISTS.getFriendsList().contains(playerEntry.getProfileId());
        }
        return false;
    }

    private void handlePlayerEnter(ServerPlayerInVisualRangeEvent event) {
        if (!CONFIG.logPlayerEnter) {
            return;
        }

        if (shouldIgnorePlayer(event.playerEntry())) {
            return;
        }

        logEvent("PLAYER_ENTER", event.playerEntry(), event.playerEntity());
        debug("Logged player enter: " + event.playerEntry().getName());
    }

    private void handlePlayerLeave(ServerPlayerLeftVisualRangeEvent event) {
        if (!CONFIG.logPlayerLeave) {
            return;
        }

        if (shouldIgnorePlayer(event.playerEntry())) {
            return;
        }

        logEvent("PLAYER_LEAVE", event.playerEntry(), event.playerEntity());
        debug("Logged player leave: " + event.playerEntry().getName());
    }

    private void handlePlayerLogout(ServerPlayerLogoutInVisualRangeEvent event) {
        if (!CONFIG.logPlayerLogout) {
            return;
        }

        if (shouldIgnorePlayer(event.playerEntry())) {
            return;
        }

        logEvent("PLAYER_LOGOUT", event.playerEntry(), event.playerEntity());
        debug("Logged player logout: " + event.playerEntry().getName());
    }
}
