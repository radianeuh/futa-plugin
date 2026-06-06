package com.github.futa.module;

import com.github.futa.FutaPlugin;
import com.github.futa.config.ElytraUnbreakConfig;
import com.github.rfresh2.EventConsumer;
import com.zenith.Globals;
import com.zenith.cache.data.inventory.Container;
import com.zenith.event.client.ClientBotTick;
import com.zenith.feature.inventory.InventoryActionRequest;
import com.zenith.feature.inventory.actions.InventoryAction;
import com.zenith.feature.inventory.util.InventoryActionMacros;
import com.zenith.feature.player.Input;
import com.zenith.feature.player.InputRequest;
import com.zenith.mc.item.ItemRegistry;
import com.zenith.module.api.Module;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.ByteEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerState;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerCommandPacket;

import java.util.ArrayList;
import java.util.List;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.*;

/**
 * 无限耐久鞘翅模块 - 移植自 Meteor Client
 * <p>
 * 通过在飞行期间自动切换鞘翅来防止耐久度消耗。
 * 核心逻辑：卸下鞘翅 -> 下一tick重新装备 -> 发送开始滑翔包
 * <p>
 * 主要功能：
 * 1. 周期性切换鞘翅（卸下再装备）防止耐久消耗
 * 2. 防踢飞（无法滑翔时发送跳跃包）
 */
public class ElytraUnbreakModule extends Module {

    ElytraUnbreakConfig config = FutaPlugin.PLUGIN_CONFIG.elytraUnbreak;

    private int tickCounter = 0;
    private boolean wasFallFlying = false;
    private volatile boolean nextTickShouldStartFly = false;

    private static final int INVENTORY_PRIORITY = 20000;
    private static final int CHEST_SLOT_ID = 6; // 胸甲槽在容器中的 slot ID

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
        info("ElytraUnbreak 已启用 - 切换周期: {} tick", config.period);
    }

    @Override
    public void onDisable() {
        resetState();
    }

    private void resetState() {
        tickCounter = 0;
        wasFallFlying = false;
        nextTickShouldStartFly = false;
    }

    private void onTick(ClientBotTick event) {
        var player = CACHE.getPlayerCache().getThePlayer();
        if (player == null) return;

        boolean isFlying = isFallFlyingMetadata();

        // 检测是否刚开始滑翔
        if (isFlying && !wasFallFlying) {
            tickCounter = 0;
        }
        wasFallFlying = isFlying;

        if (!isFlying) {
            // 不在滑翔时，检查是否需要自动装备鞘翅开始滑翔
            if (nextTickShouldStartFly) {
                nextTickShouldStartFly = false;
                if (canContinueGliding() && !isWearingElytra()) {
                    equipElytra();
                    startFallFlying();
                }
            }
            if (isWearingElytra() && !BOT.isOnGround()) {
                startFallFlying();
            }
            return;
        }

        // 正在滑翔中
        tickCounter++;

        // 检查是否需要切换鞘翅
        if (shouldSwitchElytra()) {
            performElytraSwitch();
        }
    }

    /**
     * 检查是否应该切换鞘翅状态
     */
    private boolean shouldSwitchElytra() {
        return tickCounter >= config.period;
    }

    /**
     * 执行鞘翅切换操作
     * 核心逻辑：卸下鞘翅 -> 下一tick重新装备 -> 发送开始滑翔包
     */
    private void performElytraSwitch() {
        if (!canContinueGliding()) {
            if (config.antiKick) {
                // 无法继续滑翔时，发送跳跃防止被踢
                sendJump();
            }
            return;
        }

        // 当前装备的是鞘翅，需要卸下
        if (isWearingElytra()) {
            unequipElytra();
            // 标记下一tick需要重新装备并开始滑翔
            nextTickShouldStartFly = true;
        }

        tickCounter = 0;
    }

    public void takeoff() {
        if (!isWearingElytra()) {
            equipElytra();
        }

        if (canContinueGliding()) {
            startFallFlying();
        }
    }

    /**
     * 装备鞘翅到胸甲槽
     * 如果鞘翅在背包（slot 9-35），先移到快捷栏，再装备
     * 如果鞘翅已在快捷栏（slot 36-44），直接装备
     */
    private void equipElytra() {
        int elytraSlot = findElytraInInventory();
        if (elytraSlot == -1) return;

        if (elytraSlot >= 36) {
            // 鞘翅已在快捷栏，直接交换到胸甲槽
            INVENTORY.submit(InventoryActionRequest.builder()
                    .owner(this)
                    .actions(InventoryActionMacros.swapSlots(elytraSlot, CHEST_SLOT_ID))
                    .actionDelayTicks(0)
                    .priority(INVENTORY_PRIORITY)
                    .build());

        } else {
            // 鞘翅在背包（9-35），先移到快捷栏
            int targetSlot = findSwapSlot(elytraSlot);
            if (targetSlot != -1) {

                var actions = new ArrayList<InventoryAction>();
                // 第一步：背包→快捷栏
                // 第二步：快捷栏→胸甲
                actions.addAll(InventoryActionMacros.swapSlots(elytraSlot, targetSlot));
                actions.addAll(InventoryActionMacros.swapSlots(targetSlot, CHEST_SLOT_ID));
                INVENTORY.submit(InventoryActionRequest.builder()
                        .owner(this)
                        .actions(actions)
                        .actionDelayTicks(0)
                        .priority(INVENTORY_PRIORITY)
                        .build());
            }
        }
    }

    /**
     * 查找可以交换的快捷栏位置
     */
    private int findSwapSlot(int excludeSlot) {
        var playerInventory = CACHE.getPlayerCache().getPlayerInventory();
        var elytraId = ItemRegistry.ELYTRA.id();

        // 优先找空位
        for (int i = 36; i <= 44; i++) {
            var item = playerInventory.get(i);
            if ((item == null || item == Container.EMPTY_STACK) && i != excludeSlot) {
                return i;
            }
        }
        // 找非鞘翅的位置
        for (int i = 36; i <= 44; i++) {
            var item = playerInventory.get(i);
            if (item != null && item != Container.EMPTY_STACK && item.getId() != elytraId && i != excludeSlot) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 卸下鞘翅到背包
     */
    private void unequipElytra() {
        int emptySlot = findEmptySlotInInventory();
        if (emptySlot == -1) return;

        // 使用 swapSlots 将胸甲槽的鞘翅交换到空背包位置
        INVENTORY.submit(InventoryActionRequest.builder()
                .owner(this)
                .actions(InventoryActionMacros.swapSlots(CHEST_SLOT_ID, emptySlot))
                .actionDelayTicks(0)
                .priority(INVENTORY_PRIORITY)
                .build());
    }

    /**
     * 在玩家库存中查找鞘翅
     * 返回容器中的 slot ID（9-44）
     */
    private int findElytraInInventory() {
        var playerInventory = CACHE.getPlayerCache().getPlayerInventory();
        var elytraId = ItemRegistry.ELYTRA.id();

        // 搜索快捷栏区域（36-44）
        for (int i = 36; i <= 44; i++) {
            var item = playerInventory.get(i);
            if (item != null && item != Container.EMPTY_STACK && item.getId() == elytraId) {
                return i;
            }
        }

        // 搜索背包区域（9-35）
        for (int i = 9; i <= 35; i++) {
            var item = playerInventory.get(i);
            if (item != null && item != Container.EMPTY_STACK && item.getId() == elytraId) {
                return i;
            }
        }

        return -1;
    }

    /**
     * 在玩家库存中查找空槽位
     * 返回容器中的 slot ID（9-44）
     */
    private int findEmptySlotInInventory() {
        var playerInventory = CACHE.getPlayerCache().getPlayerInventory();

        // 搜索快捷栏区域（36-44）
        for (int i = 36; i <= 44; i++) {
            if (playerInventory.get(i) == Container.EMPTY_STACK) {
                return i;
            }
        }

        // 搜索背包区域（9-35）
        for (int i = 9; i <= 35; i++) {
            if (playerInventory.get(i) == Container.EMPTY_STACK) {
                return i;
            }
        }

        return -1;
    }

    /**
     * 检查玩家是否穿着鞘翅
     */
    public static boolean isWearingElytra() {
        var chestStack = CACHE.getPlayerCache().getEquipment(EquipmentSlot.CHESTPLATE);
        if (chestStack == null) return false;
        return chestStack.getId() == ItemRegistry.ELYTRA.id();
    }

    private void sendJump() {
        INPUTS.submit(InputRequest.builder()
                .owner(this)
                .input(Input.builder()
                        .jumping(true)
                        .build())
                .priority(INVENTORY_PRIORITY)
                .build());
    }

    /**
     * 发送开始滑翔数据包
     */
    private void sendStartFallFlying() {
        sendClientPacketAsync(new ServerboundPlayerCommandPacket(CACHE.getPlayerCache().getEntityId(), PlayerState.START_ELYTRA_FLYING));
    }

    public void startFallFlying() {
        // takeoff in the air with same jump input as players:
        INPUTS.submit(InputRequest.builder()
                .owner(this)
                .priority(INVENTORY_PRIORITY)
                .input(Input.builder()
                        .jumping(true)
                        .build())
                .build());
    }

    void startFallFlying2() {
        var metadata0 = CACHE.getPlayerCache().getThePlayer().getMetadata().get(0);
        if (metadata0 instanceof ByteEntityMetadata bmd0) {
            var b = bmd0.getPrimitiveValue();
            bmd0.setValue((byte) (b | 0x80));
        } else {
            var md = new ByteEntityMetadata(0, MetadataTypes.BYTE, (byte) 0x80);
            CACHE.getPlayerCache().getThePlayer().getMetadata().put(0, md);
        }
    }

    boolean isFallFlyingMetadata() {
        var fallFlyingMetadata = CACHE.getPlayerCache().getThePlayer().getMetadata().get(0);
        if (fallFlyingMetadata instanceof ByteEntityMetadata byteEntityMetadata) {
            var b = byteEntityMetadata.getPrimitiveValue();
            return (b & 0x80) != 0;
        } else {
            return false;
        }
    }


    /**
     * 检查是否可以继续滑翔
     */
    private boolean canContinueGliding() {
        var player = CACHE.getPlayerCache().getThePlayer();
        if (player == null) return false;

        // 基本条件检查
        if (BOT.isOnGround()) {
            return false;
        }

        // 液体检查
        if (BOT.isTouchingWater()
                || Globals.BOT.isSwimming()
                || BOT.isTouchingLava()) {
            return false;
        }

        return true;
    }
}
