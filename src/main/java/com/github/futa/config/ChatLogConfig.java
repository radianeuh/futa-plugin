package com.github.futa.config;

public class ChatLogConfig {
    public boolean enabled = false;
    public boolean logPlayerMessages = true;
    public boolean logSystemMessages = false;
    public boolean includeTimestamp = true;
    public String dateFormat = "yyyy-MM-dd HH:mm:ss";
    public int maxLogFileSizeMB = 10000;
    public boolean autoRotateLogs = true;
}
