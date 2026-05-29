package com.github.futa.command;

import com.github.futa.module.ChatLogModule;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.github.futa.FutaPlugin.PLUGIN_CONFIG;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

/**
 * 聊天记录相关命令
 */
public class ChatLogCommand extends Command {

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
                .name("chatlog")
                .aliases("cl")
                .category(CommandCategory.MODULE)
                .description("""
                        控制聊天记录功能 - 记录公共聊天到日志文件
                        """)
                .usageLines(
                        "on/off - 启用/禁用聊天记录",
                        "status - 显示当前状态",
                        "clear - 清空日志文件",
                        "path - 显示日志文件路径",
                        "view - 查看最近的日志内容"
                )
                .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("chatlog")
                // 基本开关命令
                .then(argument("toggle", toggle()).executes(c -> {
                    PLUGIN_CONFIG.chatLog.enabled = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                            .title("ChatLog " + toggleStrCaps(PLUGIN_CONFIG.chatLog.enabled));
                }))
                // 显示状态
                .then(literal("status").executes(c -> {
                    showStatus(c);
                }))
                // 清空日志文件
                .then(literal("clear").executes(c -> {
                    clearLog(c);
                }))
                // 显示日志文件路径
                .then(literal("path").executes(c -> {
                    showLogPath(c);
                }))
                // 查看最近的日志内容
                .then(literal("view").executes(c -> {
                    viewRecentLogs(c);
                }));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        var config = PLUGIN_CONFIG.chatLog;
        embed
                .primaryColor()
                .title("ChatLog 状态")
                .addField("启用状态", toggleStr(config.enabled))
                .addField("记录玩家消息", toggleStr(config.logPlayerMessages))
                .addField("记录系统消息", toggleStr(config.logSystemMessages))
                .addField("包含时间戳", toggleStr(config.includeTimestamp))
                .addField("时间格式", config.dateFormat)
                .addField("最大文件大小", config.maxLogFileSizeMB + " MB")
                .addField("自动轮转日志", toggleStr(config.autoRotateLogs));
    }

    private void showStatus(com.mojang.brigadier.context.CommandContext<CommandContext> context) {
        defaultEmbed(context.getSource().getEmbed());
    }

    private void clearLog(com.mojang.brigadier.context.CommandContext<CommandContext> context) {
        try {
            ChatLogModule.clearLogFile();
            context.getSource().getEmbed()
                    .successColor()
                    .title("日志清空成功")
                    .description("聊天日志文件已清空");
        } catch (Exception e) {
            context.getSource().getEmbed()
                    .errorColor()
                    .title("日志清空失败")
                    .description("清空日志文件时出错: " + e.getMessage());
        }
    }

    private void showLogPath(com.mojang.brigadier.context.CommandContext<CommandContext> context) {
        String logPath = ChatLogModule.getLogFilePath();
        File logFile = new File(logPath);

        String fileSize = "不存在";
        if (logFile.exists()) {
            long bytes = logFile.length();
            if (bytes < 1024) {
                fileSize = bytes + " B";
            } else if (bytes < 1024 * 1024) {
                fileSize = (bytes / 1024) + " KB";
            } else {
                fileSize = (bytes / (1024 * 1024)) + " MB";
            }
        }

        context.getSource().getEmbed()
                .primaryColor()
                .title("日志文件信息")
                .addField("文件路径", logPath)
                .addField("文件大小", fileSize)
                .addField("文件状态", logFile.exists() ? "存在" : "不存在");
    }

    private void viewRecentLogs(com.mojang.brigadier.context.CommandContext<CommandContext> context) {
        try {
            String logPath = ChatLogModule.getLogFilePath();
            Path path = Paths.get(logPath);

            if (!Files.exists(path)) {
                context.getSource().getEmbed()
                        .primaryColor()
                        .title("最近日志")
                        .addField("", "日志文件不存在");
                return;
            }

            var lines = Files.readAllLines(path);
            if (lines.isEmpty()) {
                context.getSource().getEmbed()
                        .primaryColor()
                        .title("最近日志")
                        .addField("", "日志文件为空");
                return;
            }

            // 获取最后20行
            int startIndex = Math.max(0, lines.size() - 20);
            var recentLines = lines.subList(startIndex, lines.size());

            StringBuilder content = new StringBuilder();
            for (int i = 0; i < recentLines.size(); i++) {
                content.append(recentLines.get(i));
                if (i < recentLines.size() - 1) {
                    content.append("\n");
                }
            }

            context.getSource().getEmbed()
                    .primaryColor()
                    .title("最近日志 (最后 " + recentLines.size() + " 行)")
                    .addField("总行数", String.valueOf(lines.size()))
                    .addField("", "```\n" + content.toString() + "\n```");

        } catch (Exception e) {
            context.getSource().getEmbed()
                    .errorColor()
                    .title("查看日志失败")
                    .addField("", "读取日志文件时出错: " + e.getMessage());
        }
    }

    /**
     * 工具方法：获取 toggle 字符串
     */
    public static String toggleStr(boolean value) {
        return value ? "✅ 启用" : "❌ 禁用";
    }

    /**
     * 工具方法：获取 toggle 大写字符串
     */
    public static String toggleStrCaps(boolean value) {
        return value ? "ENABLED" : "DISABLED";
    }
}
