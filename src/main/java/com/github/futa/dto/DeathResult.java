package com.github.futa.dto;

import com.zenith.feature.deathmessages.Killer;

import java.util.Optional;

public record DeathResult(
        String victim,
        Optional<Killer> killer,
        Optional<String> weapon,
        Optional<String> weaponName,
        String schemaKey
) {
}
