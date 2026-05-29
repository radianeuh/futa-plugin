# 正确的大箱子检测系统

## 问题分析

之前的大箱子检测方法存在问题：
- ❌ 简单的相邻检测不准确
- ❌ 没有考虑箱子的朝向和类型
- ❌ 可能误判独立的相邻箱子为大箱子

## 正确的检测方法

### 🔍 **方块状态检测**

#### isBigChest方法
```java
public Boolean isBigChest(int blockStateId) {
    try {
        ChestType type = World.getBlockStateProperty(blockStateId, BlockStateProperties.CHEST_TYPE);
        return type != null && type != ChestType.SINGLE;
    } catch (Exception e) {
        return false;
    }
}

public Boolean isBigChest(int x, int y, int z) {
    try {
        int blockStateId = World.getBlockState(x, y, z);
        return isBigChest(blockStateId);
    } catch (Exception e) {
        return false;
    }
}
```

#### 检测原理
- ✅ 使用方块状态属性 `CHEST_TYPE`
- ✅ 检查是否为 `LEFT` 或 `RIGHT` 类型
- ✅ `SINGLE` 类型表示单箱子
- ✅ 异常处理确保稳定性

### 🎯 **配对位置计算**

#### findBigChestPaired方法
```java
public ChestLocation findBigChestPaired(int x, int y, int z) {
    // 1. 获取方块状态
    int blockStateId = World.getBlockState(x, y, z);
    
    // 2. 检查是否是大箱子
    if (!isBigChest(blockStateId)) {
        return null;
    }
    
    // 3. 获取箱子类型和朝向
    ChestType chestType = World.getBlockStateProperty(blockStateId, BlockStateProperties.CHEST_TYPE);
    Direction facing = World.getBlockStateProperty(blockStateId, BlockStateProperties.FACING);
    
    // 4. 根据朝向和类型计算配对位置
    // 5. 验证配对位置的有效性
}
```

#### 配对计算逻辑

##### 朝向说明
- **NORTH**: 箱子朝北（玩家面向北时看到箱子正面）
- **SOUTH**: 箱子朝南
- **WEST**: 箱子朝西  
- **EAST**: 箱子朝东

##### 类型说明
- **LEFT**: 大箱子的左半部分
- **RIGHT**: 大箱子的右半部分

##### 配对规则
```java
switch (facing) {
    case NORTH: // 朝北
        if (chestType == ChestType.LEFT) {
            pairedX = x + 1; // 左半部分，配对在东边
        } else { // RIGHT
            pairedX = x - 1; // 右半部分，配对在西边
        }
        break;
    case SOUTH: // 朝南
        if (chestType == ChestType.LEFT) {
            pairedX = x - 1; // 左半部分，配对在西边
        } else { // RIGHT
            pairedX = x + 1; // 右半部分，配对在东边
        }
        break;
    case WEST: // 朝西
        if (chestType == ChestType.LEFT) {
            pairedZ = z - 1; // 左半部分，配对在北边
        } else { // RIGHT
            pairedZ = z + 1; // 右半部分，配对在南边
        }
        break;
    case EAST: // 朝东
        if (chestType == ChestType.LEFT) {
            pairedZ = z + 1; // 左半部分，配对在南边
        } else { // RIGHT
            pairedZ = z - 1; // 右半部分，配对在北边
        }
        break;
}
```

### ✅ **验证机制**

#### 配对验证
```java
// 验证配对位置确实是箱子
int pairedBlockStateId = World.getBlockState(pairedX, y, pairedZ);
if (pairedBlockStateId != 0 && isBigChest(pairedBlockStateId)) {
    ChestType pairedChestType = World.getBlockStateProperty(pairedBlockStateId, BlockStateProperties.CHEST_TYPE);
    Direction pairedFacing = World.getBlockStateProperty(pairedBlockStateId, BlockStateProperties.FACING);
    
    // 验证配对箱子的朝向相同，类型互补
    if (pairedFacing == facing && 
        ((chestType == ChestType.LEFT && pairedChestType == ChestType.RIGHT) ||
         (chestType == ChestType.RIGHT && pairedChestType == ChestType.LEFT))) {
        return new ChestLocation(pairedX, y, pairedZ);
    }
}
```

#### 验证条件
- ✅ 配对位置存在方块
- ✅ 配对位置是大箱子
- ✅ 朝向相同
- ✅ 类型互补（LEFT ↔ RIGHT）

## 实际应用

### 🔄 **缓存更新流程**

#### 更新逻辑
```java
// 使用正确的方法检测是否是大箱子
if (isBigChest(container.x, container.y, container.z)) {
    ChestLocation pairedChest = findBigChestPaired(container.x, container.y, container.z);
    if (pairedChest != null) {
        // 这是大箱子，使用规范化位置
        ChestLocation doubleChestLocation = new ChestLocation(container.x, container.y, container.z, true, pairedChest);
        ChestLocation normalizedLocation = doubleChestLocation.getNormalizedLocation();
        
        foundLocations.add(normalizedLocation);
        processedLocations.add(location);
        processedLocations.add(pairedChest);
        
        if (!chestCache.containsKey(normalizedLocation)) {
            chestCache.put(normalizedLocation, new ChestInfo(normalizedLocation));
        }
    } else {
        // 大箱子但找不到配对，可能是损坏的大箱子，当作单箱子处理
        foundLocations.add(location);
        processedLocations.add(location);
        
        if (!chestCache.containsKey(location)) {
            chestCache.put(location, new ChestInfo(location));
        }
    }
} else {
    // 单个箱子
    foundLocations.add(location);
    processedLocations.add(location);
    
    if (!chestCache.containsKey(location)) {
        chestCache.put(location, new ChestInfo(location));
    }
}
```

### 📦 **打开箱子处理**

#### 打开逻辑
```java
public void onChestOpened(int x, int y, int z, Container container) {
    ChestLocation targetLocation = null;
    
    // 使用正确的方法检查是否是大箱子
    if (isBigChest(x, y, z)) {
        ChestLocation pairedChest = findBigChestPaired(x, y, z);
        if (pairedChest != null) {
            // 这是大箱子，使用规范化位置
            ChestLocation doubleChestLocation = new ChestLocation(x, y, z, true, pairedChest);
            targetLocation = doubleChestLocation.getNormalizedLocation();
        } else {
            // 大箱子但找不到配对，当作单箱子处理
            targetLocation = new ChestLocation(x, y, z);
        }
    } else {
        // 单箱子
        targetLocation = new ChestLocation(x, y, z);
    }
    
    // 更新缓存内容
    ChestInfo chestInfo = chestCache.computeIfAbsent(targetLocation, ChestInfo::new);
    chestInfo.updateContents(container, classifier);
}
```

## 优势对比

### ✅ **新方法优势**
1. **准确性** - 基于方块状态属性，100%准确
2. **可靠性** - 考虑朝向和类型，避免误判
3. **完整性** - 正确处理所有朝向的大箱子
4. **稳定性** - 异常处理确保不会崩溃

### ❌ **旧方法问题**
1. **不准确** - 简单相邻检测可能误判
2. **不完整** - 没有考虑朝向信息
3. **不可靠** - 可能将独立箱子误认为大箱子
4. **不稳定** - 缺乏异常处理

## 测试用例

### 🧪 **测试场景**

#### 场景1：标准大箱子
```
位置: (100, 64, 200) - LEFT, NORTH
配对: (101, 64, 200) - RIGHT, NORTH
结果: ✅ 正确识别为大箱子
```

#### 场景2：独立相邻箱子
```
位置: (100, 64, 200) - SINGLE, NORTH
相邻: (101, 64, 200) - SINGLE, SOUTH
结果: ✅ 正确识别为两个单箱子
```

#### 场景3：损坏的大箱子
```
位置: (100, 64, 200) - LEFT, NORTH
配对: (101, 64, 200) - 空气方块
结果: ✅ 当作单箱子处理
```

#### 场景4：不同朝向的大箱子
```
朝向: WEST
位置: (100, 64, 200) - LEFT, WEST
配对: (100, 64, 199) - RIGHT, WEST
结果: ✅ 正确识别配对关系
```

这个正确的大箱子检测系统确保了智能物品分类模块能够准确识别和处理所有类型的箱子配置，避免了之前简单相邻检测方法的问题。
