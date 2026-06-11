package com.github.futa.util;


import com.zenith.Globals;
import com.zenith.feature.inventory.InventoryActionRequest;
import com.zenith.feature.inventory.actions.CloseContainer;
import com.zenith.feature.inventory.actions.InventoryAction;
import com.zenith.feature.pathfinder.BlockStateInterface;
import com.zenith.mc.block.BlockRegistry;
import com.zenith.util.RequestFuture;

import java.util.ArrayList;
import java.util.List;

import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.INVENTORY;

public class ZUtil {


    /**
     * 判断坐标 (x, z) 是否在区域 [100,300] × [100,300] 内
     *
     * @return true 表示在区域内，否则 false
     */
    public static boolean isIn3cSpawn() {
        if (!Globals.CONFIG.client.server.address.toLowerCase().contains("3c3u.org")) {
            return false;
        }
        return isIn3cSpawn(CACHE.getPlayerCache().getX(), CACHE.getPlayerCache().getZ());
    }

    /**
     * 判断坐标 (x, z) 是否在区域 [100,300] × [100,300] 内
     *
     * @param x x坐标
     * @param z z坐标
     * @return true 表示在区域内，否则 false
     */
    public static boolean isIn3cSpawn(double x, double z) {
        return x >= 100 && x <= 300 && z >= 100 && z <= 300;
    }

    /**
     * 验证是否为有效的容器
     */
    public static boolean isValidContainer(int blockStateId) {

        // 检查是否为箱子、桶或其他容器方块
        return isShulkerBox(blockStateId) ||
                (blockStateId >= BlockRegistry.CHEST.minStateId() && blockStateId <= BlockRegistry.CHEST.maxStateId()) ||
                (blockStateId >= BlockRegistry.BARREL.minStateId() && blockStateId <= BlockRegistry.BARREL.maxStateId()) ||
                (blockStateId >= BlockRegistry.HOPPER.minStateId() && blockStateId <= BlockRegistry.HOPPER.maxStateId()) ||
                (blockStateId >= BlockRegistry.DROPPER.minStateId() && blockStateId <= BlockRegistry.DROPPER.maxStateId()) ||
                (blockStateId >= BlockRegistry.DISPENSER.minStateId() && blockStateId <= BlockRegistry.DISPENSER.maxStateId());
    }

    public static boolean isShulkerBox(int blockStateId) {
        // 检查是否为潜影盒方块
        return blockStateId >= BlockRegistry.SHULKER_BOX.minStateId() &&
                blockStateId <= BlockRegistry.BLACK_SHULKER_BOX.maxStateId();
    }

    public static boolean isShulkerBox(int x, int y, int z) {
        int blockStateId = BlockStateInterface.getId(x, y, z);
        // 检查是否是潜影盒
        return isShulkerBox(blockStateId);
    }


    public static RequestFuture closeIfOpen(Object owner) {
        var openContainer = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
        if (openContainer != null && openContainer.getContainerId() != 0) {
            List<InventoryAction> actions = new ArrayList<>();
            actions.add(new CloseContainer(openContainer.getContainerId()));
            return INVENTORY.submit(InventoryActionRequest.builder()
                    .owner(owner)
                    .actionDelayTicks(0)
                    .actions(actions)
                    .build());
        }
        return RequestFuture.rejected;
    }
}
