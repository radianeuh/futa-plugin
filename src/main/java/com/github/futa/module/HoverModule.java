package com.github.futa.module;

import com.github.futa.FutaPlugin;
import com.github.futa.config.HoverConfig;
import com.github.rfresh2.EventConsumer;
import com.zenith.Proxy;
import com.zenith.event.client.ClientBotTick;
import com.zenith.feature.inventory.InventoryActionRequest;
import com.zenith.module.api.Module;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.Attribute;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundPlayerInputPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;

import java.util.List;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.*;

/**
 * 空中悬停模块
 * <p>
 * 通过欺骗服务器实现悬停：
 * 1. 发送位置包时速度为 0
 * 2. 设置 NoGravity 属性为 true
 * 3. 发送数据包后恢复 NoGravity 原值
 * 4. 下一个 tick 重复
 */
public class HoverModule extends Module {

    HoverConfig config = FutaPlugin.PLUGIN_CONFIG.hover;

    private double originalGravity = 0.08; // Minecraft 默认重力值

    @Override
    public boolean enabledSetting() {
        return config.enabled;
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
                of(ClientBotTick.class, this::onTick)
        );
    }

    @Override
    public void onEnable() {
        // 保存原始重力值
        var player = CACHE.getPlayerCache().getThePlayer();
        if (player != null) {
            var gravityAttr = player.getAttributes().get(AttributeType.Builtin.GRAVITY);
            if (gravityAttr != null) {
                originalGravity = gravityAttr.getValue();
            }
        }
        info("Hover 已启用");
    }

    @Override
    public void onDisable() {
        // 恢复原始重力值
        restoreGravity();
    }

    private void onTick(ClientBotTick event) {
        if (!config.antiGravity) return;

        var player = CACHE.getPlayerCache().getThePlayer();
        if (player == null) return;

        // 在地面时不悬停
        if (BOT.isOnGround()) return;

        // 在水中或岩浆中不悬停
        if (BOT.isTouchingWater() || BOT.isTouchingLava()) return;

        // 1. 设置 NoGravity 为 true
        setNoGravity(true);

        // 2. 发送位置包（速度为 0）
        sendPositionWithZeroVelocity();

        // 3. 恢复 NoGravity 原值（服务器已接受这一 tick 的状态）
        setNoGravity(false);
    }

    /**
     * 设置 NoGravity 属性
     */
    private void setNoGravity(boolean noGravity) {
        var player = CACHE.getPlayerCache().getThePlayer();
        if (player == null) return;

        var attributes = player.getAttributes();
        var gravityAttr = attributes.get(AttributeType.Builtin.GRAVITY);
        if (gravityAttr != null) {
            if (noGravity) {
                // 设置为 0 表示无重力
                attributes.put(AttributeType.Builtin.GRAVITY, new Attribute(AttributeType.Builtin.GRAVITY, 0));
            } else {
                // 恢复原始值
                attributes.put(AttributeType.Builtin.GRAVITY, new Attribute(AttributeType.Builtin.GRAVITY, originalGravity));
            }
        }
    }

    /**
     * 发送位置包（包含速度为 0 的信息）
     */
    private void sendPositionWithZeroVelocity() {
        var player = CACHE.getPlayerCache().getThePlayer();
        if (player == null) return;
        player.setVelX(0).setVelY(0).setVelZ(0);
        BOT.getVelocity().set(0, 0, 0);

        // 发送位置包，速度为 0
        // ServerboundMovePlayerPosRotPacket 不包含速度，速度是通过 ServerboundPlayerInputPacket 控制的
        Proxy.getInstance().getClient().sendAsync(
                new ServerboundMovePlayerPosRotPacket(
                        BOT.isOnGround(),
                        false, // horizontalCollision
                        BOT.getX(),
                        BOT.getY(),
                        BOT.getZ(),
                        BOT.getYaw(),
                        BOT.getPitch()
                )
        );

//        INVENTORY.submit(InventoryActionRequest.noAction(this, 1000));

        // 发送输入包，所有方向为 0 表示速度为 0
        Proxy.getInstance().getClient().sendAsync(
                new ServerboundPlayerInputPacket(
                        false, // forward
                        false, // backward
                        false, // left
                        false, // right
                        false, // jumping
                        false, // sneaking
                        false  // Unknown
                )
        );
    }

    /**
     * 恢复重力值
     */
    private void restoreGravity() {
        var player = CACHE.getPlayerCache().getThePlayer();
        if (player == null) return;

        var attributes = player.getAttributes();
        attributes.put(AttributeType.Builtin.GRAVITY, new Attribute(AttributeType.Builtin.GRAVITY, originalGravity));
    }

    /**
     * 检查玩家是否穿着鞘翅
     */
    private boolean isWearingElytra() {
        var chestStack = CACHE.getPlayerCache().getEquipment(EquipmentSlot.CHESTPLATE);
        if (chestStack == null) return false;
        return chestStack.getId() == com.zenith.mc.item.ItemRegistry.ELYTRA.id();
    }
}
