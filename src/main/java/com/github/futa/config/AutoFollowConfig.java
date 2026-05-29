package com.github.futa.config;

import java.util.ArrayList;
import java.util.List;

public class AutoFollowConfig {
    public boolean enabled = false;
    public List<String> targetPlayers = new ArrayList<>();
    public boolean chat = false;
    public boolean followAnyone = true;
    public double followDistance = 3;
    public int maxFollowDistance = 100;
    public int updateInterval = 20; // ticks
    public boolean avoidObstacles = true;
    public boolean stopInCombat = true;
    public boolean autoClickBed = true; // 自动点击床功能
    public boolean autoClickBoat = true;
    public boolean autoClickCar = true;
    public int bedSearchRadius = 4; // 床搜索半径
    public long bedClickCooldownMs = 5000; // 床点击冷却时间（毫秒）
}
