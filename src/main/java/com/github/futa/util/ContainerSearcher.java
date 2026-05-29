package com.github.futa.util;

import com.zenith.feature.pathfinder.BlockStateInterface;
import com.zenith.mc.block.BlockRegistry;

import java.util.ArrayList;
import java.util.List;

import static com.zenith.Globals.CACHE;

/**
 * 容器搜索工具类 - 负责搜索附近的箱子和潜影盒
 */
public class ContainerSearcher {

    /**
     * 容器信息类
     */
    public static class ContainerInfo {
        public final int x, y, z;
        public final ContainerType type;

        public ContainerInfo(int x, int y, int z, ContainerType type) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.type = type;
        }

        @Override
        public String toString() {
            return type + " at (" + x + ", " + y + ", " + z + ")";
        }
    }

    /**
     * 容器类型枚举
     */
    public enum ContainerType {
        CHEST,
        SHULKER_BOX,
        BARREL,
        ENDER_CHEST,
        HOPPER,
        DROPPER,
        DISPENSER,
        UNKNOWN
    }

    /**
     * 搜索指定位置附近的容器
     *
     * @param centerX 中心X坐标
     * @param centerY 中心Y坐标
     * @param centerZ 中心Z坐标
     * @param radius  搜索半径（仅用于X和Z轴）
     * @return 找到的容器列表
     */
    public static List<ContainerInfo> searchNearbyContainers(int centerX, int centerY, int centerZ, int radius) {
        List<ContainerInfo> containers = new ArrayList<>();

        // Y轴搜索范围：向下1格，向上4格
        int minY = centerY - 1;
        int maxY = centerY + 3;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = minY; dy <= maxY; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int x = centerX + dx;
                    int y = dy;
                    int z = centerZ + dz;

                    ContainerType type = getContainerType(x, y, z);
                    if (type == ContainerType.CHEST) {
                        containers.add(new ContainerInfo(x, y, z, type));
                    }
                }
            }
        }

        return containers;
    }

    /**
     * 搜索玩家附近的容器
     *
     * @param radius 搜索半径
     * @return 找到的容器列表
     */
    public static List<ContainerInfo> searchNearbyContainers(int radius) {
        int playerX = (int) CACHE.getPlayerCache().getX();
        int playerY = (int) CACHE.getPlayerCache().getY();
        int playerZ = (int) CACHE.getPlayerCache().getZ();

        return searchNearbyContainers(playerX, playerY, playerZ, radius);
    }

    /**
     * 获取指定坐标的容器类型
     *
     * @param x X坐标
     * @param y Y坐标
     * @param z Z坐标
     * @return 容器类型
     */
    public static ContainerType getContainerType(int x, int y, int z) {
        int blockStateId = BlockStateInterface.getId(x, y, z);

        if (isChest(blockStateId)) {
            return ContainerType.CHEST;
        } else if (isShulkerBox(blockStateId)) {
            return ContainerType.SHULKER_BOX;
        } else if (isBarrel(blockStateId)) {
            return ContainerType.BARREL;
        } else if (isEnderChest(blockStateId)) {
            return ContainerType.ENDER_CHEST;
        } else if (isHopper(blockStateId)) {
            return ContainerType.HOPPER;
        } else if (isDropper(blockStateId)) {
            return ContainerType.DROPPER;
        } else if (isDispenser(blockStateId)) {
            return ContainerType.DISPENSER;
        }

        return ContainerType.UNKNOWN;
    }

    /**
     * 检查是否为箱子
     */
    public static boolean isChest(int blockStateId) {
        return blockStateId >= BlockRegistry.CHEST.minStateId() &&
                blockStateId <= BlockRegistry.CHEST.maxStateId();
    }

    /**
     * 检查是否为潜影盒
     */
    public static boolean isShulkerBox(int blockStateId) {
        return (blockStateId >= BlockRegistry.SHULKER_BOX.minStateId() &&
                blockStateId <= BlockRegistry.SHULKER_BOX.maxStateId()) ||
                (blockStateId >= BlockRegistry.WHITE_SHULKER_BOX.minStateId() &&
                        blockStateId <= BlockRegistry.BLACK_SHULKER_BOX.maxStateId());
    }

    /**
     * 检查是否为桶
     */
    public static boolean isBarrel(int blockStateId) {
        return blockStateId >= BlockRegistry.BARREL.minStateId() &&
                blockStateId <= BlockRegistry.BARREL.maxStateId();
    }

    /**
     * 检查是否为末影箱
     */
    public static boolean isEnderChest(int blockStateId) {
        return blockStateId >= BlockRegistry.ENDER_CHEST.minStateId() &&
                blockStateId <= BlockRegistry.ENDER_CHEST.maxStateId();
    }

    /**
     * 检查是否为漏斗
     */
    public static boolean isHopper(int blockStateId) {
        return blockStateId >= BlockRegistry.HOPPER.minStateId() &&
                blockStateId <= BlockRegistry.HOPPER.maxStateId();
    }

    /**
     * 检查是否为投掷器
     */
    public static boolean isDropper(int blockStateId) {
        return blockStateId >= BlockRegistry.DROPPER.minStateId() &&
                blockStateId <= BlockRegistry.DROPPER.maxStateId();
    }

    /**
     * 检查是否为发射器
     */
    public static boolean isDispenser(int blockStateId) {
        return blockStateId >= BlockRegistry.DISPENSER.minStateId() &&
                blockStateId <= BlockRegistry.DISPENSER.maxStateId();
    }


    /**
     * 检查容器是否可用（存在且可访问）
     *
     * @param x X坐标
     * @param y Y坐标
     * @param z Z坐标
     * @return 是否可用
     */
    public static boolean isContainerAccessible(int x, int y, int z) {
        ContainerType type = getContainerType(x, y, z);
        if (type == ContainerType.UNKNOWN) {
            return false;
        }

        // 检查区块是否已加载
        if (!BlockStateInterface.isLoaded(x, z)) {
            return false;
        }

        // TODO: 可以添加更多的可访问性检查，比如距离、障碍物等

        return true;
    }

    /**
     * 获取容器的优先级评分（用于选择最佳容器）
     *
     * @param container 容器信息
     * @param playerX   玩家X坐标
     * @param playerY   玩家Y坐标
     * @param playerZ   玩家Z坐标
     * @return 优先级评分，数值越高优先级越高
     */
    public static int getContainerPriority(ContainerInfo container, int playerX, int playerY, int playerZ) {
        int score = 0;

        // 基于容器类型的评分
        switch (container.type) {
            case SHULKER_BOX:
                score += 100; // 潜影盒优先级最高
                break;
            case CHEST:
                score += 80;
                break;
            case BARREL:
                score += 70;
                break;
            case ENDER_CHEST:
                score += 60;
                break;
            case HOPPER:
                score += 40;
                break;
            case DROPPER:
            case DISPENSER:
                score += 20;
                break;
            default:
                score += 10;
                break;
        }

        // 基于距离的评分（距离越近评分越高）
        double distance = Math.sqrt(
                Math.pow(container.x - playerX, 2) +
                        Math.pow(container.y - playerY, 2) +
                        Math.pow(container.z - playerZ, 2)
        );
        score -= (int) distance; // 距离越远扣分越多

        return score;
    }
}
