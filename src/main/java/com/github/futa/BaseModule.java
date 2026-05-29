package com.github.futa;

import com.github.futa.config.FutaConfig;
import com.zenith.module.api.Module;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

public abstract class BaseModule extends Module {


    public static FutaConfig PLUGIN_CONFIG = FutaPlugin.PLUGIN_CONFIG;
    public static ComponentLogger log = FutaPlugin.log;


}
