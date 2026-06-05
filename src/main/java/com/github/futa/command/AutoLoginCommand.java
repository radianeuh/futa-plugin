package com.github.futa.command;

import com.github.futa.module.AutoLoginModule;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;

import static com.github.futa.FutaPlugin.PLUGIN_CONFIG;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class AutoLoginCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
                .name("autologin")
                .category(CommandCategory.MODULE)
                .description("""
                        cccuuu autologin plugin command
                        """)
                .usageLines(
                        "on/off",
                        "reboot on/off",
                        "timeout <seconds>"
                )
                .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("autologin")
                .then(argument("toggle", toggle()).executes(c -> {
                    PLUGIN_CONFIG.autoLogin = getToggle(c, "toggle");
                    // make sure to sync so the module is actually toggled
                    MODULE.get(AutoLoginModule.class).syncEnabledFromConfig();
                    c.getSource().getEmbed()
                            // if no title is set, no embed response will be sent
                            // other properties like fields can be left unset without issues
                            .title("autologin Plugin " + toggleStrCaps(PLUGIN_CONFIG.autoLogin));
                }))
                .then(literal("reboot").then(argument("toggle", toggle()).executes(c -> {
                    PLUGIN_CONFIG.autoReboot = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                            .title("auto reboot " + (PLUGIN_CONFIG.autoReboot ? "Enabled" : "Disabled"));
                    return OK;
                })))
                .then(literal("timeout").then(argument("分", integer(1, 300)).executes(c -> {
                    PLUGIN_CONFIG.autoLoginTimeout = getInteger(c, "分");
                    c.getSource().getEmbed()
                            .title("Auto Login Timeout")
                            .description("已设置为 " + PLUGIN_CONFIG.autoLoginTimeout + " 分")
                            .primaryColor();
                    return OK;
                })))
                ;
    }

    @Override
    public void defaultEmbed(Embed embed) {
        embed
                .primaryColor()
                .addField("Enabled", toggleStr(PLUGIN_CONFIG.autoLogin))
                .addField("Auto Reboot", toggleStr(PLUGIN_CONFIG.autoReboot))
                .addField("Timeout (超时重连时间)", PLUGIN_CONFIG.autoLoginTimeout + " 分");

    }
}
