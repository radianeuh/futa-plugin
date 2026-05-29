package com.github.futa.command;

import com.github.futa.FutaPlugin;
import com.github.futa.module.PearlPlusModule;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;

import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class PPCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
                .name("pp")
                .category(CommandCategory.MODULE)
                .description("""
                        私聊自动拉珍珠
                        """)
                .usageLines(
                        "on/off"
                )
                .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("pp")
                .then(argument("toggle", toggle()).executes(c -> {
                    FutaPlugin.PLUGIN_CONFIG.pearlPlus.enabled = getToggle(c, "toggle");
                    MODULE.get(PearlPlusModule.class).syncEnabledFromConfig();
                    c.getSource().getEmbed()
                            .title("珍珠传送 " + toggleStrCaps(FutaPlugin.PLUGIN_CONFIG.pearlPlus.enabled))
                            .primaryColor();
                    return OK;
                }));
    }
}
