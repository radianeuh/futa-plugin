package com.github.futa.command;

import com.github.futa.FutaPlugin;
import com.github.futa.config.VillagerTraderConfig;
import com.github.futa.module.VillagerTrader;
import com.github.futa.module.VillagerTrader.VillagerProfession;
import com.github.futa.util.EnchantmentUtil;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.zenith.Globals.CONFIG;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.BlockPosArgument.blockPos;
import static com.zenith.command.brigadier.BlockPosArgument.getBlockPos;
import static com.zenith.command.brigadier.ItemArgument.getItem;
import static com.zenith.command.brigadier.ItemArgument.item;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class VillagerTraderCommand extends Command {

    VillagerTraderConfig traderConfig = FutaPlugin.PLUGIN_CONFIG.trader;

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
                .name("traderplus")
                .category(CommandCategory.MODULE)
                .description("""
                        Buys items from villagers with emeralds.
                        
                        Automatically restocks emeralds, trades with villagers, and stores the bought items
                        
                        `restockStacks` -> how many stacks of emeralds/emerald blocks to restock. Emerald blocks are crafted down to emeralds.
                        `villagerTradeRestockWait` -> seconds it waits after all villagers are out of stock. 1200 = 1 minecraft day
                        `maxSpendPerTrade` -> max emeralds to spend per trade (global default)
                        `itemMaxSpend` -> set max emeralds to spend per trade for specific items (overrides global setting)
                        `buyItemStoreStacksThreshold` -> how many stacks/slots of items to buy before it stores them
                        `waitForInteractTimeout` -> timeout for server interactions like opening villager trade window
                        `desiredEnchantments` -> specific enchantments to buy on enchanted books
                        `onlyBuyDesiredEnchantments` -> whether to only buy enchanted books with desired enchantments
                        `onlyBuyMaxLevelEnchantments` -> whether to only buy enchanted books at maximum levels
                        """)
                .usageLines(
                        "on/off",
                        "professions add/del <profession>",
                        "professions clear",
                        "buyItems add/del <item>",
                        "buyItems clear",
                        "restockStacks <stacks>",
                        "restockEmeraldCountThreshold <amount>",
                        "restockChest <x> <y> <z>",
                        "storeChest <x> <y> <z>",
                        "bookRestockChest <x> <y> <z>",
                        "villagerTradeRestockWait <seconds>",
                        "maxSpendPerTrade <amount>",
                        "itemMaxSpend set <item> <amount>",
                        "itemMaxSpend del <item>",
                        "itemMaxSpend clear",
                        "itemMaxSpend list",
                        "buyItemStoreStacksThreshold <stacks>",
                        "waitForInteractTimeout <ticks>",
                        "desiredEnchantments add <enchantment> <level>",
                        "desiredEnchantments del <enchantment>",
                        "desiredEnchantments clear",
                        "desiredEnchantments list",
                        "desiredEnchantments available",
                        "onlyBuyDesiredEnchantments <true/false>",
                        "onlyBuyMaxLevelEnchantments <true/false>"
                )
                .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("ftrader")
                .then(argument("toggle", toggle()).executes(c -> {
                    traderConfig.enabled = getToggle(c, "toggle");
                    MODULE.get(VillagerTrader.class).syncEnabledFromConfig();
                    c.getSource().getEmbed()
                            .title("Villager Trader " + toggleStrCaps(traderConfig.enabled))
                            .primaryColor();
                }))
                .then(literal("professions")
                        .then(literal("add").then(argument("profession", enumStrings(VillagerProfession.values())).executes(c -> {
                            var profStr = getString(c, "profession");
                            VillagerProfession prof;
                            try {
                                prof = VillagerProfession.valueOf(profStr.toUpperCase());
                            } catch (Exception e) {
                                c.getSource().getEmbed()
                                        .title("Invalid Profession")
                                        .description("Valid professions: "
                                                + Arrays.stream(VillagerProfession.values())
                                                .map(p -> p.name().toLowerCase())
                                                .collect(Collectors.joining(", ")));
                                return ERROR;
                            }
                            if (!traderConfig.villagerProfessions.contains(prof)) {
                                traderConfig.villagerProfessions.add(prof);
                            }
                            c.getSource().getEmbed()
                                    .title("Profession Added");
                            return OK;
                        })))
                        .then(literal("del").then(argument("profession", enumStrings(VillagerProfession.values())).executes(c -> {
                            var profStr = getString(c, "profession");
                            VillagerProfession prof;
                            try {
                                prof = VillagerProfession.valueOf(profStr.toUpperCase());
                            } catch (Exception e) {
                                c.getSource().getEmbed()
                                        .title("Invalid Profession")
                                        .description("Valid professions: "
                                                + Arrays.stream(VillagerProfession.values())
                                                .map(p -> p.name().toLowerCase())
                                                .collect(Collectors.joining(", ")));
                                return ERROR;
                            }
                            traderConfig.villagerProfessions.remove(prof);
                            c.getSource().getEmbed()
                                    .title("Profession Removed");
                            return OK;
                        })))
                        .then(literal("clear").executes(c -> {
                            traderConfig.villagerProfessions.clear();
                            c.getSource().getEmbed()
                                    .title("Professions Cleared");
                        })))
                .then(literal("buyItems")
                        .then(literal("add").then(argument("item", item()).executes(c -> {
                            var itemData = getItem(c, "item");
                            if (!traderConfig.buyItems.contains(itemData.name())) {
                                traderConfig.buyItems.add(itemData.name());
                            }
                            c.getSource().getEmbed()
                                    .title("Item Added");
                            return OK;
                        })))
                        .then(literal("del").then(argument("item", item()).executes(c -> {
                            var itemData = getItem(c, "item");
                            traderConfig.buyItems.remove(itemData.name());
                            c.getSource().getEmbed()
                                    .title("Item Removed");
                            return OK;
                        })))
                        .then(literal("clear").executes(c -> {
                            traderConfig.buyItems.clear();
                            c.getSource().getEmbed()
                                    .title("Items Cleared");
                        })))
                .then(literal("restockStacks").then(argument("stackCount", integer(1, 36)).executes(c -> {
                    traderConfig.restockStacks = getInteger(c, "stackCount");
                    c.getSource().getEmbed()
                            .title("Restock Stacks Set");
                })))
                .then(literal("restockEmeraldCountThreshold").then(argument("amount", integer(1, 250)).executes(c -> {
                    traderConfig.restockEmeraldCountThreshold = getInteger(c, "amount");
                    c.getSource().getEmbed()
                            .title("Restock Emerald Count Threshold Set");
                })))
                .then(literal("restockChest").then(argument("pos", blockPos()).executes(c -> {
                    traderConfig.restockChest = getBlockPos(c, "pos");
                    c.getSource().getEmbed()
                            .title("Restock Chest Set");
                })))
                .then(literal("storeChest").then(argument("pos", blockPos()).executes(c -> {
                    traderConfig.storeChest = getBlockPos(c, "pos");
                    c.getSource().getEmbed()
                            .title("Store Chest Set");
                })))
                .then(literal("bookRestockChest").then(argument("pos", blockPos()).executes(c -> {
                    traderConfig.bookRestockChest = getBlockPos(c, "pos");
                    c.getSource().getEmbed()
                            .title("Book Restock Chest Set");
                })))
                .then(literal("villagerTradeRestockWait").then(argument("seconds", integer(1, (int) TimeUnit.MINUTES.toSeconds(30))).executes(c -> {
                    traderConfig.villagerTradeRestockWaitSeconds = getInteger(c, "seconds");
                    c.getSource().getEmbed()
                            .title("Villager Trade Restock Wait Set");
                })))
                .then(literal("maxSpendPerTrade").then(argument("spend", integer(1, 1000)).executes(c -> {
                    traderConfig.maxSpendPerTrade = getInteger(c, "spend");
                    c.getSource().getEmbed()
                            .title("Max Spend Per Trade Set");
                })))
                .then(literal("itemMaxSpend")
                        .then(literal("set").then(argument("item", item()).then(argument("spend", integer(1, 1000)).executes(c -> {
                            var itemData = getItem(c, "item");
                            int spend = getInteger(c, "spend");
                            traderConfig.itemMaxSpendPerTrade.put(itemData.name(), spend);
                            c.getSource().getEmbed()
                                    .title("Item Max Spend Set")
                                    .description("Set max spend for " + itemData.name() + " to " + spend);
                            return OK;
                        }))))
                        .then(literal("del").then(argument("item", item()).executes(c -> {
                            var itemData = getItem(c, "item");
                            Integer removed = traderConfig.itemMaxSpendPerTrade.remove(itemData.name());
                            if (removed == null) {
                                c.getSource().getEmbed()
                                        .title("Item Max Spend Not Found")
                                        .description("No max spend configured for: " + itemData.name());
                                return ERROR;
                            }
                            c.getSource().getEmbed()
                                    .title("Item Max Spend Removed")
                                    .description("Removed max spend for " + itemData.name());
                            return OK;
                        })))
                        .then(literal("clear").executes(c -> {
                            int count = traderConfig.itemMaxSpendPerTrade.size();
                            traderConfig.itemMaxSpendPerTrade.clear();
                            c.getSource().getEmbed()
                                    .title("Item Max Spend Cleared")
                                    .description("Cleared " + count + " item-specific max spend settings");
                            return OK;
                        }))
                        .then(literal("list").executes(c -> {
                            if (traderConfig.itemMaxSpendPerTrade.isEmpty()) {
                                c.getSource().getEmbed()
                                        .title("Item Max Spend Settings")
                                        .description("No item-specific max spend configured. Use `itemMaxSpend set <item> <amount>` to add settings.");
                                return OK;
                            }

                            StringBuilder sb = new StringBuilder();
                            sb.append("Currently configured item max spend settings:\n\n");

                            for (Map.Entry<String, Integer> entry : traderConfig.itemMaxSpendPerTrade.entrySet()) {
                                sb.append("**").append(entry.getKey()).append("**: ")
                                        .append(entry.getValue()).append(" emeralds\n");
                            }

                            c.getSource().getEmbed()
                                    .title("Item Max Spend Settings")
                                    .description(sb.toString());
                            return OK;
                        })))
                .then(literal("buyItemStoreStacksThreshold").then(argument("stackCount", integer(1, 36)).executes(c -> {
                    traderConfig.buyItemStoreStacksThreshold = getInteger(c, "stackCount");
                    c.getSource().getEmbed()
                            .title("Buy Item Store Stacks Threshold Set");
                })))
                .then(literal("waitForInteractTimeout").then(argument("ticks", integer(1, 1000)).executes(c -> {
                    traderConfig.waitForInteractTimeoutTicks = getInteger(c, "ticks");
                    c.getSource().getEmbed()
                            .title("Wait For Interact Timeout Set");
                })))
                .then(literal("desiredEnchantments")
                        .then(literal("add").then(argument("enchantment", enumStrings(EnchantmentUtil.getAllEnchantment().toArray(new String[0]))).then(argument("level", integer(1, 5)).executes(c -> {
                            String enchantment = getString(c, "enchantment");
                            int level = getInteger(c, "level");
                            Integer maxLevel = EnchantmentUtil.getMaxLevel(enchantment);
                            if (maxLevel == null) {
                                c.getSource().getEmbed()
                                        .title("Invalid Enchantment")
                                        .description("Enchantment not found: " + enchantment);
                                return ERROR;
                            }
                            if (level < 1 || level > maxLevel) {
                                c.getSource().getEmbed()
                                        .title("Invalid Level")
                                        .description("Level must be between 1 and " + maxLevel + " for " + enchantment);
                                return ERROR;
                            }
                            traderConfig.desiredEnchantments.put(enchantment, level);
                            c.getSource().getEmbed()
                                    .title("Enchantment Added")
                                    .description("Added " + enchantment + " level " + level);
                            return OK;
                        }))))
                        .then(literal("del").then(argument("enchantment", enumStrings(EnchantmentUtil.getAllEnchantment().toArray(new String[0]))).executes(c -> {
                            String enchantment = getString(c, "enchantment");
                            Integer removed = traderConfig.desiredEnchantments.remove(enchantment);
                            if (removed == null) {
                                c.getSource().getEmbed()
                                        .title("Enchantment Not Found")
                                        .description("Enchantment not in desired list: " + enchantment);
                                return ERROR;
                            }
                            c.getSource().getEmbed()
                                    .title("Enchantment Removed")
                                    .description("Removed " + enchantment + " level " + removed);
                            return OK;
                        })))
                        .then(literal("clear").executes(c -> {
                            int count = traderConfig.desiredEnchantments.size();
                            traderConfig.desiredEnchantments.clear();
                            c.getSource().getEmbed()
                                    .title("Enchantments Cleared")
                                    .description("Cleared " + count + " enchantments");
                            return OK;
                        })))
                .then(literal("list").executes(c -> {
                    if (traderConfig.desiredEnchantments.isEmpty()) {
                        c.getSource().getEmbed()
                                .title("Configured Enchantments")
                                .description("No enchantments configured. Use `desiredEnchantments add <enchantment> <level>` to add enchantments.");
                        return OK;
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append("Currently configured enchantments:\n\n");

                    // Group by level for better readability
                    Map<Integer, List<String>> enchantmentsByLevel = new TreeMap<>();

                    for (Map.Entry<String, Integer> entry : traderConfig.desiredEnchantments.entrySet()) {
                        String enchantment = entry.getKey();
                        int level = entry.getValue();

                        enchantmentsByLevel.computeIfAbsent(level, k -> new ArrayList<>())
                                .add(enchantment);
                    }

                    for (Map.Entry<Integer, List<String>> entry : enchantmentsByLevel.entrySet()) {
                        int level = entry.getKey();
                        List<String> enchantments = entry.getValue();
                        enchantments.sort(String.CASE_INSENSITIVE_ORDER);

                        sb.append("**Level ").append(level).append("**: ")
                                .append(String.join(", ", enchantments))
                                .append("\n");
                    }

                    c.getSource().getEmbed()
                            .title("Configured Enchantments")
                            .description(sb.toString());
                    return OK;
                }))
                .then(literal("available").executes(c -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Available enchantments and their maximum levels:\n\n");

                    // Group enchantments by level for better readability
                    Map<Integer, List<String>> enchantmentsByLevel = new TreeMap<>();

                    for (String enchantment : EnchantmentUtil.getAllEnchantment()) {
                        int maxLevel = EnchantmentUtil.getMaxLevel(enchantment);
                        if (maxLevel > 0) {
                            enchantmentsByLevel.computeIfAbsent(maxLevel, k -> new ArrayList<>())
                                    .add(enchantment);
                        }
                    }

                    for (Map.Entry<Integer, List<String>> entry : enchantmentsByLevel.entrySet()) {
                        int level = entry.getKey();
                        List<String> enchantments = entry.getValue();
                        enchantments.sort(String.CASE_INSENSITIVE_ORDER);

                        sb.append("**Level ").append(level).append("**: ")
                                .append(String.join(", ", enchantments))
                                .append("\n");
                    }

                    c.getSource().getEmbed()
                            .title("Available Enchantments")
                            .description(sb.toString());
                    return OK;
                }))
                .then(literal("buyEnchantBook").then(argument("toggle", toggle()).executes(c -> {
                    traderConfig.buyEnchantBook = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                            .title("buyEnchantBook " + (traderConfig.buyEnchantBook ? "Enabled" : "Disabled"));
                    return OK;
                })))
                .then(literal("onlyBuyDesiredEnchantments").then(argument("toggle", toggle()).executes(c -> {
                    traderConfig.onlyBuyDesiredEnchantments = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                            .title("Only Buy Desired Enchantments " + (traderConfig.onlyBuyDesiredEnchantments ? "Enabled" : "Disabled"));
                    return OK;
                })))
                .then(literal("onlyBuyMaxLevelEnchantments").then(argument("toggle", toggle()).executes(c -> {
                    traderConfig.onlyBuyMaxLevelEnchantments = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                            .title("Only Buy Max Level Enchantments " + (traderConfig.onlyBuyMaxLevelEnchantments ? "Enabled" : "Disabled"));
                    return OK;
                })));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        String enchantmentsStr = traderConfig.desiredEnchantments.entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining(", ", "[", "]"));

        embed.addField("Villager Trader", toggleStr(traderConfig.enabled))
                .addField("Professions", traderConfig.villagerProfessions.stream().map(p -> p.name().toLowerCase()).collect(Collectors.joining(", ", "[", "]")))
                .addField("Buy Items", "[" + String.join(", ", traderConfig.buyItems) + "]")
                .addField("Restock Stacks", traderConfig.restockStacks)
                .addField("Restock Emerald Count Threshold", traderConfig.restockEmeraldCountThreshold)
                .addField("Restock Chest", "||" + (CONFIG.discord.reportCoords ? traderConfig.restockChest : "Coords disabled") + "||")
                .addField("Store Chest", "||" + (CONFIG.discord.reportCoords ? traderConfig.storeChest : "Coords disabled") + "||")
                .addField("Book Restock Chest", "||" + (CONFIG.discord.reportCoords ? traderConfig.bookRestockChest : "Coords disabled") + "||")
                .addField("Villager Trade Restock Wait", traderConfig.villagerTradeRestockWaitSeconds + "s")
                .addField("Max Spend Per Trade", traderConfig.maxSpendPerTrade)
                .addField("Item Max Spend Settings", traderConfig.itemMaxSpendPerTrade.isEmpty() ? "[None]" :
                    traderConfig.itemMaxSpendPerTrade.entrySet().stream()
                        .map(e -> e.getKey() + ":" + e.getValue())
                        .collect(Collectors.joining(", ", "[", "]")))
                .addField("Buy Item Store Stacks Threshold", traderConfig.buyItemStoreStacksThreshold + " stacks")
                .addField("Wait For Interact Timeout", traderConfig.waitForInteractTimeoutTicks + " ticks")
                .addField("Desired Enchantments", traderConfig.desiredEnchantments.isEmpty() ? "[None]" : enchantmentsStr)
                .addField("Only Buy Desired Enchantments", toggleStr(traderConfig.onlyBuyDesiredEnchantments))
                .addField("Only Buy Max Level Enchantments", toggleStr(traderConfig.onlyBuyMaxLevelEnchantments))
                .addField("avaliable", """
                                                    NONE=无业
                                                    ARMORER=盔甲匠
                                                    BUTCHER=屠夫
                                                    CARTOGRAPHER=制图师
                                                    CLERIC=牧师
                                                    FARMER=农民
                                                    FISHERMAN=渔夫
                                                    FLETCHER=制箭师
                                                    LEATHERWORKER=皮匠
                                                    LIBRARIAN=图书管理员
                                                    MASON=石匠
                                                    NITWIT=傻子
                                                    SHEPHERD=牧羊人
                                                    TOOLSMITH=工具匠
                                                    WEAPONSMITH=武器匠
                                                    """)
                .primaryColor();
    }
}
