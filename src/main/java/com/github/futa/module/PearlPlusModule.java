package com.github.futa.module;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.github.futa.BaseModule;
import com.github.futa.dto.ParsedMessage;
import com.github.futa.util.FChatUtil;
import com.github.rfresh2.EventConsumer;
import com.viaversion.nbt.io.MNBTIO;
import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.ListTag;
import com.viaversion.nbt.tag.StringTag;
import com.zenith.Proxy;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandSource;
import com.zenith.discord.Embed;
import com.zenith.event.chat.SystemChatEvent;
import com.zenith.event.client.ClientBotTick;
import com.zenith.feature.pathfinder.BlockStateInterface;
import com.zenith.feature.player.World;
import com.zenith.mc.block.BlockPos;
import com.zenith.mc.block.BlockRegistry;
import com.zenith.util.ChatUtil;
import com.zenith.util.ComponentSerializer;
import com.zenith.util.config.Config;
import com.zenith.util.math.MathHelper;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockEntityInfo;

import java.util.ArrayList;
import java.util.List;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.*;

public class PearlPlusModule extends BaseModule {

    @Override
    public boolean enabledSetting() {
        return PLUGIN_CONFIG.pearlPlus.enabled;
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
                of(ClientBotTick.class, this::onTick),
                of(SystemChatEvent.class, this::onChat)
        );
    }

    private void onTick(ClientBotTick clientBotTick) {

        if (!(PLUGIN_CONFIG.pearlPlus.auto)) {
            return;
        }

        if (AutoLoginModule.isIn3cSpawn()) {
            return;
        }

        String autoId = PLUGIN_CONFIG.pearlPlus.autoId;
        if (StrUtil.isNotEmpty(autoId)) {

            var pearls = CONFIG.client.extra.pearlLoader.pearls;
            for (var pearl : pearls) {
                if (pearl.id().equals(autoId)) {
                    BlockPos current = CACHE.getPlayerCache().getThePlayer().blockPos();
                    BARITONE.rightClickBlock(pearl.x(), pearl.y(), pearl.z())
                            .addExecutedListener(f -> {

                                if (CONFIG.client.extra.pearlLoader.returnToStartPos) {
                                    BARITONE.pathTo(current.x(), current.z())
                                            .addExecutedListener(f2 -> {
                                                Proxy.getInstance().disconnect();
                                            });
                                }

                            });

                }
            }


            PLUGIN_CONFIG.pearlPlus.auto = false;

            PLUGIN_CONFIG.pearlPlus.autoId = "";

        }

    }

    private void onChat(SystemChatEvent event) {

//        String msg = "📨 xxx ➡ 你好";
        ParsedMessage result = FChatUtil.parsePrivateMessage(event.message());
        if (result != null) {
            System.out.println("是私信消息");
            String username = result.username;
            System.out.println("用户名: " + username);
            System.out.println("消息: " + result.content);

            if (StrUtil.startWith(result.content, "拉")) {
                info("收到来自 " + username + " 的拉珍珠请求 " + event.message());


                Config.Client.Extra.PearlLoader.Pearl pearl = getPearl(username);
                var isExist = pearl != null;

                int playerX = (int) CACHE.getPlayerCache().getX();
                int playerY = (int) CACHE.getPlayerCache().getY();
                int playerZ = (int) CACHE.getPlayerCache().getZ();

                if (!isExist) {

                    boolean foundSign = false;

                    // 在搜索范围内查找
                    int radius = 64;
                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dy = -4; dy <= 4; dy++) {
                            for (int dz = -radius; dz <= radius; dz++) {
                                int x = playerX + dx;
                                int y = playerY + dy;
                                int z = playerZ + dz;

                                if (isSign(x, y, z)) {
                                    List<String> signText = getSignText(x, y, z);
                                    if (signText.size() >= 1) {
                                        for (String string : signText) {
                                            String trim = StrUtil.trimToEmpty(string);
                                            if (StrUtil.containsIgnoreCase(trim, username)) {

                                                info("找到: {}, {}, {}", x, y, z);
                                                pearl = addPearl(username, x, y - 1, z);

                                                foundSign = true;
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                            if (foundSign) break;
                        }
                        if (foundSign) break;
                    }
                    if (foundSign) {
                        isExist = true;
                    }

                }

                if (isExist) {
                    int distance3d = (int) MathHelper.manhattanDistance3d(playerX, playerY, playerZ, pearl.x(), pearl.y(), pearl.z());
                    if (distance3d > 120) {
                        sendClientPacketAsync(ChatUtil.getWhisperChatPacket(username, "我现在离你的珍珠太远 距离" + distance3d));
                        return;
                    }

                    var ctx = CommandContext.create("pl load " + username, PearlPlusCommandSource.INSTANCE);
                    // carry sender to CommandSource for reply
                    ctx.getData().put("PearlPlusSender", username);
                    COMMAND.execute(ctx);

                    var embed = ctx.getEmbed();
                    String resp = embed.isTitlePresent() ? ChatUtil.sanitizeChatMessage(embed.title()) : "正在去拉";
                    if (resp.contains("Can't")) {
                        sendClientPacketAsync(ChatUtil.getWhisperChatPacket(username, "你珍珠没了 " + RandomUtil.randomString(4)));
                    } else {
                        discordAndIngameNotification(embed);
                        sendClientPacketAsync(ChatUtil.getWhisperChatPacket(username, "正在路上，距离：" + distance3d));
                    }

                } else {
                    sendClientPacketAsync(ChatUtil.getWhisperChatPacket(username, "没找到你的珍珠啊 " + username));
                }
            }
        }
    }

    private static Config.Client.Extra.PearlLoader.Pearl getPearl(String username) {
        var pearls = CONFIG.client.extra.pearlLoader.pearls;
        for (var pearl : pearls) {
            if (pearl.id().equals(username)) {
                return pearl;
            }
        }
        return null;
    }

    private Config.Client.Extra.PearlLoader.Pearl addPearl(String username, int x, int y, int z) {
        info("添加用户 " + username + " 的珍珠信息");
        Config.Client.Extra.PearlLoader.Pearl pearl = new Config.Client.Extra.PearlLoader.Pearl(username, x, y, z);
        CONFIG.client.extra.pearlLoader.pearls.add(pearl);

        return pearl;
    }

    private boolean isSign(int x, int y, int z) {

        int blockStateId = BlockStateInterface.getId(x, y, z);
        boolean isSign = blockStateId >= BlockRegistry.OAK_WALL_SIGN.minStateId() &&
                blockStateId <= BlockRegistry.BAMBOO_WALL_SIGN.maxStateId();

        int blockStateId2 = BlockStateInterface.getId(x, y - 1, z);
        boolean isTrapdoor = blockStateId2 >= BlockRegistry.OAK_TRAPDOOR.minStateId() &&
                blockStateId2 <= BlockRegistry.BAMBOO_TRAPDOOR.maxStateId();

        return isSign && isTrapdoor;
    }


    public List<String> getSignText(final int x, final int y, final int z) {
        List<String> signText = new ArrayList<>();

        try {
            Component textComponent = Component.empty();
            //等价于 x / 16 和 z / 16（向下取整）
            var chunk = World.getChunk(x >> 4, z >> 4);
            var blockEntities = chunk.getBlockEntities();
            //x & 15 等价于 x % 16，但更快（因为 16 是 2 的幂）。
            // 由于区块大小是 16，所以 x & 15 得到的是 x 在该区块中的局部 X 坐标（0~15）
            int relativeX = x & 15;
            int relativeZ = z & 15;
            for (BlockEntityInfo blockEntity : blockEntities) {
                if (blockEntity.getX() == relativeX && blockEntity.getY() == y && blockEntity.getZ() == relativeZ) {
                    CompoundTag nbt = (CompoundTag) MNBTIO.read(blockEntity.getNbt());
                    CompoundTag frontTextNbt = nbt.getCompoundTag("front_text");
                    ListTag<StringTag> messageLinesNbt = frontTextNbt.getListTag("messages", StringTag.class);

                    for (StringTag line : messageLinesNbt) {
                        var messageComponent = ComponentSerializer.deserialize(line.getValue());
                        textComponent = textComponent.append(messageComponent).appendNewline();

                        // if you just want the line as a string:
                        // do not just do `line.getValue()`, it will have extra formatting, it needs to go through components
                        String lineText = ComponentSerializer.serializePlain(messageComponent);
                        signText.add(lineText);
                    }
                    return signText;
                }
            }
        } catch (Exception e) {
            error("error:" + e.toString());
        }
        return signText;
    }

    public static ParsedMessage parsePrivateMessage(String msg) {
        // 正则：📨 后跟空格 + 用户名 + 空格 + ➡ + 空格 + 消息
        String regex = "^📨\\s+([^➡]+?)\\s+➡\\s+(.+)$";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
        java.util.regex.Matcher matcher = pattern.matcher(msg);

        if (matcher.matches()) {
            String username = matcher.group(1).trim();
            String content = matcher.group(2).trim();
            return new ParsedMessage(username, content);
        }
        return null; // 不是私信格式
    }


    public static class PearlPlusCommandSource implements CommandSource {
        private static final String SENDER_KEY = "PearlPlusSender";

        public static final PearlPlusCommandSource INSTANCE = new PearlPlusCommandSource();

        @Override
        public String name() {
            return "Pearl+";
        }

        @Override
        public boolean validateAccountOwner(CommandContext ctx) {
            return false;
        }

        @Override
        public void logEmbed(CommandContext ctx, Embed embed) {
        }
    }
}
