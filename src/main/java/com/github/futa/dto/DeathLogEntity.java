package com.github.futa.dto;

import cn.hutool.core.date.DateUtil;
import com.zenith.feature.deathmessages.Killer;
import com.zenith.util.ComponentSerializer;

import java.util.List;
import java.util.Optional;

public class DeathLogEntity {
    public final Long timestamp;
    public final String time;
    public final String victim;
    public final String killer;
    public final String killerType;
    public final String weapon;
    public final String message;
    public final String rawMessage;
    public final String componentJson;
    public final String chineseMessage; // 翻译后的中文消息
    public List<String> players;
    public String schemaKey;

    public DeathLogEntity(DeathResult deathMessage, String message, String componentJson, String chineseMessage) {
        this.timestamp = System.currentTimeMillis();
        this.time = DateUtil.now();

        this.victim = deathMessage.victim();

        Optional<Killer> killerOpt = deathMessage.killer();
        this.killer = killerOpt.map(Killer::name).orElse(null);
        this.killerType = killerOpt.map(k -> k.type().name()).orElse(null);

        this.weapon = deathMessage.weaponName().orElse(deathMessage.weapon().orElse("Unknown"));
        this.message = message;
        this.rawMessage = ComponentSerializer.serializePlain(
                ComponentSerializer.deserialize(componentJson)
        );
        this.componentJson = componentJson;
        this.chineseMessage = chineseMessage;
    }

}
