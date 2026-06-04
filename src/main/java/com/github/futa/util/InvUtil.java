package com.github.futa.util;

import com.zenith.Proxy;
import com.zenith.cache.data.inventory.Container;
import com.zenith.mc.item.ItemData;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import lombok.experimental.UtilityClass;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerActionType;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.MoveToHotbarAction;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;

import java.util.Objects;

import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.CLIENT_LOG;

@UtilityClass
public class InvUtil {


    /**
     * 是否存在某物品
     *
     * @param data eg. ItemData data = ItemRegistry.SOUL_SAND;
     * @return true 如果玩家背包中存在该物品
     */
    public static boolean hasItem(ItemData data) {
        var inv = CACHE.getPlayerCache().getPlayerInventory();
        for (int i = 0; i < inv.size(); i++) {
            var item = inv.get(i);
            if (item != null && item != Container.EMPTY_STACK) {
                if (item.getId() == data.id()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 将指定物品无延迟swap到主手
     * 参考 Meteor Client 的 inventorySwap 实现，使用 SWAP 操作立即交换物品到当前选中的 hotbar slot
     *
     * @param id 物品ID
     * @return true 如果成功发送了swap包或物品已在主手
     */
    public boolean switchToItem(int id) {
        if (isItemOnHand(id)) {
            return true;
        }

        var container = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
        var mouseStack = CACHE.getPlayerCache().getInventoryCache().getMouseStack();

        // 如果鼠标上有物品，无法执行swap操作
        if (mouseStack != null && mouseStack != Container.EMPTY_STACK) {
            CLIENT_LOG.debug("InvUtil: 鼠标上有物品，无法执行swap操作");
            return false;
        }

        // 获取当前选中的 hotbar slot (0-8)
        int selectedSlot = CACHE.getPlayerCache().getHeldItemSlot();

        // 在背包中查找指定物品 (slot 9-44, 其中 36-44 是 hotbar)
        for (int slot = 44; slot >= 9; slot--) {
            ItemStack itemStack = container.getItemStack(slot);
            if (itemStack != null && itemStack != Container.EMPTY_STACK && id == itemStack.getId()) {
                // 检查物品是否已经在当前选中的 hotbar slot 中
                int hotbarIndex = slot - 36;
                if (hotbarIndex >= 0 && hotbarIndex == selectedSlot) {
                    // 物品已经在当前选中的 hotbar slot 中
                    return true;
                }

                // 构建 SWAP 操作的数据包
                sendSwapPacket(container.getContainerId(), slot, selectedSlot);
                return true;
            }
        }

        CLIENT_LOG.debug("InvUtil: 未找到物品 ID: {}", id);
        return false;
    }

    /**
     * 发送无延迟的 SWAP 数据包，将指定 slot 的物品交换到 hotbar
     * 参考 Meteor Client 的 clickSlot 实现
     *
     * @param containerId 容器ID
     * @param slot        要交换的背包slot (9-44)
     * @param hotbarSlot  目标 hotbar slot (0-8)
     */
    private void sendSwapPacket(int containerId, int slot, int hotbarSlot) {
        var container = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
        var slotItem = container.getItemStack(slot);
        var hotbarItem = container.getItemStack(36 + hotbarSlot);

        // 构建 changed slots 映射
        Int2ObjectArrayMap<ItemStack> changedSlots = new Int2ObjectArrayMap<>();
        changedSlots.put(slot, hotbarItem != null ? hotbarItem : Container.EMPTY_STACK);
        changedSlots.put(36 + hotbarSlot, slotItem != null ? slotItem : Container.EMPTY_STACK);

        // 创建 MOVE_TO_HOTBAR_SLOT 操作的数据包
        // MoveToHotbarAction.SLOT_1 到 SLOT_9 对应 hotbar slot 0-8
        MoveToHotbarAction hotbarAction = getMoveToHotbarAction(hotbarSlot);

        ServerboundContainerClickPacket packet = new ServerboundContainerClickPacket(
                containerId,
                CACHE.getPlayerCache().getActionId().incrementAndGet(),
                slot,
                ContainerActionType.MOVE_TO_HOTBAR_SLOT,
                hotbarAction,
                Container.EMPTY_STACK,
                changedSlots
        );

        // 无延迟发送数据包
        Proxy.getInstance().getClient().sendAwait(packet);
        CLIENT_LOG.debug("InvUtil: 已发送SWAP包, slot={}, hotbarSlot={}", slot, hotbarSlot);
    }

    /**
     * 将 hotbar slot index (0-8) 转换为 MoveToHotbarAction 枚举
     */
    private MoveToHotbarAction getMoveToHotbarAction(int hotbarSlot) {
        return switch (hotbarSlot) {
            case 0 -> MoveToHotbarAction.SLOT_1;
            case 1 -> MoveToHotbarAction.SLOT_2;
            case 2 -> MoveToHotbarAction.SLOT_3;
            case 3 -> MoveToHotbarAction.SLOT_4;
            case 4 -> MoveToHotbarAction.SLOT_5;
            case 5 -> MoveToHotbarAction.SLOT_6;
            case 6 -> MoveToHotbarAction.SLOT_7;
            case 7 -> MoveToHotbarAction.SLOT_8;
            case 8 -> MoveToHotbarAction.SLOT_9;
            default -> throw new IllegalArgumentException("Invalid hotbar slot: " + hotbarSlot);
        };
    }

    /**
     * 判断物品是否在手
     *
     * @param itemId 物品ID
     * @return true 如果物品在主手
     */
    public boolean isItemOnHand(int itemId) {
        ItemStack mainHandStack = CACHE.getPlayerCache().getEquipment(EquipmentSlot.MAIN_HAND);
        return Objects.nonNull(mainHandStack) && itemId == mainHandStack.getId();
    }
}
