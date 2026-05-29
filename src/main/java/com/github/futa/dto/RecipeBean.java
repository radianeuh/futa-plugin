package com.github.futa.dto;

import com.alibaba.fastjson2.JSON;
import com.zenith.mc.item.ItemData;
import com.zenith.mc.item.ItemRegistry;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecipeBean {

    private String type;
    private Map<String, Integer> ingredients;
    private String resultId;
    private int resultCount;

    public static RecipeBean fromJson(String json) {
        return JSON.parseObject(json, RecipeBean.class);
    }

    // 获取合成结果物品ID
    public int resultItemId() {
        return ItemRegistry.REGISTRY.get(resultId).id();
    }

    // 获取合成结果物品ID
    public static int getItemIdByName(String name) {
        ItemData data = ItemRegistry.REGISTRY.get(name);
        if (data == null) return -1;
        return data.id();
    }

    public String recipeId() {
        return  "minecraft:" + resultId;
    }

    public List<CraftingMaterial> materials() {
        ArrayList<CraftingMaterial> list = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : ingredients.entrySet()) {
            list.add(new CraftingMaterial(getItemIdByName(entry.getKey()), entry.getValue()));
        }
        return list;
    }


    // 合成材料记录类
    public record CraftingMaterial(int itemId, int amount) {
        // itemId: 物品ID, amount: 数量
    }
}
