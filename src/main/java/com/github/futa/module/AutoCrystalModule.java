package com.github.futa.module;

import com.github.futa.BaseModule;
import com.github.futa.config.AutoCrystalConfig;
import com.github.rfresh2.EventConsumer;
import com.zenith.cache.data.entity.EntityPlayer;
import com.zenith.event.client.ClientBotTick;
import com.zenith.event.client.ClientBotTick.Stopped;
import com.zenith.feature.pathfinder.PathingRequestFuture;
import com.zenith.util.timer.Timer;
import com.zenith.util.timer.Timers;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.BARITONE;
import static com.zenith.Globals.CACHE;

public class AutoCrystalModule extends BaseModule {
    public static final int PRIORITY = 9500;

    private State state = State.IDLE;
    private final Timer actionTimer = Timers.tickTimer();
    private PathingRequestFuture pathingFuture = PathingRequestFuture.rejected;

    // 目标追踪
    private UUID currentTarget;
    private int targetAttackTime;

    // 水晶管理
    private Map<UUID, Long> activeCrystals = new ConcurrentHashMap<>();
    private int lastCrystalPlaceTime;

    // 战斗状态
    private boolean inCombat = false;
    private int combatEndTime;

    AutoCrystalConfig config = PLUGIN_CONFIG.autoCrystal;

    @Override
    public boolean enabledSetting() {
        return config.enabled;
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
                of(ClientBotTick.class, this::onTick),
                of(Stopped.class, e -> reset())
        );
    }

    @Override
    public void onDisable() {
        reset();
    }

    private void reset() {
        state = State.IDLE;
        currentTarget = null;
        targetAttackTime = 0;
        activeCrystals.clear();
        lastCrystalPlaceTime = 0;
        inCombat = false;
        combatEndTime = 0;
        pathingFuture = PathingRequestFuture.rejected;
        actionTimer.reset();
    }

    private void onTick(ClientBotTick event) {
        if (!config.isValid()) {
            warn("AutoCrystal 配置无效，已禁用");
            config.enabled = false;
            return;
        }

        // 更新水晶状态
        updateActiveCrystals();

        // 更新战斗状态
        updateCombatStatus();

        switch (state) {
            case IDLE -> {
                if (actionTimer.tick(20)) { // 每秒检查一次
                    if (shouldStartCombat()) {
                        setState(State.FIND_TARGET);
                    }
                }
            }
            case FIND_TARGET -> {
                var target = findBestTarget();
                if (target != null) {
                    currentTarget = target;
                    targetAttackTime = 0;
                    inCombat = true;
                    info("发现目标: " + target);
                    setState(State.APPROACH_TARGET);
                } else {
                    setState(State.IDLE);
                }
            }
            case APPROACH_TARGET -> {
                if (currentTarget == null || !isValidTarget(currentTarget)) {
                    setState(State.FIND_TARGET);
                    return;
                }
                EntityPlayer targetEntity = CACHE.getEntityCache().getPlayers().get(currentTarget);
                if (targetEntity != null) {
                    var distance = getDistance(targetEntity);

                    if (distance <= config.placeRange) {
                        setState(State.PLACE_CRYSTAL);
                    } else {
                        // 移动到目标附近
                        if (pathingFuture.isDone()) {
                            pathingFuture = BARITONE.pathTo(
                                    (int) targetEntity.getX(),
                                    (int) targetEntity.getY(),
                                    (int) targetEntity.getZ()
                            );
                        }
                    }
                } else {
                    setState(State.FIND_TARGET);
                }
            }
            case PLACE_CRYSTAL -> {
                if (currentTarget == null || !isValidTarget(currentTarget)) {
                    setState(State.FIND_TARGET);
                    return;
                }

                if (actionTimer.tick(config.delayTicks)) {
                    if (canPlaceCrystal()) {
                        placeCrystal();
                        setState(State.DETONATE_CRYSTAL);
                    } else {
                        setState(State.FIND_TARGET);
                    }
                }
            }
            case DETONATE_CRYSTAL -> {
                if (actionTimer.tick(5)) { // 短暂延迟后引爆
                    detonateCrystals();
                    targetAttackTime++;

                    // 检查是否需要继续攻击
                    if (shouldContinueAttack()) {
                        setState(State.PLACE_CRYSTAL);
                    } else {
                        setState(State.IDLE);
                    }
                }
            }
        }
    }

    private void updateActiveCrystals() {
        // 清理已爆炸的水晶
        activeCrystals.entrySet().removeIf(entry -> {
            var crystal = CACHE.getPlayerCache().getEntityCache().getEntities().get(entry.getKey());

            //fixme
            return crystal == null;
        });
    }

    private void updateCombatStatus() {
        if (inCombat && currentTarget == null) {
            inCombat = false;
            combatEndTime = (int) System.currentTimeMillis();
        }
    }

    private boolean shouldStartCombat() {
        // 检查是否有敌人 nearby
        return findBestTarget() != null &&
                activeCrystals.size() < config.maxCrystalsActive;
    }

    private UUID findBestTarget() {
        var player = CACHE.getPlayerCache().getThePlayer();
        var entities = CACHE.getPlayerCache().getEntityCache().getPlayers();

        UUID bestTarget = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (var entity : entities.entrySet()) {
            if (isValidTarget(entity.getKey())) {
                double score = calculateTargetScore(entity);
                if (score > bestScore) {
                    bestScore = score;
                    bestTarget = entity.getKey();
                }
            }
        }

        return bestTarget;
    }

    private boolean isValidTarget(UUID targetUuid) {
        var targetEntity = CACHE.getPlayerCache().getEntityCache().getPlayers().get(targetUuid);
        if (targetEntity == null) return false;

//        // 检查是否是玩家
//        if (!targetEntity.isPlayer()) return false;

        // 检查距离
        var distance = getDistance(targetEntity);
        if (distance > config.targetRange) return false;

        // 检查生命值
        var health = targetEntity.getHealth();
        if (health < config.minHealth || health > config.maxHealth) return false;

        // 检查黑名单和白名单
        var playerName = targetEntity.getUuid();
        if (config.blacklistedPlayers.contains(playerName)) return false;
        if (!config.whitelistedPlayers.isEmpty() && !config.whitelistedPlayers.contains(playerName)) {
            return false;
        }

        return true;
    }

    private double calculateTargetScore(Object entity) {
        // 简单的目标评分系统
        double score = 0;

        // 距离越近分数越高
        var distance = getDistance(entity);
        score += (config.targetRange - distance) * 10;

        // 生命值越低分数越高（补刀）
        var health = getHealth(entity);
        score += (config.maxHealth - health) * 5;

        // 如果启用护甲优先，检查护甲值
        if (config.prioritizeArmor) {
            var armor = getArmorValue(entity);
            score += armor * 2;
        }

        return score;
    }

    private boolean canPlaceCrystal() {
        // 检查是否有水晶在物品栏
        if (!hasCrystalsInInventory()) {
            return false;
        }

        // 检查是否可以放置水晶在当前位置
        var player = CACHE.getPlayerCache().getThePlayer();
        var blockPos = player.blockPos();

        // 简化的放置检查
        return isGoodCrystalPosition(blockPos.x(), blockPos.y(), blockPos.z());
    }

    private boolean hasCrystalsInInventory() {
        var inventory = CACHE.getPlayerCache().getInventoryCache();
        //fixme
        return true;
//        return inventory.hasItem("end_crystal") || inventory.hasItem("crystal");
    }

    private boolean isGoodCrystalPosition(int x, int y, int z) {
        // 检查位置是否适合放置水晶
        // 这里应该实现更复杂的逻辑检查
        return true;
    }

    private void placeCrystal() {
        // 放置水晶逻辑
        if (config.debugMode) {
            info("放置水晶");
        }

        // 实际的水晶放置逻辑
        lastCrystalPlaceTime = (int) System.currentTimeMillis();
    }

    private void detonateCrystals() {
        // 引爆所有激活的水晶
        if (config.debugMode) {
            info("引爆水晶，数量: " + activeCrystals.size());
        }

        // 实际的水晶引爆逻辑
        activeCrystals.clear();
    }

    private boolean shouldContinueAttack() {
        if (currentTarget == null) return false;

        // 检查目标是否仍然有效
        if (!isValidTarget(currentTarget)) return false;

        // 检查攻击次数限制
        if (targetAttackTime > 10) return false; // 最多攻击10次

        // 检查是否过于危险
        if (config.safeMode && isPlayerInDanger()) return false;

        return true;
    }

    private boolean isPlayerInDanger() {
        var player = CACHE.getPlayerCache().getThePlayer();
        var health = player.getHealth();
        return health < 6.0; // 生命值低于3心时认为危险
    }

    private double getDistance(Object entity) {
        var player = CACHE.getPlayerCache().getThePlayer();
        var dx = player.getX() - getX(entity);
        var dy = player.getY() - getY(entity);
        var dz = player.getZ() - getZ(entity);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private double getX(Object entity) {
        return 0;
    } // 简化实现

    private double getY(Object entity) {
        return 0;
    } // 简化实现

    private double getZ(Object entity) {
        return 0;
    } // 简化实现

    private double getHealth(Object entity) {
        return 10.0;
    } // 简化实现

    private int getArmorValue(Object entity) {
        return 0;
    } // 简化实现

    private void setState(State newState) {
        if (config.debugMode) {
            debug("AutoCrystal state change: {} -> {}", state, newState);
        }
        this.state = newState;
    }

    public enum State {
        IDLE,           // 空闲状态
        FIND_TARGET,    // 寻找目标
        APPROACH_TARGET, // 接近目标
        PLACE_CRYSTAL,  // 放置水晶
        DETONATE_CRYSTAL // 引爆水晶
    }
}
