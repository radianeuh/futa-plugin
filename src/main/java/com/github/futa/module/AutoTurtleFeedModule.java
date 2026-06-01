package com.github.futa.module;

import com.github.rfresh2.EventConsumer;
import com.zenith.cache.data.entity.EntityLiving;
import com.zenith.cache.data.entity.EntityStandard;
import com.zenith.event.client.ClientBotTick;
import com.zenith.feature.player.*;
import com.zenith.feature.player.raycast.RaycastHelper;
import com.zenith.mc.item.ItemRegistry;
import com.zenith.module.impl.AbstractInventoryModule;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.futa.FutaPlugin.PLUGIN_CONFIG;
import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.INPUTS;

public class AutoTurtleFeedModule extends AbstractInventoryModule {
    public static final int PRIORITY = 500;

    // 存储已喂食的海龟及其最后喂食时间
    private final Map<Integer, Instant> fedTurtles = new HashMap<>();
    private WeakReference<EntityLiving> currentTarget = new WeakReference<>(null);
    private int delay = 0;

    public AutoTurtleFeedModule() {
        super(HandRestriction.MAIN_HAND, 1);
    }

    @Override
    public boolean enabledSetting() {
        return PLUGIN_CONFIG.autoTurtleFeed.enabled;
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
                of(ClientBotTick.class, this::onTick),
                of(ClientBotTick.Stopped.class, e -> reset())
        );
    }

    @Override
    public void onDisable() {
        reset();
    }

    private void reset() {
        delay = 0;
        currentTarget = new WeakReference<>(null);
        // 不清理fedTurtles，保持冷却记录
    }

    private void onTick(ClientBotTick event) {
        if (!CACHE.getPlayerCache().getThePlayer().isAlive()) {
            return;
        }

        // 处理延迟
        if (delay > 0) {
            delay--;
            EntityLiving target = currentTarget.get();
            if (target != null && canReach(target)) {
                // 在延迟期间持续瞄准目标
                rotateTo(target);
            }
            return;
        }

        // 寻找需要喂食的海龟
        EntityLiving target = findUnfedTurtle();
        if (target != null) {
            currentTarget = new WeakReference<>(target);

            // 切换到海草
            if (switchToSeagrass()) {
                // 执行喂食
                feedTurtle(target).addInputExecutedListener(this::onFeedExecuted);
            }
        } else {
            currentTarget = new WeakReference<>(null);
        }
    }

    @Nullable
    private EntityLiving findUnfedTurtle() {
        long cooldownMillis = PLUGIN_CONFIG.autoTurtleFeed.feedCooldownMinutes * 60L * 1000L;
        Instant now = Instant.now();

        return CACHE.getEntityCache().getEntities().values().stream()
                .filter(e -> e instanceof EntityStandard)
                .map(e -> (EntityStandard) e)
                .filter(e -> e.getEntityType() == EntityType.TURTLE)
                .filter(EntityLiving::isAlive)
                .filter(e -> !isRecentlyFed(e.getEntityId(), now, cooldownMillis))
                .filter(e -> isWithinRange(e))
                .filter(this::canReach)
                .min(Comparator.comparingDouble(e -> CACHE.getPlayerCache().distanceSqToSelf(e)))
                .orElse(null);
    }

    private boolean isRecentlyFed(int entityId, Instant now, long cooldownMillis) {
        Instant lastFed = fedTurtles.get(entityId);
        if (lastFed == null) {
            return false;
        }
        long elapsed = now.toEpochMilli() - lastFed.toEpochMilli();
        return elapsed < cooldownMillis;
    }

    private boolean isWithinRange(EntityLiving entity) {
        double maxDistSq = PLUGIN_CONFIG.autoTurtleFeed.maxDistance * PLUGIN_CONFIG.autoTurtleFeed.maxDistance;
        return CACHE.getPlayerCache().distanceSqToSelf(entity) <= maxDistSq;
    }

    private boolean canReach(final EntityLiving entity) {
        var rotation = RotationHelper.shortestRotationTo(entity);
        if (rotation == null) return false;
        var raycastResult = RaycastHelper.playerEyeRaycastThroughToTarget(entity, rotation.getX(), rotation.getY());
        return raycastResult.hit();
    }

    private InputRequestFuture feedTurtle(final EntityLiving entity) {
        var rotation = RotationHelper.shortestRotationTo(entity);
        if (rotation == null) return InputRequestFuture.rejected;

        return INPUTS.submit(InputRequest.builder()
                .owner(this)
                .input(Input.builder()
                        .rightClick(true)
                        .clickRequiresRotation(false)
                        .clickTarget(new ClickTarget.EntityInstance(entity))
                        .build())
                .yaw(rotation.getX())
                .pitch(rotation.getY())
                .priority(PRIORITY)
                .build());
    }

    private void onFeedExecuted(InputRequestFuture future) {
        if (future.getClickResult() instanceof ClickResult.RightClickResult rightClickResult
                && rightClickResult.getEntity() != null) {
            EntityLiving fedEntity = (EntityLiving) rightClickResult.getEntity();
            if (fedEntity.getEntityType() == EntityType.TURTLE) {
                // 标记该海龟已喂食
                fedTurtles.put(fedEntity.getEntityId(), Instant.now());
                delay = PLUGIN_CONFIG.autoTurtleFeed.feedIntervalTick; // 喂食后延迟

                if (PLUGIN_CONFIG.autoTurtleFeed.debugMode) {
                    info("成功喂食海龟 [ID: {}]，下次可喂食时间: {}分钟后",
                            fedEntity.getEntityId(),
                            PLUGIN_CONFIG.autoTurtleFeed.feedCooldownMinutes);
                }

                // 清理过期的冷却记录（超过冷却时间两倍的记录）
                cleanupOldRecords();
            }
        }
    }

    private void cleanupOldRecords() {
        long maxAge = PLUGIN_CONFIG.autoTurtleFeed.feedCooldownMinutes * 2L * 60L * 1000L;
        Instant now = Instant.now();
        fedTurtles.entrySet().removeIf(entry ->
                now.toEpochMilli() - entry.getValue().toEpochMilli() > maxAge
        );
    }

    private void rotateTo(EntityLiving entity) {
        var rotation = RotationHelper.shortestRotationTo(entity);
        if (rotation == null) return;
        INPUTS.submit(InputRequest.builder()
                .owner(this)
                .yaw(rotation.getX())
                .pitch(rotation.getY())
                .priority(PRIORITY)
                .build());
    }

    private boolean switchToSeagrass() {
        if (isItemOnHand(ItemRegistry.SEAGRASS.id())) {
            return true;
        }

        // 使用父类的物品切换方法
        delay = doInventoryActions();
        return delay == 0;
    }

    private boolean isItemOnHand(int itemId) {
        ItemStack mainHandStack = CACHE.getPlayerCache().getEquipment(
                org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot.MAIN_HAND
        );
        return mainHandStack != null && mainHandStack.getId() == itemId;
    }

    @Override
    public boolean itemPredicate(ItemStack itemStack) {
        return itemStack != null && itemStack.getId() == ItemRegistry.SEAGRASS.id();
    }

    @Override
    public int getPriority() {
        return 0;
    }
}
