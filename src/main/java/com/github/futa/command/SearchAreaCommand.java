package com.github.futa.command;

import com.github.futa.FutaPlugin;
import com.github.futa.config.SearchAreaConfig;
import com.github.futa.module.SearchAreaModule;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;

import static com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

/**
 * SearchAreaModule 命令类
 * <p>
 * 提供搜索区域模块的配置命令
 */
public class SearchAreaCommand extends Command {

    SearchAreaConfig config = FutaPlugin.PLUGIN_CONFIG.searchArea;

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
                .name("searchArea")
                .category(CommandCategory.MODULE)
                .description("""
                        SearchAreaModule - 自动区块加载/搜索模块
                        用于配合 ElytraFlyModule，通过预定义路径自动遍历区域加载区块。

                        支持两种模式：
                        - Rectangle: 矩形锯齿形遍历（需设置起始点和终点）
                        - Spiral: 螺旋扩展遍历（从当前位置向外扩展）

                        使用方法：
                        1. 设置搜索模式
                        2. 设置起始点/终点（矩形模式）
                        3. 启用模块
                        4. 启用 ElytraFlyModule 进行飞行
                        """)
                .usageLines(
                        "on/off",
                        "mode <Rectangle/Spiral>",
                        "start <x> <y> <z>",
                        "end <x> <y> <z>",
                        "gap <chunks>",
                        "saveName <name>",
                        "disconnect on/off",
                        "debug on/off"
                )
                .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("searchArea")
                .then(argument("toggle", toggle()).executes(c -> {
                    config.enabled = getToggle(c, "toggle");
                    MODULE.get(SearchAreaModule.class).syncEnabledFromConfig();
                    c.getSource().getEmbed()
                            .title("SearchAreaModule " + toggleStrCaps(config.enabled))
                            .primaryColor();
                    return OK;
                }))
                .then(literal("mode").then(argument("mode", string()).executes(c -> {
                    String mode = getString(c, "mode");
                    if (!mode.equals("Rectangle") && !mode.equals("Spiral")) {
                        c.getSource().getEmbed()
                                .title("错误")
                                .description("模式必须是 Rectangle 或 Spiral")
                                .errorColor();
                        return OK;
                    }
                    config.mode = mode;
                    if (mode.equals("Spiral")) {
                        MODULE.get(SearchAreaModule.class).createNewPathData();
                    }
                    c.getSource().getEmbed()
                            .title("搜索模式已设置为 " + mode)
                            .primaryColor();
                    return OK;
                })))
                .then(literal("start").then(argument("x", integer()).then(argument("y", integer()).then(argument("z", integer()).executes(c -> {
                    config.startX = getInteger(c, "x");
                    config.startY = getInteger(c, "y");
                    config.startZ = getInteger(c, "z");
                    c.getSource().getEmbed()
                            .title("起始点已设置为 (" + config.startX + ", " + config.startY + ", " + config.startZ + ")")
                            .primaryColor();
                    return OK;
                })))))
                .then(literal("end").then(argument("x", integer()).then(argument("y", integer()).then(argument("z", integer()).executes(c -> {
                    config.endX = getInteger(c, "x");
                    config.endY = getInteger(c, "y");
                    config.endZ = getInteger(c, "z");
                    c.getSource().getEmbed()
                            .title("终点已设置为 (" + config.endX + ", " + config.endY + ", " + config.endZ + ")")
                            .primaryColor();
                    return OK;
                })))))
                .then(literal("gap").then(argument("chunks", integer(1, 50)).executes(c -> {
                    config.pathGap = getInteger(c, "chunks");
                    c.getSource().getEmbed()
                            .title("路径间隔已设置为 " + config.pathGap + " chunks (" + (config.pathGap * 16) + " 格)")
                            .primaryColor();
                    return OK;
                })))
                .then(literal("saveName").then(argument("name", string()).executes(c -> {
                    config.saveName = getString(c, "name");
                    c.getSource().getEmbed()
                            .title("保存名称已设置为 " + config.saveName)
                            .primaryColor();
                    return OK;
                })))
                .then(literal("disconnect").then(argument("toggle", toggle()).executes(c -> {
                    config.disconnectOnCompletion = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                            .title("完成断开连接 " + toggleStrCaps(config.disconnectOnCompletion))
                            .description(config.disconnectOnCompletion
                                    ? "启用：搜索完成后自动断开连接"
                                    : "禁用：搜索完成后保持连接")
                            .primaryColor();
                    return OK;
                })))
                .then(literal("debug").then(argument("toggle", toggle()).executes(c -> {
                    config.debug = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                            .title("调试模式 " + toggleStrCaps(config.debug))
                            .primaryColor();
                    return OK;
                })));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        embed.title("SearchAreaModule 配置")
                .addField("Enabled", toggleStr(config.enabled))
                .addField("Mode (模式)", config.mode)
                .addField("Start (起始点)", "(" + config.startX + ", " + config.startY + ", " + config.startZ + ")")
                .addField("End (终点)", "(" + config.endX + ", " + config.endY + ", " + config.endZ + ")")
                .addField("Gap (间隔)", config.pathGap + " chunks (" + (config.pathGap * 16) + " 格)")
                .addField("Save Name (保存名称)", config.saveName)
                .addField("Disconnect On Completion (完成断开)", toggleStr(config.disconnectOnCompletion))
                .addField("Debug (调试)", toggleStr(config.debug))
                .primaryColor();
    }
}
