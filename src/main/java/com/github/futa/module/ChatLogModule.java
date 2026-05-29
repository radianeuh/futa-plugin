package com.github.futa.module;

import com.github.futa.BaseModule;
import com.github.rfresh2.EventConsumer;
import com.zenith.event.chat.PublicChatEvent;

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

/**
 * 聊天记录模块 - 监听PublicChatEvent并记录到日志文件
 */
public class ChatLogModule extends BaseModule {

    private static final String LOG_DIR = "logs";
    private static final String LOG_FILE = "chat.log";
    private BufferedWriter logWriter;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Override
    public boolean enabledSetting() {
        return PLUGIN_CONFIG.chatLog.enabled;
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
                of(PublicChatEvent.class, this::onPublicChat)
        );
    }

    @Override
    public void onEnable() {
        super.onEnable();
        initializeLogWriter();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        closeLogWriter();
    }

    private void onPublicChat(PublicChatEvent event) {
        if (!PLUGIN_CONFIG.chatLog.enabled) {
            return;
        }

        try {
            String senderName = event.sender() != null ? event.sender().getName() : "Unknown";
            String message = event.message();
            String timestamp = dateFormat.format(new Date());

            String logEntry = String.format("[%SimpleCache] %SimpleCache: %SimpleCache", timestamp, senderName, message);

            writeLog(logEntry);

        } catch (Exception e) {
            System.err.println("Error writing chat log: " + e.getMessage());
        }
    }

    private void initializeLogWriter() {
        try {
            Path logDirPath = Paths.get(LOG_DIR);
            if (!Files.exists(logDirPath)) {
                Files.createDirectories(logDirPath);
            }

            File logFile = new File(LOG_DIR, LOG_FILE);
            boolean fileExists = logFile.exists();

            this.logWriter = new BufferedWriter(new FileWriter(logFile, true));

            if (!fileExists) {
                writeLog("=== Chat Log Started ===");
            } else {
                writeLog("");
                writeLog("=== Chat Log Resumed ===");
            }

            info("Chat log initialized: " + logFile.getAbsolutePath());

        } catch (IOException e) {
            System.err.println("Failed to initialize chat log writer: " + e.getMessage());
        }
    }

    private void writeLog(String message) {
        if (logWriter != null) {
            try {
                logWriter.write(message);
                logWriter.newLine();
                logWriter.flush();
            } catch (IOException e) {
                System.err.println("Error writing to chat log: " + e.getMessage());
            }
        }
    }

    private void closeLogWriter() {
        if (logWriter != null) {
            try {
                writeLog("=== Chat Log Stopped ===");
                logWriter.close();
                logWriter = null;
                info("Chat log writer closed");
            } catch (IOException e) {
                System.err.println("Error closing chat log writer: " + e.getMessage());
            }
        }
    }

    /**
     * 获取日志文件路径
     */
    public static String getLogFilePath() {
        return Paths.get(LOG_DIR, LOG_FILE).toAbsolutePath().toString();
    }

    /**
     * 清空日志文件
     */
    public static void clearLogFile() {
        try {
            File logFile = new File(LOG_DIR, LOG_FILE);
            if (logFile.exists()) {
                BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, false));
                writer.write("=== Chat Log Cleared ===");
                writer.newLine();
                writer.close();
            }
        } catch (IOException e) {
            System.err.println("Error clearing chat log: " + e.getMessage());
        }
    }
}
