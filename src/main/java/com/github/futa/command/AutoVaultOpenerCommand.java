package com.github.futa.command;

import com.github.futa.FutaPlugin;
import com.github.futa.config.AutoVaultOpenerConfig;
import com.github.futa.module.AutoVaultOpenerModule;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.zenith.Globals.MODULE;
import static com.zenith.Globals.CONFIG;
import static com.zenith.command.brigadier.CustomStringArgumentType.getString;
import static com.zenith.command.brigadier.CustomStringArgumentType.wordWithChars;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class AutoVaultOpenerCommand extends Command {
    private final AutoVaultOpenerConfig config = FutaPlugin.PLUGIN_CONFIG.autoVaultOpen;

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
                .name("autovault")
                .category(CommandCategory.MODULE)
                .description("""
                        自动开宝库插件控制命令
                        """)
                .usageLines(
                        "on/off",
                        "start - 开始自动开宝库",
                        "stop - 停止自动开宝库",
                        "status - 查看当前状态",
                        "reload - 重新加载配置",
                        "add <username> <password> - 添加账号",
                        "list - 列出所有账号",
                        "remove <index> - 移除指定账号",
                        "vault add <vaultX> <vaultY> <vaultZ> <buttonX> <buttonY> <buttonZ> <keyX> <keyY> <keyZ> - 添加宝库",
                        "vault del <index> - 删除宝库",
                        "vault clear - 清空宝库",
                        "vault list - 列出所有宝库",
                        "help - 显示帮助"
                )
                .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("autovault")
                .then(argument("toggle", toggle()).executes(c -> {
                    config.enabled = getToggle(c, "toggle");
                    MODULE.get(AutoVaultOpenerModule.class).syncEnabledFromConfig();
                    c.getSource().getEmbed()
                            .title("autovault " + toggleStrCaps(config.enabled))
                            .primaryColor();
                }))

                .then(literal("start").executes(c -> {
                    if (config.accounts.isEmpty()) {
                        c.getSource().getEmbed()
                                .title("错误")
                                .description("没有配置账号，请先添加账号");
                        return ERROR;
                    }
                    if (!config.enabled) {
                        config.enabled = true;
                        MODULE.get(AutoVaultOpenerModule.class).syncEnabledFromConfig();
                        c.getSource().getEmbed()
                                .title("已启用")
                                .description("自动开宝库插件已启用");
                    } else {
                        c.getSource().getEmbed()
                                .title("已运行")
                                .description("自动开宝库插件已在运行中");
                    }
                    return OK;
                }))
                .then(literal("stop").executes(c -> {
                    config.enabled = false;
                    MODULE.get(AutoVaultOpenerModule.class).syncEnabledFromConfig();
                    c.getSource().getEmbed()
                            .title("已停止")
                            .description("自动开宝库插件已停止");
                    return OK;
                }))
                .then(literal("status").executes(c -> {
                    var embed = c.getSource().getEmbed()
                            .title("自动开宝库状态")
                            .description("插件状态: " + (config.enabled ? "启用" : "禁用"));
                    embed.addField("账号数量", String.valueOf(config.accounts.size()), true);
                    embed.addField("登录命令", config.loginCommand, true);
                    embed.addField("搜索范围", String.valueOf(config.searchRadius), true);
                    return OK;
                }))
                .then(literal("reload").executes(c -> {
                    // 这里可以重新加载配置文件
                    c.getSource().getEmbed()
                            .title("配置重载")
                            .description("配置已重新加载");
                    return OK;
                }))
                .then(literal("add")
                        .then(argument("username", wordWithChars())
                                .then(argument("password", wordWithChars())
                                        .executes(c -> {
                                            String username = getString(c, "username");
                                            String password = getString(c, "password");
                                            AutoVaultOpenerConfig.AccountInfo account = new AutoVaultOpenerConfig.AccountInfo(username, password);
                                            config.accounts.add(account);
                                            c.getSource().getEmbed()
                                                    .title("账号已添加")
                                                    .description("用户名: " + username);
                                            return OK;
                                        }))
                                .executes(c -> {
                                    String username = getString(c, "username");
                                    String password = getString(c, "password");
                                    AutoVaultOpenerConfig.AccountInfo account = new AutoVaultOpenerConfig.AccountInfo(username, password);
                                    config.accounts.add(account);
                                    c.getSource().getEmbed()
                                            .title("账号已添加")
                                            .description("用户名: " + username);
                                    return OK;
                                })
                        )
                )
                .then(literal("list").executes(c -> {
                    var embed = c.getSource().getEmbed().title("账号列表");
                    if (config.accounts.isEmpty()) {
                        embed.description("没有配置账号");
                        return OK;
                    }
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < config.accounts.size(); i++) {
                        AutoVaultOpenerConfig.AccountInfo account = config.accounts.get(i);
                        sb.append(i + 1).append(". ").append(account.username);

                        sb.append("\n");
                    }
                    embed.description(sb.toString());
                    return OK;
                }))
                .then(literal("remove")
                        .then(argument("index", IntegerArgumentType.integer(1)).executes(c -> {
                                    int index = getInteger(c, "index") - 1;
                                    if (index >= 0 && index < config.accounts.size()) {
                                        AutoVaultOpenerConfig.AccountInfo removed = config.accounts.remove(index);
                                        c.getSource().getEmbed()
                                                .title("账号已移除")
                                                .description("已移除账号: " + removed.username);
                                    } else {
                                        c.getSource().getEmbed()
                                                .title("索引错误")
                                                .description("无效的账号索引");
                                    }
                                    return OK;
                                })
                        ))
                .then(literal("vault")
                        .then(literal("add")
                                .then(argument("vaultX", integer())
                                        .then(argument("vaultY", integer())
                                                .then(argument("vaultZ", integer())
                                                        .then(argument("buttonX", integer())
                                                                .then(argument("buttonY", integer())
                                                                        .then(argument("buttonZ", integer())
                                                                                .then(argument("keyX", integer())
                                                                                        .then(argument("keyY", integer())
                                                                                                .then(argument("keyZ", integer())
                                                                                                        .executes(c -> {
                                                                                                            AutoVaultOpenerConfig.VaultInfo vault = new AutoVaultOpenerConfig.VaultInfo();
                                                                                                            vault.vaultX = getInteger(c, "vaultX");
                                                                                                            vault.vaultY = getInteger(c, "vaultY");
                                                                                                            vault.vaultZ = getInteger(c, "vaultZ");
                                                                                                            vault.buttonX = getInteger(c, "buttonX");
                                                                                                            vault.buttonY = getInteger(c, "buttonY");
                                                                                                            vault.buttonZ = getInteger(c, "buttonZ");
                                                                                                            vault.keyContainerX = getInteger(c, "keyX");
                                                                                                            vault.keyContainerY = getInteger(c, "keyY");
                                                                                                            vault.keyContainerZ = getInteger(c, "keyZ");
                                                                                                            config.vaults.add(vault);
                                                                                                            c.getSource().getEmbed()
                                                                                                                    .title("宝库已添加")
                                                                                                                    .description("宝库位置: (" + vault.vaultX + ", " + vault.vaultY + ", " + vault.vaultZ + ")");
                                                                                                            return OK;
                                                                                                        })))))))))))
                        .then(literal("del").then(argument("index", integer(0, 100)).executes(c -> {
                            int index = getInteger(c, "index");
                            if (index < 0 || index >= config.vaults.size()) {
                                c.getSource().getEmbed()
                                        .title("无效索引")
                                        .description("索引必须在 0 和 " + (config.vaults.size() - 1) + " 之间");
                                return ERROR;
                            }
                            AutoVaultOpenerConfig.VaultInfo removed = config.vaults.remove(index);
                            c.getSource().getEmbed()
                                    .title("宝库已删除")
                                    .description("已删除宝库: (" + removed.vaultX + ", " + removed.vaultY + ", " + removed.vaultZ + ")");
                            return OK;
                        })))
                        .then(literal("clear").executes(c -> {
                            config.vaults.clear();
                            c.getSource().getEmbed()
                                    .title("宝库已清空")
                                    .description("所有宝库配置已清空");
                            return OK;
                        }))
                        .then(literal("list").executes(c -> {
                            if (config.vaults.isEmpty()) {
                                c.getSource().getEmbed()
                                        .title("宝库列表")
                                        .description("没有配置宝库");
                                return OK;
                            }

                            StringBuilder sb = new StringBuilder();
                            sb.append("宝库列表:\n\n");
                            for (int i = 0; i < config.vaults.size(); i++) {
                                AutoVaultOpenerConfig.VaultInfo vault = config.vaults.get(i);
                                sb.append("**").append(i).append("**: ")
                                        .append("宝库: ||").append(CONFIG.discord.reportCoords ? "(" + vault.vaultX + ", " + vault.vaultY + ", " + vault.vaultZ + ")" : "Coords disabled").append("||\n")
                                        .append("按钮: ||").append(CONFIG.discord.reportCoords ? "(" + vault.buttonX + ", " + vault.buttonY + ", " + vault.buttonZ + ")" : "Coords disabled").append("||\n")
                                        .append("钥匙容器: ||").append(CONFIG.discord.reportCoords ? "(" + vault.keyContainerX + ", " + vault.keyContainerY + ", " + vault.keyContainerZ + ")" : "Coords disabled").append("||\n\n");
                            }

                            c.getSource().getEmbed()
                                    .title("宝库列表")
                                    .description(sb.toString());
                            return OK;
                        })))
                .then(literal("help").executes(c -> {
                    var embed = c.getSource().getEmbed().title("自动开宝库帮助");
                    StringBuilder sb = new StringBuilder();
                    sb.append("**start** - 开始自动开宝库\n");
                    sb.append("**stop** - 停止自动开宝库\n");
                    sb.append("**status** - 查看当前状态\n");
                    sb.append("**reload** - 重新加载配置\n");
                    sb.append("**add** - 添加账号\n");
                    sb.append("**list** - 列出所有账号\n");
                    sb.append("**remove** - 移除指定账号\n");
                    sb.append("**vault add** - 添加宝库\n");
                    sb.append("**vault del** - 删除宝库\n");
                    sb.append("**vault clear** - 清空宝库\n");
                    sb.append("**vault list** - 列出所有宝库\n");
                    sb.append("**help** - 显示此帮助\n");
                    embed.description(sb.toString());
                    return OK;
                }));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        embed.primaryColor()
                .addField("Enabled", toggleStr(config.enabled))
                .addField("Accounts", String.valueOf(config.accounts.size()))
                .addField("LoginCmd", config.loginCommand)
                .addField("SearchRadius", String.valueOf(config.searchRadius))
                .addField("Vaults", String.valueOf(config.vaults.size()));
    }
}
