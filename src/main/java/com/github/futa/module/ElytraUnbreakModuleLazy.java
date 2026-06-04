package com.github.futa.module;

import com.github.futa.FutaPlugin;
import com.github.futa.config.ElytraUnbreakConfig;
import com.github.rfresh2.EventConsumer;
import com.zenith.cache.data.inventory.Container;
import com.zenith.event.client.ClientBotTick;
import com.zenith.feature.inventory.InventoryActionRequest;
import com.zenith.feature.inventory.util.InventoryActionMacros;
import com.zenith.feature.player.Input;
import com.zenith.feature.player.InputRequest;
import com.zenith.mc.item.ItemRegistry;
import com.zenith.module.api.Module;
import com.zenith.network.codec.PacketHandlerCodec;
import com.zenith.network.codec.PacketHandlerStateCodec;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.ByteEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerState;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerCommandPacket;

import java.util.ArrayList;
import java.util.List;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.*;

public class ElytraUnbreakModuleLazy extends Module {

    ElytraUnbreakConfig config = FutaPlugin.PLUGIN_CONFIG.elytraUnbreak;

    // tick 计数由外部 FallFlyingTick 事件传入，这里用本地计数模拟
    private int tickCounter = 0;
    private boolean wasFallFlying = false;

    // 对应彗星的 nextTimeLaunchElytraUnbreakable
    private volatile boolean nextTimeLaunchElytraUnbreakable = false;
    // 对应彗星的 elytraUnbreakableSwitchSlot，-1 表示走"无空槽"路径
    private volatile int elytraUnbreakableSwitchSlot = -1;

    private static final int INVENTORY_PRIORITY = 20000;
    private static final int CHEST_SLOT_ID = 6;
    // 对应彗星 switchSlotToArmor 里用到的临时快捷栏槽（offhand index=40，取快捷栏最后一格=44）
    private static final int TEMP_HOTBAR_SLOT = 44;

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
        resetState();
        info("ElytraUnbreak 已启用 - 切换周期: %d tick", config.period);
    }

    @Override
    public void onDisable() {
        resetState();
    }

    private void resetState() {
        tickCounter = 0;
        wasFallFlying = false;
        nextTimeLaunchElytraUnbreakable = false;
        elytraUnbreakableSwitchSlot = -1;
    }

    // ==================== Codec 注册 ====================

    @Override
    public PacketHandlerCodec registerClientPacketHandlerCodec() {
        return PacketHandlerCodec.clientBuilder()
                .setId("ely-log")
                .setPriority(100000)
                .state(
                        ProtocolState.GAME,
                        PacketHandlerStateCodec.clientBuilder()
                                .inbound(ClientboundSetEntityDataPacket.class, (packet, session) -> {

                                    return handleEntityDataPacket(packet);
                                })
                                .build()
                )
                .build();
    }


    /**
     * 对应彗星的 handleEntityDataUpdate 里 shouldElytraUnbreakable() 分支
     * 在 CACHE 更新之前拦截，修改包内容防止客户端感知停止滑翔
     */
    @SuppressWarnings("unchecked")
    private ClientboundSetEntityDataPacket handleEntityDataPacket(ClientboundSetEntityDataPacket packet) {
        if (!config.enabled) return packet;
        if (!nextTimeLaunchElytraUnbreakable) return packet;

        // 只处理自身实体
        if (packet.getEntityId() != CACHE.getPlayerCache().getEntityId()) return packet;

        var player = CACHE.getPlayerCache().getThePlayer();
        if (player == null) return packet;

        // 遍历元数据，找 index=0 的 flags byte
        var metadata = packet.getMetadata();
        int flagsIndex = -1;
        byte currentFlags = 0;

        for (int i = 0; i < metadata.size(); i++) {
            var entry = metadata.get(i);
            if (entry.getId() == 0 && entry.getValue() instanceof Byte b) {
                flagsIndex = i;
                currentFlags = b;
                break;
            }
        }

        if (flagsIndex == -1) return packet; // 本包不含 flags，不处理

        boolean serverSaysStopped = (currentFlags & (1 << 7)) == 0; // bit7 = FALL_FLYING
        if (!serverSaysStopped) return packet; // 服务端没说停，不处理

        // ---- 以下对应彗星 handleEntityDataUpdate 里 nextTimeLaunchElytraUnbreakable 分支 ----

        nextTimeLaunchElytraUnbreakable = false;

        // 如果有预定换装槽，把鞘翅装回胸甲
        if (elytraUnbreakableSwitchSlot != -1) {
            switchSlotToArmor(elytraUnbreakableSwitchSlot);
        }

        if (canContinueGliding()) {
            // 发 START_FALL_FLYING 包
            sendClientPacketAsync(new ServerboundPlayerCommandPacket(
                    CACHE.getPlayerCache().getEntityId(),
                    PlayerState.START_ELYTRA_FLYING));

            // 修改包：把 bit7 置回 1，客户端不感知停止
            byte patched = (byte) (currentFlags | (1 << 7));
            var patchedMetadata = new ArrayList<>(metadata);
            patchedMetadata.set(flagsIndex,
                    new ByteEntityMetadata(0, MetadataTypes.BYTE, patched));

            elytraUnbreakableSwitchSlot = -1;
            return new ClientboundSetEntityDataPacket(packet.getEntityId(), patchedMetadata);

        } else {
            // 无法继续滑翔，放行包，让客户端正常停止
            elytraUnbreakableSwitchSlot = -1;
            return packet;
        }
    }

    // ==================== Tick 逻辑 ====================

    private void onTick(ClientBotTick event) {
        var player = CACHE.getPlayerCache().getThePlayer();
        if (player == null) return;

        boolean isFlying = BOT.isFallFlying();

        if (isFlying && !wasFallFlying) {
            tickCounter = 0;
        }
        wasFallFlying = isFlying;

        if (!isFlying) return; // 重装和起飞完全交给 codec，tick 不再干预

        tickCounter++;
        if (tickCounter < config.period) return;

        runElytraUnbreakable();
    }

    /**
     * 对应彗星的 runElytraUnbreakable
     */
    private void runElytraUnbreakable() {
        if (!shouldElytraUnbreakable()) return;
        if (!canContinueGliding()) return;
        if (!BOT.isFallFlying()) return;

        int switchSlot = findEmptyPlaceForElytra();

        if (switchSlot == -1) {
            // 无空槽路径：直接发 STOP 包触发服务端停止，由 codec 拦截重启
            // 对应彗星：sendPacket(START_FALL_FLYING) 作为 toggle stop
            sendClientPacketAsync(new ServerboundPlayerCommandPacket(
                    CACHE.getPlayerCache().getEntityId(),
                    PlayerState.START_ELYTRA_FLYING));
            nextTimeLaunchElytraUnbreakable = true;
            elytraUnbreakableSwitchSlot = -1;
        } else {
            // 有空槽路径：换出鞘翅，codec 收到停止包时换回
            switchSlotToArmor(switchSlot);
            nextTimeLaunchElytraUnbreakable = true;
            elytraUnbreakableSwitchSlot = switchSlot;
        }

        tickCounter = 0;
    }

    // ==================== 工具方法 ====================

    /**
     * 对应彗星的 shouldElytraUnbreakable
     * 不在 armorFly 模式，且鞘翅没有 Unbreakable 组件
     */
    private boolean shouldElytraUnbreakable() {
        var chestStack = CACHE.getPlayerCache().getEquipment(EquipmentSlot.CHESTPLATE);
        if (chestStack == null) return false;
        // 若服务端标记了 Unbreakable，跳过（对应彗星的 DataComponentTypes.UNBREAKABLE 检查）
        // ZenithBot 中暂无直接 API，保守处理：始终执行切换
        return chestStack.getId() == ItemRegistry.ELYTRA.id();
    }

    /**
     * 对应彗星的 switchSlotToArmor
     * <p>
     * 彗星实现：
     *   if slot 在快捷栏(36-45): 用 SWAP(number key) 直接与胸甲槽交换
     *   else (背包 9-35):
     *     step1: SWAP(slot, hotbar40)   背包↔快捷栏临时位
     *     step2: SWAP(armorSlot, hotbar40) 胸甲↔快捷栏临时位
     *     step3: SWAP(slot, hotbar40)   归还临时位原物品
     * <p>
     * ZenithBot 用 swapSlots 模拟，快捷栏路径一步完成，背包路径三步完成
     */
    private void switchSlotToArmor(int slot) {
        if (slot >= 36 && slot <= 44) {
            // 快捷栏直接与胸甲槽交换
            submitSwap(slot, CHEST_SLOT_ID);
        } else {
            // 背包三步 swap，临时借用 TEMP_HOTBAR_SLOT(44)
            submitSwap(slot, TEMP_HOTBAR_SLOT);          // 背包→临时快捷栏
            submitSwap(CHEST_SLOT_ID, TEMP_HOTBAR_SLOT); // 胸甲→临时快捷栏（原物品回胸甲）
            submitSwap(slot, TEMP_HOTBAR_SLOT);          // 归还
        }
    }

    private void submitSwap(int slotA, int slotB) {
        INVENTORY.submit(InventoryActionRequest.builder()
                .owner(this)
                .actions(InventoryActionMacros.swapSlots(slotA, slotB))
                .actionDelayTicks(0)
                .priority(INVENTORY_PRIORITY)
                .build());
    }

    /**
     * 对应彗星的 findEmptyPlaceForElytra
     * 找空槽，或找可以被换走的非鞘翅胸甲位物品所在槽
     */
    private int findEmptyPlaceForElytra() {
        var inv = CACHE.getPlayerCache().getPlayerInventory();
        var elytraId = ItemRegistry.ELYTRA.id();

        // 优先快捷栏空槽
        for (int i = 36; i <= 44; i++) {
            var item = inv.get(i);
            if (item == null || item == Container.EMPTY_STACK) return i;
        }
        // 背包空槽
        for (int i = 9; i <= 35; i++) {
            var item = inv.get(i);
            if (item == null || item == Container.EMPTY_STACK) return i;
        }
        // 快捷栏非鞘翅槽（对应彗星：非 canGlide 且 preferredSlot==CHEST 的物品）
        for (int i = 36; i <= 44; i++) {
            var item = inv.get(i);
            if (item != null && item != Container.EMPTY_STACK && item.getId() != elytraId) return i;
        }
        // 背包非鞘翅槽
        for (int i = 9; i <= 35; i++) {
            var item = inv.get(i);
            if (item != null && item != Container.EMPTY_STACK && item.getId() != elytraId) return i;
        }
        return -1;
    }

    /**
     * 检查是否可以继续滑翔（对应彗星 canContinueGliding）
     */
    private boolean canContinueGliding() {
        var player = CACHE.getPlayerCache().getThePlayer();
        if (player == null) return false;
        if (BOT.isOnGround()) return false;
        if (BOT.isTouchingWater() || BOT.isTouchingLava()) return false;
        return true;
    }

    /**
     * 防踢飞
     */
    private void sendJump() {
        INPUTS.submit(InputRequest.builder()
                .owner(this)
                .input(Input.builder().jumping(true).build())
                .priority(INVENTORY_PRIORITY)
                .build());
    }

    public static boolean isWearingElytra() {
        var chestStack = CACHE.getPlayerCache().getEquipment(EquipmentSlot.CHESTPLATE);
        if (chestStack == null) return false;
        return chestStack.getId() == ItemRegistry.ELYTRA.id();
    }
}
