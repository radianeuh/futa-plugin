package com.github.futa.command;

import com.github.futa.FutaPlugin;
import com.github.futa.config.FutaConfig;
import com.github.futa.module.AutoDropModule;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.Proxy;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;
import com.zenith.mc.item.ItemRegistry;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static com.zenith.Globals.MODULE;
import static com.zenith.Globals.saveConfigAsync;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class AutoDropCommand extends Command {

    public static FutaConfig PLUGIN_CONFIG = FutaPlugin.PLUGIN_CONFIG;

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
                .name("autodrop2")
                .category(CommandCategory.MODULE)
                .description("""
                        Automatically drop specified items from player inventory.
                        """)
                .usageLines(
                        "",
                        "<on/off>",
                        "mode <whitelist/blacklist>",
                        "add <item>",
                        "remove <item>",
                        "list",
                        "clear",
                        "delay <ticks>"
                )
                .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("autodrop")
                .executes(c -> {
                    if (!verifyLoggedIn(c.getSource().getEmbed())) return ERROR;
                    printStatus(c.getSource().getEmbed());
                    return OK;
                })
                .then(argument("toggle", toggle()).executes(c -> {
                    PLUGIN_CONFIG.autoDrop.enabled = getToggle(c, "toggle");
                    MODULE.get(AutoDropModule.class).syncEnabledFromConfig();
                    saveConfigAsync();
                    settingsEmbed(c.getSource().getEmbed(), "AutoDrop " + (PLUGIN_CONFIG.autoDrop.enabled ? "Enabled" : "Disabled"));
                    return OK;
                }))
                .then(literal("mode").then(argument("mode", string()).executes(c -> {
                    if (!verifyLoggedIn(c.getSource().getEmbed())) return ERROR;
                    String mode = getString(c, "mode").toLowerCase();
                    if (mode.equals("whitelist")) {
                        PLUGIN_CONFIG.autoDrop.whitelistMode = true;
                    } else if (mode.equals("blacklist")) {
                        PLUGIN_CONFIG.autoDrop.whitelistMode = false;
                    } else {
                        c.getSource().getEmbed()
                                .title("Error")
                                .description("Invalid mode. Use 'whitelist' or 'blacklist'")
                                .errorColor();
                        return ERROR;
                    }
                    saveConfigAsync();
                    settingsEmbed(c.getSource().getEmbed(), "Mode set to: " + mode);
                    return OK;
                })))
                .then(literal("add").then(argument("item", string()).executes(c -> {
                    if (!verifyLoggedIn(c.getSource().getEmbed())) return ERROR;
                    String item = getString(c, "item");
                    if (!isValidItem(item)) {
                        c.getSource().getEmbed()
                                .title("Error")
                                .description("Invalid item: " + item)
                                .errorColor();
                        return ERROR;
                    }
                    if (PLUGIN_CONFIG.autoDrop.items.contains(item)) {
                        c.getSource().getEmbed()
                                .title("Error")
                                .description("Item already in list: " + item)
                                .errorColor();
                        return ERROR;
                    }
                    PLUGIN_CONFIG.autoDrop.items.add(item);
                    saveConfigAsync();
                    settingsEmbed(c.getSource().getEmbed(), "Added item: " + item);
                    return OK;
                })))
                .then(literal("remove").then(argument("item", string()).executes(c -> {
                    if (!verifyLoggedIn(c.getSource().getEmbed())) return ERROR;
                    String item = getString(c, "item");
                    if (!PLUGIN_CONFIG.autoDrop.items.contains(item)) {
                        c.getSource().getEmbed()
                                .title("Error")
                                .description("Item not in list: " + item)
                                .errorColor();
                        return ERROR;
                    }
                    PLUGIN_CONFIG.autoDrop.items.remove(item);
                    saveConfigAsync();
                    settingsEmbed(c.getSource().getEmbed(), "Removed item: " + item);
                    return OK;
                })))
                .then(literal("list").executes(c -> {
                    if (!verifyLoggedIn(c.getSource().getEmbed())) return ERROR;
                    printItemList(c.getSource().getEmbed());
                    return OK;
                }))
                .then(literal("clear").executes(c -> {
                    if (!verifyLoggedIn(c.getSource().getEmbed())) return ERROR;
                    PLUGIN_CONFIG.autoDrop.items.clear();
                    saveConfigAsync();
                    settingsEmbed(c.getSource().getEmbed(), "Item list cleared");
                    return OK;
                }))
                .then(literal("delay").then(argument("ticks", string()).executes(c -> {
                    if (!verifyLoggedIn(c.getSource().getEmbed())) return ERROR;
                    try {
                        int delay = Integer.parseInt(getString(c, "ticks"));
                        if (delay < 1 || delay > 1200) {
                            c.getSource().getEmbed()
                                    .title("Error")
                                    .description("Delay must be between 1 and 1200 ticks")
                                    .errorColor();
                            return ERROR;
                        }
                        PLUGIN_CONFIG.autoDrop.delayBetweenDrops = delay;
                        saveConfigAsync();
                        settingsEmbed(c.getSource().getEmbed(), "Delay set to: " + delay + " ticks");
                        return OK;
                    } catch (NumberFormatException e) {
                        c.getSource().getEmbed()
                                .title("Error")
                                .description("Invalid number format")
                                .errorColor();
                        return ERROR;
                    }
                })));
    }

    private void printStatus(Embed embed) {
        embed.title("AutoDrop Status")
                .addField("Enabled", PLUGIN_CONFIG.autoDrop.enabled)
                .addField("Mode", PLUGIN_CONFIG.autoDrop.whitelistMode ? "Whitelist" : "Blacklist")
                .addField("Delay", PLUGIN_CONFIG.autoDrop.delayBetweenDrops + " ticks")
                .addField("Items Count", PLUGIN_CONFIG.autoDrop.items.size())
                .primaryColor();
    }

    private void printItemList(Embed embed) {
        embed.title("AutoDrop Item List (" + (PLUGIN_CONFIG.autoDrop.whitelistMode ? "Whitelist" : "Blacklist") + ")");
        for (String item : PLUGIN_CONFIG.autoDrop.items) {
            embed.addField(item, isValidItem(item) ? "✓ Valid" : "✗ Invalid", false);
        }
        embed.primaryColor();
    }

    private boolean isValidItem(String itemName) {
        return ItemRegistry.REGISTRY.get(itemName) != null;
    }

    private void settingsEmbed(Embed embed, String message) {
        embed.title("AutoDrop Settings")
                .description(message)
                .addField("Enabled", PLUGIN_CONFIG.autoDrop.enabled)
                .addField("Mode", PLUGIN_CONFIG.autoDrop.whitelistMode ? "Whitelist" : "Blacklist")
                .addField("Delay", PLUGIN_CONFIG.autoDrop.delayBetweenDrops + " ticks")
                .addField("Items Count", PLUGIN_CONFIG.autoDrop.items.size())
                .primaryColor();
    }

    private boolean verifyLoggedIn(Embed embed) {
        var client = Proxy.getInstance().getClient();
        if (client == null || !Proxy.getInstance().isConnected()) {
            embed.title("Error")
                    .description("Not logged in!")
                    .errorColor();
            return false;
        }
        return true;
    }
}
