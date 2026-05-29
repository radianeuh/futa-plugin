# 源箱子排除系统

## 问题描述

在智能物品分类系统中，存在两种类型的箱子：
1. **源箱子（取货箱子）** - 配置在 `config.chestLocations` 中，用于提取待分类物品
2. **存储箱子（收货箱子）** - 用于存储分类后的物品

如果不正确区分，可能会导致：
- ❌ 源箱子被当作存储箱子使用
- ❌ 物品被存储回取货箱子，形成循环
- ❌ 分类逻辑混乱

## 解决方案

### 🚫 **源箱子排除机制**

#### 排除逻辑
```java
// 在缓存更新时排除源箱子
for (ContainerSearcher.ContainerInfo container : containers) {
    if (container.type == ContainerSearcher.ContainerType.CHEST) {
        // 排除配置的源箱子（取货箱子）
        if (isSourceChest(container.x, container.y, container.z)) {
            continue; // 跳过，不加入存储箱子缓存
        }
        
        // 其他逻辑...
    }
}
```

#### isSourceChest方法
```java
private boolean isSourceChest(int x, int y, int z) {
    for (ChestLocation sourceChest : config.chestLocations) {
        // 检查直接坐标匹配
        if (sourceChest.x == x && sourceChest.y == y && sourceChest.z == z) {
            return true;
        }
        
        // 如果源箱子是大箱子，也要检查其配对位置
        if (isBigChest(sourceChest.x, sourceChest.y, sourceChest.z)) {
            ChestLocation pairedChest = findBigChestPaired(sourceChest.x, sourceChest.y, sourceChest.z);
            if (pairedChest != null && pairedChest.x == x && pairedChest.y == y && pairedChest.z == z) {
                return true;
            }
        }
    }
    return false;
}
```

### 📦 **配置类结构**

#### ChestLocation
```java
public static class ChestLocation {
    public int x, y, z;
    
    public ChestLocation() {}
    
    public ChestLocation(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ChestLocation that = (ChestLocation) obj;
        return x == that.x && y == that.y && z == that.z;
    }
    
    @Override
    public String toString() {
        return "(" + x + ", " + y + ", " + z + ")";
    }
}
```

#### 配置字段
```java
// 要处理的箱子坐标列表（源箱子，用于取货）
public List<ChestLocation> chestLocations = new ArrayList<>();
```

### 🔍 **大箱子处理**

#### 大箱子源箱子的特殊处理
当源箱子是大箱子时，需要排除其两个部分：

```java
// 检查配置的源箱子位置
if (sourceChest.x == x && sourceChest.y == y && sourceChest.z == z) {
    return true; // 直接匹配
}

// 如果源箱子是大箱子，检查配对位置
if (isBigChest(sourceChest.x, sourceChest.y, sourceChest.z)) {
    ChestLocation pairedChest = findBigChestPaired(sourceChest.x, sourceChest.y, sourceChest.z);
    if (pairedChest != null && pairedChest.x == x && pairedChest.y == y && pairedChest.z == z) {
        return true; // 配对位置匹配
    }
}
```

#### 示例场景
```
配置的源箱子: (100, 64, 200)
如果是大箱子，配对位置: (101, 64, 200)

排除结果:
- (100, 64, 200) ❌ 排除（直接配置）
- (101, 64, 200) ❌ 排除（配对位置）
- (102, 64, 200) ✅ 可用作存储箱子
```

## 实际应用

### 🔄 **缓存更新流程**

#### 更新逻辑
```java
public void updateCache() {
    // 获取附近的所有箱子
    List<ContainerSearcher.ContainerInfo> containers = ContainerSearcher.searchNearbyContainers(
            playerX, playerY, playerZ, config.chestSearchRadius);
    
    Set<ChestLocation> foundLocations = new HashSet<>();
    Set<ChestLocation> processedLocations = new HashSet<>();
    
    for (ContainerSearcher.ContainerInfo container : containers) {
        if (container.type == ContainerSearcher.ContainerType.CHEST) {
            ChestLocation location = new ChestLocation(container.x, container.y, container.z);
            
            // 1. 检查是否已处理（避免大箱子重复）
            if (processedLocations.contains(location)) {
                continue;
            }
            
            // 2. 排除源箱子（关键步骤）
            if (isSourceChest(container.x, container.y, container.z)) {
                continue;
            }
            
            // 3. 处理大箱子逻辑
            if (isBigChest(container.x, container.y, container.z)) {
                // 大箱子处理...
            } else {
                // 单箱子处理...
            }
        }
    }
}
```

### 📊 **统计信息**

#### 缓存统计增强
```java
public String getCacheStats() {
    int totalChests = chestCache.size();
    int emptyChests = (int) chestCache.values().stream().filter(chest -> chest.isEmpty).count();
    int dedicatedChests = (int) chestCache.values().stream().filter(chest -> chest.dedicatedItemType != null).count();
    int doubleChests = (int) chestCache.keySet().stream().filter(location -> location.isDoubleChest).count();
    int sourceChests = config.chestLocations.size();
    
    return String.format("箱子缓存: 总计%d个, 空箱子%d个, 专用箱子%d个, 大箱子%d个 (已排除%d个源箱子)", 
            totalChests, emptyChests, dedicatedChests, doubleChests, sourceChests);
}
```

#### 统计信息示例
```
箱子缓存: 总计25个, 空箱子12个, 专用箱子13个, 大箱子5个 (已排除3个源箱子)
```

### 🎯 **命令系统**

#### 添加源箱子
```bash
# 添加源箱子命令
itemsorter addchest 100 64 200

# 内部处理
ChestLocation chest = new ChestLocation(x, y, z);
config.chestLocations.add(chest);
```

#### 查看源箱子
```bash
# 查看所有源箱子
itemsorter listchests

# 显示结果
源箱子列表:
1. (100, 64, 200)
2. (105, 64, 200)  
3. (110, 64, 200)
```

## 工作流程

### 📋 **完整流程**

#### 1. 配置阶段
```
用户配置源箱子 → 添加到 config.chestLocations → 系统记录取货位置
```

#### 2. 发现阶段
```
搜索附近箱子 → 检查是否是源箱子 → 排除源箱子 → 缓存存储箱子
```

#### 3. 分类阶段
```
从源箱子取货 → 分类物品 → 查找存储箱子 → 存储到非源箱子
```

#### 4. 验证阶段
```
确保物品不会存储回源箱子 → 避免循环处理 → 保持系统稳定
```

## 优势特点

### ✅ **功能分离**
- **源箱子** - 专门用于取货，不参与存储
- **存储箱子** - 专门用于存储，不参与取货
- **清晰职责** - 避免角色混淆

### 🛡️ **循环防护**
- **排除机制** - 源箱子不会被当作存储目标
- **逻辑隔离** - 取货和存货完全分离
- **稳定运行** - 避免无限循环处理

### 🎯 **精确控制**
- **大箱子支持** - 正确处理大箱子的两个部分
- **配置灵活** - 用户可以自由配置源箱子位置
- **状态透明** - 统计信息显示排除的源箱子数量

## 注意事项

### ⚠️ **配置要点**
1. **源箱子位置** - 确保源箱子坐标配置正确
2. **大箱子处理** - 配置大箱子时只需配置一个位置
3. **存储分离** - 确保存储区域与源箱子区域分离

### 🔧 **维护建议**
1. **定期检查** - 确认源箱子配置的有效性
2. **统计监控** - 通过统计信息监控排除状态
3. **测试验证** - 定期测试确保源箱子不会被用于存储

这个源箱子排除系统确保了智能物品分类模块的取货和存货功能完全分离，避免了循环处理和逻辑混乱的问题。
