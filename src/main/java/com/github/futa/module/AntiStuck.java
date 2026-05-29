package com.github.futa.module;

import com.github.rfresh2.EventConsumer;
import com.zenith.event.client.ClientBotTick;
import com.zenith.feature.player.World;
import com.zenith.mc.block.Block;
import com.zenith.mc.block.BlockPos;
import com.zenith.mc.block.BlockRegistry;
import com.zenith.module.api.Module;
import com.zenith.network.client.ClientSession;
import com.zenith.network.codec.AsyncPacketHandler;
import com.zenith.network.codec.PacketHandlerCodec;
import com.zenith.network.codec.PacketHandlerStateCodec;
import com.zenith.util.math.MathHelper;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.github.futa.FutaPlugin.PLUGIN_CONFIG;
import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.BARITONE;
import static com.zenith.Globals.CACHE;

public class AntiStuck extends Module {
    private final Queue<Long> teleportAcceptTimes = new ConcurrentLinkedQueue<>();
    private boolean antiStuckActive = false;
    private int antiStuckTicks = 0;

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
                of(ClientBotTick.class, this::handleClientBotTick)
        );
    }

    @Override
    public boolean enabledSetting() {
        return PLUGIN_CONFIG.antiStuck.enabled;
    }

    @Override
    public PacketHandlerCodec registerClientPacketHandlerCodec() {
        return PacketHandlerCodec.clientBuilder()
                .setId("anti-stuck")
                .setPriority(0)
                .state(ProtocolState.GAME, PacketHandlerStateCodec.clientBuilder()
                        .postOutbound(ServerboundAcceptTeleportationPacket.class, (AsyncPacketHandler<ServerboundAcceptTeleportationPacket, ClientSession>) (packet, session) -> {
                            // 记录传送确认包的时间
                            teleportAcceptTimes.add(System.currentTimeMillis());

                            // 清理5秒前的记录
                            long fiveSecondsAgo = System.currentTimeMillis() - 5000;
                            while (!teleportAcceptTimes.isEmpty() && teleportAcceptTimes.peek() < fiveSecondsAgo) {
                                teleportAcceptTimes.poll();
                            }

                            // 如果5秒内超过6次，则触发防卡逻辑
                            if (teleportAcceptTimes.size() >= 6) {
                                activateAntiStuck();
                            }
                            return true;
                        })
                        .build())
                .build();
    }

    private void activateAntiStuck() {
        if (!antiStuckActive) {
            antiStuckActive = true;
            antiStuckTicks = 0;
            info("激活防卡机制 - 检测到频繁传送确认包");
        }
    }

    public void handleClientBotTick(ClientBotTick event) {
        if (antiStuckActive) {
            antiStuckTicks++;

            // 等待一段时间确保状态稳定
            if (antiStuckTicks < 20) return;

            // 寻找安全位置并移动
            BlockPos safePos = findSafePosition();
            if (safePos != null) {
                info("尝试移动到安全位置: {}, {}, {}", safePos.x(), safePos.y(), safePos.z());
                // 使用强制位置同步
                BARITONE.pathTo(safePos.x(), safePos.y(), safePos.z());
                info("已发送强制位置同步");
            }

            // 重置状态
            antiStuckActive = false;
            antiStuckTicks = 0;
        }
    }

    private BlockPos findSafePosition() {
        double playerX = CACHE.getPlayerCache().getX();
        double playerY = CACHE.getPlayerCache().getY();
        double playerZ = CACHE.getPlayerCache().getZ();

        int playerBlockX = MathHelper.floorI(playerX);
        int playerBlockY = MathHelper.floorI(playerY);
        int playerBlockZ = MathHelper.floorI(playerZ);

        // 检查当前位置周围5x5范围内的位置
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                // 跳过当前位置
                if (dx == 0 && dz == 0) continue;

                int targetX = playerBlockX + dx;
                int targetZ = playerBlockZ + dz;

                // 检查从当前高度向下搜索安全位置
                for (int dy = 0; dy >= -3; dy--) {
                    int targetY = playerBlockY + dy;
                    BlockPos pos = new BlockPos(targetX, targetY, targetZ);

                    if (isSafePosition(pos)) {
                        return pos;
                    }
                }

                // 检查从当前高度向上搜索安全位置
                for (int dy = 1; dy <= 3; dy++) {
                    int targetY = playerBlockY + dy;
                    BlockPos pos = new BlockPos(targetX, targetY, targetZ);

                    if (isSafePosition(pos)) {
                        return pos;
                    }
                }
            }
        }

        return null;
    }

    private boolean isSafePosition(BlockPos pos) {
        // 检查脚下位置是否为固体方块
        Block floorBlock = World.getBlock(pos.x(), pos.y(), pos.z());
        if (floorBlock == BlockRegistry.AIR || World.isFluid(floorBlock)) {
            return false;
        }

        // 检查玩家身体所在位置（1格高）是否为空气
        Block bodyBlock = World.getBlock(pos.x(), pos.y() + 1, pos.z());
        if (bodyBlock != BlockRegistry.AIR) {
            return false;
        }

        // 检查玩家头部所在位置（2格高）是否为空气
        Block headBlock = World.getBlock(pos.x(), pos.y() + 2, pos.z());
        return headBlock == BlockRegistry.AIR;
    }
}
