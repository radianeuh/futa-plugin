package com.github.futa.util;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.github.futa.module.DeathLogger;
import com.zenith.cache.data.inventory.Container;
import com.zenith.mc.enchantment.EnchantmentData;
import com.zenith.mc.enchantment.EnchantmentRegistry;
import com.zenith.mc.item.ItemRegistry;
import com.zenith.util.ComponentSerializer;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponents;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.ItemEnchantments;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

public class EnchantmentUtil {

    public static JSONObject chineseBookName = JSON.parseObject(EnchantmentUtil.class.getResourceAsStream("/mcdata/bookname.json"));

    public static JSONObject enchantingCost = JSON.parseObject(EnchantmentUtil.class.getResourceAsStream("/mcdata/enchanting_cost.json"));

    public static int getEnchantmentLevel(ItemStack item, EnchantmentData enchantmentData) {
        return Optional.of(item)
                .filter(i -> i != Container.EMPTY_STACK)
                .map(ItemStack::getDataComponents)
                .map(dataComponents -> dataComponents.get(DataComponentTypes.ENCHANTMENTS))
                .map(ItemEnchantments::getEnchantments)
                .map(enchantments -> enchantments.get(enchantmentData.id()))
                .orElse(0);
    }


    public static Set<String> getUsefulBookName() {
        InputStream inputStream = DeathLogger.class.getResourceAsStream("/mcdata/bookneeded.json");
        JSONObject json = JSON.parseObject(inputStream);

        return json.keySet();
    }

    public static String getChinese(String name) {

        String chinese = chineseBookName.getString(name);
        if (StrUtil.isEmpty(chinese)) {
            return name;
        }
        return chinese;
    }


    public static boolean isEnchantedBook(ItemStack itemStack) {
        return itemStack != null && itemStack.getId() == ItemRegistry.ENCHANTED_BOOK.id();
    }

    public static boolean isMaxLevel(String name, int level) {

        return getMaxLevel(name) == level;
    }

    public static int getMaxLevel(String name) {
        EnchantmentData data = EnchantmentRegistry.REGISTRY.get(name);
        if (data == null) {
            return 0;
        }
        return data.maxLevel();
    }

    /**
     * 获取当前版本的所有附魔
     *
     * @return
     */
    public static List<String> getAllEnchantment() {
        List<String> enchantments = new ArrayList<>();
        for (int i = 0; i < EnchantmentRegistry.REGISTRY.size(); i++) {
            EnchantmentData data = EnchantmentRegistry.REGISTRY.get(i);
            enchantments.add(data.name());
        }
        return enchantments;
    }


    public static Map<String, Integer> getEnchantmentMapNoStream(ItemStack itemStack) {
        Map<String, Integer> enchantments = new HashMap<>();
        DataComponents components = itemStack.getDataComponents();
        if (components == null) return new HashMap<>();
        ItemEnchantments itemEnchantments = components.get(DataComponentTypes.STORED_ENCHANTMENTS);
        if (itemEnchantments != null) {
            for (var enchantEntry : itemEnchantments.getEnchantments().int2IntEntrySet()) {
                int enchantmentId = enchantEntry.getIntKey();
                int level = enchantEntry.getIntValue();
                EnchantmentData enchantmentData = EnchantmentRegistry.REGISTRY.get(enchantmentId);
                if (enchantmentData != null) {
                    enchantments.put(enchantmentData.name(), level);
                }
            }
        }
        return enchantments;
    }


    /**
     * 获取物品的附魔
     *
     * @param itemStack
     * @return
     */
    public static Map<String, Integer> getEnchantmentMapItem(ItemStack itemStack) {
        return Optional.ofNullable(itemStack.getDataComponents())
                .map(components -> components.get(DataComponentTypes.ENCHANTMENTS))
                .map(ItemEnchantments::getEnchantments)
                .map(enchantments -> enchantments.int2IntEntrySet().stream()
                        .collect(Collectors.toMap(
                                entry -> Optional.ofNullable(EnchantmentRegistry.REGISTRY.get(entry.getIntKey()))
                                        .map(EnchantmentData::name)
                                        .orElse("unknown_" + entry.getIntKey()),
                                entry -> entry.getIntValue(),
                                (existing, replacement) -> replacement // 处理重复key的情况
                        )))
                .orElse(new HashMap<>());
    }

    /**
     * 获取物品的附魔，只返回最高级
     *
     * @param itemStack
     * @return
     */
    public static Map<String, Integer> getEnchantmentMapItemOnlyMax(ItemStack itemStack) {
        Map<String, Integer> map = getEnchantmentMapItem(itemStack);
        map.entrySet().removeIf(entry -> entry.getValue() < getMaxLevel(entry.getKey()));

        return map;
    }

    public static Integer getEnchantingCost(String enchant) {
        return enchantingCost.getIntValue(enchant, 0);
    }

    public static Integer getItemRepairCost(ItemStack itemStack) {
        return Optional.ofNullable(itemStack.getDataComponents())
                .map(components -> components.get(DataComponentTypes.REPAIR_COST))
                .orElse(0);
    }

    public static String getCustomName(ItemStack itemStack) {
        return Optional.ofNullable(itemStack.getDataComponents())
                .map(components -> components.get(DataComponentTypes.CUSTOM_NAME))
                .map(component -> ComponentSerializer.serializePlain(component))
                .orElse("");
    }

    /**
     * 计算一次铁砧合并所需的等级
     *
     * @param penaltyLevel 物品当前的 repair cost (惩罚等级)
     * @param enchantCost  被合并项的附魔费用
     * @return 该次铁砧操作所需的等级
     */
    public static int calculateAnvilCost(int penaltyLevel, int enchantCost) {
        // 实际上惩罚费用就是 repairCost 本身
        return penaltyLevel + enchantCost;
    }


    /**
     * 计算将某个附魔书附到物品上所需的等级
     *
     * @param itemStack 目标物品
     * @param enchant   附魔名
     * @return 铁砧操作所需的等级
     */
    public static int calculateAnvilCostForItem(ItemStack itemStack, String enchant) {
        // 当前物品的累积惩罚等级（repair cost）
        int penaltyLevel = getItemRepairCost(itemStack);

        // 附魔书的附魔费用
        int enchantCost = getEnchantingCost(enchant);

        // 调用已有方法计算
        return calculateAnvilCost(penaltyLevel, enchantCost);
    }

    /**
     * 计算将某个附魔书附到物品上所需的等级
     *
     * @param itemStack 目标物品
     * @param enchant   附魔名
     * @return 铁砧操作所需的等级
     */
    public static int calculateAnvilCostForItem(ItemStack itemStack, ItemStack enchant) {
        if (enchant == null || itemStack == null) {
            return 0;
        }

        // 当前物品的累积惩罚等级（repair cost）
        int enchantCost = getItemRepairCost(itemStack);

        enchantCost = enchantCost + getItemRepairCost(enchant);

        for (String string : getEnchantmentMap(enchant).keySet()) {
            // 附魔书的附魔费用
            enchantCost += getEnchantingCost(string);
        }

        // 调用已有方法计算
        return enchantCost;
    }

    /**
     * 获取附魔书的附魔
     *
     * @param itemStack
     * @return
     */
    public static Map<String, Integer> getEnchantmentMap(ItemStack itemStack) {
        return Optional.ofNullable(itemStack.getDataComponents())
                .map(components -> components.get(DataComponentTypes.STORED_ENCHANTMENTS))
                .map(ItemEnchantments::getEnchantments)
                .map(enchantments -> enchantments.int2IntEntrySet().stream()
                        .collect(Collectors.toMap(
                                entry -> Optional.ofNullable(EnchantmentRegistry.REGISTRY.get(entry.getIntKey()))
                                        .map(EnchantmentData::name)
                                        .orElse("unknown_" + entry.getIntKey()),
                                entry -> entry.getIntValue(),
                                (existing, replacement) -> replacement // 处理重复key的情况
                        )))
                .orElse(new HashMap<>());
    }

    /**
     * 获取附魔书的附魔
     *
     * @param itemStack
     * @return
     */
    public static Map<String, Integer> getBookEnchantmentMapMaxLevel(ItemStack itemStack) {
        return Optional.ofNullable(itemStack.getDataComponents())
                .map(components -> components.get(DataComponentTypes.STORED_ENCHANTMENTS))
                .map(ItemEnchantments::getEnchantments)
                .map(enchantments -> enchantments.int2IntEntrySet().stream()
                        .map(entry -> Map.entry(EnchantmentRegistry.REGISTRY.get(entry.getIntKey()).name(),
                                entry.getIntValue()))
                        // 使用独立方法判断
                        .filter(e -> isMaxLevel(e.getKey(), e.getValue()))
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue,
                                (existing, replacement) -> replacement
                        )))
                .orElse(new HashMap<>());
    }

    public static Map<String, Integer> getEnchantmentMapCN(ItemStack itemStack) {
        return Optional.ofNullable(itemStack.getDataComponents())
                .map(components -> components.get(DataComponentTypes.STORED_ENCHANTMENTS))
                .map(ItemEnchantments::getEnchantments)
                .map(enchantments -> enchantments.int2IntEntrySet().stream()
                        .collect(Collectors.toMap(
                                entry -> Optional.ofNullable(EnchantmentRegistry.REGISTRY.get(entry.getIntKey()))
                                        .map(EnchantmentData::name)
                                        .map(EnchantmentUtil::getChinese)
                                        .orElse("unknown_" + entry.getIntKey()),
                                entry -> entry.getIntValue(),
                                (existing, replacement) -> replacement // 处理重复key的情况
                        )))
                .orElse(new HashMap<>());
    }


    public static String getEnchantmentJsonItemCN(ItemStack itemStack) {
        if (itemStack == null) {
            return "{}";
        }

        Map<String, Integer> mapItem = getEnchantmentMapItem(itemStack);
        Map<String, Integer> cnmap = mapItem.entrySet().stream()
                .collect(Collectors.toMap(entry -> getChinese(entry.getKey()), entry -> entry.getValue()));

        return mapToJsonStringStream(cnmap);
    }

    public static String getEnchantmentJson(ItemStack itemStack) {
        return mapToJsonStringStream(getEnchantmentMapCN(itemStack));
    }


    // 使用Stream API的版本
    public static String mapToJsonStringStream(Map<String, Integer> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }

        return map.entrySet().stream()
                .map(entry -> "\"" + entry.getKey() + "\":" + entry.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }
}
