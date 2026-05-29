package com.github.futa.module;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.github.futa.dto.DeathLogEntity;
import com.github.futa.dto.DeathResult;
import com.github.futa.util.DieMessageParser;
import com.github.rfresh2.EventConsumer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.zenith.event.chat.SystemChatEvent;
import com.zenith.event.client.ClientDeathMessageEvent;
import com.zenith.feature.deathmessages.DeathMessageParseResult;
import com.zenith.feature.deathmessages.DeathMessagesParser;
import com.zenith.module.api.Module;
import com.zenith.util.ComponentSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import net.kyori.adventure.text.event.HoverEvent;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static com.github.futa.FutaPlugin.PLUGIN_CONFIG;
import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.*;
import static java.util.Objects.nonNull;

/**
 * DeathLogger 模块 - 将所有玩家死亡信息记录到控制台和 JSON 文件中
 * <p>
 * 功能：
 * - 以 JSON 格式记录详细的死亡信息
 * - 可配置的控制台输出
 * - 以数组形式保存到本地 JSON 文件
 * - 包含时间戳、坐标（可选）、武器信息和击杀者详情
 * - 提供美观的 JSON 输出选项
 */
public class DeathLogger extends Module {

    public static final String DEATHS_DIRECTORY = "deaths";
    public static Gson gson = new Gson();
    public final Gson prettyGson;
    private static Map<String, String> translationMap = loadTranslationMap();

    public DeathLogger() {
        super();
        this.prettyGson = new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();

        // Create deaths directory if it doesn't exist
        try {
            Path deathsPath = Paths.get(DEATHS_DIRECTORY);
            if (!Files.exists(deathsPath)) {
                Files.createDirectories(deathsPath);
                info("Created deaths directory: " + deathsPath.toAbsolutePath());
            }
        } catch (IOException e) {
            error("Failed to create deaths directory", e);
        }
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
                of(SystemChatEvent.class, this::handleSystemChat)
        );
    }


    @Override
    public boolean enabledSetting() {
        return PLUGIN_CONFIG.die.enabled;
    }

    private void handleSystemChat(SystemChatEvent event) {
        String messageString = event.message();

        Component component = event.component();
        if (!isDeathMessage(component)) {
            return;
        }

        DeathResult result = DieMessageParser.parseDeathMessage(component);

        info("RAW messageString:" + messageString);
        info("RAW getPlayerNames:" + getPlayerNames(component));
        String json = ComponentSerializer.serializeJson(component);
        info("RAW json:" + json);

//        saveDeathToFile(new DeathLogEntry(result, messageString, json));
        handleDeathMessage(result, component, messageString);
    }

    private void handleDeathMessage(DeathResult deathResult, Component component, String messageString) {

        try {
            String componentJson = ComponentSerializer.serializeJson(component);

            info("RAW:" + componentJson);

            String message = messageString;

            // 翻译死亡消息为中文
            String chineseMessage = translateComponent(component.children().get(0));
            // Create death log entry
            DeathLogEntity logEntry = new DeathLogEntity(deathResult, message, componentJson, chineseMessage);
            logEntry.players = getPlayerNames(component);
            logEntry.schemaKey = deathResult.schemaKey();

            // Print to console if enabled
            if (PLUGIN_CONFIG.die.printToConsole) {
                printDeathToConsole(logEntry);
            }

            // Save to file if enabled
            if (PLUGIN_CONFIG.die.saveToFile) {
                saveDeathToFile(logEntry);
            }

        } catch (Exception e) {
            error("Error processing death message", e);
        }
    }

    public static boolean isDeathMessage(final Component component) {
        return component.children().stream().anyMatch(child -> nonNull(child.color())
                && Objects.equals(child.color().value(), 14520464));
    }

    private Optional<DeathMessageParseResult> parseDeathMessage3c(final Component component, final String messageString) {
        if (component.children().stream().anyMatch(child -> nonNull(child.color())
                && Objects.equals(child.color().value(), 14520464))) { // death message color on 2b
            var deathMessage = DeathMessagesParser.INSTANCE.parse(component, messageString);
            if (deathMessage.isPresent()) {
                if (deathMessage.get().victim().equals(CACHE.getProfileCache().getProfile().getName())) {
                    EVENT_BUS.postAsync(new ClientDeathMessageEvent(messageString));
                }
                return deathMessage;
            } else {
                CLIENT_LOG.warn("Failed to parse death message: {}", messageString);
            }
        }
        return Optional.empty();
    }

    //{"extra":[{"color":"#DD9090","translate":"death.attack.mob","with":[{"insertion":"ZnWen","click_event":{"action":"suggest_command","command":"/tell ZnWen "},"hover_event":{"action":"show_entity","id":"minecraft:player","uuid":[1721974265,1777483501,-1634362655,-816330816],"name":"ZnWen"},"text":"ZnWen"},{"insertion":"9560617f-a9e9-4196-a0c0-c017bb324c73","hover_event":{"action":"show_entity","id":"minecraft:piglin_brute","uuid":[-1788845697,-1444331114,-1597980649,-1154331533],"name":{"translate":"entity.minecraft.piglin_brute"}},"translate":"entity.minecraft.piglin_brute"}]}],"text":""}


    public void printDeathToConsole(DeathLogEntity logEntry) {
        try {
            Gson outputGson = PLUGIN_CONFIG.die.prettyPrintJson ? prettyGson : gson;
            String jsonOutput = outputGson.toJson(logEntry);

            info("=== DEATH MESSAGE LOGGED ===");
            info("Victim: {}", logEntry.victim);
            if (logEntry.killer != null) {
                info("Killer: {} ({})", logEntry.killer, logEntry.killerType);
            }
            if (logEntry.weapon != null) {
                info("Weapon: {}", logEntry.weapon);
            }

            info("Original Message: {}", logEntry.message);
            info("Chinese Message: {}", logEntry.chineseMessage);

            info("Timestamp: {}", logEntry.timestamp);
            info("JSON: {}", jsonOutput);
            info("============================");

        } catch (Exception e) {
            error("Error printing death to console", e);
        }
    }

    public void saveDeathToFile(DeathLogEntity logEntry) {
        try {
            // 保存为JSON格式
            saveToJsonFile(logEntry);

            // 保存为原始消息的TXT文件
            saveToTxtFile(logEntry);

            // 保存为中文翻译的TXT文件
            saveToChineseTxtFile(logEntry);

        } catch (Exception e) {
            error("Error saving death to file", e);
        }
    }

    /**
     * 将死亡日志保存为JSON格式文件
     *
     * @param logEntry 死亡日志条目
     */
    private void saveToJsonFile(DeathLogEntity logEntry) {
        try {
            String fileName = PLUGIN_CONFIG.die.fileName;
            Path filePath = Paths.get(DEATHS_DIRECTORY, fileName);

            // 读取现有数据或创建新数组
            JsonArray deathsArray = readExistingJsonData(filePath);

            // 添加新条目
            JsonObject entryJson = gson.toJsonTree(logEntry).getAsJsonObject();
            deathsArray.add(entryJson);

            // 写入文件
            Gson outputGson = PLUGIN_CONFIG.die.prettyPrintJson ? prettyGson : gson;
            String json = outputGson.toJson(deathsArray);
            FileUtil.writeUtf8String(json, filePath.toFile());

        } catch (Exception e) {
            warn("Failed to save death log to JSON file: " + e.getMessage());
        }
    }

    /**
     * 将死亡日志保存为原始消息的TXT文件
     *
     * @param logEntry 死亡日志条目
     */
    private void saveToTxtFile(DeathLogEntity logEntry) {
        try {
            String fileNameTxt = PLUGIN_CONFIG.die.fileNameTxt;
            Path filePathTxt = Paths.get(DEATHS_DIRECTORY, fileNameTxt);

            // 确保目录存在
            ensureDirectoryExists(filePathTxt.getParent());

            // 追加消息到TXT文件，一行一个
            FileUtil.appendUtf8String(logEntry.message + "\n", filePathTxt.toFile());

        } catch (Exception e) {
            error("Error saving death message to txt file", e);
        }
    }

    /**
     * 将死亡日志保存为中文翻译的TXT文件
     *
     * @param logEntry 死亡日志条目
     */
    private void saveToChineseTxtFile(DeathLogEntity logEntry) {
        try {
            String fileNameChineseTxt = PLUGIN_CONFIG.die.fileNameChineseTxt;
            Path filePathChineseTxt = Paths.get(DEATHS_DIRECTORY, fileNameChineseTxt);

            // 确保目录存在
            ensureDirectoryExists(filePathChineseTxt.getParent());

            // 追加中文消息到TXT文件，一行一个
            FileUtil.appendUtf8String(DateUtil.formatTime(new Date()) + " " + logEntry.chineseMessage + "\n",
                    filePathChineseTxt.toFile());

        } catch (Exception e) {
            error("Error saving Chinese death message to txt file", e);
        }
    }


    /**
     * 读取现有的JSON数据文件
     *
     * @param filePath 文件路径
     * @return JSON数组
     */
    private JsonArray readExistingJsonData(Path filePath) {
        if (!Files.exists(filePath)) {
            return new JsonArray();
        }

        try {
            String read = FileUtil.readUtf8String(filePath.toFile());
            JsonArray deathsArray = gson.fromJson(read, JsonArray.class);
            return deathsArray != null ? deathsArray : new JsonArray();
        } catch (Exception e) {
            warn("Error reading existing death log file, creating new array: {}", e.getMessage());
            return new JsonArray();
        }
    }

    /**
     * 确保指定目录存在，如果不存在则创建
     *
     * @param directoryPath 目录路径
     */
    private void ensureDirectoryExists(Path directoryPath) throws IOException {
        if (!Files.exists(directoryPath)) {
            Files.createDirectories(directoryPath);
        }
    }


    @Override
    public void onEnable() {
        if (PLUGIN_CONFIG.die.saveToFile) {
            Path jsonPath = Paths.get(DEATHS_DIRECTORY, PLUGIN_CONFIG.die.fileName).toAbsolutePath();
            Path txtPath = Paths.get(DEATHS_DIRECTORY, PLUGIN_CONFIG.die.fileNameTxt).toAbsolutePath();
            Path chineseTxtPath = Paths.get(DEATHS_DIRECTORY, PLUGIN_CONFIG.die.fileNameChineseTxt).toAbsolutePath();
            info("DeathLogger enabled - will log deaths to: {}, {} and {}", jsonPath, txtPath, chineseTxtPath);
        } else {
            info("DeathLogger enabled - will log deaths to console only");
        }
    }

    @Override
    public void onDisable() {
        info("DeathLogger disabled");
    }

    List<String> getPlayerNames(final Component component) {

        List<String> names = new ArrayList<>();
        for (var child : component.children()) {
            if (child instanceof TranslatableComponent translatableComponent) {
                if (translatableComponent.key().startsWith("death.")) {
                    for (var translationArgument : translatableComponent.arguments()) {
                        if (translationArgument.value() instanceof TextComponent argumentComponent) {
                            if (argumentComponent.hoverEvent() != null && argumentComponent.hoverEvent().value() instanceof HoverEvent.ShowEntity showEntityHoverEvent) {
                                if (showEntityHoverEvent.name() instanceof TextComponent entityNameComponent) {
                                    String playerName = entityNameComponent.content();
                                    // do something with the player name

                                    names.add(playerName);
                                }
                            }
                        }
                    }
                }
            }
        }

        return names;
    }

    List<String> getPlayerNamesStream(final Component component) {

        return component.children().stream()
                .filter(child -> child instanceof TranslatableComponent)
                .map(child -> (TranslatableComponent) child)
                .filter(translatableComponent -> translatableComponent.key().startsWith("death."))
                .flatMap(translatableComponent -> translatableComponent.arguments().stream())
                .filter(translationArgument -> translationArgument.value() instanceof TextComponent)
                .map(translationArgument -> (TextComponent) translationArgument.value())
                .filter(argumentComponent -> argumentComponent.hoverEvent() != null)
                .filter(argumentComponent -> argumentComponent.hoverEvent().value() instanceof HoverEvent.ShowEntity)
                .map(argumentComponent -> (HoverEvent.ShowEntity) argumentComponent.hoverEvent().value())
                .filter(showEntityHoverEvent -> showEntityHoverEvent.name() instanceof TextComponent)
                .map(showEntityHoverEvent -> (TextComponent) showEntityHoverEvent.name())
                .map(TextComponent::content)
                .filter(Objects::nonNull)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toList());


    }

    /**
     * 从资源文件加载翻译模板
     */
    private static Map<String, String> loadTranslationMap() {
        try {
            InputStream inputStream = DeathLogger.class.getResourceAsStream("/mcdata/death.json");
            if (inputStream == null) {
                return Map.of();
            }

            try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                TypeToken<Map<String, String>> typeToken = new TypeToken<Map<String, String>>() {
                };


                Map<String, String> translations = gson.fromJson(reader, typeToken.getType());
                if (translations == null) {
                    return Map.of();
                }

                return translations;
            }
        } catch (IOException e) {
            return Map.of();
        }
    }

    /**
     * 将死亡消息 Component 翻译为中文
     *
     * @param component 要翻译的组件
     * @return 翻译后的中文消息
     */
    public static String translateComponent(Component component) {
        if (component == null) {
            return "";
        }


        // 如果是 TranslatableComponent，进行翻译
        if (component instanceof TranslatableComponent translatable) {

            String result = translateTranslatableComponent(translatable);

            return result;
        }

        // 如果是 TextComponent，直接返回文本内容
        if (component instanceof TextComponent textComponent) {
            String content = textComponent.content();

            return content;
        }

        // 递归处理子组件
        StringBuilder result = new StringBuilder();

        for (Component child : component.children()) {
            String childResult = translateComponent(child);

            result.append(childResult);
        }

        String finalResult = result.toString();

        return finalResult;
    }

    /**
     * 翻译 TranslatableComponent
     */
    public static String translateTranslatableComponent(TranslatableComponent translatable) {
        String key = translatable.key();


        if (!key.startsWith("death.")) {

            String weaponName = DieMessageParser.extractWeaponName(translatable);
            if (StrUtil.isNotEmpty(weaponName)) {
                return "【" + weaponName + "】";
            }

            // 如果不是死亡消息键，返回原始内容或键名
            return ComponentSerializer.serializePlain(translatable);
        }

        // 获取翻译模板
        String template = translationMap.get(key);

        if (template == null) {

            // 如果没有找到翻译模板，返回原始内容
            return ComponentSerializer.serializePlain(translatable);
        }

        // 获取参数
        List<TranslationArgument> args = translatable.arguments();
        String[] argValues = new String[args.size()];


        for (int i = 0; i < args.size(); i++) {
            Object arg = args.get(i).value();

            if (arg instanceof Component) {
                // 如果参数是 Component，递归翻译
                argValues[i] = translateComponent((Component) arg);

            } else if (arg != null) {
                // 其他类型直接转换为字符串
                argValues[i] = arg.toString();

            } else {
                argValues[i] = "";

            }
        }

        String result = applyArgumentsToTemplate(template, argValues);

        return result;
    }

    /**
     * 将参数应用到翻译模板
     */
    private static String applyArgumentsToTemplate(String template, String[] args) {

        String result = template;

        // Minecraft 使用 %1$SimpleCache, %2$SimpleCache, %3$SimpleCache 等作为占位符
        for (int i = 0; i < args.length; i++) {
            String placeholder = "%" + (i + 1) + "$SimpleCache";
            String replacement = args[i] != null ? args[i] : "";

            result = result.replace(placeholder, replacement);
        }


        return result;
    }

//    List<String> getPlayerNames(final Component component) {
//        return component.children().stream()
//                .flatMap(child -> {
//                    // 检查是否有 with 参数（翻译键的参数）
//                    if (child instanceof TranslatableComponent translatableComponent) {
//                        return translatableComponent.arguments().stream()
//                                .filter(arg -> arg instanceof Component)
//                                .map(arg -> (Component) arg)
//                                .filter(argComponent -> argComponent.clickEvent() != null)
//                                .filter(argComponent -> argComponent.clickEvent().action() == ClickEvent.Action.SUGGEST_COMMAND)
//                                .filter(argComponent -> argComponent.clickEvent().payload() instanceof ClickEvent.Payload.Text)
//                                .map(argComponent -> (ClickEvent.Payload.Text) argComponent.clickEvent().payload())
//                                .filter(textPayload -> textPayload.value().startsWith("/tell"))
//                                .map(textPayload -> textPayload.value().replace("/tell ", "").trim());
//                    }
//                    return Stream.empty();
//                })
//                .toList();
//    }

}
