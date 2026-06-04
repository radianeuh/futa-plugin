package com.github.futa;

import com.zenith.feature.inventory.InventoryActionRequest;
import com.zenith.feature.inventory.util.InventoryActionMacros;
import com.zenith.feature.player.InputRequest;
import com.zenith.util.ItemUtil;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.ByteEntityMetadata;

import static com.zenith.Globals.*;

/**
 * 用于给AI agent参考的常见功能写法、最佳实现等
 *
 * @see com.github.futa.util.EnchantmentUtil
 */
public class BestPracticeExample {


    /**
     * 客户端代码的等效
     * mc.interactionManager.clickSlot(syncId, targetSlot, slot, SlotActionType.SWAP, mc.player);
     */
    public void clickSlot(int fromSlotId, int toSlotId) {
        INVENTORY.submit(InventoryActionRequest.builder()
                .actions(InventoryActionMacros.swapSlots(fromSlotId, toSlotId))
                .actionDelayTicks(0)
                .owner(this)
                .priority(10000) //priority 越大越优先执行
                .build());
    }


    private void rotateToAngle() {
        float yaw = 0;
        float pitch = 0;

        INPUTS.submit(InputRequest.builder()
                .owner(this)
                .yaw(yaw)
                .pitch(pitch)
                .priority(10000)
                .build());
    }


    private boolean isFallFlying() {
        var player = CACHE.getPlayerCache().getThePlayer();
        if (player == null) {
            return false;
        }
        EntityMetadata<?, ?> metadata = player.getMetadata().get(0);
        if (metadata instanceof ByteEntityMetadata byteMetadata) {
            return (byteMetadata.getPrimitiveValue() & 0x80) != 0;
        }
        return false;
    }

    void elytraEXT() {

        // 获取鞘翅装备
        var elytraStack = CACHE.getPlayerCache().getEquipment(EquipmentSlot.CHESTPLATE);

        // 获取当前伤害值（已消耗的耐久）
        int damageValue = ItemUtil.getDamageValue(elytraStack);

        // 获取最大耐久
        int maxDamage = ItemUtil.getMaxDamage(elytraStack);

        // 获取剩余耐久
        int durability = ItemUtil.getDamageUntilBreak(elytraStack);

    }
}
