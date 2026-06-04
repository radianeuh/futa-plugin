package com.github.futa.command;

import com.github.futa.FutaPlugin;
import com.github.futa.config.ElytraFlyConfig;
import com.github.futa.module.ElytraFlyModule;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;

import static com.mojang.brigadier.arguments.DoubleArgumentType.getDouble;
import static com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class ElytraFlyCommand extends Command {

    ElytraFlyConfig config = FutaPlugin.PLUGIN_CONFIG.elytraFly;

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
                .name("elytraFly")
                .category(CommandCategory.MODULE)
                .description("""
                        ElytraFly - 鞘翅自动飞行模块

                        让玩家在鞘翅飞行时自动控制视角 pitch40 飞行的效果。
                        
                        推荐打开 ElytraUnbreak

                        注意：先手动起飞
                        """)
                .usageLines(
                        "on/off",
                        "upper <height>",
                        "lower <height>",
                        "speed <degrees>",
                        "gap <blocks>"
                )
                .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("elytraFly")
                .then(argument("toggle", toggle()).executes(c -> {
                    config.enabled = getToggle(c, "toggle");
                    MODULE.get(ElytraFlyModule.class).syncEnabledFromConfig();
                    c.getSource().getEmbed()
                            .title("ElytraFly " + toggleStrCaps(config.enabled))
                            .primaryColor();
                    return OK;
                }))
                .then(literal("upper").then(argument("height", doubleArg(0, 256)).executes(c -> {
                    config.pitch40UpperBounds = getDouble(c, "height");
                    c.getSource().getEmbed()
                            .title("上边界已设置为 " + config.pitch40UpperBounds)
                            .primaryColor();
                    return OK;
                })))
                .then(literal("lower").then(argument("height", doubleArg(0, 256)).executes(c -> {
                    config.pitch40LowerBounds = getDouble(c, "height");
                    c.getSource().getEmbed()
                            .title("下边界已设置为 " + config.pitch40LowerBounds)
                            .primaryColor();
                    return OK;
                })))
                .then(literal("speed").then(argument("degrees", doubleArg(1, 10)).executes(c -> {
                    config.pitch40RotationSpeed = getDouble(c, "degrees");
                    c.getSource().getEmbed()
                            .title("旋转速度已设置为 " + config.pitch40RotationSpeed + " 度/tick")
                            .primaryColor();
                    return OK;
                })))
                .then(literal("gap").then(argument("blocks", doubleArg(10, 100)).executes(c -> {
                    config.boundGap = getDouble(c, "blocks");
                    c.getSource().getEmbed()
                            .title("边界间距已设置为 " + config.boundGap + " 格")
                            .primaryColor();
                    return OK;
                })))
                .then(literal("unloadedChunks").then(argument("toggle", toggle()).executes(c -> {
                    config.noUnloadedChunks = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                            .title("阻止进入未加载区块 " + toggleStrCaps(config.noUnloadedChunks))
                            .description(config.noUnloadedChunks ? "启用：阻止进入未加载区块" : "禁用：允许进入未加载区块")
                            .primaryColor();
                    return OK;
                })))
                .then(literal("debug").then(argument("toggle", toggle()).executes(c -> {
                    config.debug = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                            .title("日志 " + toggleStrCaps(config.debug))
                            .description(config.debug ? "启用" : "禁用")
                            .primaryColor();
                    return OK;
                })));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        embed.title("ElytraFly 配置")
                .addField("Enabled", toggleStr(config.enabled))
                .addField("Upper Bounds (上边界)", config.pitch40UpperBounds + " 格")
                .addField("Lower Bounds (下边界)", config.pitch40LowerBounds + " 格")
                .addField("Rotation Speed (旋转速度)", config.pitch40RotationSpeed + " 度/tick")
                .addField("Bound Gap (边界间距)", config.boundGap + " 格")
                .addField("No Unloaded Chunks (阻止未加载区块)", toggleStr(config.noUnloadedChunks))
                .addField("debug (日志)", toggleStr(config.debug))
                .primaryColor();
    }
}
