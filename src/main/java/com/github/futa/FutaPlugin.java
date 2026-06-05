package com.github.futa;

import com.github.futa.command.*;
import com.github.futa.config.FutaConfig;
import com.github.futa.module.*;
import com.github.futa.module.ccchandler.AuthBypasserModule;
import com.zenith.plugin.api.Plugin;
import com.zenith.plugin.api.PluginAPI;
import com.zenith.plugin.api.ZenithProxyPlugin;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

/**
 * FOR AI developers
 * 常见功能写法、最佳实现等, 可以参考 BestPracticeExample
 *
 * @see BestPracticeExample
 */
@Plugin(
        id = "futa-manager",
        version = BuildConstants.VERSION,
        description = "futa craft plugin",
        url = "https://github.com/futa-team/futa-plugin",
        authors = {"futa"},
        mcVersions = "*"
        // to indicate any MC version: @Plugin(mcVersions = "*")
        // if you touch packet classes, you almost certainly need to pin to a single mc version
)
public class FutaPlugin implements ZenithProxyPlugin {
    public static FutaConfig PLUGIN_CONFIG;
    public static ComponentLogger log;

    @Override
    public void onLoad(PluginAPI pluginAPI) {
        log = pluginAPI.getLogger();
        log.info("futa plugin loading...");
        // initialize any configurations before modules or commands might need to read them
        PLUGIN_CONFIG = pluginAPI.registerConfig("futa", FutaConfig.class);

        pluginAPI.registerModule(new AuthBypasserModule());

        pluginAPI.registerModule(new AutoChestManagerModule());
        pluginAPI.registerModule(new ItemSorterModule());

        pluginAPI.registerModule(new DeathLogger());
        pluginAPI.registerModule(new AutoFollow());
        pluginAPI.registerModule(new WanderModule());
        pluginAPI.registerModule(new AutoLoginModule());
        pluginAPI.registerModule(new PearlPlusModule());
        pluginAPI.registerModule(new ChatLogModule());
        pluginAPI.registerModule(new Shop());
        pluginAPI.registerModule(new AutoCraftModule());
        pluginAPI.registerModule(new AutoEnchantModule());
//        pluginAPI.registerModule(new AutoDropModule());
        pluginAPI.registerModule(new VillagerTrader());
        pluginAPI.registerModule(new AutoCrystalModule());
        pluginAPI.registerModule(new EndGateway());
        pluginAPI.registerModule(new VisualRangeLogger());
        pluginAPI.registerModule(new AutoVaultOpenerModule());
        pluginAPI.registerModule(new FixedAngleView());
        pluginAPI.registerModule(new EnchantBookSorterModule());
        pluginAPI.registerModule(new NetherWartFarmModule());
        pluginAPI.registerModule(new AutoWitherModule());
        pluginAPI.registerModule(new AutoTurtleFeedModule());
        pluginAPI.registerModule(new AutoOmenPlusModule());
        pluginAPI.registerModule(new ElytraFlyModule());
        pluginAPI.registerModule(new ElytraUnbreakModule());
        pluginAPI.registerModule(new BaseFinder());
        pluginAPI.registerModule(new SearchAreaModule());
        pluginAPI.registerModule(new ContainerStressTestModule());

        pluginAPI.registerCommand(new ShowEntityCommand());
        pluginAPI.registerCommand(new AutoFollowCommand());
        pluginAPI.registerCommand(new WanderCommand());
        pluginAPI.registerCommand(new AutoLoginCommand());
        pluginAPI.registerCommand(new ChatLogCommand());

        pluginAPI.registerCommand(new AutoChestManagerCommand());
        pluginAPI.registerCommand(new ItemSorterCommand());
        pluginAPI.registerCommand(new ShopCommand());
        pluginAPI.registerCommand(new AutoCraftCommand());
        pluginAPI.registerCommand(new AutoEnchantCommand());
        pluginAPI.registerCommand(new PPCommand());
//        pluginAPI.registerCommand(new AutoDropCommand());
        pluginAPI.registerCommand(new VillagerTraderCommand());
        pluginAPI.registerCommand(new AutoCrystalCommand());
        pluginAPI.registerCommand(new EndGatewayCommand());
        pluginAPI.registerCommand(new VisualRangeLoggerCommand());
        pluginAPI.registerCommand(new AutoVaultOpenerCommand());
        pluginAPI.registerCommand(new FixedAngleViewCommand());
        pluginAPI.registerCommand(new EnchantBookSorterCommand());
        pluginAPI.registerCommand(new NetherWartFarmCommand());
        pluginAPI.registerCommand(new AutoWitherCommand());
        pluginAPI.registerCommand(new AutoTurtleFeedCommand());
        pluginAPI.registerCommand(new AutoOmenPlusCommand());
        pluginAPI.registerCommand(new ElytraFlyCommand());
        pluginAPI.registerCommand(new ElytraUnbreakCommand());
        pluginAPI.registerCommand(new LoginOnceCommand());
        pluginAPI.registerCommand(new SearchAreaCommand());
        pluginAPI.registerCommand(new ContainerStressTestCommand());

        log.info("futa plugin loaded.");
    }


}
