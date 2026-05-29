package com.github.futa.config;

public class VisualRangeLoggerConfig {
    public boolean enabled = true;
    public boolean logPlayerEnter = true;
    public boolean logPlayerLeave = true;
    public boolean logPlayerLogout = true;
    public boolean logCoordinates = true;
    public boolean logTimestamp = true;
    public String dateFormat = "yyyy-MM-dd HH:mm:ss";
    public String logFilePath = "visual_range_log.txt";
    public boolean appendToFile = true;
    public int maxLogFileSizeMB = 1000;
    public boolean autoRotateLogs = true;
    public boolean ignoreFriends = false;
    public boolean logDistance = true;
}
