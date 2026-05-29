package com.github.futa.command;

import cn.hutool.core.util.StrUtil;
import com.github.futa.module.PearlPlusModule;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.Proxy;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;
import com.zenith.network.server.ServerSession;

import static com.github.futa.FutaPlugin.PLUGIN_CONFIG;
import static com.zenith.Globals.COMMAND;
import static com.zenith.Globals.CONFIG;
import static com.zenith.command.brigadier.CustomStringArgumentType.getString;
import static com.zenith.command.brigadier.CustomStringArgumentType.wordWithChars;
import static com.zenith.util.config.Config.Authentication.AccountType.OFFLINE;

public class LoginOnceCommand extends Command {


    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
                .name("loginonce")
                .category(CommandCategory.MANAGE)
                .description("""
                        Login once with specific credentials and pearId
                        """)
                .usageLines(
                        "loginonce <username> <password> <pearId>"
                )
                .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("loginonce")
                .then(argument("username", wordWithChars())
                        .then(argument("password", wordWithChars())
                                .then(argument("pearId", wordWithChars())
                                        .executes(c -> {
                                            String username = getString(c, "username");
                                            String password = getString(c, "password");
                                            String pearId = getString(c, "pearId");

                                            try {
                                                if (Proxy.getInstance().isConnected()) {
                                                    ServerSession session = Proxy.getInstance().getCurrentPlayer().get();
                                                    if (session != null && !StrUtil.equals(username, session.getName())) {
                                                        Proxy.getInstance().disconnect();
                                                    } else {

                                                        var ctx = CommandContext.create("pl load " + pearId, PearlPlusModule.PearlPlusCommandSource.INSTANCE);
                                                        // carry sender to CommandSource for reply
                                                        ctx.getData().put("PearlPlusSender", username);
                                                        COMMAND.execute(ctx);
                                                    }

                                                    return OK;
                                                }

                                                PLUGIN_CONFIG.autoLogin = true;
                                                PLUGIN_CONFIG.autoReboot = false;
                                                PLUGIN_CONFIG.pearlPlus.auto = true;
                                                PLUGIN_CONFIG.pearlPlus.autoId = pearId;

                                                // Set authentication config
                                                CONFIG.authentication.username = username;
                                                CONFIG.authentication.password = password;
                                                CONFIG.authentication.accountType = OFFLINE;

                                                // Clear auth cache and cancel current login
                                                Proxy.getInstance().connect();

                                                c.getSource().getEmbed()
                                                        .title("Login Once Successful")
                                                        .addField("Username", username)
                                                        .addField("Pear ID", pearId);


                                            } catch (Exception e) {
                                                c.getSource().getEmbed()
                                                        .title("Login Once Error")
                                                        .addField("Error", e.getMessage());
                                            }

                                            return 1;
                                        }))));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        embed
                .title("Login Once Command")
                .description("Usage: loginonce <username> <password> <pearId>")
                .primaryColor();
    }
}
