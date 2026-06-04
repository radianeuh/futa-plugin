package com.github.futa.config;

/**
 * configuration POJO.
 * <p>
 * Configurations are saved and loaded to JSON files
 * <p>
 * All fields should be public and mutable.
 * <p>
 * Fields to static inner classes generate nested JSON objects.
 */
public class FutaConfig {
    // 是否启用插件
    public boolean enabled = true;

    public String key = "";

    /**
     * 一个简单的自动登录插件 AutoLoginPlugin，只注册了 SystemChatEvent 事件监听器。插件会在聊天中出现 /login 时自动发送登录指令
     */
    public boolean autoLogin = true;

    public int autoLoginTimeout = 60;
    /**
     * 断线自动重连
     */
    public boolean autoReboot = true;

    public AutoChestManagerConfig autoChest = new AutoChestManagerConfig();

    public ItemSorterConfig itemSorter = new ItemSorterConfig();

    public AutoFollowConfig autoFollow = new AutoFollowConfig();
    public AntiStuckConfig antiStuck = new AntiStuckConfig();
    public DieConfig die = new DieConfig();

    public WanderConfig wander = new WanderConfig();

    public PearlPlusConfig pearlPlus = new PearlPlusConfig();

    public ChatLogConfig chatLog = new ChatLogConfig();

    public ShopConfig shop = new ShopConfig();

    public AutoCraftConfig autoCraft = new AutoCraftConfig();

    public AutoEnchantConfig autoEnchant = new AutoEnchantConfig();

    public AutoDropConfig autoDrop = new AutoDropConfig();
    public VillagerTraderConfig trader = new VillagerTraderConfig();
    public AutoCrystalConfig autoCrystal = new AutoCrystalConfig();
    public EndGatewayConfig endGateway = new EndGatewayConfig();
    public VisualRangeLoggerConfig visualRangeLogger = new VisualRangeLoggerConfig();
    public AutoVaultOpenerConfig autoVaultOpen = new AutoVaultOpenerConfig();
    public FixedAngleViewConfig fixedAngleView = new FixedAngleViewConfig();
    public EnchantBookSorterConfig enchantBookSorter = new EnchantBookSorterConfig();
    public NetherWartFarmConfig netherWartFarm = new NetherWartFarmConfig();
    public AutoWitherConfig autoWither = new AutoWitherConfig();
    public AutoTurtleFeedConfig autoTurtleFeed = new AutoTurtleFeedConfig();
    public AuthBypasserConfig cccuuuAuthBypasser = new AuthBypasserConfig();
    public AutoOmenPlusConfig autoOmenPlus = new AutoOmenPlusConfig();
    public ElytraFlyConfig elytraFly = new ElytraFlyConfig();
    public ElytraUnbreakConfig elytraUnbreak = new ElytraUnbreakConfig();
    public BaseFinderConfig baseFinder = new BaseFinderConfig();
    public SearchAreaConfig searchArea = new SearchAreaConfig();
}
