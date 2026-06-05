package com.github.futa.util;


import com.zenith.Proxy;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.ObjectEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundRemoveEntitiesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddEntityPacket;

import java.util.ArrayList;
import java.util.UUID;

public class TextDisplayHelper {

    /**
     * 向控制玩家发送一个 TextDisplay 实体
     * <p>
     * TextDisplay 元数据索引参考
     * <p>
     * ┌──────┬────────┬──────────────────────────────────────────┐
     * │ 索引 │  类型  │                   说明                   │
     * ├──────┼────────┼──────────────────────────────────────────┤
     * │ 22   │ String │ 文本内容（JSON Text Component）          │
     * ├──────┼────────┼──────────────────────────────────────────┤
     * │ 23   │ Int    │ 背景色（ARGB，如 0x40000000 = 半透明黑） │
     * ├──────┼────────┼──────────────────────────────────────────┤
     * │ 24   │ Byte   │ 文字透明度                               │
     * ├──────┼────────┼──────────────────────────────────────────┤
     * │ 25   │ Byte   │ 是否有阴影                               │
     * ├──────┼────────┼──────────────────────────────────────────┤
     * │ 26   │ Byte   │ 是否透视显示                             │
     * ├──────┼────────┼──────────────────────────────────────────┤
     * │ 27   │ Byte   │ 是否显示背景                             │
     * ├──────┼────────┼──────────────────────────────────────────┤
     * │ 28   │ Byte   │ 对齐方式（0=左，1=居中，2=右）           │
     * └──────┴────────┴──────────────────────────────────────────┘
     * <p>
     * 关键点
     * <p>
     * - 实体 ID：你需要自己管理一个不与服务端实体 ID 冲突的 ID，可以用一个递增计数器
     * - 坐标：这是客户端侧的显示坐标，不经过服务端验证，所以可以放在任意位置
     * - JSON Text Component：text 字段支持 {"text":"hello","color":"red"} 格式
     * - 只对控制玩家可见：因为是你直接发给 ServerSession 的，不在服务端的世界中，所以只有连接到 bot 的玩家能看到
     * - 如果要跟随 bot 移动：需要在 tick 中持续发送 ClientboundTeleportEntityPacket 更新位置
     *
     * @param text     显示的文本内容（支持 JSON Text Component）
     * @param x        X 坐标
     * @param y        Y 坐标
     * @param z        Z 坐标
     * @param entityId 实体 ID（需要你自己分配一个不冲突的 ID）
     */
    public static void sendTextDisplay(String text, double x, double y, double z, int entityId) {
        var connection = Proxy.getInstance().getActivePlayer();
        if (connection == null) return; // 没有控制玩家

        UUID uuid = UUID.randomUUID();

        // 1. 生成 TextDisplay 实体
        connection.sendAsync(new ClientboundAddEntityPacket(
                entityId,
                uuid,
                EntityType.TEXT_DISPLAY,
                x, y, z,
                0f,  // yaw
                0f,  // headYaw
                0f   // pitch
        ));

        // 2. 设置 TextDisplay 元数据
        var metadata = new ArrayList<EntityMetadata<?, ?>>();
        // index 22: text (String) - 文本内容
        metadata.add(new ObjectEntityMetadata<>(22, MetadataTypes.STRING, text));
        // index 23: background (Integer) - 背景色 ARGB，可选
        // metadata.add(new IntEntityMetadata(23, MetadataTypes.INT, 0x40000000)); // 半透明黑色背景

        connection.sendAsync(new ClientboundSetEntityDataPacket(entityId, metadata));
    }

    /**
     * 移除 TextDisplay
     */
    public static void removeTextDisplay(int entityId) {
        var connection = Proxy.getInstance().getActivePlayer();
        if (connection == null) return;
        connection.sendAsync(new ClientboundRemoveEntitiesPacket(new int[]{entityId}));
    }

    /**
     * 向所有观察玩家发送 chat 消息
     *
     * @param minimessage minimessage 格式的文本
     */
    public static void sendChatAlertToSpectators(String minimessage) {
        Proxy.getInstance().getSpectatorConnections().forEach(s -> {
            s.sendAsyncAlert(minimessage);
        });
    }

    /**
     * 向所有观察玩家发送 chat 消息
     *
     * @param minimessage minimessage 格式的文本
     */
    public static void sendChatToSpectators(String minimessage) {
        Proxy.getInstance().getSpectatorConnections().forEach(s -> {
            s.sendAsyncMessage(minimessage);
        });
    }
}
