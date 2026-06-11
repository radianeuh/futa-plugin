package com.github.futa.module;

import com.github.futa.BaseModule;
import com.github.futa.FutaPlugin;
import com.github.futa.config.ElytraFlyConfig;
import com.github.futa.util.ZUtil;
import com.github.rfresh2.EventConsumer;
import com.zenith.Globals;
import com.zenith.Proxy;
import com.zenith.cache.data.entity.EntityPlayer;
import com.zenith.event.client.ClientBotTick;
import com.zenith.feature.player.Bot;
import com.zenith.feature.player.InputRequest;
import com.zenith.feature.player.RotationHelper;
import com.zenith.module.impl.AutoArmor;
import com.zenith.util.ItemUtil;
import com.zenith.util.math.MutableVec3d;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.*;
import static com.zenith.mc.item.ItemRegistry.ELYTRA;

/**
 * ElytraFly 模块 - 移植自 Meteor Client
 * <p>
 * 让玩家在鞘翅飞行时自动控制视角 pitch，在 -40（抬头）和 +40（低头）之间振荡，
 * 实现在设定的高度范围内自动上下飞行的效果。
 * <p>
 * 主要行为：
 * 1. 在 upperBounds 和 lowerBounds 之间自动调整 pitch
 * 2. 当玩家高度低于 lowerBounds-10 时自动重置边界
 * 3. 当到达最高点（pitch=-40 且 Y 坐标不再上升）时重置边界
 * 4. 通过跟踪 Y 位置变化来检测是否到达最高点
 */
public class ElytraFlyModule extends BaseModule {

    ElytraFlyConfig config = FutaPlugin.PLUGIN_CONFIG.elytraFly;

    private boolean pitchingDown = true;
    private int pitch = 40;
    private boolean goingUp = true;
    private double lastY = 0;
    int tick = 0;
    private static final int ROTATION_PRIORITY = 10000;
    private static final float UNSET_YAW = 10000;

    /**
     * 外部模块（如 SearchAreaModule）可通过设置此字段来覆盖下一 tick 的 yaw。
     * 设置后会被 handlePitchControl 消费并重置为 UNSET。
     */
    public static float nextYaw = UNSET_YAW;

    @Override
    public boolean enabledSetting() {
        return config.enabled;
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
                of(ClientBotTick.class, Bot.POST_TICK_PRIORITY - 500, this::onTick),
                of(ClientBotTick.Starting.class, this::onTickStart)
        );
    }

    private void onTickStart(ClientBotTick.Starting starting) {
        if (ZUtil.isIn3cSpawn()) {
            return;
        }

        if (CONFIG.client.extra.autoArmor.enabled) {
            CONFIG.client.extra.autoArmor.enabled = false;
            MODULE.get(AutoArmor.class).syncEnabledFromConfig();
            info("AutoArmor 已关闭， 防止干扰挂机飞行");
        }
        if (!PLUGIN_CONFIG.elytraUnbreak.enabled) {
            PLUGIN_CONFIG.elytraUnbreak.enabled = true;
            MODULE.get(ElytraUnbreakModule.class).syncEnabledFromConfig();
            info("elytraUnbreak 已开启， 无消耗挂机飞行");

        }

        var player = CACHE.getPlayerCache().getThePlayer();
        if (!Globals.BOT.isOnGround()) {
            resetBounds(player);
        }

        pitch = 40;
        pitchingDown = true;
        goingUp = true;
        lastY = player.getY();
        // 重置 goto 目标，防止跨会话残留导致转圈


        info("ElytraFly 开始 upper:{}, lower:{}", (int) config.pitch40UpperBounds, (int) config.pitch40LowerBounds);
    }

    @Override
    public void onEnable() {
        var player = CACHE.getPlayerCache().getThePlayer();
        if (player == null) {
            error("玩家未连接");
            return;
        }
        if (ZUtil.isIn3cSpawn()) {
            return;
        }

        pitch = 40;
        pitchingDown = true;
        goingUp = true;
        lastY = player.getY();
        // 重置 goto 目标，防止跨会话残留导致转圈
        info("ElytraFly 已启用 upper:{}, lower:{}", (int) config.pitch40UpperBounds, (int) config.pitch40LowerBounds);

    }

    /**
     * 保留2位小数
     *
     * @return
     */
    public static int keep2Decimal(double x) {

        return (int) x;
    }

    private void onTick(ClientBotTick event) {
        var player = CACHE.getPlayerCache().getThePlayer();
        if (player == null) return;
        if (ZUtil.isIn3cSpawn()) {
            return;
        }
        if (player.getY() > config.pitch40UpperBounds + config.boundGap * 3) {
            resetBounds(player);
        }

        if (!Globals.BOT.isOnGround() && !Globals.BOT.isFallFlying() && !Globals.BOT.isTouchingWater()) {
            if (config.debug) {
                info("BOT 在空中意外停止滑翔状态了{}", tick);
            }
            MODULE.get(ElytraUnbreakModule.class).takeoff();
        }

        tick++;
        if (config.debug && tick % (config.debugLogPeriod * 20) == 0) {
            info("===========================");
            info("BOT loction: {} {} {}", keep2Decimal(player.getX()), keep2Decimal(player.getY()), keep2Decimal(player.getZ()));
            info("BOT Pitch: {} Yaw: {}", player.getPitch(), player.getYaw());
            info("BOT Speed: {}", getSpeed());
            info("BOT isFallFlying: {}", Globals.BOT.isFallFlying());
            info("BOT isOnGround: {}", Globals.BOT.isOnGround());
            info("BOT isWearingElytra: {}", isWearingElytra());
            if (isWearingElytra()) {
                var elytraStack = CACHE.getPlayerCache().getEquipment(EquipmentSlot.CHESTPLATE);
                // 获取最大耐久
                int maxDamage = ItemUtil.getMaxDamage(elytraStack);

                // 获取剩余耐久
                int durability = ItemUtil.getDamageUntilBreak(elytraStack);
                info("BOT Elytra: {}/{}", durability, maxDamage);
            }

            info("upper:{}, lower:{}", (int) config.pitch40UpperBounds, (int) config.pitch40LowerBounds);
            info("===========================");
        }

//        // 检查是否穿着鞘翅
//        if (!isWearingElytra()) return;
//
//        // 检查是否在滑翔（鞘翅飞行中）
//        if (!isFallFlying()) return;

        // ========== TickEvent.Pre 逻辑：边界重置和状态管理 ==========
        handleBoundsReset(player);

        // ========== TickEvent.Post 逻辑：pitch 控制 ==========
        handlePitchControl(player);

        // ========== 检查是否到达目标坐标 ==========
        if (config.disconnectOnReach) {
            checkAndDisconnect(player);
        }

        // ========== 检查是否低于指定Y坐标 ==========
        if (config.disconnectOnLowY > 0 && player.getY() < config.disconnectOnLowY && player.getY() != 0) {
            info("玩家 Y={} 低于 {}，自动下线", String.format("%.1f", player.getY()), config.disconnectOnLowY);
            Proxy.getInstance().disconnect();
        }

        lastY = player.getY();
    }

    /**
     * 控制 pitch 在 -40 到 +40 之间振荡
     */
    private void handlePitchControl(EntityPlayer player) {
        // 判断是否应该切换俯冲方向
        if (pitchingDown && player.getY() <= config.pitch40LowerBounds) {
            pitchingDown = false;
        } else if (!pitchingDown && player.getY() >= config.pitch40UpperBounds) {
            pitchingDown = true;
        }

        // 调整 pitch
        if (!pitchingDown && player.getPitch() > -40) {
            // 向上抬头
            pitch -= (int) config.pitch40RotationSpeed;
            if (pitch < -40) pitch = -40;
        } else if (pitchingDown && player.getPitch() < 40) {
            // 向下低头
            pitch += (int) config.pitch40RotationSpeed;
            if (pitch > 40) pitch = 40;
        }

        // 计算 yaw：优先级 1) nextYaw 覆盖（SearchAreaModule等外部使用） 2) 持续朝向目标坐标 3) 玩家当前yaw
        float playerYaw;
        if (nextYaw != UNSET_YAW) {
            // 外部模块设置了一次性 yaw 覆盖
            playerYaw = nextYaw;
            nextYaw = UNSET_YAW;
        } else if (config.targetX != 0 || config.targetZ != 0) {
            // goto 设置了目标坐标，每 tick 持续计算朝向
            playerYaw = RotationHelper.yawToXZ(config.targetX, config.targetZ);
        } else {
            playerYaw = player.getYaw();
        }

        INPUTS.submit(InputRequest.builder()
                .owner(this)
                .yaw(playerYaw)
                .pitch(pitch)
                .priority(ROTATION_PRIORITY)
                .build());
    }

    /**
     * 处理边界重置和状态管理
     */
    private void handleBoundsReset(EntityPlayer player) {
        // 玩家跌落到 lower bounds - 10 以下，重置边界
        if (player.getY() <= config.pitch40LowerBounds - 10) {
            resetBounds(player);
            return;
        }

        // pitch 达到 -40（最高点），标记为正在上升
        if (pitch == -40) {
            goingUp = true;
        } else if (goingUp && BOT.getVelocity().getY() <= 0) {
            // 正在上升且 Y 坐标不再上升（到达最高点），重置边界
            goingUp = false;
            resetBounds(player);
        }
    }

    /**
     * 根据玩家当前位置重置上下边界
     */
    private void resetBounds(EntityPlayer player) {
        config.pitch40UpperBounds = player.getY() - 5;
        config.pitch40LowerBounds = player.getY() - 5 - config.boundGap;
        debug("resetBounds - upper: {}, lower: {}", config.pitch40UpperBounds, config.pitch40LowerBounds);
    }

    /**
     * 检查玩家是否穿着鞘翅
     */
    private boolean isWearingElytra() {
        var chestStack = CACHE.getPlayerCache().getEquipment(EquipmentSlot.CHESTPLATE);
        if (chestStack == null) return false;
        // 检查物品名称是否为 elytra
        return chestStack.getId() == ELYTRA.id();
    }

    /**
     * 检查是否到达目标坐标附近，如果到达则断开连接
     */
    private void checkAndDisconnect(EntityPlayer player) {
        double dx = player.getX() - config.targetX;
        double dz = player.getZ() - config.targetZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance <= config.disconnectDistance && (config.targetX != 0 || config.targetZ != 0)) {
            info("已到达目标坐标 ({}, {}) 附近，距离: {} 格", config.targetX, config.targetZ, String.format("%.1f", distance));
            info("正在断开连接...");
            Proxy.getInstance().disconnect();
        }
    }


    public static String getSpeed() {
        MutableVec3d velocity = BOT.getVelocity();

        // 1. 运用勾股定理，计算 X 和 Z 轴的合速度（单位：格/刻）
        double horizontalTicks = Math.sqrt(velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ());

        // 2. 乘以 20 转换为 “格/秒” (Blocks per second)
        double bpsHorizontal = horizontalTicks * 20;
        return BigDecimal.valueOf(bpsHorizontal).stripTrailingZeros().setScale(1, RoundingMode.HALF_UP).toPlainString();
    }
}
