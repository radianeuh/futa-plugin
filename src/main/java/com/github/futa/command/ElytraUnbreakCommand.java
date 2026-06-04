package com.github.futa.command;

import com.github.futa.FutaPlugin;
import com.github.futa.config.ElytraUnbreakConfig;
import com.github.futa.module.ElytraUnbreakModule;
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

public class ElytraUnbreakCommand extends Command {

    ElytraUnbreakConfig config = FutaPlugin.PLUGIN_CONFIG.elytraUnbreak;

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
                .name("elytraUnbreak")
                .category(CommandCategory.MODULE)
                .description("""
                        无限耐久鞘翅 - 通过自动切换鞘翅来防止耐久度消耗

                        核心逻辑：卸下鞘翅 -> 下一tick重新装备 -> 发送开始滑翔包
                        """)
                .usageLines(
                        "on/off",
                        "period <ticks>",
                        "antiKick on/off"
                )
                .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("elytraUnbreak")
                .then(argument("toggle", toggle()).executes(c -> {
                    config.enabled = getToggle(c, "toggle");
                    MODULE.get(ElytraUnbreakModule.class).syncEnabledFromConfig();
                    c.getSource().getEmbed()
                            .title("ElytraUnbreak " + toggleStrCaps(config.enabled))
                            .primaryColor();
                    return OK;
                }))
                .then(literal("period").then(argument("ticks", integer(1, 100)).executes(c -> {
                    config.period = getInteger(c, "ticks");
                    c.getSource().getEmbed()
                            .title("切换周期已设置为 " + config.period + " tick")
                            .primaryColor();
                    return OK;
                })))
                .then(literal("antiKick").then(argument("toggle", toggle()).executes(c -> {
                    config.antiKick = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                            .title("防踢飞 " + toggleStrCaps(config.antiKick))
                            .description(config.antiKick ? "启用：无法滑翔时发送跳跃包防止被踢" : "禁用：不发送跳跃包")
                            .primaryColor();
                    return OK;
                })));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        embed.title("ElytraUnbreak 配置")
                .addField("Enabled", toggleStr(config.enabled))
                .addField("Period (切换周期)", config.period + " tick")
                .addField("Anti Kick (防踢飞)", toggleStr(config.antiKick))
                .primaryColor();
    }
}