package com.github.futa.config;

import java.util.ArrayList;
import java.util.List;


/**
 * Example configuration POJO.
 * <p>
 * Configurations are saved and loaded to JSON files
 * <p>
 * All fields should be public and mutable.
 * <p>
 * Fields to static inner classes generate nested JSON objects.
 */
public class AutoVaultOpenerConfig {
    // 是否启用插件
    public boolean enabled = false;

    public String secretKey = "KEY";

    // 当前处理到的账号索引（用于记录进度）
    public int currentAccountIndex = 0;


    //只上号看刷怪笼
    public boolean justLogin = false;

    public boolean justPathing = false;

    // true=循环，false=一轮结束
    public boolean loopAccounts = true;
    //Reverse cycle 反向循环账号
    public boolean reverseCycle = false;

    public boolean allowDropHotbar = false;

    // 登录命令
    public String loginCommand = "/login";

    // 等待时间设置（秒）
    public int waitAfterShulkerOpen = 1;
    public int waitAfterVaultOpen = 1;
    public int waitBetweenAccounts = 1;

    // 搜索范围
    public int searchRadius = 5;


    // 重试设置
    public int pathingRange = 400;
    public int timeoutSeconds = 90;
    public int accountTimeoutSeconds = 70; // 每个账号操作的超时时间（秒）

    // 钥匙数量阈值配置
    public int minKeyCount = 129; // 最小钥匙数量阈值

    public List<VaultInfo> vaults = new ArrayList<>();

    // 账号列表
    public List<AccountInfo> accounts = new ArrayList<>();

    public static class VaultInfo {
        public int vaultX, vaultY, vaultZ;
        public int buttonX, buttonY, buttonZ;
        public int keyContainerX, keyContainerY, keyContainerZ;
    }


    // 账号信息类
    public static class AccountInfo {
        public String username;
        public String password;

        public AccountInfo() {
        }

        public AccountInfo(String username, String password) {
            this.username = username;
            this.password = password;
        }

    }
}
