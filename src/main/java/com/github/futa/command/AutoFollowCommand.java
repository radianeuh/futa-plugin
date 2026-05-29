package com.github.futa.command;

import com.github.futa.module.AutoFollow;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;

import java.util.List;

import static com.github.futa.FutaPlugin.PLUGIN_CONFIG;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

/**
 * AutoFollow 相关命令
 */
public class AutoFollowCommand extends Command {

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
                .name("autofollow")
                .aliases("af")
                .category(CommandCategory.MODULE)
                .description("""
                        控制自动跟随功能 - 自动跟随配置的玩家
                        """)
                .usageLines(
                        "on/off - 启用/禁用自动跟随",
                        "add <player> - 添加跟随目标玩家",
                        "remove <player> - 移除跟随目标玩家",
                        "list - 显示目标玩家列表",
                        "status - 显示当前状态"
                )
                .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("autofollow")
                // 基本开关命令
                .then(argument("toggle", toggle()).executes(c -> {
                    PLUGIN_CONFIG.autoFollow.enabled = getToggle(c, "toggle");
                    // 同步模块状态
                    MODULE.get(AutoFollow.class).syncEnabledFromConfig();
                    c.getSource().getEmbed()
                            .title("AutoFollow " + toggleStrCaps(PLUGIN_CONFIG.autoFollow.enabled));
                }))
                // 添加目标玩家
                .then(literal("add").then(argument("player", string()).executes(c -> {
                    String playerName = getString(c, "player");
                    addTargetPlayer(c, playerName);
                })))
                // 移除目标玩家
                .then(literal("remove").then(argument("player", string()).executes(c -> {
                    String playerName = getString(c, "player");
                    removeTargetPlayer(c, playerName);
                })))
                // 显示目标列表
                .then(literal("list").executes(c -> {
                    listTargetPlayers(c);
                }))
                // 显示状态
                .then(literal("status").executes(c -> {
                    showStatus(c);
                }));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        var config = PLUGIN_CONFIG.autoFollow;
        embed
                .primaryColor()
                .title("AutoFollow 状态")
                .addField("启用状态", toggleStr(config.enabled))
                .addField("目标玩家数量", String.valueOf(config.targetPlayers.size()))
                .addField("跟随距离", config.followDistance + " 格")
                .addField("最大跟随距离", config.maxFollowDistance + " 格")
                .addField("更新间隔", config.updateInterval + " tick")
                .addField("规避障碍", toggleStr(config.avoidObstacles))
                .addField("战斗暂停", toggleStr(config.stopInCombat))
                .addField("自动点击床", toggleStr(config.autoClickBed))
                .addField("床搜索半径", config.bedSearchRadius + " 格")
                .addField("床点击冷却", config.bedClickCooldownMs / 1000 + " 秒");

        // 如果有目标玩家，显示详细信息
        if (!config.targetPlayers.isEmpty()) {
            StringBuilder playersList = new StringBuilder();
            for (int i = 0; i < config.targetPlayers.size(); i++) {
                playersList.append(i + 1).append(". ").append(config.targetPlayers.get(i));
                if (i < config.targetPlayers.size() - 1) {
                    playersList.append("\n");
                }
            }
            embed.addField("目标玩家", playersList.toString());
        }

        // 获取模块状态信息
        try {
            AutoFollow module = MODULE.get(AutoFollow.class);
            embed.addField("模块状态", module.getFollowStatus());
        } catch (Exception e) {
            // 模块可能未加载，忽略错误
        }
    }

    private void addTargetPlayer(com.mojang.brigadier.context.CommandContext<CommandContext> context, String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            context.getSource().getEmbed()
                    .errorColor()
                    .title("添加失败")
                    .description("玩家名不能为空");
            return;
        }

        playerName = playerName.trim();

        if (PLUGIN_CONFIG.autoFollow.targetPlayers.contains(playerName)) {
            context.getSource().getEmbed()
                    .errorColor()
                    .title("添加失败")
                    .description("玩家 '" + playerName + "' 已在目标列表中");
            return;
        }

        PLUGIN_CONFIG.autoFollow.targetPlayers.add(playerName);
        context.getSource().getEmbed()
                .successColor()
                .title("添加成功")
                .description("已添加玩家 '" + playerName + "' 到跟随目标列表");
    }

    private void removeTargetPlayer(com.mojang.brigadier.context.CommandContext<CommandContext> context, String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            context.getSource().getEmbed()
                    .errorColor()
                    .title("移除失败")
                    .description("玩家名不能为空");
            return;
        }

        playerName = playerName.trim();

        if (PLUGIN_CONFIG.autoFollow.targetPlayers.remove(playerName)) {
            context.getSource().getEmbed()
                    .successColor()
                    .title("移除成功")
                    .description("已从跟随目标列表中移除玩家 '" + playerName + "'");
        } else {
            context.getSource().getEmbed()
                    .errorColor()
                    .title("移除失败")
                    .description("玩家 '" + playerName + "' 不在目标列表中");
        }
    }

    private void listTargetPlayers(com.mojang.brigadier.context.CommandContext<CommandContext> context) {
        List<String> targets = PLUGIN_CONFIG.autoFollow.targetPlayers;

        if (targets.isEmpty()) {
            context.getSource().getEmbed()
                    .primaryColor()
                    .title("目标玩家列表")
                    .description("当前没有配置跟随目标玩家\n\n使用 `/autofollow add <玩家名>` 添加目标");
            return;
        }

        StringBuilder description = new StringBuilder();
        for (int i = 0; i < targets.size(); i++) {
            description.append("**").append(i + 1).append(".** ").append(targets.get(i));
            if (i < targets.size() - 1) {
                description.append("\n");
            }
        }

        context.getSource().getEmbed()
                .primaryColor()
                .title("跟随目标玩家列表 (" + targets.size() + ")")
                .description(description.toString());
    }

    private void showStatus(com.mojang.brigadier.context.CommandContext<CommandContext> context) {
        defaultEmbed(context.getSource().getEmbed());
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
