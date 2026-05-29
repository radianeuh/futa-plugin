package com.github.futa.util;

import com.github.futa.config.ItemSorterConfig;
import com.zenith.mc.item.ItemData;
import com.zenith.mc.item.ItemRegistry;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponents;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.ItemEnchantments;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 物品分类器 - 负责将物品分类到不同的类别中
 */
public class ItemClassifier {

    private final ItemSorterConfig config;

    public ItemClassifier(ItemSorterConfig config) {
        this.config = config;
    }

    /**
     * 获取物品的分类
     *
     * @param itemStack 物品堆栈
     * @return 分类名称
     */
    public String classifyItem(ItemStack itemStack) {
        if (itemStack == null) {
            return "unknown_item";
        }

        ItemData itemData = ItemRegistry.REGISTRY.get(itemStack.getId());
        if (itemData == null) {
            return "unknown_item";
        }

        String itemName = itemData.name();

        // 1. 首先检查用户自定义分类
        for (Map.Entry<String, List<String>> entry : config.customCategories.entrySet()) {
            if (entry.getValue().contains(itemName)) {
                return entry.getKey();
            }
        }

//        // 2. 如果启用智能分类，基于物品属性进行分类
//        if (config.enableSmartClassification) {
//            String smartCategory = classifyByAttributes(itemData, itemStack);
//            if (smartCategory != null) {
//                return smartCategory;
//            }
//        }

        // 3. 缺省分类：物品名称本身就是分类
        return itemData.name();
    }

    /**
     * 基于物品属性进行智能分类
     */
    private String classifyByAttributes(ItemData itemData, ItemStack itemStack) {
        String itemName = itemData.name();

        // 潜影盒特殊处理
        if (isShulkerBox(itemName)) {
            String shulkerCategory = classifyShulkerBox(itemStack);
            if (shulkerCategory != null) {
                return shulkerCategory;
            }
            return ItemRegistry.SHULKER_BOX.name();
        }

        // 附魔书特殊处理
        if ("enchanted_book".equals(itemName)) {
            return "enchanted_books";
        }

        // 超稀有物品（生存可获得）
        if (isUltraRare(itemName)) {
            return "ultra_rare";
        }

        // 音乐唱片
        if (itemName.startsWith("music_disc_")) {
            return "music_discs";
        }

        // 陶片
        if (itemName.endsWith("_pottery_sherd")) {
            return "pottery_sherds";
        }

        // 药水
        if (isPotion(itemName)) {
            return "potions";
        }

        // 垃圾物品
        if (isTrashItem(itemName)) {
            return "trash";
        }

        // 颜色变种物品检查
        String colorVariant = getColorVariantCategory(itemName);
        if (colorVariant != null) {
            return colorVariant;
        }

        // 缺省分类：如果没有找到任何分类，物品名称本身就是分类
        return itemName; // 物品自己单独作为一个分类
    }

    private boolean isUltraRare(String itemName) {
        return itemName.equals("elytra") || itemName.equals("totem_of_undying") ||
                itemName.equals("nether_star") || itemName.equals("beacon") || itemName.equals("conduit") ||
                itemName.equals("dragon_head"); // 龙蛋是创造模式物品，移除
    }

    /**
     * 获取颜色变种物品的分类
     */
    private String getColorVariantCategory(String itemName) {
        // 羊毛
        if (itemName.endsWith("_wool")) {
            return "wool";
        }

        // 混凝土
        if (itemName.endsWith("_concrete")) {
            return "concrete";
        }

        // 陶瓦
        if (itemName.endsWith("_terracotta") || itemName.equals("terracotta")) {
            return "terracotta";
        }

        // 玻璃
        if (itemName.endsWith("_glass") || itemName.equals("glass") || itemName.equals("tinted_glass")) {
            return "glass";
        }

        // 地毯
        if (itemName.endsWith("_carpet")) {
            return "carpet";
        }

        // 床
        if (itemName.endsWith("_bed")) {
            return "bed";
        }

        return null;
    }


    private boolean isPotion(String itemName) {
        return itemName.contains("potion") || itemName.equals("glass_bottle") ||
                itemName.equals("dragon_breath") || itemName.equals("experience_bottle") ||
                itemName.equals("ominous_bottle");
    }

    private boolean isTrashItem(String itemName) {
        return itemName.equals("rotten_flesh") || itemName.equals("spider_eye") ||
                itemName.equals("fermented_spider_eye") || itemName.equals("bone") ||
                itemName.equals("string") || itemName.equals("feather") ||
                itemName.equals("leather") || itemName.equals("rabbit_hide") || itemName.equals("slime_ball") ||
                itemName.equals("stick") || itemName.equals("phantom_membrane");
    }

    /**
     * 检查物品是否有附魔
     */
    public boolean isEnchanted(ItemStack itemStack) {
        if (itemStack == null) return false;

        try {
            DataComponents dataComponents = itemStack.getDataComponents();
            if (dataComponents == null) return false;

            // 检查存储的附魔（附魔书）
            ItemEnchantments storedEnchantments = dataComponents.get(DataComponentTypes.STORED_ENCHANTMENTS);
            if (storedEnchantments != null && !storedEnchantments.getEnchantments().isEmpty()) {
                return true;
            }

            // 检查物品附魔
            ItemEnchantments itemEnchantments = dataComponents.get(DataComponentTypes.ENCHANTMENTS);
            if (itemEnchantments != null && !itemEnchantments.getEnchantments().isEmpty()) {
                return true;
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取物品的稀有度评分（用于优先级排序）
     *
     * @param itemStack 物品堆栈
     * @return 稀有度评分，数值越高越稀有
     */
    public int getRarityScore(ItemStack itemStack) {
        if (itemStack == null) return 0;

        ItemData itemData = ItemRegistry.REGISTRY.get(itemStack.getId());
        if (itemData == null) return 0;

        String itemName = itemData.name();
        int score = 0;

        // 超稀有物品最高优先级（生存可获得）
        if (itemName.equals("elytra")) score += 500;
        else if (itemName.equals("totem_of_undying")) score += 400;
        else if (itemName.equals("nether_star")) score += 350;
        else if (itemName.equals("beacon")) score += 300;
        else if (itemName.equals("conduit")) score += 280;
        else if (itemName.equals("dragon_head")) score += 250;

            // 特殊收藏品（生存可获得）
        else if (itemName.startsWith("music_disc_")) score += 200;
        else if (itemName.endsWith("_pottery_sherd")) score += 180;

            // 附魔书
        else if (itemName.equals("enchanted_book")) score += 120;

        // 基础材质稀有度评分
        if (itemName.contains("netherite")) score += 100;
        else if (itemName.contains("diamond")) score += 80;
        else if (itemName.contains("emerald")) score += 70;
        else if (itemName.contains("gold")) score += 50;
        else if (itemName.contains("iron")) score += 30;
        else if (itemName.contains("copper")) score += 20;

        // 附魔加分
        if (isEnchanted(itemStack)) {
            score += 50;
        }

        // 垃圾物品降低优先级
        if (isTrashItem(itemName)) {
            score -= 100;
        }

        return Math.max(0, score); // 确保不会是负数
    }

    /**
     * 检查物品是否为潜影盒
     */
    private boolean isShulkerBox(String itemName) {

        return itemName.contains(ItemRegistry.SHULKER_BOX.name());
    }

    /**
     * 对潜影盒进行特殊分类处理
     * 通过分析盒内物品，找出数量最多的物品类型作为该潜影盒的分类
     */
    private String classifyShulkerBox(ItemStack shulkerBoxStack) {
        if (shulkerBoxStack == null) {
            return null;
        }

        try {
            DataComponents dataComponents = shulkerBoxStack.getDataComponents();
            if (dataComponents == null) {
                return null;
            }

            // 获取容器内的物品列表
            @SuppressWarnings("unchecked")
            List<ItemStack> containerItems = dataComponents.get(DataComponentTypes.CONTAINER);
            if (containerItems == null || containerItems.isEmpty()) {
                return null;
            }

            // 统计每种物品类型的数量
            Map<String, Integer> itemTypeCounts = new HashMap<>();

            for (ItemStack item : containerItems) {
                if (item == null) continue;

                ItemData itemData = ItemRegistry.REGISTRY.get(item.getId());
                if (itemData == null) continue;

                // 递归分类盒内物品（避免无限递归，潜影盒内的潜影盒直接按名称分类）
                String itemType;
                if (isShulkerBox(itemData.name())) {
                    itemType = itemData.name(); // 潜影盒内的潜影盒直接用物品名称
                } else {
                    itemType = classifyByAttributes(itemData, item);
                }

                int count = item.getAmount();
                itemTypeCounts.put(itemType, itemTypeCounts.getOrDefault(itemType, 0) + count);
            }

            // 找出数量最多的物品类型
            String dominantType = null;
            int maxCount = 0;

            for (Map.Entry<String, Integer> entry : itemTypeCounts.entrySet()) {
                if (entry.getValue() > maxCount) {
                    maxCount = entry.getValue();
                    dominantType = entry.getKey();
                }
            }

            return dominantType;

        } catch (Exception e) {
            // 如果解析失败，返回null让其按默认逻辑处理
            return null;
        }
    }
}
