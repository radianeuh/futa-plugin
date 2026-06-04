package com.github.futa.command;

import com.github.futa.FutaPlugin;
import com.github.futa.config.AutoOmenPlusConfig;
import com.github.futa.module.AutoOmenPlusModule;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class AutoOmenPlusCommand extends Command {

    AutoOmenPlusConfig config = FutaPlugin.PLUGIN_CONFIG.autoOmenPlus;

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
                .name("autoOmenPlus")
                .category(CommandCategory.MODULE)
                .description("""
                        AutoOmen Plus - 增强版自动喝不祥之瓶
                        
                        相比原版 AutoOmen，提供：
                        - before：提前量控制（效果剩余时间 < before tick 时触发续杯）
                        - one：保留一瓶（确保背包至少留一瓶）
                        - drinkTimeout：喝药超时保护（秒），防止卡死
                        - e：计时日志开关
                        - KillAura 自动联动
                        """)
                .usageLines(
                        "on/off",
                        "before <ticks>",
                        "one on/off",
                        "drinkTimeout <seconds>",
                        "debug on/off"
                )
                .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("autoOmenPlus")
                .then(argument("toggle", toggle()).executes(c -> {
                    config.enabled = getToggle(c, "toggle");
                    MODULE.get(AutoOmenPlusModule.class).syncEnabledFromConfig();
                    c.getSource().getEmbed()
                            .title("AutoOmen Plus " + toggleStrCaps(config.enabled))
                            .primaryColor();
                    return OK;
                }))
                .then(literal("before").then(argument("ticks", integer(0, 1000)).executes(c -> {
                    config.before = getInteger(c, "ticks");
                    c.getSource().getEmbed()
                            .title("提前量已设置为 " + config.before + " tick")
                            .primaryColor();
                    return OK;
                })))
                .then(literal("one").then(argument("toggle", toggle()).executes(c -> {
                    config.one = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                            .title("保留一瓶 " + toggleStrCaps(config.one))
                            .description(config.one ? "启用：确保背包至少留一瓶不祥之瓶" : "禁用：可以使用所有不祥之瓶")
                            .primaryColor();
                    return OK;
                })))
                .then(literal("drinkTimeout").then(argument("seconds", integer(0, 10)).executes(c -> {
                    config.drinkTimeout = getInteger(c, "seconds");
                    c.getSource().getEmbed()
                            .title("喝药超时已设置为 " + config.drinkTimeout + " 秒")
                            .description(config.drinkTimeout == 0 ? "禁用超时保护" : "超过此时间将强制停止喝药")
                            .primaryColor();
                    return OK;
                })))
                .then(literal("debug").then(argument("toggle", toggle()).executes(c -> {
                    config.debug = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                            .title("计时日志 " + toggleStrCaps(config.debug))
                            .description(config.debug ? "启用：输出喝药计时日志" : "禁用：不再输出喝药计时日志")
                            .primaryColor();
                    return OK;
                })));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        embed.title("AutoOmen Plus 配置")
                .addField("Enabled", toggleStr(config.enabled))
                .addField("Before (提前量)", config.before + " tick (" + (config.before / 20) + "s)")
                .addField("One (保留一瓶)", toggleStr(config.one))
                .addField("DrinkTimeout (超时)", config.drinkTimeout + "s" + (config.drinkTimeout == 0 ? " (禁用)" : ""))
                .addField("debug (计时日志)", toggleStr(config.debug))
                .primaryColor();
    }
}
