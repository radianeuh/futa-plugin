package com.github.futa.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleCache {
    private static class CacheEntry {
        String value;
        long timestamp; // 存储时间戳（毫秒）

        CacheEntry(String value) {
            this.value = value;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private final Map<String, CacheEntry> map = new ConcurrentHashMap<>();
    private final long expireMillis = 2 * 60 * 1000; // 2分钟

    public SimpleCache() {
        // 启动后台线程定期清理过期数据
        Thread cleaner = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(60 * 1000); // 每分钟清一次
                    long now = System.currentTimeMillis();
                    map.entrySet().removeIf(e -> now - e.getValue().timestamp >= expireMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        cleaner.setDaemon(true); // 后台线程
        cleaner.start();
    }

    public void put(String key, String value) {
        map.put(key, new CacheEntry(value));
    }

    public String get(String key) {
        CacheEntry entry = map.get(key);
        if (entry == null) return null;
        if (System.currentTimeMillis() - entry.timestamp >= expireMillis) {
            map.remove(key); // 已过期
            return null;
        }
        return entry.value;
    }

    public void remove(String key) {
        map.remove(key);
    }

    public int size() {
        return map.size();
    }

    // 测试
    public static void main(String[] args) throws InterruptedException {


        cn.hutool.core.lang.SimpleCache cache = new cn.hutool.core.lang.SimpleCache();
        cache.put("hello", "world");
        System.out.println(cache.get("hello")); // world
        Thread.sleep(2 * 60 * 1000 + 1000);
        System.out.println(cache.get("hello")); // null（过期）
    }
}
