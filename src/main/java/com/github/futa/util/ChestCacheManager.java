package com.github.futa.util;

import com.github.futa.config.ItemSorterConfig;
import com.github.futa.dto.ChestInfo;
import com.github.futa.dto.ChestLocation;
import com.github.futa.module.ItemSorterModule;
import com.zenith.cache.data.inventory.Container;
import com.zenith.feature.player.World;
import com.zenith.mc.block.Direction;
import com.zenith.mc.block.properties.ChestType;
import com.zenith.mc.block.properties.api.BlockStateProperties;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.saveConfigAsync;

/**
 * 智能箱子缓存管理器
 * 负责搜索、缓存和管理附近的箱子及其内容
 */
public class ChestCacheManager {

    private final ItemSorterConfig config;
    private final ItemClassifier classifier;

    // 箱子缓存 - 位置 -> 箱子信息
    private Map<String, ChestInfo> chestCache = new ConcurrentHashMap<>();

    // 物品类型 -> 箱子位置映射
    private final Map<String, ChestLocation> itemTypeToChest = new ConcurrentHashMap<>();
    ItemSorterModule module = null;
    // 上次更新时间
    private long lastUpdateTime = 0;

    public ChestCacheManager(ItemSorterConfig config, ItemClassifier classifier, ItemSorterModule itemSorterModule) {
        this.config = config;
        this.chestCache = config.chestCache;
        this.classifier = classifier;
        this.module = itemSorterModule;
    }


    /**
     * 更新箱子缓存
     */
    public void updateCache() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime < config.chestCacheUpdateInterval * 1000) {
            return; // 还没到更新时间
        }
        module.info("开始更新存储箱子缓存");

        lastUpdateTime = currentTime;

        // 获取玩家位置
        int playerX = (int) CACHE.getPlayerCache().getX();
        int playerY = (int) CACHE.getPlayerCache().getY();
        int playerZ = (int) CACHE.getPlayerCache().getZ();

        // 搜索附近的箱子
        List<ContainerSearcher.ContainerInfo> containers = ContainerSearcher.searchNearbyContainers(playerX, playerY, playerZ, config.chestSearchRadius);

        // 清理过时的缓存
        Set<ChestLocation> foundLocations = new LinkedHashSet<>();

        Set<ChestLocation> processedLocations = new HashSet<>();

        for (ContainerSearcher.ContainerInfo container : containers) {
            if (container.type == ContainerSearcher.ContainerType.CHEST) {
                ChestLocation location = new ChestLocation(container.x, container.y, container.z);

                // 检查是否已经处理过这个位置（避免大箱子重复处理）
                if (processedLocations.contains(location)) {
                    continue;
                }

                // 排除配置的源箱子（取货箱子）
                if (isSourceChest(container.x, container.y, container.z)) {
                    // 调试日志：排除源箱子
                    module.info("[ChestCache] 排除源箱子: (" + container.x + ", " + container.y + ", " + container.z + ")");
                    continue;
                }

                // 使用正确的方法检测是否是大箱子
                if (isBigChest(container.x, container.y, container.z)) {
                    // 查找配对的箱子
                    ChestLocation pairedChest = findBigChestPaired(container.x, container.y, container.z);
                    if (pairedChest != null) {
                        // 这是大箱子，使用规范化位置
                        ChestLocation doubleChestLocation = new ChestLocation(container.x, container.y, container.z, true, pairedChest);
                        ChestLocation normalizedLocation = doubleChestLocation.getNormalizedLocation(pairedChest);

                        foundLocations.add(normalizedLocation);
                        processedLocations.add(location);
                        processedLocations.add(pairedChest);

                        // 如果是新箱子，添加到缓存
                        if (!chestCache.containsKey(normalizedLocation.toJson())) {
                            chestCache.put(normalizedLocation.toJson(), new ChestInfo(normalizedLocation));
                        }
                    } else {
                        // 大箱子但找不到配对，可能是损坏的大箱子，当作单箱子处理
                        foundLocations.add(location);
                        processedLocations.add(location);

                        if (!chestCache.containsKey(location.toJson())) {
                            chestCache.put(location.toJson(), new ChestInfo(location));
                        }
                    }
                } else {
                    // 单个箱子
                    foundLocations.add(location);
                    processedLocations.add(location);

                    // 如果是新箱子，添加到缓存
                    if (!chestCache.containsKey(location.toJson())) {
                        chestCache.put(location.toJson(), new ChestInfo(location));
                    }
                }
            }
        }
        module.info("更新存储箱子缓存完成，数量:" + chestCache.size());
        if (chestCache.size() == 0) {
            lastUpdateTime = 0;

        }

//        // 不需要移除不在范围内的箱子 注释掉
//        chestCache.entrySet().removeIf(entry -> {
//            ChestLocation location = entry.getKey();
//            double distance = location.distanceTo(playerX, playerY, playerZ);
//            return distance > config.chestSearchRadius || !foundLocations.contains(location);
//        });

        saveConfigAsync();

        // 更新物品类型映射
        updateItemTypeMapping();
    }

    /**
     * 查找配对的箱子（用于检测大箱子）
     */
    private ChestLocation findPairedChest(int x, int y, int z, List<ContainerSearcher.ContainerInfo> containers) {
        // 检查相邻的4个方向
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        for (int[] dir : directions) {
            int checkX = x + dir[0];
            int checkZ = z + dir[1];

            // 在容器列表中查找相邻的箱子
            for (ContainerSearcher.ContainerInfo container : containers) {
                if (container.type == ContainerSearcher.ContainerType.CHEST &&
                        container.x == checkX && container.y == y && container.z == checkZ) {
                    return new ChestLocation(checkX, y, checkZ);
                }
            }
        }
        return null; // 没有找到配对的箱子
    }

    /**
     * 检查指定位置是否是配置的源箱子（取货箱子）
     */
    private boolean isSourceChest(int x, int y, int z) {
        // 调试日志：显示配置的源箱子
        if (config.chestLocations.isEmpty()) {
            module.info("[ChestCache] 警告: 没有配置源箱子");
        }

        for (ChestLocation sourceChest : config.chestLocations) {
            if (sourceChest.x == x && sourceChest.y == y && sourceChest.z == z) {
                module.info("[ChestCache] 匹配到源箱子: (" + x + ", " + y + ", " + z + ")");
                return true;
            }

            // 如果源箱子是大箱子，也要检查其配对位置
            if (isBigChest(sourceChest.x, sourceChest.y, sourceChest.z)) {
                module.info("[ChestCache] 源箱子是大箱子: (" + x + ", " + y + ", " + z + ")");

                ChestLocation pairedChest = findBigChestPaired(sourceChest.x, sourceChest.y, sourceChest.z);
                if (pairedChest != null) {
                    if (pairedChest.x == x && pairedChest.y == y && pairedChest.z == z) {
                        module.info("[ChestCache] 匹配到大箱子配对位置: (" + x + ", " + y + ", " + z + ")");
                        return true;
                    }
                } else {
                    module.info("[ChestCache] 源箱子大箱子是null");
                }

            }
        }

        // 新增：检查传入位置本身是否是大箱子，其配对位置是否在源箱子配置中
        if (isBigChest(x, y, z)) {
            module.info("[ChestCache] 箱子是大箱子: (" + x + ", " + y + ", " + z + ")");
            ChestLocation pairedChest = findBigChestPaired(x, y, z);
            if (pairedChest != null) {
                for (ChestLocation sourceChest : config.chestLocations) {
                    if (sourceChest.x == pairedChest.x && sourceChest.y == pairedChest.y && sourceChest.z == pairedChest.z) {
                        module.info("[ChestCache] 传入位置是大箱子，其配对位置匹配源箱子: (" + x + ", " + y + ", " + z + ")");
                        return true;
                    }
                }
            } else {
                module.info("[ChestCache] 大箱子是null");
            }
        }

        module.info("[ChestCache] 位置 (" + x + ", " + y + ", " + z + ") 不是源箱子");
        return false;
    }

    /**
     * 判断指定方块状态是否是大箱子
     */
    public Boolean isBigChest(int blockStateId) {
        try {
            ChestType type = World.getBlockStateProperty(blockStateId, BlockStateProperties.CHEST_TYPE);
            return type != null && type != ChestType.SINGLE;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 判断指定位置是否是大箱子
     */
    public Boolean isBigChest(int x, int y, int z) {
        try {
            int blockStateId = World.getBlockStateId(x, y, z);

            ChestType type = World.getBlockStateProperty(blockStateId, BlockStateProperties.CHEST_TYPE);
            return type != null && type != ChestType.SINGLE;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 查找大箱子的配对位置
     */
    public ChestLocation findBigChestPaired(ChestLocation location) {
        return findBigChestPaired(location.x, location.y, location.z);
    }

    public ChestLocation findBigChestPaired(int x, int y, int z) {
        try {
            int blockStateId = World.getBlockStateId(x, y, z);
            if (blockStateId == 0) return null;

            // 获取箱子类型和朝向
            ChestType chestType = World.getBlockStateProperty(blockStateId, BlockStateProperties.CHEST_TYPE);
            Direction facing = World.getBlockStateProperty(blockStateId, BlockStateProperties.HORIZONTAL_FACING);
            module.info("查找大箱子的配对位置faceing: " + facing + "chestType: " + chestType);

            if (chestType == null || facing == null || chestType == ChestType.SINGLE) {
                return null;
            }

            // 根据箱子类型和朝向计算配对位置
            int pairedX = x;
            int pairedZ = z;

            switch (facing) {
                case NORTH: // 朝北
                    if (chestType == ChestType.LEFT) {
                        pairedX = x + 1; // 左半部分，配对在东边
                    } else { // RIGHT
                        pairedX = x - 1; // 右半部分，配对在西边
                    }
                    break;
                case SOUTH: // 朝南
                    if (chestType == ChestType.LEFT) {
                        pairedX = x - 1; // 左半部分，配对在西边
                    } else { // RIGHT
                        pairedX = x + 1; // 右半部分，配对在东边
                    }
                    break;
                case WEST: // 朝西
                    if (chestType == ChestType.LEFT) {
                        pairedZ = z - 1; // 左半部分，配对在北边
                    } else { // RIGHT
                        pairedZ = z + 1; // 右半部分，配对在南边
                    }
                    break;
                case EAST: // 朝东
                    if (chestType == ChestType.LEFT) {
                        pairedZ = z + 1; // 左半部分，配对在南边
                    } else { // RIGHT
                        pairedZ = z - 1; // 右半部分，配对在北边
                    }
                    break;
                default:
                    return null;
            }

            if (true) {
                return new ChestLocation(pairedX, y, pairedZ);
            }
            // 验证配对位置确实是箱子
            int pairedBlockStateId = World.getBlockStateId(pairedX, y, pairedZ);
            if (pairedBlockStateId != 0 && isBigChest(pairedBlockStateId)) {
                ChestType pairedChestType = World.getBlockStateProperty(pairedBlockStateId, BlockStateProperties.CHEST_TYPE);
                Direction pairedFacing = World.getBlockStateProperty(pairedBlockStateId, BlockStateProperties.FACING);

                // 验证配对箱子的朝向相同，类型互补
                if (pairedFacing == facing &&
                        ((chestType == ChestType.LEFT && pairedChestType == ChestType.RIGHT) ||
                                (chestType == ChestType.RIGHT && pairedChestType == ChestType.LEFT))) {
                    return new ChestLocation(pairedX, y, pairedZ);
                }
            }

            return null;
        } catch (Exception e) {
            module.info("查找大箱子的配对位置出错了," + e);
            return null;
        }
    }


    /**
     * 更新物品类型到箱子的映射
     */
    private void updateItemTypeMapping() {
//        itemTypeToChest.clear();

        for (ChestInfo chestInfo : chestCache.values()) {
            if (chestInfo.dedicatedItemType != null) {
                itemTypeToChest.put(chestInfo.dedicatedItemType, chestInfo.location);
            }
        }
    }

    /**
     * 当打开箱子时更新缓存
     */
    public boolean onChestOpened(int x, int y, int z, Container container, String category) {
        // 检查是否是源箱子，如果是则不加入缓存
        if (isSourceChest(x, y, z)) {
            module.info("[ChestCache] onChestOpened: 排除源箱子 (" + x + ", " + y + ", " + z + ")");
            return true; // 源箱子不参与存储缓存
        }

        ChestLocation targetLocation = null;

        // 使用正确的方法检查是否是大箱子
        if (isBigChest(x, y, z)) {
            ChestLocation pairedChest = findBigChestPaired(x, y, z);
            if (pairedChest != null) {
                // 这是大箱子，使用规范化位置
                ChestLocation doubleChestLocation = new ChestLocation(x, y, z, true, pairedChest);
                targetLocation = doubleChestLocation.getNormalizedLocation(pairedChest);
            } else {
                // 大箱子但找不到配对，当作单箱子处理
                targetLocation = new ChestLocation(x, y, z);
            }
        } else {
            // 单箱子
            targetLocation = new ChestLocation(x, y, z);
        }

        // 如果目标位置不在缓存中，检查是否有其他位置指向同一个大箱子
        if (!chestCache.containsKey(targetLocation.toJson()) && targetLocation.isDoubleChest) {
            // 检查是否已经有这个大箱子的缓存（可能是从另一半创建的）
            for (String str : chestCache.keySet()) {
                ChestLocation cachedLocation = ChestLocation.fromJson(str);
                if (cachedLocation.isDoubleChest) {
                    ChestLocation paired = findBigChestPaired(cachedLocation);
                    if ((cachedLocation.x == targetLocation.x && cachedLocation.y == targetLocation.y && cachedLocation.z == targetLocation.z) ||
                            (paired.x == targetLocation.x && paired.y == targetLocation.y && paired.z == targetLocation.z)) {
                        targetLocation = cachedLocation;
                        break;
                    }
                }
            }
        }


        ChestInfo chestInfo = chestCache.computeIfAbsent(targetLocation.toJson(), ChestInfo::new);

        chestInfo.updateContents(container, classifier);

        // 更新映射
        if (chestInfo.dedicatedItemType != null) {
            itemTypeToChest.put(chestInfo.dedicatedItemType, targetLocation);
        }


        // 检查箱子内容是否适合存储当前分类
        Map<String, Integer> chestContents = new HashMap<>();
        int totalItems = 0;
        //大箱子 0 - 53
        //小箱子 0 - 26
        int maxslot = 26;
        if (container.getSize() > 62) {
            maxslot = 53;
        }
        for (int slot = 0; slot <= maxslot; slot++) {
            ItemStack item = container.getItemStack(slot);
            if (item != null && item != Container.EMPTY_STACK) {
                String itemCategory = classifier.classifyItem(item);
                chestContents.put(itemCategory, chestContents.getOrDefault(itemCategory, 0) + item.getAmount());
                totalItems += item.getAmount();
            }
        }

        // 如果箱子不为空，检查是否适合存储当前分类
        if (totalItems > 0) {
            String dominantCategory = chestContents.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);

            if (dominantCategory != null && !dominantCategory.equals(category)) {
                module.info("箱子主要存储 " + dominantCategory + "，不适合存储 " + category + "，跳过此分类");

                return true;
            }

        }
        return false;
    }

    /**
     * 查找适合存储指定物品类型的箱子
     */
    public ChestLocation findChestForItem(String itemType) {
        // 首先查找专用箱子
        ChestLocation dedicatedChest = itemTypeToChest.get(itemType);
        if (dedicatedChest != null && chestCache.containsKey(dedicatedChest.toJson())) {
            ChestInfo chestInfo = chestCache.get(dedicatedChest.toJson());
            if (chestInfo.canStore(itemType) && chestInfo.getAvailableSpace() > 0) {
                return dedicatedChest;
            }
        }

        // 查找空箱子
        return chestCache.values().stream()
                .filter(chest -> chest.isEmpty)
                .min(Comparator.comparingDouble(chest -> {
                    int playerX = (int) CACHE.getPlayerCache().getX();
                    int playerY = (int) CACHE.getPlayerCache().getY();
                    int playerZ = (int) CACHE.getPlayerCache().getZ();
                    return chest.location.distanceTo(playerX, playerY, playerZ);
                }))
                .map(chest -> chest.location)
                .orElse(null);
    }

    /**
     * 获取所有缓存的箱子信息
     */
    public Collection<ChestInfo> getAllChests() {
        return chestCache.values();
    }

    /**
     * 获取缓存统计信息
     */
    public String getCacheStats() {
        int totalChests = chestCache.size();
        int emptyChests = (int) chestCache.values().stream().filter(chest -> chest.isEmpty).count();
        int dedicatedChests = (int) chestCache.values().stream().filter(chest -> chest.dedicatedItemType != null).count();
        int doubleChests = (int) chestCache.keySet().stream().filter(location -> ChestLocation.fromJson(location).isDoubleChest).count();
        int sourceChests = config.chestLocations.size();

        return String.format("箱子缓存: 总计%d个, 空箱子%d个, 专用箱子%d个, 大箱子%d个 (已排除%d个源箱子)",
                totalChests, emptyChests, dedicatedChests, doubleChests, sourceChests);
    }

    /**
     * 清空缓存
     */
    public void clearCache() {
        chestCache.clear();
        itemTypeToChest.clear();
    }
}
