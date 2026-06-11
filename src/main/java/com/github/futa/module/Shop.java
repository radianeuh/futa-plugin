package com.github.futa.module;

import com.github.futa.BaseModule;
import com.github.futa.dto.ParsedMessage;
import com.github.futa.util.FChatUtil;
import com.github.futa.util.SimpleCache;
import com.github.rfresh2.EventConsumer;
import com.zenith.Proxy;
import com.zenith.event.chat.SystemChatEvent;
import com.zenith.event.client.ClientBotTick;
import com.zenith.util.ChatUtil;
import com.zenith.util.timer.Timer;
import com.zenith.util.timer.Timers;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.CONFIG;

public class Shop extends BaseModule {
    private final Timer tickTimer = Timers.tickTimer();
    private int spamIndex = 0;
    private final HashSet<String> whisperedPlayers = new HashSet<>();

    SimpleCache cache = new SimpleCache();

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
                of(ClientBotTick.class, this::handleClientTickEvent),
                of(SystemChatEvent.class, this::handleSystemChatEvent),
                of(ClientBotTick.Starting.class, this::clientTickStarting)
        );
    }

    @Override
    public boolean enabledSetting() {
        return PLUGIN_CONFIG.shop.enabled;
    }

    @Override
    public void onEnable() {
        whisperedPlayers.clear();
    }

    @Override
    public void onDisable() {
        whisperedPlayers.clear();
    }

    public void handleSystemChatEvent(final SystemChatEvent event) {
        ParsedMessage result = FChatUtil.parsePrivateMessage(event.message());
        if (result != null) {
            if (cache.get(result.username) != null) {
                return;
            }

            String radomMsg = getRadomMsg();
            info("回复私信消息：{}  {}", result.username, radomMsg);
            sendClientPacketAsync(ChatUtil.getWhisperChatPacket(result.username, radomMsg));
            cache.put(result.username, result.username);
        }
    }

    public void handleClientTickEvent(final ClientBotTick event) {
        if (!Proxy.getInstance().isOnlineForAtLeastDuration(Duration.ofSeconds(5))) return;

        if (tickTimer.tick(PLUGIN_CONFIG.shop.delaySeconds * 20)) {
            sendSpam();
        }
    }

    public void clientTickStarting(final ClientBotTick.Starting event) {
        tickTimer.reset();
        spamIndex = 0;
    }

    private void sendSpam() {
        if (PLUGIN_CONFIG.shop.messages.isEmpty()) return;
        if (PLUGIN_CONFIG.shop.randomOrder) {
            spamIndex = (int) (Math.random() * PLUGIN_CONFIG.shop.messages.size());
        } else {
            spamIndex = (spamIndex + 1) % PLUGIN_CONFIG.shop.messages.size();
        }
        if (PLUGIN_CONFIG.shop.whisper) {
            String player = getNextPlayer();

            if (player != null) {
                if (cache.get(player) == null) {

                    var packet = ChatUtil.getWhisperChatPacket(player, getRadomMsg() + getRadomTail());
                    info(">发私聊广告中 {}", packet.getMessage());
                    sendClientPacketAsync(packet);
                    cache.put(player, player);
                }
            }
        }

        String radomMsg = getRadomMsg();

        info(">发广告中 {}", radomMsg);

        sendClientPacketAsync(new ServerboundChatPacket(radomMsg + getRadomTail()));

    }

    private static String getRadomTail() {
        return PLUGIN_CONFIG.shop.appendRandom ? " " + UUID.randomUUID().toString().substring(0, 6) : "";
    }

    private static String getRadomMsg() {
        return ">" + PLUGIN_CONFIG.shop.messages.get((int) (Math.random() * PLUGIN_CONFIG.shop.messages.size()));
    }

    private @Nullable String getNextPlayer() {
        var nextPlayer = CACHE.getTabListCache().getEntries().stream()
                .map(PlayerListEntry::getName)
                .filter(name -> !name.equals(CONFIG.authentication.username))
                .filter(name -> !this.whisperedPlayers.contains(name))
                .collect(Collectors.collectingAndThen(
                        Collectors.toList(),
                        l -> {
                            Collections.shuffle(l);
                            return l.stream();
                        }
                ))
                .findFirst();
        if (nextPlayer.isPresent()) {
            this.whisperedPlayers.add(nextPlayer.get());
            return nextPlayer.get();
        } else {
            if (this.whisperedPlayers.isEmpty()) return null;
            this.whisperedPlayers.clear();
            return getNextPlayer();
        }
    }
}
