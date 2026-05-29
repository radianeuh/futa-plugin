package com.github.futa.util;

import com.github.futa.dto.RecipeBean;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecipeMaterialCounter {

    private static final Gson gson = new Gson();

    public static void main(String[] args) throws IOException {
        // 示例：加载 resources/recipes/golden_axe.json
        analyzeRecipe("golden_axe");

        // 示例：加载 resources/recipes/gold_nugget.json
        analyzeRecipe("gold_nugget");

        RecipeBean axe = analyzeRecipe("golden_axe");
        System.out.println("Golden Axe Recipe → " + axe.getIngredients() + " => " + axe.getResultId());

        RecipeBean nugget = analyzeRecipe("gold_nugget");
        System.out.println("Gold Nugget Recipe → " + nugget.getIngredients() + " => " + nugget.getResultId());

    }


    /**
     * 统计合成材料并输出
     */
    public static void countMaterials(String json) {
        JsonObject obj = gson.fromJson(json, JsonObject.class);

        String type = obj.get("type").getAsString();
        Map<String, Integer> materialCount = new HashMap<>();

        switch (type) {
            case "minecraft:crafting_shaped" -> {
                JsonObject key = obj.getAsJsonObject("key");
                JsonArray pattern = obj.getAsJsonArray("pattern");
                for (JsonElement line : pattern) {
                    String row = line.getAsString();
                    for (char c : row.toCharArray()) {
                        if (c == ' ') continue;
                        String symbol = String.valueOf(c);
                        if (key.has(symbol)) {
                            String item = key.getAsJsonObject(symbol).get("item").getAsString().replace("minecraft:", "");
                            materialCount.put(item, materialCount.getOrDefault(item, 0) + 1);
                        }
                    }
                }
            }
            case "minecraft:crafting_shapeless" -> {
                JsonArray ingredients = obj.getAsJsonArray("ingredients");
                for (JsonElement ing : ingredients) {
                    JsonObject ingredient = ing.getAsJsonObject();
                    if (ingredient.has("item")) {
                        String item = ingredient.get("item").getAsString().replace("minecraft:", "");
                        materialCount.put(item, materialCount.getOrDefault(item, 0) + 1);
                    }
                }
            }
            default -> System.out.println("⚠️ 未处理的配方类型: " + type);
        }

        // 输出材料统计
        materialCount.forEach((item, count) ->
                System.out.println(item + " × " + count)
        );

        // 输出合成结果
        JsonObject result = obj.getAsJsonObject("result");
        String resultId = result.get("id").getAsString();
        int resultCount = result.get("count").getAsInt();
        System.out.println("=> Result: " + resultId + " × " + resultCount);
    }


    /**
     * 从 resources/recipes/ 文件夹加载指定配方，并返回 RecipeBean
     */
    public static RecipeBean analyzeRecipe(String recipeName) {
        String resourcePath = "/recipes/" + recipeName + ".json";

        try (InputStream in = RecipeMaterialCounter.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new RuntimeException("配方文件不存在: " + resourcePath);
            }

            String json = new String(in.readAllBytes());
            return parseRecipeBean(json);
        } catch (IOException e) {
            throw new RuntimeException("error: " + e);

        }
    }

    public static List<String> getTagItems(String tag) {
        if (tag.startsWith("#")) {
            tag = tag.replace("#", "");
        }

        String resourcePath = "/itemtag/" + tag + ".json";

        try (InputStream in = RecipeMaterialCounter.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new RuntimeException("配方文件不存在: " + resourcePath);
            }

            String json = new String(in.readAllBytes());

            JsonObject obj = gson.fromJson(json, JsonObject.class);
            JsonArray values = obj.getAsJsonArray("values");

            List<String> items = new ArrayList<>();
            for (JsonElement jsonElement : values) {
                String v = jsonElement.getAsString();
                items.add(v.replace("minecraft:", ""));
            }
            return items;
        } catch (IOException e) {
            throw new RuntimeException("error: " + e);

        }
    }

    /**
     * 解析 JSON → RecipeBean
     */
    public static RecipeBean parseRecipeBean(String json) {
        JsonObject obj = gson.fromJson(json, JsonObject.class);

        String type = obj.get("type").getAsString();
        Map<String, Integer> materialCount = new HashMap<>();

        switch (type) {
            case "minecraft:crafting_shaped" -> {
                JsonObject key = obj.getAsJsonObject("key");
                for (JsonElement line : obj.getAsJsonArray("pattern")) {
                    for (char c : line.getAsString().toCharArray()) {
                        if (c == ' ') continue;
                        String symbol = String.valueOf(c);
                        if (key.has(symbol)) {
                            JsonObject ingredient = key.getAsJsonObject(symbol);
                            handleIngredient(ingredient, materialCount);
                        }
                    }
                }
            }
            case "minecraft:crafting_shapeless" -> {
                for (JsonElement ing : obj.getAsJsonArray("ingredients")) {
                    JsonObject ingredient = ing.getAsJsonObject();
                    handleIngredient(ingredient, materialCount);
                }
            }
            default -> System.out.println("⚠️ 未处理的配方类型: " + type);
        }

        JsonObject result = obj.getAsJsonObject("result");
        String resultId = result.get("id").getAsString().replace("minecraft:", "");
        int resultCount = result.get("count").getAsInt();

        return new RecipeBean(type, materialCount, resultId, resultCount);
    }

    /**
     * 处理单个 ingredient，可以是 item 或 tag。
     */
    private static void handleIngredient(JsonObject ingredient, Map<String, Integer> materialCount) {
        if (ingredient.has("item")) {
            String item = ingredient.get("item").getAsString().replace("minecraft:", "");
            materialCount.put(item, materialCount.getOrDefault(item, 0) + 1);
        } else if (ingredient.has("tag")) {
            String tag = ingredient.get("tag").getAsString().replace("minecraft:", "");
            // ✅ 你可以选择直接把 tag 记录下来，
            // 或者展开为 tag 下所有物品，这里给出两种写法：

            // ✅ 写法 1：直接记录 tag 名
            materialCount.put("#" + tag, materialCount.getOrDefault("#" + tag, 0) + 1);

            // ✅ 写法 2（可选）：如果你想展开 tag（需要在游戏环境中运行）
        /*
        TagKey<Item> tagKey = TagKey.of(RegistryKeys.ITEM, new Identifier("minecraft", tag));
        for (Holder<Item> holder : Registries.ITEM.getOrCreateTag(tagKey)) {
            Item item = holder.value();
            String itemId = Registries.ITEM.getId(item).getPath();
            materialCount.put(itemId, materialCount.getOrDefault(itemId, 0) + 1);
        }
        */
        }
    }
}
