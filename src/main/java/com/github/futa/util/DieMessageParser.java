package com.github.futa.util;

import com.github.futa.dto.DeathResult;
import com.github.futa.module.DeathLogger;
import com.google.gson.Gson;
import com.zenith.feature.deathmessages.Killer;
import com.zenith.feature.deathmessages.KillerType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DieMessageParser {

    // 物品翻译缓存
    private static Map<String, String> itemTranslations = new HashMap<>();
    private static boolean translationsLoaded = false;

    // 静态初始化块，加载物品翻译
    static {
        loadItemTranslations();
    }

    /**
     * 加载物品翻译数据
     */
    private static void loadItemTranslations() {
        if (translationsLoaded) {
            return;
        }

        try (InputStream inputStream = DieMessageParser.class.getResourceAsStream("/mcdata/items.json")) {
            if (inputStream != null) {
                // 使用简单的JSON解析来加载物品翻译
                String jsonContent = new String(inputStream.readAllBytes());
                parseItemsJson(jsonContent);
                translationsLoaded = true;

            }
        } catch (IOException e) {
            System.err.println("无法加载物品翻译文件: " + e.getMessage());
        }
    }

    /**
     * 解析物品JSON文件内容
     */
    private static void parseItemsJson(String jsonContent) {

        Gson gson = new Gson();
        itemTranslations = gson.fromJson(jsonContent, new HashMap<String, String>().getClass());

    }

    /**
     * 翻译物品ID为中文名称
     *
     * @param itemId 物品ID (例如: "minecraft:diamond_sword")
     * @return 中文名称，如果找不到翻译则返回原ID
     */
    public static String translateItem(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return itemId;
        }

        // 移除 "minecraft:" 前缀
        String key = itemId;
        if (itemId.startsWith("minecraft:")) {
            key = itemId.substring("minecraft:".length());
        }

        // 查找翻译
        String translation = itemTranslations.get(key);


        return translation;
    }

    /**
     * 重新加载物品翻译（用于运行时更新）
     */
    public static void reloadItemTranslations() {
        itemTranslations.clear();
        translationsLoaded = false;
        loadItemTranslations();
    }

    /**
     * 获取已加载的物品翻译数量
     */
    public static int getLoadedTranslationCount() {
        return itemTranslations.size();
    }

    public static DeathResult parseDeathMessage(Component component) {
        String victim = null;
        String schemaString = "";
        Optional<Killer> killer = Optional.empty();
        Optional<String> weapon = Optional.empty();
        Optional<String> weaponName = Optional.empty();

        for (Component child : component.children()) {
            if (child instanceof TranslatableComponent translatable) {
                String key = translatable.key();
                if (!key.startsWith("death.")) continue;
                schemaString = key;
                List<TranslationArgument> args = translatable.arguments();


                // victim 总在第一个参数
                if (args.size() >= 1 && args.get(0).value() instanceof TextComponent victimComponent) {
                    victim = extractNameFromHoverOrText(victimComponent);
                }

                // killer（player 或 mob）
                if (args.size() >= 2 && args.get(1).value() instanceof TextComponent killerComponent) {
                    String killerName = extractNameFromHoverOrText(killerComponent);
                    KillerType type = determineKillerType(killerComponent);
                    killer = Optional.of(new Killer(killerName, type));
                }

                // weapon
                if (args.size() >= 3) {
                    Object weaponArg = args.get(2).value();
                    if (weaponArg instanceof TextComponent weaponComponent) {
                        String weaponId = extractWeaponIdFromComponent(weaponComponent);
                        weapon = Optional.of(weaponId);
                    } else if (weaponArg instanceof TranslatableComponent weaponTranslatable) {
                        String weaponId = extractWeaponIdFromTranslatableComponent(weaponTranslatable);
                        weapon = Optional.of(weaponId);
                        weaponName = Optional.of(extractWeaponName(weaponTranslatable));
                    }
                }

                break; // 停止在第一个有效 death.message 上
            }
        }

        // fallback victim 为 unknown
        if (victim == null) victim = "[Unknown]";

        return new DeathResult(victim, killer, weapon, weaponName, schemaString);
    }

    private static String extractNameFromHoverOrText(TextComponent component) {
        HoverEvent<?> hover = component.hoverEvent();
        if (hover.value() instanceof HoverEvent.ShowEntity showEntity) {
            Component name = showEntity.name();
            if (name instanceof TextComponent nameText) {
                return nameText.content(); // 玩家名
            } else if (name instanceof TranslatableComponent translatableName) {
                // 处理 mob 的翻译名称
                String translateKey = translatableName.key();
                if (translateKey != null && translateKey.startsWith("entity.minecraft.")) {
                    return translateKey.substring("entity.minecraft.".length()); // 返回 mob 类型名
                }
                return translateKey; // 返回翻译键
            } else {
                return name.toString();
            }
        }

        return component.content(); // fallback: 普通文本
    }

    private static String extractWeaponIdFromComponent(TextComponent component) {
        HoverEvent<?> hover = component.hoverEvent();
        if (hover.value() instanceof HoverEvent.ShowItem showItem) {
            // 从 show_item hover event 中获取物品ID
            String itemId = showItem.item().asString();
            if (itemId != null && !itemId.isEmpty()) {
                return itemId;
            }
        }

        // fallback: 返回组件的文本内容
        return component.content();
    }

    public static String extractWeaponIdFromTranslatableComponent(TranslatableComponent component) {
        HoverEvent<?> hover = component.hoverEvent();
        if (hover.value() instanceof HoverEvent.ShowItem showItem) {
            // 从 show_item hover event 中获取物品ID
            String itemId = showItem.item().asString();
            if (itemId != null && !itemId.isEmpty()) {
                return itemId;
            }
        }

        // fallback: 返回翻译键
        return component.key();
    }

    public static String extractWeaponName(TranslatableComponent component) {
        String key = component.key();
        HoverEvent<?> hover = component.hoverEvent();
        if (hover.value() instanceof HoverEvent.ShowItem showItem) {
            // 从 show_item hover event 中获取物品ID
            String itemId = showItem.item().value();
            if (itemId != null && !itemId.isEmpty()) {
                String translation = translateItem(itemId);
                return translation;
            }
        }

        // fallback: 返回翻译键
        return null;
    }

    private static KillerType determineKillerType(TextComponent component) {
        HoverEvent hover = component.hoverEvent();
        if (hover.value() instanceof HoverEvent.ShowEntity showEntity) {
            String id = showEntity.type().asString();
            if ("minecraft:player".equals(id)) {
                return KillerType.PLAYER;
            } else if (id != null && id.startsWith("minecraft:")) {
                return KillerType.MOB;
            }

            // 通过组件名称类型判断
            Component name = showEntity.name();
            if (name instanceof TranslatableComponent) {
                String translateKey = ((TranslatableComponent) name).key();
                if (translateKey != null && translateKey.startsWith("entity.minecraft.")) {
                    return KillerType.MOB;
                }
            }
        }

        // fallback: 通过 heuristic 判断
        String text = component.content();
        if (text != null && !text.isEmpty()) {
            // 如果文本内容看起来像玩家名（通常是简单字符串，不包含空格或特殊字符）
            if (text.matches("[a-zA-Z0-9_]{3,16}")) {
                return KillerType.PLAYER;
            }
        }

        return KillerType.MOB;
    }


    public static void main(String[] args) {
        // 测试玩家击杀的死亡消息 JSON
        String playerKillJson = """
                {
                  "extra": [
                    {
                      "color": "#DD9090",
                      "translate": "death.attack.player",
                      "with": [
                        {
                          "insertion": "YXliyi",
                          "click_event": {
                            "action": "suggest_command",
                            "command": "/tell YXliyi "
                          },
                          "hover_event": {
                            "action": "show_entity",
                            "id": "minecraft:player",
                            "uuid": [
                              -104881374,
                              1256274675,
                              -2060076186,
                              -2095296780
                            ],
                            "name": "YXliyi"
                          },
                          "text": "YXliyi"
                        },
                        {
                          "insertion": "YXlixi",
                          "click_event": {
                            "action": "suggest_command",
                            "command": "/tell YXlixi "
                          },
                          "hover_event": {
                            "action": "show_entity",
                            "id": "minecraft:player",
                            "uuid": [
                              827302963,
                              -136695470,
                              -1278948957,
                              656435051
                            ],
                            "name": "YXlixi"
                          },
                          "text": "YXlixi"
                        }
                      ]
                    }
                  ],
                  "text": ""
                }
                """;

        // 测试 mob 击杀的死亡消息 JSON
        String mobKillJson = """
                {
                  "extra": [
                    {
                      "color": "#DD9090",
                      "translate": "death.attack.mob",
                      "with": [
                        {
                          "insertion": "PlayerName",
                          "click_event": {
                            "action": "suggest_command",
                            "command": "/tell PlayerName "
                          },
                          "hover_event": {
                            "action": "show_entity",
                            "id": "minecraft:player",
                            "uuid": [1, 2, 3, 4],
                            "name": "PlayerName"
                          },
                          "text": "PlayerName"
                        },
                        {
                          "insertion": "minecraft:zombie",
                          "click_event": {
                            "action": "suggest_command",
                            "command": "/tell minecraft:zombie "
                          },
                          "hover_event": {
                            "action": "show_entity",
                            "id": "minecraft:zombie",
                            "uuid": [5, 6, 7, 8],
                            "name": {
                              "translate": "entity.minecraft.zombie"
                            }
                          },
                          "text": "Zombie"
                        }
                      ]
                    }
                  ],
                  "text": ""
                }
                """;

        String withWeapon = "{\n" +
                "  \"extra\": [\n" +
                "    {\n" +
                "      \"color\": \"#DD9090\",\n" +
                "      \"translate\": \"death.attack.player.item\",\n" +
                "      \"with\": [\n" +
                "        {\n" +
                "          \"insertion\": \"MaoShenShen02\",\n" +
                "          \"click_event\": {\n" +
                "            \"action\": \"suggest_command\",\n" +
                "            \"command\": \"/tell MaoShenShen02 \"\n" +
                "          },\n" +
                "          \"hover_event\": {\n" +
                "            \"action\": \"show_entity\",\n" +
                "            \"id\": \"minecraft:player\",\n" +
                "            \"uuid\": [\n" +
                "              2119505344,\n" +
                "              -844939269,\n" +
                "              -2116090470,\n" +
                "              994158554\n" +
                "            ],\n" +
                "            \"name\": \"MaoShenShen02\"\n" +
                "          },\n" +
                "          \"text\": \"MaoShenShen02\"\n" +
                "        },\n" +
                "        {\n" +
                "          \"insertion\": \"MR_CHEAT_\",\n" +
                "          \"click_event\": {\n" +
                "            \"action\": \"suggest_command\",\n" +
                "            \"command\": \"/tell MR_CHEAT_ \"\n" +
                "          },\n" +
                "          \"hover_event\": {\n" +
                "            \"action\": \"show_entity\",\n" +
                "            \"id\": \"minecraft:player\",\n" +
                "            \"uuid\": [\n" +
                "              211450061,\n" +
                "              -939051617,\n" +
                "              -1689756498,\n" +
                "              1097910348\n" +
                "            ],\n" +
                "            \"name\": \"MR_CHEAT_\"\n" +
                "          },\n" +
                "          \"text\": \"MR_CHEAT_\"\n" +
                "        },\n" +
                "        {\n" +
                "          \"color\": \"light_purple\",\n" +
                "          \"hover_event\": {\n" +
                "            \"action\": \"show_item\",\n" +
                "            \"id\": \"minecraft:mace\",\n" +
                "            \"count\": 1,\n" +
                "            \"components\": {\n" +
                "              \"minecraft:damage\": 82,\n" +
                "              \"minecraft:enchantments\": {\n" +
                "                \"levels\": {\n" +
                "                  \"minecraft:mending\": 1,\n" +
                "                  \"minecraft:fire_aspect\": 2,\n" +
                "                  \"minecraft:breach\": 3,\n" +
                "                  \"minecraft:unbreaking\": 3\n" +
                "                }\n" +
                "              },\n" +
                "              \"minecraft:repair_cost\": 3,\n" +
                "              \"minecraft:custom_name\": \"\\\"Ezzzzzzzzzzzzz\\\"\"\n" +
                "            }\n" +
                "          },\n" +
                "          \"translate\": \"chat.square_brackets\",\n" +
                "          \"with\": [\n" +
                "            {\n" +
                "              \"italic\": true,\n" +
                "              \"extra\": [\n" +
                "                \"Ezzzzzzzzzzzzz\"\n" +
                "              ],\n" +
                "              \"text\": \"\"\n" +
                "            }\n" +
                "          ]\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  ],\n" +
                "  \"text\": \"\"\n" +
                "}";


        System.out.println("=== 测试玩家击杀 ===");
        testParseMessage(playerKillJson);

        System.out.println("\n=== 测试 Mob 击杀 ===");
        testParseMessage(mobKillJson);

        System.out.println("\n=== 测试带武器的击杀 ===");
        testParseMessage(withWeapon);
        Component component = GsonComponentSerializer.gson().deserialize(withWeapon);

        String chineseMessage = DeathLogger.translateComponent(component.children().get(0));
        System.out.println("\n " + chineseMessage);

        // 测试物品翻译功能
        System.out.println("\n=== 测试物品翻译功能 ===");
        testItemTranslation();
    }

    private static void testItemTranslation() {
        System.out.println("已加载的物品翻译数量: " + getLoadedTranslationCount());

        // 测试一些常见的物品翻译
        String[] testItems = {
                "minecraft:diamond_sword",
                "minecraft:bow",
                "minecraft:trident",
                "minecraft:crossbow",
                "minecraft:shield",
                "diamond_sword",
                "bow",
                "unknown_item"
        };

        for (String item : testItems) {
            String translation = translateItem(item);
            System.out.println(item + " -> " + translation);
        }
    }

    private static void testParseMessage(String json) {
        // 反序列化 JSON 为 Adventure 的 Component
        Component component = GsonComponentSerializer.gson().deserialize(json);

        // 解析为结构化结果
        DeathResult result = parseDeathMessage(component);

        // 打印结果
        System.out.println("Victim: " + result.victim());
        result.killer().ifPresentOrElse(
                k -> System.out.println("Killer: " + k.name() + " (" + k.type() + ")"),
                () -> System.out.println("Killer: N/A")
        );
        result.weapon().ifPresentOrElse(
                w -> System.out.println("Weapon: " + w),
                () -> System.out.println("Weapon: N/A")
        );
        System.out.println("Schema: " + result.schemaKey());
    }
}
