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
                - KillAura 自动联动
                """)
            .usageLines(
                "on/off",
                "before <ticks>",
                "one on/off"
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
            })));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        embed.title("AutoOmen Plus 配置")
            .addField("Enabled", toggleStr(config.enabled))
            .addField("Before (提前量)", config.before + " tick (" + (config.before / 20) + "s)")
            .addField("One (保留一瓶)", toggleStr(config.one))
            .primaryColor();
    }
}
