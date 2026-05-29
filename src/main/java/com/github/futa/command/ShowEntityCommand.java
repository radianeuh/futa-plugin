package com.github.futa.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.cache.data.entity.Entity;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;

import java.util.Collection;

import static com.zenith.Globals.CACHE;


/**
 * 显示世界实体信息命令
 */
public class ShowEntityCommand extends Command {

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
                .name("showentity")
                .aliases("se", "entities")
                .category(CommandCategory.MODULE)
                .description("""
                        显示世界实体数量和详细信息
                        """)
                .usageLines(
                        "count - 显示实体总数",
                        "detail - 显示实体详细信息",
                        "types - 按类型显示实体统计"
                )
                .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("showentity")
                // 显示实体总数
                .then(literal("count").executes(c -> {
                    showEntityCount(c);
                }))
                // 显示实体详细信息
                .then(literal("detail").executes(c -> {
                    showEntityDetails(c);
                }))
                // 按类型显示实体统计
                .then(literal("types").executes(c -> {
                    showEntityTypes(c);
                }));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        embed
                .primaryColor()
                .title("世界实体信息")
                .description("使用 `showentity <count|detail|types>` 查看实体信息");
    }

    private void showEntityCount(com.mojang.brigadier.context.CommandContext<CommandContext> context) {
        try {
            int entityCount = CACHE.getEntityCache().getEntities().values().size();
            context.getSource().getEmbed()
                    .primaryColor()
                    .title("世界实体数量")
                    .addField("实体总数", String.valueOf(entityCount))
                    .addField("查看详情", "使用 `/showentity detail` 查看详细信息\n使用 `/showentity types` 查看类型统计");
        } catch (Exception e) {
            context.getSource().getEmbed()
                    .errorColor()
                    .title("获取实体数量失败")
                    .description("无法获取当前世界的实体信息: " + e.getMessage());
        }
    }

    private void showEntityDetails(com.mojang.brigadier.context.CommandContext<CommandContext> context) {
        try {
            var entityList = CACHE.getEntityCache().getEntities().values();

            if (entityList == null || entityList.isEmpty()) {
                context.getSource().getEmbed()
                        .primaryColor()
                        .title("实体详细信息")
                        .addField("当前世界没有实体", "");
                return;
            }

            StringBuilder description = new StringBuilder();
            int count = 0;

            for (var entity : entityList) {
                if (count >= 50) { // 限制显示数量避免消息过长
                    description.append("... (还有 ").append(entityList.size() - count).append(" 个实体未显示)");
                    break;
                }

                String entityInfo = String.format("**%d.** %SimpleCache (%d, %d, %d)",
                        count + 1,
                        entity.getEntityType().name(),
                        (int) entity.getX(),
                        (int) entity.getY(),
                        (int) entity.getZ());

                description.append(entityInfo);
                if (count < entityList.size() - 1 && count < 49) {
                    description.append("\n");
                }
                count++;
            }

            context.getSource().getEmbed()
                    .primaryColor()
                    .title("实体详细信息 (" + entityList.size() + ")")
                    .addField("", description.toString());

        } catch (Exception e) {
            context.getSource().getEmbed()
                    .errorColor()
                    .title("获取实体详细信息失败")
                    .description("无法获取实体详细信息: " + e.getMessage());
        }
    }

    private void showEntityTypes(com.mojang.brigadier.context.CommandContext<CommandContext> context) {
        try {
            var entityList = getEntityList();

            if (entityList == null || entityList.isEmpty()) {
                context.getSource().getEmbed()
                        .primaryColor()
                        .title("实体类型统计")
                        .description("当前世界没有实体");
                return;
            }

            // 统计各类型实体数量
            var typeCountMap = new java.util.HashMap<String, Integer>();

            for (var entity : entityList) {
                String entityName = entity.getEntityType().name();
                typeCountMap.put(entityName, typeCountMap.getOrDefault(entityName, 0) + 1);
            }

            // 按数量排序
            var sortedEntries = typeCountMap.entrySet().stream()
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                    .toList();

            StringBuilder description = new StringBuilder();
            int totalTypes = sortedEntries.size();

            for (var entry : sortedEntries) {
                String typeInfo = String.format("**%SimpleCache:** %d 个",
                        entry.getKey(), entry.getValue());
                description.append(typeInfo);
                if (!entry.equals(sortedEntries.get(sortedEntries.size() - 1))) {
                    description.append("\n");
                }
            }

            context.getSource().getEmbed()
                    .primaryColor()
                    .title("实体类型统计 (" + totalTypes + " 种类型)")
                    .addField("实体总数", String.valueOf(entityList.size()))
                    .addField("类型分布", description.toString());

        } catch (Exception e) {
            context.getSource().getEmbed()
                    .errorColor()
                    .title("获取实体类型统计失败")
                    .description("无法获取实体类型统计: " + e.getMessage());
        }
    }

    private Collection<Entity> getEntityList() {
        Collection<Entity> values = CACHE.getEntityCache().getEntities().values();
        return values;
    }

    /**
     * 工具方法：格式化坐标
     */
    private String formatPos(double x, double y, double z) {
        return String.format("(%.1f, %.1f, %.1f)", x, y, z);
    }
}
