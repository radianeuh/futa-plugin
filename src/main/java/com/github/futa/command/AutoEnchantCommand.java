package com.github.futa.command;

import com.github.futa.FutaPlugin;
import com.github.futa.config.AutoEnchantConfig;
import com.github.futa.module.AutoEnchantModule;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;
import com.zenith.mc.item.ItemRegistry;

import java.util.Arrays;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static com.zenith.Globals.CONFIG;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.BlockPosArgument.blockPos;
import static com.zenith.command.brigadier.BlockPosArgument.getBlockPos;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class AutoEnchantCommand extends Command {

    AutoEnchantConfig enchantConfig = FutaPlugin.PLUGIN_CONFIG.autoEnchant;

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
                .name("autoenchant")
                .category(CommandCategory.MODULE)
                .description("""
                        Automatically enchants diamond equipment with configured enchantment strategies.
                        
                        Only processes diamond equipment (sword, pickaxe, helmet, chestplate, leggings, boots).
                        Each equipment type has its own enchantment strategy configuration.
                        
                        Default enchantment strategies:
                        🔸 钻石头盔: 水下呼吸 III + 保护 IV + 耐久 III + 经验修补 + 水下速掘 I
                        🔸 钻石胸甲: 保护 IV + 耐久 III + 经验修补
                        🔸 钻石护腿: 爆炸保护 IV + 耐久 III + 经验修补
                        🔸 钻石靴子: 深海探索者 III + 摔落保护 IV + 保护 IV + 耐久 III + 经验修补
                        🔸 钻石镐: 效率 V + 耐久 III + 经验修补 + 精准采集 I
                        🔸 钻石剑: 横扫之刃 III + 抢夺 III + 锋利 V + 火焰附加 II + 耐久 III + 击退 II + 经验修补
                        
                        The module will collect experience, find equipment, match enchantment books,
                        use anvils to enchant, and store results automatically.
                        """)
                .usageLines(
                        "on/off",
                        "equipmentChest add <x> <y> <z>",
                        "equipmentChest del <index>",
                        "equipmentChest clear",
                        "equipmentChest list",
                        "enchantBookChest add <x> <y> <z>",
                        "enchantBookChest del <index>",
                        "enchantBookChest clear",
                        "enchantBookChest list",
                        "resultChest add <x> <y> <z>",
                        "resultChest del <index>",
                        "resultChest clear",
                        "resultChest list",
                        "xpFarm <x> <y> <z>",
                        "requiredLevel <level>",
                        "searchRadius <radius>",
                        "strategy <equipment> <enchants...>",
                        "maxEnchants <equipment> <max>",
                        "enableEquipment <equipment>",
                        "disableEquipment <equipment>",
                        "debug <true/false>"
                )
                .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("autoenchant")
                .then(argument("toggle", toggle()).executes(c -> {
                    enchantConfig.enabled = getToggle(c, "toggle");
                    MODULE.get(AutoEnchantModule.class).syncEnabledFromConfig();
                    c.getSource().getEmbed()
                            .title("Auto Enchant " + toggleStrCaps(enchantConfig.enabled))
                            .primaryColor();
                    return OK;
                }))
                .then(literal("equipmentChest")
                        .then(literal("add").then(argument("pos", blockPos()).executes(c -> {
                            enchantConfig.equipmentChests.add(getBlockPos(c, "pos"));
                            c.getSource().getEmbed()
                                    .title("Equipment Chest Added");
                            return OK;
                        })))
                        .then(literal("del").then(argument("index", integer(0, 100)).executes(c -> {
                            int index = getInteger(c, "index");
                            if (index < 0 || index >= enchantConfig.equipmentChests.size()) {
                                c.getSource().getEmbed()
                                        .title("Invalid Index")
                                        .description("Index must be between 0 and " + (enchantConfig.equipmentChests.size() - 1));
                                return ERROR;
                            }
                            enchantConfig.equipmentChests.remove(index);
                            c.getSource().getEmbed()
                                    .title("Equipment Chest Removed");
                            return OK;
                        })))
                        .then(literal("clear").executes(c -> {
                            enchantConfig.equipmentChests.clear();
                            c.getSource().getEmbed()
                                    .title("Equipment Chests Cleared");
                            return OK;
                        }))
                        .then(literal("list").executes(c -> {
                            if (enchantConfig.equipmentChests.isEmpty()) {
                                c.getSource().getEmbed()
                                        .title("Equipment Chests")
                                        .description("No equipment chests configured.");
                                return OK;
                            }

                            StringBuilder sb = new StringBuilder();
                            sb.append("Equipment Chests:\n\n");
                            for (int i = 0; i < enchantConfig.equipmentChests.size(); i++) {
                                sb.append("**").append(i).append("**: ")
                                        .append("||").append(CONFIG.discord.reportCoords ? enchantConfig.equipmentChests.get(i) : "Coords disabled")
                                        .append("||\n");
                            }

                            c.getSource().getEmbed()
                                    .title("Equipment Chests")
                                    .description(sb.toString());
                            return OK;
                        })))
                .then(literal("enchantBookChest")
                        .then(literal("add").then(argument("pos", blockPos()).executes(c -> {
                            enchantConfig.enchantBookChests.add(getBlockPos(c, "pos"));
                            c.getSource().getEmbed()
                                    .title("Enchant Book Chest Added");
                            return OK;
                        })))
                        .then(literal("del").then(argument("index", integer(0, 100)).executes(c -> {
                            int index = getInteger(c, "index");
                            if (index < 0 || index >= enchantConfig.enchantBookChests.size()) {
                                c.getSource().getEmbed()
                                        .title("Invalid Index")
                                        .description("Index must be between 0 and " + (enchantConfig.enchantBookChests.size() - 1));
                                return ERROR;
                            }
                            enchantConfig.enchantBookChests.remove(index);
                            c.getSource().getEmbed()
                                    .title("Enchant Book Chest Removed");
                            return OK;
                        })))
                        .then(literal("clear").executes(c -> {
                            enchantConfig.enchantBookChests.clear();
                            c.getSource().getEmbed()
                                    .title("Enchant Book Chests Cleared");
                            return OK;
                        }))
                        .then(literal("list").executes(c -> {
                            if (enchantConfig.enchantBookChests.isEmpty()) {
                                c.getSource().getEmbed()
                                        .title("Enchant Book Chests")
                                        .description("No enchant book chests configured.");
                                return OK;
                            }

                            StringBuilder sb = new StringBuilder();
                            sb.append("Enchant Book Chests:\n\n");
                            for (int i = 0; i < enchantConfig.enchantBookChests.size(); i++) {
                                sb.append("**").append(i).append("**: ")
                                        .append("||").append(CONFIG.discord.reportCoords ? enchantConfig.enchantBookChests.get(i) : "Coords disabled")
                                        .append("||\n");
                            }

                            c.getSource().getEmbed()
                                    .title("Enchant Book Chests")
                                    .description(sb.toString());
                            return OK;
                        })))
                .then(literal("resultChest")
                        .then(literal("add").then(argument("pos", blockPos()).executes(c -> {
                            enchantConfig.resultChests.add(getBlockPos(c, "pos"));
                            c.getSource().getEmbed()
                                    .title("Result Chest Added");
                            return OK;
                        })))
                        .then(literal("del").then(argument("index", integer(0, 100)).executes(c -> {
                            int index = getInteger(c, "index");
                            if (index < 0 || index >= enchantConfig.resultChests.size()) {
                                c.getSource().getEmbed()
                                        .title("Invalid Index")
                                        .description("Index must be between 0 and " + (enchantConfig.resultChests.size() - 1));
                                return ERROR;
                            }
                            enchantConfig.resultChests.remove(index);
                            c.getSource().getEmbed()
                                    .title("Result Chest Removed");
                            return OK;
                        })))
                        .then(literal("clear").executes(c -> {
                            enchantConfig.resultChests.clear();
                            c.getSource().getEmbed()
                                    .title("Result Chests Cleared");
                            return OK;
                        }))
                        .then(literal("list").executes(c -> {
                            if (enchantConfig.resultChests.isEmpty()) {
                                c.getSource().getEmbed()
                                        .title("Result Chests")
                                        .description("No result chests configured.");
                                return OK;
                            }

                            StringBuilder sb = new StringBuilder();
                            sb.append("Result Chests:\n\n");
                            for (int i = 0; i < enchantConfig.resultChests.size(); i++) {
                                sb.append("**").append(i).append("**: ")
                                        .append("||").append(CONFIG.discord.reportCoords ? enchantConfig.resultChests.get(i) : "Coords disabled")
                                        .append("||\n");
                            }

                            c.getSource().getEmbed()
                                    .title("Result Chests")
                                    .description(sb.toString());
                            return OK;
                        })))
                .then(literal("xpFarm").then(argument("pos", blockPos()).executes(c -> {
                    enchantConfig.xpFarmPos = getBlockPos(c, "pos");
                    c.getSource().getEmbed()
                            .title("Experience Farm Position Set");
                    return OK;
                })))
                .then(literal("failChest").then(argument("pos", blockPos()).executes(c -> {
                    enchantConfig.failChest = getBlockPos(c, "pos");
                    c.getSource().getEmbed()
                            .title("failChest Position Set");
                    return OK;
                })))
                .then(literal("searchRadius").then(argument("radius", integer(1, 50)).executes(c -> {
                    enchantConfig.anvilSearchRadius = getInteger(c, "radius");
                    c.getSource().getEmbed()
                            .title("Anvil Search Radius Set");
                    return OK;
                })))
                .then(literal("strategy").then(argument("equipment", string()).then(argument("enchants", string()).executes(c -> {
                    String equipment = getString(c, "equipment").toLowerCase();
                    String enchants = getString(c, "enchants");

                    AutoEnchantConfig.EnchantStrategy strategy = getEquipmentStrategy(equipment);
                    if (strategy == null) {
                        c.getSource().getEmbed()
                                .title("Invalid Equipment Type")
                                .description("Valid equipment types: sword, pickaxe, helmet, chestplate, leggings, boots");
                        return ERROR;
                    }

                    strategy.enchantments.clear();
                    strategy.enchantments.addAll(Arrays.asList(enchants.split("\\s+")));
                    c.getSource().getEmbed()
                            .title("Enchant Strategy Set")
                            .description("Set " + equipment + " strategy: " + String.join(", ", strategy.enchantments));
                    return OK;
                }))))
                .then(literal("debug").then(argument("toggle", toggle()).executes(c -> {
                    enchantConfig.debugMode = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                            .title("Debug Mode " + (enchantConfig.debugMode ? "Enabled" : "Disabled"));
                    return OK;
                })));
    }

    private AutoEnchantConfig.EnchantStrategy getEquipmentStrategy(String equipment) {
        return switch (equipment) {
            case "sword" -> enchantConfig.getEquipmentStrategy(ItemRegistry.DIAMOND_SWORD.name());
            case "pickaxe" -> enchantConfig.getEquipmentStrategy(ItemRegistry.DIAMOND_PICKAXE.name());
            case "helmet" -> enchantConfig.getEquipmentStrategy(ItemRegistry.DIAMOND_HELMET.name());
            case "chestplate" -> enchantConfig.getEquipmentStrategy(ItemRegistry.DIAMOND_CHESTPLATE.name());
            case "leggings" -> enchantConfig.getEquipmentStrategy(ItemRegistry.DIAMOND_LEGGINGS.name());
            case "boots" -> enchantConfig.getEquipmentStrategy(ItemRegistry.DIAMOND_BOOTS.name());
            default -> null;
        };
    }

    @Override
    public void defaultEmbed(Embed embed) {
        embed.title("Auto Enchant Configuration")
                .addField("Enabled", toggleStr(enchantConfig.enabled))
                .addField("Equipment Chests", enchantConfig.equipmentChests.size() + " configured")
                .addField("Enchant Book Chests", enchantConfig.enchantBookChests.size() + " configured")
                .addField("Result Chests", enchantConfig.resultChests.size() + " configured")
                .addField("XP Farm", "||" + (CONFIG.discord.reportCoords ? enchantConfig.xpFarmPos : "Coords disabled") + "||")
                .addField("Anvil Search Radius", String.valueOf(enchantConfig.anvilSearchRadius))
                .addField("Debug Mode", toggleStr(enchantConfig.debugMode))
                .addField("Equipment Strategies", formatStrategies())
                .primaryColor();
    }

    private String formatStrategies() {
        StringBuilder sb = new StringBuilder();

        sb.append("**Sword**: ").append(formatStrategy(enchantConfig.getEquipmentStrategy(ItemRegistry.DIAMOND_SWORD.name()))).append("\n");
        sb.append("**Pickaxe**: ").append(formatStrategy(enchantConfig.getEquipmentStrategy(ItemRegistry.DIAMOND_PICKAXE.name()))).append("\n");
        sb.append("**Helmet**: ").append(formatStrategy(enchantConfig.getEquipmentStrategy(ItemRegistry.DIAMOND_HELMET.name()))).append("\n");
        sb.append("**Chestplate**: ").append(formatStrategy(enchantConfig.getEquipmentStrategy(ItemRegistry.DIAMOND_CHESTPLATE.name()))).append("\n");
        sb.append("**Leggings**: ").append(formatStrategy(enchantConfig.getEquipmentStrategy(ItemRegistry.DIAMOND_LEGGINGS.name()))).append("\n");
        sb.append("**Boots**: ").append(formatStrategy(enchantConfig.getEquipmentStrategy(ItemRegistry.DIAMOND_BOOTS.name())));

        return sb.toString();
    }

    private String formatStrategy(AutoEnchantConfig.EnchantStrategy strategy) {
        String status = strategy.enabled ? "✅" : "❌";
        String enchants = strategy.enchantments.isEmpty() ? "None" : String.join(", ", strategy.enchantments);
        return status + " " + enchants;
    }

    // 获取装备类型的显示名称
    private String getEquipmentDisplayName(String equipmentType) {
        return switch (equipmentType) {
            case "sword" -> "钻石剑";
            case "pickaxe" -> "钻石镐";
            case "helmet" -> "钻石头盔";
            case "chestplate" -> "钻石胸甲";
            case "leggings" -> "钻石护腿";
            case "boots" -> "钻石靴子";
            default -> equipmentType;
        };
    }
}
