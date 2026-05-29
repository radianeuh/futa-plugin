# 大箱子处理系统

## 问题描述

在Minecraft中，大箱子（双箱子）由两个相邻的箱子方块组成，但在游戏中表现为一个54格的容器。如果不正确处理，可能会导致：

1. **重复缓存** - 将大箱子的两个部分分别缓存为两个独立的箱子
2. **重复打开** - 尝试打开同一个大箱子的两个部分
3. **容量错误** - 将54格的大箱子误认为27格的单箱子

## 解决方案

### 🔍 **大箱子检测**

#### 检测逻辑
```java
// 检查相邻的4个方向（东西南北）
int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

for (int[] dir : directions) {
    int checkX = x + dir[0];
    int checkZ = z + dir[1];
    
    // 查找相邻位置是否有箱子
    if (isChestAt(checkX, y, checkZ)) {
        return new ChestLocation(checkX, y, checkZ); // 找到配对箱子
    }
}
```

#### 检测条件
- ✅ 相同Y坐标
- ✅ X或Z坐标相差1
- ✅ 都是箱子方块类型
- ✅ 相邻位置（东西南北方向）

### 📦 **规范化处理**

#### 规范化原则
对于大箱子，总是使用**坐标较小**的那一半作为主箱子：

```java
public ChestLocation getNormalizedLocation() {
    if (!isDoubleChest || pairedChest == null) {
        return this;
    }
    
    // 比较坐标，返回较小的那个作为主箱子
    if (x < pairedChest.x || (x == pairedChest.x && z < pairedChest.z)) {
        return this; // 当前位置是主箱子
    } else {
        return pairedChest; // 配对位置是主箱子
    }
}
```

#### 规范化示例
```
大箱子位置: (100, 64, 200) 和 (101, 64, 200)
规范化结果: (100, 64, 200) [较小的X坐标]

大箱子位置: (100, 64, 200) 和 (100, 64, 201)  
规范化结果: (100, 64, 200) [较小的Z坐标]
```

### 🗂️ **缓存管理**

#### 缓存策略
1. **发现阶段** - 检测所有箱子，识别大箱子
2. **规范化阶段** - 将大箱子规范化为主箱子位置
3. **去重阶段** - 避免重复处理同一个大箱子的两个部分

#### 处理流程
```java
Set<ChestLocation> processedLocations = new HashSet<>();

for (ContainerInfo container : containers) {
    ChestLocation location = new ChestLocation(container.x, container.y, container.z);
    
    // 检查是否已经处理过（避免大箱子重复处理）
    if (processedLocations.contains(location)) {
        continue;
    }
    
    // 检测配对箱子
    ChestLocation pairedChest = findPairedChest(container.x, container.y, container.z, containers);
    if (pairedChest != null) {
        // 大箱子处理
        ChestLocation doubleChest = new ChestLocation(container.x, container.y, container.z, true, pairedChest);
        ChestLocation normalized = doubleChest.getNormalizedLocation();
        
        // 标记两个位置都已处理
        processedLocations.add(location);
        processedLocations.add(pairedChest);
        
        // 缓存规范化位置
        chestCache.put(normalized, new ChestInfo(normalized));
    } else {
        // 单箱子处理
        processedLocations.add(location);
        chestCache.put(location, new ChestInfo(location));
    }
}
```

### 🎯 **打开箱子处理**

#### 打开逻辑
当玩家打开箱子时，需要正确识别是打开了大箱子的哪一部分：

```java
public void onChestOpened(int x, int y, int z, Container container) {
    ChestLocation targetLocation = null;
    
    // 1. 检查直接位置
    ChestLocation directLocation = new ChestLocation(x, y, z);
    if (chestCache.containsKey(directLocation)) {
        targetLocation = directLocation;
    } else {
        // 2. 检查是否是大箱子的另一半
        for (ChestLocation cachedLocation : chestCache.keySet()) {
            if (cachedLocation.isDoubleChest && cachedLocation.pairedChest != null) {
                if (isLocationMatch(cachedLocation, x, y, z)) {
                    targetLocation = cachedLocation.getNormalizedLocation();
                    break;
                }
            }
        }
    }
    
    // 3. 更新缓存内容
    ChestInfo chestInfo = chestCache.computeIfAbsent(targetLocation, ChestInfo::new);
    chestInfo.updateContents(container, classifier);
}
```

### 📏 **容量计算**

#### 容量区分
```java
public int getAvailableSpace() {
    int maxCapacity;
    if (location.isDoubleChest) {
        maxCapacity = 54 * 64; // 大箱子54格
    } else {
        maxCapacity = 27 * 64; // 单箱子27格
    }
    
    if (isEmpty) return maxCapacity;
    
    int totalItems = itemCounts.values().stream().mapToInt(Integer::intValue).sum();
    return Math.max(0, maxCapacity - totalItems);
}
```

#### 容量对比
- **单箱子**: 27格 × 64个/格 = 1,728个物品
- **大箱子**: 54格 × 64个/格 = 3,456个物品

### 📊 **统计信息**

#### 缓存统计
```java
public String getCacheStats() {
    int totalChests = chestCache.size();
    int emptyChests = (int) chestCache.values().stream().filter(chest -> chest.isEmpty).count();
    int dedicatedChests = (int) chestCache.values().stream().filter(chest -> chest.dedicatedItemType != null).count();
    int doubleChests = (int) chestCache.keySet().stream().filter(location -> location.isDoubleChest).count();
    
    return String.format("箱子缓存: 总计%d个, 空箱子%d个, 专用箱子%d个, 大箱子%d个", 
            totalChests, emptyChests, dedicatedChests, doubleChests);
}
```

## 实际效果

### ✅ **正确处理**
```
发现箱子: (100, 64, 200) 和 (101, 64, 200)
识别为: 大箱子
规范化为: (100, 64, 200) [主箱子]
缓存条目: 1个 (而不是2个)
容量: 54格 (而不是27格)
```

### ❌ **错误处理（已避免）**
```
发现箱子: (100, 64, 200) 和 (101, 64, 200)
错误处理: 两个独立的单箱子
缓存条目: 2个 (重复)
容量: 27格 × 2 = 54格 (计算错误)
```

## 优势特点

### 🎯 **准确性**
- 正确识别大箱子和单箱子
- 避免重复缓存同一个容器
- 准确计算容器容量

### 🚀 **效率**
- 减少缓存条目数量
- 避免重复操作同一个箱子
- 优化存储空间计算

### 🛡️ **稳定性**
- 防止并发访问同一个大箱子
- 确保数据一致性
- 避免状态冲突

## 注意事项

### ⚠️ **限制条件**
1. **相邻检测** - 只检测东西南北4个方向，不检测对角线
2. **同一高度** - 大箱子必须在相同的Y坐标
3. **方块类型** - 只处理箱子类型，不处理其他容器

### 🔧 **维护要点**
1. **缓存一致性** - 确保规范化位置的一致性
2. **更新同步** - 打开箱子时正确更新缓存
3. **清理机制** - 定期清理无效的缓存条目

这个大箱子处理系统确保了智能物品分类模块能够正确处理Minecraft中的双箱子结构，避免重复操作和错误计算。
