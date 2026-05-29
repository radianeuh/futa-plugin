package com.github.futa.config;

import com.github.futa.dto.ChestLocation;
import com.github.futa.util.EnchantmentUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AutoChestManagerConfig {


    // 是否启用模块
    public boolean enabled = false;

    // 垃圾桶坐标
    public int trashX = 0;
    public int trashY = 0;
    public int trashZ = 0;

    // 潜影盒放置坐标
    public int shulkerX = 0;
    public int shulkerY = 0;
    public int shulkerZ = 0;

    // 需要的物品列表（注意：enchanted_book 会通过附魔列表单独判断）
    public Set<String> wantedItems = new HashSet<>(List.of(
            "diamond_chestplate",
            "diamond_axe"
    ));

    // 需要的附魔列表（附魔书专用）
    public Set<String> wantedEnchantments = EnchantmentUtil.getUsefulBookName();


    // 垃圾物品列表
    public List<String> trashItems = new ArrayList<>(List.of(
            "flow_banner_pattern",
            "rotten_flesh",
            "bone",
            "potion",
            "crossbow",
            "spider_eye",
            "poisonous_potato",
            "dead_bush",
            "grass",
            "tallgrass",
            "seagrass",
            "kelp",
            "clay_ball",
            "dirt",
            "coarse_dirt",
            "gravel",
            "sand",
            "red_sand",
            "netherrack",
            "stone",
            "cobblestone",
            "andesite",
            "diorite",
            "granite",
            "deepslate",
            "tuff",
            "calcite",
            "blackstone",
            "basalt",
            "glowstone_dust",
            "gunpowder",
            "sugar",
            "paper",
            "stick",
            "flint",
            "pufferfish",
            "saddle",
            "lily_pad",
            "bowl",
            "stick",
            "tripwire_hook",
            "nautilus_shell",
            "leather_boots",
            "cod",
            "tropical_fish",
            "charcoal"
    ));

    // 处理间隔时间秒
    public int interval = 10;

    // 要处理的箱子坐标列表
    public List<ChestLocation> chestLocations = new ArrayList<>();


}
