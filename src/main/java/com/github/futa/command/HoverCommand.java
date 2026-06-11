package com.github.futa.command;

import com.github.futa.FutaPlugin;
import com.github.futa.config.HoverConfig;
import com.github.futa.module.HoverModule;
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

public class HoverCommand extends Command {

    HoverConfig config = FutaPlugin.PLUGIN_CONFIG.hover;

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
                .name("hover")
                .category(CommandCategory.MODULE)
                .description("""
                        空中悬停 - 让玩家悬停在空中

                        通过周期性发送跳跃包抵消重力，实现悬停效果。
                        """)
                .usageLines(
                        "on/off",
                        "interval <ticks>",
                        "antiGravity on/off"
                )
                .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("hover")
                .then(argument("toggle", toggle()).executes(c -> {
                    config.enabled = getToggle(c, "toggle");
                    MODULE.get(HoverModule.class).syncEnabledFromConfig();
                    c.getSource().getEmbed()
                            .title("Hover " + toggleStrCaps(config.enabled))
                            .primaryColor();
                    return OK;
                }))
                .then(literal("interval").then(argument("ticks", integer(1, 20)).executes(c -> {
                    config.jumpInterval = getInteger(c, "ticks");
                    c.getSource().getEmbed()
                            .title("跳跃间隔已设置为 " + config.jumpInterval + " tick")
                            .primaryColor();
                    return OK;
                })))
                .then(literal("antiGravity").then(argument("toggle", toggle()).executes(c -> {
                    config.antiGravity = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                            .title("抗重力 " + toggleStrCaps(config.antiGravity))
                            .description(config.antiGravity ? "启用：周期性跳跃抵消重力" : "禁用：不发送跳跃包")
                            .primaryColor();
                    return OK;
                })));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        embed.title("Hover 配置")
                .addField("Enabled", toggleStr(config.enabled))
                .addField("Anti Gravity (抗重力)", toggleStr(config.antiGravity))
                .addField("Jump Interval (跳跃间隔)", config.jumpInterval + " tick")
                .primaryColor();
    }
}