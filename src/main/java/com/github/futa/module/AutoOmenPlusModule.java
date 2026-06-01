package com.github.futa.module;

import com.github.futa.BaseModule;
import com.github.futa.config.AutoOmenPlusConfig;
import com.github.rfresh2.EventConsumer;
import com.zenith.cache.data.entity.PotionEffect;
import com.zenith.cache.data.inventory.Container;
import com.zenith.event.client.ClientBotTick;
import com.zenith.feature.inventory.InventoryActionRequest;
import com.zenith.feature.inventory.actions.MoveToHotbarSlot;
import com.zenith.feature.inventory.actions.SetHeldItem;
import com.zenith.feature.player.ClickTarget;
import com.zenith.feature.player.Input;
import com.zenith.feature.player.InputRequest;
import com.zenith.mc.item.ItemData;
import com.zenith.mc.item.ItemRegistry;
import com.zenith.module.impl.KillAura;
import org.geysermc.mcprotocollib.protocol.data.game.entity.Effect;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.MoveToHotbarAction;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;

import java.util.List;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.*;

/**
 * AutoOmen Plus - 增强版自动喝不祥之瓶模块
 * <p>
 * 相比原版 AutoOmen，提供：
 * - before：提前量控制（效果剩余时间 < before tick 时触发续杯）
 * - one：保留一瓶（确保背包至少留一瓶）
 * - KillAura 自动联动
 */
public class AutoOmenPlusModule extends BaseModule {
    public static final int PRIORITY = 600;
    public static AutoOmenPlusConfig config = PLUGIN_CONFIG.autoOmenPlus;

    private int delay = 0;
    private boolean drinking = false;
    private long drinkStartTime = 0L;
    private int slot = -1;

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
        delay = 0;
        drinkStartTime = System.currentTimeMillis();
        if (!needDrink()) {
            stopDrinking();
            enableKillAura();
            setDelay(600);
        }
        drinking = false;
    }

    @Override
    public void onDisable() {
        if (drinking) {
            stopDrinking();
        }
    }

    // ==================== 主循环 ====================

    private void onTick(ClientBotTick event) {
        // 前置检查
        if (!CACHE.getPlayerCache().getThePlayer().isAlive()) return;
        if (CACHE.getPlayerCache().getGameMode() == GameMode.CREATIVE) return;
        if (CACHE.getPlayerCache().getGameMode() == GameMode.SPECTATOR) return;

        // 阶段一：延迟未结束
        if (delay > 0) {
            delay--;
            if (delay % 20 == 0) {
                info("喝药计时: " + (delay / 20) + "s");
            }
            return;
        }

        // 阶段二：延迟结束
        if (drinking) {
            handleDrinkingTick();
        } else {
            handleIdleTick();
        }
    }

    // ==================== 子情况处理 ====================

    private void handleDrinkingTick() {
        if (needDrink()) {
            drink();
        } else {
            info("喝药结束");
            stopDrinking();
            enableKillAura();
            setDelay(600);
            drinkStartTime = System.currentTimeMillis();
        }
    }

    private void handleIdleTick() {
        if (!needDrink()) {
            return;
        }
        disableKillAura();
        slot = findSlot();
        if (slot >= 0) {
            info("开始喝药");
            drink();
        } else {
            error("缺少<不详之瓶>");
        }
    }

    // ==================== 核心判断方法 ====================

    /**
     * 返回 true 表示需要喝药
     * 条件：
     * 1. 没有 BAD_OMEN 效果
     * 2. 且（没有 RAID_OMEN 效果 或 RAID_OMEN 剩余时间 < before）
     * 3. 且距离上次 drink() 已超过 5 秒
     */
    private boolean needDrink() {
        if (hasEffect(Effect.BAD_OMEN)) {
            return false;
        }
        if (hasRaidOmen()) {
            return false;
        }
        return System.currentTimeMillis() - drinkStartTime > 5000;
    }

    /**
     * 检查是否存在 RAID_OMEN 效果，且剩余持续时间 >= before
     * 此时认为倒计时充足，不需要喝药
     */
    private boolean hasRaidOmen() {
        PotionEffect effect = CACHE.getPlayerCache().getThePlayer().getPotionEffectMap().get(Effect.RAID_OMEN);
        return effect != null && effect.getDuration() >= config.before;
    }

    private boolean hasEffect(Effect effect) {
        return CACHE.getPlayerCache().getThePlayer().getPotionEffectMap().containsKey(effect);
    }

    // ==================== 喝药动作 ====================

    private void drink() {
        if (slot < 0) return;
        // 切换到药水槽位
        INVENTORY.submit(InventoryActionRequest.builder()
                .owner(this)
                .actions(List.of(new SetHeldItem(slot)))
                .priority(PRIORITY)
                .build());
        // 按住右键
        INPUTS.submit(InputRequest.builder()
                .owner(this)
                .input(Input.builder()
                        .rightClick(true)
                        .clickTarget(ClickTarget.None.INSTANCE)
                        .build())
                .priority(PRIORITY)
                .build());
        drinking = true;
        drinkStartTime = System.currentTimeMillis();
    }

    private void stopDrinking() {
        drinking = false;
        slot = -1;
    }

    // ==================== 查找药水 ====================

    /**
     * 遍历背包，返回可用于喝药的快捷栏槽位索引
     * 优先热键栏，且若"保留一瓶"启用则要求数量 > 1
     * 若药水不在热键栏，将其移动到空闲热键栏
     */
    private int findSlot() {
        var playerInventory = CACHE.getPlayerCache().getPlayerInventory();

        // 1. 优先在热键栏（36-44）搜索
        for (int i = 36; i <= 44; i++) {
            ItemStack itemStack = playerInventory.get(i);
            if (itemStack == Container.EMPTY_STACK) continue;
            if (!isOminousBottle(itemStack)) continue;
            if (config.one && itemStack.getAmount() <= 1) continue;
            return i;
        }

        // 2. 在背包主区域（9-35）搜索
        for (int i = 9; i <= 35; i++) {
            ItemStack itemStack = playerInventory.get(i);
            if (itemStack == Container.EMPTY_STACK) continue;
            if (!isOminousBottle(itemStack)) continue;
            if (config.one && itemStack.getAmount() <= 1) continue;

            // 找到但不在热键栏，需要移动到热键栏
            int targetHotbar = findEmptyHotbarSlot();
            if (targetHotbar < 0) {
                // 热键栏满，使用槽位 3
                targetHotbar = 3;
            }
            final int target = targetHotbar;
            INVENTORY.submit(InventoryActionRequest.builder()
                    .owner(this)
                    .actions(List.of(
                            new MoveToHotbarSlot(
                                    i,
                                    MoveToHotbarAction.from(target - 36)
                            )
                    ))
                    .priority(PRIORITY)
                    .build());
            return target;
        }

        return -1;
    }

    private int findEmptyHotbarSlot() {
        var playerInventory = CACHE.getPlayerCache().getPlayerInventory();
        for (int i = 36; i <= 44; i++) {
            if (playerInventory.get(i) == Container.EMPTY_STACK) {
                return i;
            }
        }
        return -1;
    }

    private boolean isOminousBottle(ItemStack itemStack) {
        ItemData itemData = ItemRegistry.REGISTRY.get(itemStack.getId());
        return itemData != null && itemData == ItemRegistry.OMINOUS_BOTTLE;
    }

    // ==================== KillAura 联动 ====================

    private void enableKillAura() {
        if (!CONFIG.client.extra.killAura.enabled) {
            CONFIG.client.extra.killAura.enabled = true;
            MODULE.get(KillAura.class).syncEnabledFromConfig();
            info("KillAura 已启用");
        }
    }

    private void disableKillAura() {
        if (CONFIG.client.extra.killAura.enabled) {
            CONFIG.client.extra.killAura.enabled = false;
            MODULE.get(KillAura.class).syncEnabledFromConfig();
            info("KillAura 已禁用");
        }
    }

    // ==================== 工具方法 ====================

    private void setDelay(int ticks) {
        this.delay = ticks;
    }

    // ==================== 获取当前状态 ====================

    public boolean isDrinking() {
        return drinking;
    }

    public long getDrinkStartTime() {
        return drinkStartTime;
    }

    public int getSlot() {
        return slot;
    }

    public int getDelay() {
        return delay;
    }
}
