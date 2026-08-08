package com.github.futa.config;

import com.github.futa.module.AutoEnchantModule;
import com.google.common.collect.Lists;
import com.zenith.mc.block.BlockPos;
import com.zenith.mc.item.ItemRegistry;

import java.util.*;

public class AutoEnchantConfig {
    public boolean enabled = false;
    public List<BlockPos> equipmentChests = Lists.newArrayList();
    public List<BlockPos> enchantBookChests = Lists.newArrayList();
    public List<BlockPos> resultChests = Lists.newArrayList();
    public BlockPos failChest = BlockPos.ZERO;
    public BlockPos xpFarmPos = BlockPos.ZERO;
    public int anvilSearchRadius = 26;
    public long delayBetweenActions = 40L;
    public int actionDelayTick = 40;
    public boolean pauseKillAura = false;

    public Map<String, EnchantStrategy> enchant = new LinkedHashMap<>();

    public Map<String, List<String>> names = new LinkedHashMap<>();

    // Debug options
    public boolean debugMode = false;

    public void init() {
        if (enchant.isEmpty()) {

            enchant.put(ItemRegistry.DIAMOND_SWORD.name(), new EnchantStrategy(Lists.newArrayList("sweeping_edge", "looting", "sharpness", "fire_aspect", "unbreaking", "knockback", "mending")));
            enchant.put(ItemRegistry.DIAMOND_PICKAXE.name(), new EnchantStrategy(Lists.newArrayList("efficiency", "silk_touch", "unbreaking", "mending")));
            enchant.put(ItemRegistry.DIAMOND_AXE.name(), new EnchantStrategy(Lists.newArrayList("efficiency", "unbreaking", "mending")));
            enchant.put(ItemRegistry.DIAMOND_SHOVEL.name(), new EnchantStrategy(Lists.newArrayList("efficiency", "unbreaking", "mending")));
            enchant.put(ItemRegistry.DIAMOND_HOE.name(), new EnchantStrategy(Lists.newArrayList("efficiency", "unbreaking", "mending")));
            enchant.put(ItemRegistry.DIAMOND_HELMET.name(), new EnchantStrategy(Lists.newArrayList("respiration", "protection", "unbreaking", "mending", "aqua_affinity")));
            enchant.put(ItemRegistry.DIAMOND_CHESTPLATE.name(), new EnchantStrategy(Lists.newArrayList("protection", "unbreaking", "mending")));
            enchant.put(ItemRegistry.DIAMOND_LEGGINGS.name(), new EnchantStrategy(Lists.newArrayList("blast_protection", "unbreaking", "mending")));
            enchant.put(ItemRegistry.DIAMOND_BOOTS.name(), new EnchantStrategy(Lists.newArrayList("depth_strider", "feather_falling", "protection", "unbreaking", "mending")));
            enchant.put(ItemRegistry.MACE.name(), new EnchantStrategy(Lists.newArrayList("breach", "unbreaking", "mending")));
            enchant.put(ItemRegistry.ELYTRA.name(), new EnchantStrategy(Lists.newArrayList("unbreaking", "mending")));

        }

        if (names.isEmpty()) {
            names.put(ItemRegistry.DIAMOND_SWORD.name(), Lists.newArrayList(
                    "Edynia's Sword"
            ));


            names.put(ItemRegistry.DIAMOND_PICKAXE.name(), Lists.newArrayList(
                    "Edynia's Pickaxe"
            ));

            names.put(ItemRegistry.DIAMOND_AXE.name(), Lists.newArrayList(
                    "Edynia's Axe"
            ));

            names.put(ItemRegistry.DIAMOND_SHOVEL.name(), Lists.newArrayList(
                    "Edynia's Shovel"
            ));

            names.put(ItemRegistry.DIAMOND_HOE.name(), Lists.newArrayList(
                    "Edynia's Hoe"
            ));

            names.put(ItemRegistry.DIAMOND_HELMET.name(), Lists.newArrayList(
                    "Edynia's Helmet"
            ));

            names.put(ItemRegistry.DIAMOND_CHESTPLATE.name(), Lists.newArrayList(
                    "Edynia's Chestplate"
            ));

            names.put(ItemRegistry.DIAMOND_LEGGINGS.name(), Lists.newArrayList(
                    "Edynia's Leggings"
            ));

            names.put(ItemRegistry.DIAMOND_BOOTS.name(), Lists.newArrayList(
                    "Edynia's Boots"
            ));

            names.put(ItemRegistry.ELYTRA.name(), Lists.newArrayList(
                    "Edynia's Elytra"
            ));

            names.put(ItemRegistry.MACE.name(), Lists.newArrayList(
                    "Edynia's Mace"
            ));

        }

    }

    /**
     * Return a random name from the list
     *
     * @return the randomly selected name
     */
    public String getRandomName(String type) {
        List<String> namesList = names.get(type);
        Random random = new Random();
        int index = random.nextInt(namesList.size());
        return namesList.get(index);
    }

    public EnchantStrategy getEquipmentStrategy(AutoEnchantModule.EquipmentType equipment) {
        return enchant.get(equipment);
    }

    public EnchantStrategy getEquipmentStrategy(String equipment) {
        return enchant.get(equipment);
    }

    // Enchant strategy configuration class
    public static class EnchantStrategy {
        public List<String> enchantments = Lists.newArrayList();
        public boolean enabled = true;

        // Constructor sets default values
        public EnchantStrategy() {
            // Default empty; set during initialization below
        }

        public EnchantStrategy(List<String> defaultEnchantments) {
            this.enchantments = new ArrayList<>(defaultEnchantments);
        }
    }
}
