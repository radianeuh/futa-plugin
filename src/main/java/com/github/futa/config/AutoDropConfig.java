package com.github.futa.config;

import com.google.common.collect.Lists;

import java.util.List;

public class AutoDropConfig {
    public boolean enabled = false;
    public boolean whitelistMode = true;
    public List<String> items = Lists.newArrayList("rotten_flesh");
    public int delayBetweenDrops = 20;
}
