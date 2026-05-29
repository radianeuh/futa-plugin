package com.github.futa.dto;

import com.google.gson.Gson;

import java.util.Objects;

/**
 * 箱子位置类
 */
public class ChestLocation {
    public final int x, y, z;
    public final boolean isDoubleChest;

    public ChestLocation(int x, int y, int z) {
        this(x, y, z, false, null);
    }

    public ChestLocation(int x, int y, int z, boolean isDoubleChest, ChestLocation pairedChest) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.isDoubleChest = isDoubleChest;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ChestLocation that = (ChestLocation) obj;
        return x == that.x && y == that.y && z == that.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }

    @Override
    public String toString() {

        return "(" + x + ", " + y + ", " + z + ")";
    }

    public String toJson() {

        return new Gson().toJson(this);
    }

    public static ChestLocation fromJson(String json) {

        return new Gson().fromJson(json, ChestLocation.class);
    }

    public double distanceTo(int px, int py, int pz) {
        return Math.sqrt(Math.pow(x - px, 2) + Math.pow(y - py, 2) + Math.pow(z - pz, 2));
    }

    /**
     * 获取规范化的箱子位置（对于大箱子，总是返回较小坐标的那一半）
     */
    public ChestLocation getNormalizedLocation(ChestLocation pairedChest) {
        if (!isDoubleChest || pairedChest == null) {
            return this;
        }

        // 比较坐标，返回较小的那个作为主箱子
        if (x < pairedChest.x || (x == pairedChest.x && z < pairedChest.z)) {
            return this;
        } else {
            return pairedChest;
        }
    }
}
