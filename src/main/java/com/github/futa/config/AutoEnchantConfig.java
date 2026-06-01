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
    public long delayBetweenActions = 5L;
    public int actionDelayTick = 1;
    public boolean pauseKillAura = true;

    public Map<String, EnchantStrategy> enchant = new LinkedHashMap<>();

    public Map<String, List<String>> names = new LinkedHashMap<>();

    // 调试选项
    public boolean debugMode = false;

    public void init() {
        if (enchant.isEmpty()) {
            // 各装备类型的附魔策略配置
            enchant.put(ItemRegistry.ELYTRA.name(), new EnchantStrategy(Lists.newArrayList("unbreaking", "mending")));
            enchant.put(ItemRegistry.DIAMOND_AXE.name(), new EnchantStrategy(Lists.newArrayList("efficiency", "unbreaking", "mending")));
            enchant.put(ItemRegistry.DIAMOND_SHOVEL.name(), new EnchantStrategy(Lists.newArrayList("efficiency", "unbreaking", "mending")));
            enchant.put(ItemRegistry.DIAMOND_HOE.name(), new EnchantStrategy(Lists.newArrayList("efficiency", "unbreaking", "mending")));
            enchant.put(ItemRegistry.MACE.name(), new EnchantStrategy(Lists.newArrayList("breach", "unbreaking", "mending")));
            enchant.put(ItemRegistry.DIAMOND_SWORD.name(), new EnchantStrategy(Lists.newArrayList("sweeping_edge", "looting", "sharpness", "fire_aspect", "unbreaking", "knockback", "mending")));
            enchant.put(ItemRegistry.DIAMOND_PICKAXE.name(), new EnchantStrategy(Lists.newArrayList("efficiency", "silk_touch", "unbreaking", "mending")));
            enchant.put(ItemRegistry.DIAMOND_HELMET.name(), new EnchantStrategy(Lists.newArrayList("respiration", "protection", "unbreaking", "mending", "aqua_affinity")));
            enchant.put(ItemRegistry.DIAMOND_CHESTPLATE.name(), new EnchantStrategy(Lists.newArrayList("protection", "unbreaking", "mending")));
            enchant.put(ItemRegistry.DIAMOND_LEGGINGS.name(), new EnchantStrategy(Lists.newArrayList("blast_protection", "unbreaking", "mending")));
            enchant.put(ItemRegistry.DIAMOND_BOOTS.name(), new EnchantStrategy(Lists.newArrayList("depth_strider", "feather_falling", "protection", "unbreaking", "mending")));
        }
        if (names.isEmpty()) {
            // 钻石剑
            names.put(ItemRegistry.DIAMOND_SWORD.name(), Lists.newArrayList(
                    "RR·断星之刃",
                    "RR·霜语",
                    "RR·影噬者",
                    "RR·誓约之锋",
                    "RR·永夜裁决",
                    "RR·清辉断刃",
                    "RR·血月裁决",
                    "RR·空寂长鸣",
                    "RR·殒星裂空",
                    "RR·黎明终焉",
                    "RR·破晓之剑",
                    "RR·雷霆之刃",
                    "RR·无尽斩",
                    "RR·暗影利刃",
                    "RR·烈焰之剑",
                    "RR·冰霜之锋",
                    "RR·光明守护",
                    "RR·狂风剑",
                    "RR·神罚之刃",
                    "RR·苍穹剑",
                    "RR·虚空",
                    "RR·破晓长歌",
                    "RR·血色残阳",
                    "RR·星渊幻影",
                    "RR·云霄裂响",
                    "RR·冥光寂灭"
            ));

            // 钻石头盔
            names.put(ItemRegistry.DIAMOND_HELMET.name(), Lists.newArrayList(
                    "RR·山岳之眸",
                    "RR·风语者之冠",
                    "RR·烬焰头盔",
                    "RR·幽林之冠",
                    "RR·星陨之眼",
                    "RR·星穹之冠",
                    "RR·苍穹守望者",
                    "RR·霜月之颅",
                    "RR·龙眠之冠",
                    "RR·晨曦守誓者",
                    "RR·晨曦庇佑",
                    "RR·永夜之冠",
                    "RR·苍穹望月",
                    "RR·深渊之瞳",
                    "RR·寂灭王冕",
                    "RR·暗影之盔",
                    "RR·雷霆头盔",
                    "RR·烈焰护首",
                    "RR·苍穹护顶",
                    "RR·狂风头盔",
                    "RR·星海浮梦",
                    "RR·流光初晖",
                    "RR·归墟长夜",
                    "RR·雨落惊鸿",
                    "RR·天问残响"
            ));

            // 钻石胸甲
            names.put(ItemRegistry.DIAMOND_CHESTPLATE.name(), Lists.newArrayList(
                    "RR·山岳之心",
                    "RR·风语者之躯",
                    "RR·烬焰胸铠",
                    "RR·幽林誓甲",
                    "RR·星陨之躯",
                    "RR·赤焰心铠",
                    "RR·流光圣躯",
                    "RR·狂澜铁壁",
                    "RR·虚空之心",
                    "RR·曙光",
                    "RR·雷霆护甲",
                    "RR·苍穹战衣",
                    "RR·虚空战甲",
                    "RR·赤炎逐日",
                    "RR·沧溟心曲",
                    "RR·云海之心",
                    "RR·黯夜晨曦",
                    "RR·烁影流霞"
            ));

            // 钻石护腿
            names.put(ItemRegistry.DIAMOND_LEGGINGS.name(), Lists.newArrayList(
                    "RR·踏云行者",
                    "RR·荒原疾影",
                    "RR·霜蹄",
                    "RR·星轨",
                    "RR·疾风逐影",
                    "RR·沉星",
                    "RR·黄昏残响",
                    "RR·破晓",
                    "RR·雷霆",
                    "RR·暗影",
                    "RR·苍穹",
                    "RR·虚空",
                    "RR·青霜叹息",
                    "RR·九霄孤行",
                    "RR·长风归路",
                    "RR·落尘千里",
                    "RR·逐月余歌"
            ));

            // 钻石靴子
            names.put(ItemRegistry.DIAMOND_BOOTS.name(), Lists.newArrayList(
                    "RR·逐风之履",
                    "RR·深渊低语",
                    "RR·地脉行者",
                    "RR·月影步履",
                    "RR·归途之履",
                    "RR·星辰逐日",
                    "RR·无声暗影",
                    "RR·踏火凌霜",
                    "RR·孤峰绝路",
                    "RR·幻梦之途",
                    "RR·疾风之靴",
                    "RR·雷霆战靴",
                    "RR·暗影轻靴",
                    "RR·虚空步履",
                    "RR·苍穹战靴",
                    "RR·风岚逐影",
                    "RR·星辰踏歌",
                    "RR·烈阳残梦",
                    "RR·破雪行歌",
                    "RR·夜渡惊鸿"
            ));

            // 钻石镐
            names.put(ItemRegistry.DIAMOND_PICKAXE.name(), Lists.newArrayList(
                    "RR·裂岩之诗",
                    "RR·星核凿",
                    "RR·地脉低语者",
                    "RR·千山碎骨者",
                    "RR·晨光之凿",
                    "RR·碎星之契",
                    "RR·大地裂痕",
                    "RR·沉眠之岩",
                    "RR·幽光破境",
                    "RR·远古遗痕",
                    "RR·地心探险者",
                    "RR·矿石猎手",
                    "RR·坚不可摧",
                    "RR·元素之镐",
                    "RR·深渊开拓者",
                    "RR·巨岩破碎者",
                    "RR·精钢利镐",
                    "RR·雷霆挖掘",
                    "RR·金刚开山镐",
                    "RR·深矿征服者",
                    "RR·地脉回声",
                    "RR·岩心怒吼",
                    "RR·逐矿之魂",
                    "RR·裂峰残响",
                    "RR·苍穹回掘"
            ));

            // 鞘翅
            names.put(ItemRegistry.ELYTRA.name(), Lists.newArrayList(
                    "RR·风之遗书",
                    "RR·云渡之翼",
                    "RR·星尘之羽",
                    "RR·苍穹旅人",
                    "RR·终焉之翔",
                    "RR·流光之翼",
                    "RR·星海逐风",
                    "RR·破晓翔羽",
                    "RR·永夜残翼",
                    "RR·苍穹逐梦",
                    "RR·苍穹之翼",
                    "RR·虚空之翼",
                    "RR·雷霆之翼",
                    "RR·暗影之翼",
                    "RR·光辉之翼",
                    "RR·疾风之翼",
                    "RR·流风逐影",
                    "RR·星河之渡",
                    "RR·云梦归途",
                    "RR·翔空吟咏",
                    "RR·幽岚月行"
            ));

            // 钻石斧
            names.put(ItemRegistry.DIAMOND_AXE.name(), Lists.newArrayList(
                    "RR·雷霆之斧"
            ));

            // 锤
            names.put(ItemRegistry.MACE.name(), Lists.newArrayList(
                    "RR·月影之锤",
                    "RR·风语",
                    "RR·星辰",
                    "RR·月华",
                    "RR·雷霆"
            ));

        }

    }


    /**
     * 随机返回一条name
     *
     * @return 随机选中的name
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

    // 附魔策略配置类
    public static class EnchantStrategy {
        public List<String> enchantments = Lists.newArrayList();
        public boolean enabled = true;

        // 构造器设置默认值
        public EnchantStrategy() {
            // 默认空，在下面的初始化中设置
        }

        public EnchantStrategy(List<String> defaultEnchantments) {
            this.enchantments = new ArrayList<>(defaultEnchantments);
        }
    }
}
