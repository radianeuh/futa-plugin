package com.github.futa.module;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.github.futa.util.MessageGenerator;
import com.github.rfresh2.EventConsumer;
import com.zenith.cache.data.entity.Entity;
import com.zenith.cache.data.entity.EntityLiving;
import com.zenith.cache.data.entity.EntityPlayer;
import com.zenith.event.client.ClientBotTick;
import com.zenith.event.client.ClientDeathEvent;
import com.zenith.event.module.ServerPlayerInVisualRangeEvent;
import com.zenith.feature.pathfinder.BlockStateInterface;
import com.zenith.mc.block.Block;
import com.zenith.mc.block.BlockPos;
import com.zenith.mc.block.BlockRegistry;
import com.zenith.module.api.Module;
import com.zenith.util.ChatUtil;
import com.zenith.util.math.MathHelper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.futa.FutaPlugin.PLUGIN_CONFIG;
import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.*;

/**
 * AutoFollow 模块 - 自动跟随玩家
 * <p>
 * 功能：
 * - 配置目标玩家名列表
 * - 自动跟随视距内最近的配置玩家
 * - 智能路径规划和障碍物规避
 * - 战斗状态检测和暂停
 */
public class AutoFollow extends Module {

    private final AtomicInteger tickCounter = new AtomicInteger(0);
    private EntityPlayer currentTarget = null;
    private boolean isFollowing = false;
    private boolean isPaused = false;


    private static long lastPrintTime = 0;        // 上次打印时间
    private static final long COOLDOWN_MS = 10_000; // 10秒冷却（单位：毫秒）

    // 床点击相关
    private static long lastBedClickTime = 0;     // 上次点击床的时间

    // 跟随消息发送时间跟踪 - 对每个玩家名记录最后发送时间
    private final Map<String, Long> lastFollowMessageTime = new ConcurrentHashMap<>();


    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
                of(ClientBotTick.class, this::handleClientBotTick),
                of(ServerPlayerInVisualRangeEvent.class, this::handleNewPlayerInVisualRangeEvent),
                of(ClientDeathEvent.class, this::handleDeathEvent)

        );
    }

    private void handleNewPlayerInVisualRangeEvent(ServerPlayerInVisualRangeEvent event) {
        String name = event.playerEntry().getName();
        UUID uuid = event.playerEntry().getProfileId();
        if (PLUGIN_CONFIG.autoFollow.targetPlayers.contains(name)) {
            PLAYER_LISTS.getFriendsList().add(name, uuid);
        }

    }

    @Override
    public boolean enabledSetting() {
        return PLUGIN_CONFIG.autoFollow.enabled;
    }

    /**
     * 处理客户端机器人tick事件
     */
    private void handleClientBotTick(ClientBotTick event) {
        if (!enabledSetting()) {
            return;
        }


        // 检查并点击床
        checkAndClickBed();

        // 根据配置的更新间隔来执行逻辑
        int updateInterval = PLUGIN_CONFIG.autoFollow.updateInterval;
        if (tickCounter.incrementAndGet() % updateInterval != 0) {
            return;
        }

        try {
            // 检查是否应该暂停跟随
            if (shouldPauseFollowing()) {
                if (isFollowing) {
                    stopFollowing();
                }
                return;
            }

            // 寻找最近的目标玩家
            Optional<EntityPlayer> nearestTarget = findNearestTargetPlayer();

            if (nearestTarget.isPresent()) {
                EntityPlayer target = nearestTarget.get();

                // 如果目标改变或未在跟随，开始跟随
                if (!target.equals(currentTarget) || !isFollowing) {
                    startFollowing(target);
                }

                // 执行跟随逻辑
                followTarget(target);
            } else {
                // 没有找到目标，停止跟随
                if (isFollowing) {
                    stopFollowing();
                }
            }
        } catch (Exception e) {
            error("AutoFollow tick处理异常", e);
            stopFollowing();
        }
    }

    public void handleDeathEvent(final ClientDeathEvent event) {
        // 在重生后执行代码，这里延迟5秒确保重生已完成
        EXECUTOR.schedule(this::postRespawnAction, 3, TimeUnit.SECONDS);
    }

    private void postRespawnAction() {
        // 在这里添加重生后需要执行的代码
        // 例如发送消息、执行命令等

        info("玩家重生了！正在执行重生后动作...");

        // 可以在这里添加任何你需要的逻辑
        performPostRespawnActions();
    }

    private void performPostRespawnActions() {
        // 在这里实现你的重生后逻辑
        // 1. 给玩家发坐标
        if (currentTarget != null) {
            info("给玩家发坐标");
            sendCoordinate();
        }

    }

    private void sendCoordinate() {
        double x = CACHE.getPlayerCache().getThePlayer().getX();
        double y = CACHE.getPlayerCache().getThePlayer().getY();
        double z = CACHE.getPlayerCache().getThePlayer().getZ();
        info("给玩家发坐标：{}, {}, {}", x, y, z);
        String name = getName(currentTarget);
        String message = "[" + DateUtil.now() + "] " + MessageGenerator.getHelpMessage(x, y, z);

        sendClientPacketAsync(ChatUtil.getWhisperChatPacket(name, message));

    }

    /**
     * 检查是否应该暂停跟随
     */
    private boolean shouldPauseFollowing() {
        // 检查战斗状态
//        if (PLUGIN_CONFIG.autoFollow.stopInCombat && isInCombat()) {
//            if (!isPaused) {
//                info("检测到战斗状态，暂停跟随");
//                isPaused = true;
//            }
//            return true;
//        }
//
//        // 检查玩家健康状态
//        if (isPlayerLowHealth()) {
//            if (!isPaused) {
//                info("玩家生命值过低，暂停跟随");
//                isPaused = true;
//            }
//            return true;
//        }

        isPaused = false;
        return false;
    }

    /**
     * 检查是否在战斗状态
     */
    private boolean isInCombat() {
        // TODO: 实现战斗状态检测逻辑
        // 可以通过以下方式判断：
        // 1. 检查最近是否受到伤害
        // 2. 检查是否正在攻击实体
        // 3. 检查附近是否有敌对生物
        // 4. 检查玩家是否在攻击范围内

        return false; // 暂时返回false，由用户实现具体逻辑
    }

    /**
     * 检查玩家生命值是否过低
     */
    private boolean isPlayerLowHealth() {
        // TODO: 实现生命值检测逻辑
        // 获取玩家当前生命值和最大生命值进行判断

        return false; // 暂时返回false，由用户实现具体逻辑
    }


    /**
     * 查找最近的玩家
     *
     * @return 最近的玩家实体，如果未找到则返回null
     */
    private EntityPlayer findNearestPlayer() {
        return CACHE.getEntityCache().getEntities().values().stream()
                .filter(entity -> entity instanceof EntityPlayer)
                .map(entity -> (EntityPlayer) entity)
                .filter(entityPlayer -> !entityPlayer.isSelfPlayer()) // 排除自己
                .min((e1, e2) ->
                        (int) (CACHE.getPlayerCache().distanceSqToSelf(e1) - CACHE.getPlayerCache().distanceSqToSelf(e2)))
                .orElse(null);
    }


    /**
     * 查找最近的在指定名字列表中的玩家
     *
     * @param targetNames 目标玩家名字列表
     * @return 最近的玩家实体，如果未找到则返回null
     */
    private EntityPlayer findNearestPlayerByName(List<String> targetNames) {
        return CACHE.getEntityCache().getEntities().values().stream()
                .filter(entity -> entity instanceof EntityPlayer)
                .map(entity -> (EntityPlayer) entity)
                .filter(entityPlayer -> !entityPlayer.isSelfPlayer()) // 排除自己
                .filter(entityPlayer -> CACHE.getTabListCache().get(entityPlayer.getUuid())
                        .map(playerListEntry -> targetNames.contains(playerListEntry.getName()))
                        .orElse(false))
                .min((e1, e2) ->
                        (int) (CACHE.getPlayerCache().distanceSqToSelf(e1) - CACHE.getPlayerCache().distanceSqToSelf(e2)))
                .orElse(null);
    }

    /**
     * 查找指定名字列表中任意一个玩家（不考虑距离）
     *
     * @param targetNames 目标玩家名字列表
     * @return 找到的玩家实体，如果未找到则返回null
     */
    private EntityPlayer findAnyPlayerByName(List<String> targetNames) {
        return CACHE.getEntityCache().getEntities().values().stream()
                .filter(entity -> entity instanceof EntityPlayer)
                .map(entity -> (EntityPlayer) entity)
                .filter(entityPlayer -> !entityPlayer.isSelfPlayer()) // 排除自己
                .filter(entityPlayer -> CACHE.getTabListCache().get(entityPlayer.getUuid())
                        .map(playerListEntry -> targetNames.contains(playerListEntry.getName()))
                        .orElse(false))
                .findFirst()
                .orElse(null);
    }


    private double getDistanceToPlayer(final Entity e) {
        var player = CACHE.getPlayerCache().getThePlayer();
        return MathHelper.manhattanDistance3d(e.getX(), e.getY(), e.getZ(), player.getX(), player.getY(), player.getZ());
    }

    /**
     * 寻找最近的目标玩家
     */
    private Optional<EntityPlayer> findNearestTargetPlayer() {
        if (PLUGIN_CONFIG.autoFollow.followAnyone) {
            return Optional.ofNullable(findNearestPlayer());
        }
        List<String> targetPlayers = PLUGIN_CONFIG.autoFollow.targetPlayers;
        if (targetPlayers.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(findNearestPlayerByName(targetPlayers));

    }


    /**
     * 获取当前玩家名称
     */
    private String getCurrentPlayerName() {
        // TODO: 实现获取当前玩家名称的逻辑

        return ""; // 暂时返回空字符串，由用户实现具体逻辑
    }


    /**
     * 开始跟随目标
     */
    private void startFollowing(EntityPlayer target) {
        this.currentTarget = target;
        this.isFollowing = true;
        this.isPaused = false;
        String targetName = getName(target);
        info("开始跟随玩家: {} (距离: {})", targetName, String.format("%.1f", getDistanceToPlayer(target)));

        // 清理过期的消息时间记录
        cleanupExpiredMessageTimes();

        debug("开始跟随新目标: {}，消息时间记录已清理", targetName);
    }

    /**
     * 停止跟随
     */
    private void stopFollowing() {
        if (isFollowing) {
            info("停止跟随玩家: {}", currentTarget);
            this.currentTarget = null;
            this.isFollowing = false;

            // 停止移动
            // TODO: 调用API停止当前移动
            // stopMovement();
        }
    }

    /**
     * 执行跟随逻辑
     */
    private void followTarget(EntityPlayer target) {
        if (target == null) {
            return;
        }

        double distance = getDistanceToPlayer(target);
        double followDistance = PLUGIN_CONFIG.autoFollow.followDistance;

        // 如果距离已经足够近，不需要移动
        if (distance <= followDistance) {
        }

        try {
            BARITONE.follow(target);


            String name = getName(target);
            info("跟随移动: {} -> 距离: {}", name, String.format("%.1f", distance));

            sendRandomFollowMessage();

            // 每100次tick（约5分钟）清理一次过期记录
            if (tickCounter.get() % 100 == 0) {
                cleanupExpiredMessageTimes();
            }
        } catch (Exception e) {
            error("跟随移动时发生异常", e);
        }
    }

    private static String getName(EntityPlayer target) {
        if (target == null) {
            return null;
        }
        return CACHE.getTabListCache().get(target.getUuid()).get().getName();
    }

    /**
     * 清理过期的消息时间记录
     */
    private void cleanupExpiredMessageTimes() {
        long now = System.currentTimeMillis();
        long cutoffTime = now - 120_000; // 120秒前的时间点

        // 移除所有超过120秒的记录
        lastFollowMessageTime.entrySet().removeIf(entry ->
                entry.getValue() < cutoffTime);

        debug("消息时间记录清理完成，当前记录数: {}", lastFollowMessageTime.size());
    }

    @Override
    public void onEnable() {
        info("AutoFollow 模块已启用 - 配置的目标玩家: {}",
                PLUGIN_CONFIG.autoFollow.targetPlayers);

        // 启用 KillAura 并配置目标玩家
        CONFIG.client.extra.killAura.enabled = true;
        CONFIG.client.extra.killAura.targetPlayers = true;
        info("KillAura 已启用，目标玩家攻击已开启");

        // 启用 AutoArmor
        CONFIG.client.extra.autoArmor.enabled = true;
        info("AutoArmor 已启用");

        // 启用 AutoEat
        CONFIG.client.extra.autoEat.enabled = true;
        info("AutoEat 已启用");

        // 启用 AutoTotem
        CONFIG.client.extra.autoTotem.enabled = true;
        info("AutoTotem 已启用");

        // 启用自动重连
        CONFIG.client.extra.autoReconnect.enabled = true;
        info("AutoReconnect 已启用");

        // 启用自动复活
        CONFIG.client.extra.autoRespawn.enabled = true;
        info("AutoRespawn 已启用");

        // 禁用 AntiAFK
        CONFIG.client.extra.antiafk.enabled = false;
        info("AntiAFK 已禁用");

        // 禁用 AntiKick
        CONFIG.client.extra.antiKick.enabled = false;
        info("AntiKick 已禁用");

        // 禁用 AntiLeak（假设这是防止信息泄露的模块）
        CONFIG.client.extra.antiLeak.enabled = false;
        info("AntiLeak 已禁用");

        // 禁用自动断开连接（可能在特定条件下触发）
        CONFIG.client.extra.utility.actions.autoDisconnect.enabled = false;
        info("自动断开连接功能已禁用");

        // 总结性信息
        info("所有辅助模块配置已完成，AutoFollow 模块正在运行");

    }

    @Override
    public void onDisable() {
        info("AutoFollow 模块已禁用");
        stopFollowing();
        cleanupExpiredMessageTimes(); // 禁用时清理记录
    }

    /**
     * 获取当前跟随状态
     */
    public String getFollowStatus() {
        if (!enabledSetting()) {
            return "模块已禁用";
        }
        if (isPaused) {
            return "已暂停";
        }
        if (isFollowing) {
            return String.format("正在跟随: %SimpleCache", currentTarget);
        }
        return "等待目标";
    }


    /**
     * 随机选择一条消息并发送给目标玩家，对同一目标120秒内只发送一次
     */
    public void sendRandomFollowMessage() {
        if (!PLUGIN_CONFIG.autoFollow.chat) {
            return;
        }

        long now = System.currentTimeMillis();

        // 先检查10秒通用冷却
        if (now - lastPrintTime < COOLDOWN_MS) {
            return;
        }

        String targetName = getName(currentTarget);
        if (targetName == null) {
            return; // 没有有效目标
        }

        // 检查对该特定目标的120秒冷却
        Long lastMessageTimeForTarget = lastFollowMessageTime.get(targetName);
        if (lastMessageTimeForTarget != null &&
                now - lastMessageTimeForTarget < 120_000) { // 120秒 = 120,000毫秒
            return;
        }

        // 获取随机消息
        String message = MessageGenerator.getRandomFollowMessage();
        if (PLUGIN_CONFIG.autoFollow.followAnyone) {
            message = MessageGenerator.getRandomTaunt();
        }

        // 发送消息
        sendClientPacketAsync(ChatUtil.getWhisperChatPacket(targetName, "[" + DateUtil.now() + "] " + message));

        // 更新时间记录
        lastPrintTime = now; // 更新通用打印时间
        lastFollowMessageTime.put(targetName, now); // 更新该目标的最后消息时间

        info("发送跟随消息给 {}: {}", targetName, message);
    }


    /**
     * 检查并点击床 - 在配置范围内寻找床并点击，配置冷却时间内最多一次
     */
    private void checkAndClickBed() {


        long now = System.currentTimeMillis();

        // 检查冷却时间
        if (now - lastBedClickTime < PLUGIN_CONFIG.autoFollow.bedClickCooldownMs) {
            return;
        }

        // 获取玩家位置
        var player = CACHE.getPlayerCache().getThePlayer();
        if (player == null) {
            return;
        }


        tryClickNearbyBed(player);
    }

    /**
     * 尝试点击附近的床
     *
     * @param player 玩家实体
     */
    private void tryClickNearbyBed(EntityLiving player) {
        // 检查是否启用了自动点击床功能
        if (!PLUGIN_CONFIG.autoFollow.autoClickBed) {
            tryClickNearbyBoat();
            return;
        }

        // 在配置的搜索半径范围内寻找床
        int searchRadius = PLUGIN_CONFIG.autoFollow.bedSearchRadius;
        Optional<BlockPos> bedPos = findNearbyBed(player.getX(), player.getY(), player.getZ(), searchRadius);

        boolean friendNearby = findNearestTargetPlayer().isPresent();
        if (bedPos.isPresent() && friendNearby) {

            // 找到床，尝试点击
            clickBed(bedPos.get());
            lastBedClickTime = System.currentTimeMillis(); // 更新点击时间
            info("在位置 ({}, {}, {}) 找到床并点击",
                    bedPos.get().x(), bedPos.get().y(), bedPos.get().z());
            return;
        }

        tryClickNearbyBoat();
    }


    /**
     * 尝试点击附近的船
     */
    private void tryClickNearbyBoat() {
        // 检查是否启用了自动点击床功能
        if (!PLUGIN_CONFIG.autoFollow.autoClickBoat) {
            tryClickNearbyMinecart();
            return;
        }
        EntityLiving boat = findNearestBoat();
        if (boat != null) {
            info("在位置 ({}, {}, {}) 找到船并点击",
                    boat.blockPos().x(), boat.blockPos().y(), boat.blockPos().z());
            BARITONE.rightClickEntity(boat);
            lastBedClickTime = System.currentTimeMillis(); // 更新点击时间
            return;
        }

        tryClickNearbyMinecart();
    }

    /**
     * 尝试点击附近的矿车
     */
    private void tryClickNearbyMinecart() {
        // 检查是否启用了自动点击床功能
        if (!PLUGIN_CONFIG.autoFollow.autoClickCar) {
            return;
        }


        EntityLiving minecart = findNearestMinecart();
        if (minecart != null) {
            info("在位置 ({}, {}, {}) 找到矿车并点击",
                    minecart.blockPos().x(), minecart.blockPos().y(), minecart.blockPos().z());
            BARITONE.rightClickEntity(minecart);
            lastBedClickTime = System.currentTimeMillis(); // 更新点击时间
        }
    }

    /**
     * 在指定范围内寻找床
     *
     * @param centerX 中心X坐标
     * @param centerY 中心Y坐标
     * @param centerZ 中心Z坐标
     * @param radius  搜索半径（格数）
     * @return 找到的床的位置，如果没有找到则返回空的Optional
     */
    private Optional<BlockPos> findNearbyBed(double centerX, double centerY, double centerZ, int radius) {
        try {
            // 获取玩家方块位置
            int playerBlockX = MathHelper.floorI(centerX);
            int playerBlockY = MathHelper.floorI(centerY);
            int playerBlockZ = MathHelper.floorI(centerZ);

            // 在指定范围内搜索床
            for (int x = playerBlockX - radius; x <= playerBlockX + radius; x++) {
                for (int y = Math.max(0, playerBlockY - 2); y <= playerBlockY + 2; y++) {
                    for (int z = playerBlockZ - radius; z <= playerBlockZ + radius; z++) {
                        BlockPos pos = new BlockPos(x, y, z);

                        // 检查是否是床
                        if (isBedBlock(x, y, z)) {
                            info("找到床在位置: ({}, {}, {})", x, y, z);
                            return Optional.of(pos);
                        }
                    }
                }
            }
        } catch (Exception e) {
            error("寻找床时发生异常", e);
        }

        return Optional.empty();
    }

    /**
     * 检查指定方块是否是床
     *
     * @param block 要检查的方块
     * @return 如果是床返回true，否则返回false
     */
    private boolean isBedBlock(Block block) {
        if (block == null) {
            return false;
        }

        // 检查方块类型是否为床
        // Minecraft中床的方块ID包含 "bed"
//        BlockStateInterface.getBlock(xyz)
        String blockId = block.name();
        return blockId != null && blockId.contains("bed");
    }

    /**
     * 使用Baritone点击床
     *
     * @param bedPos 床的位置
     */
    private void clickBed(BlockPos bedPos) {
        try {
            // 使用Baritone的rightClickBlock方法点击床
            BARITONE.rightClickBlock(bedPos.x(), bedPos.y(), bedPos.z());

            debug("已使用Baritone点击床: ({}, {}, {})",
                    bedPos.x(), bedPos.y(), bedPos.z());
        } catch (Exception e) {
            error("点击床时发生异常", e);
        }
    }

    private boolean isBedBlock(int x, int y, int z) {

        int blockStateId = BlockStateInterface.getId(x, y, z);
        return blockStateId >= BlockRegistry.WHITE_BED.minStateId() &&
                blockStateId <= BlockRegistry.BLACK_BED.maxStateId();
    }

    private EntityLiving findNearestBoat() {
        return CACHE.getEntityCache().getEntities().values().stream()
                .filter(entity -> entity instanceof EntityLiving)
                .map(entity -> (EntityLiving) entity)
                .filter(entity -> StrUtil.containsIgnoreCase(entity.getEntityType().name(), "BOAT"))
                .filter(entity -> getDistanceToPlayer(entity) <= 4)
                .findFirst()
                .orElse(null);
    }

    /**
     * 方法描述
     * 获取附近 minecart
     *
     * @return
     */
    private EntityLiving findNearestMinecart() {
        return CACHE.getEntityCache().getEntities().values().stream()
                .filter(entity -> entity instanceof EntityLiving)
                .map(entity -> (EntityLiving) entity)
                .filter(entity -> StrUtil.containsIgnoreCase(entity.getEntityType().name(), "minecart"))
                .filter(entity -> getDistanceToPlayer(entity) <= 4)
                .findFirst()
                .orElse(null);
    }

}
