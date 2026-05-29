# 源箱子缓存问题调试指南

## 问题描述

如果发现配置的源箱子仍然被加入到存储箱子缓存中，可能的原因包括：

1. **配置问题** - 源箱子坐标配置错误
2. **方法调用问题** - isSourceChest方法没有正确工作
3. **大箱子处理问题** - 大箱子的配对检测有问题
4. **缓存更新时机问题** - 在排除逻辑之前就加入了缓存

## 调试步骤

### 🔍 **第一步：检查配置**

#### 查看源箱子配置
```bash
# 查看当前配置的源箱子
itemsorter listchests

# 查看模块状态
itemsorter status
```

#### 验证坐标
确保配置的源箱子坐标与实际箱子位置完全一致：
- ✅ X坐标正确
- ✅ Y坐标正确  
- ✅ Z坐标正确

### 🔍 **第二步：查看调试日志**

启用模块后，系统会输出详细的调试日志：

#### 缓存更新日志
```
[ChestCache] 检查位置 (100, 64, 200) 是否是源箱子
[ChestCache] 配置的源箱子数量: 2
[ChestCache] 源箱子 1: (100, 64, 200)
[ChestCache] 源箱子 2: (105, 64, 200)
[ChestCache] 匹配到源箱子: (100, 64, 200)
[ChestCache] 排除源箱子: (100, 64, 200)
```

#### 打开箱子日志
```
[ChestCache] onChestOpened: 排除源箱子 (100, 64, 200)
```

### 🔍 **第三步：常见问题排查**

#### 问题1：没有配置源箱子
**症状**: 日志显示 "警告: 没有配置源箱子"
**解决**: 
```bash
itemsorter addchest <x> <y> <z>
```

#### 问题2：坐标不匹配
**症状**: 日志显示检查了位置但没有匹配到源箱子
**解决**: 检查配置的坐标是否与实际箱子位置一致

#### 问题3：大箱子配对问题
**症状**: 大箱子的一半被排除，另一半没有被排除
**解决**: 检查大箱子检测逻辑是否正常工作

#### 问题4：方法调用异常
**症状**: isSourceChest方法抛出异常
**解决**: 检查World.getBlockState方法调用是否正确

### 🔍 **第四步：手动验证**

#### 验证isSourceChest方法
```java
// 在控制台或日志中查看
boolean result = chestCacheManager.isSourceChest(x, y, z);
System.out.println("位置 (" + x + ", " + y + ", " + z + ") 是源箱子: " + result);
```

#### 验证配置加载
```java
// 检查配置是否正确加载
System.out.println("源箱子配置数量: " + config.chestLocations.size());
for (ChestLocation chest : config.chestLocations) {
    System.out.println("源箱子: " + chest);
}
```

## 可能的修复方案

### 🔧 **修复1：配置问题**

#### 重新配置源箱子
```bash
# 清除现有配置
itemsorter removechest 0  # 移除第一个源箱子
itemsorter removechest 0  # 移除第二个源箱子（索引会自动调整）

# 重新添加正确的坐标
itemsorter addchest 100 64 200
itemsorter addchest 105 64 200
```

### 🔧 **修复2：缓存清理**

#### 清空缓存重新扫描
```bash
# 清空箱子缓存
itemsorter clearcache

# 查看缓存状态
itemsorter cachestats
```

### 🔧 **修复3：模块重启**

#### 重启模块
```bash
# 禁用模块
itemsorter disable

# 等待几秒

# 重新启用模块
itemsorter enable
```

### 🔧 **修复4：代码修复**

如果是代码问题，可能需要修复以下方面：

#### isSourceChest方法
```java
private boolean isSourceChest(int x, int y, int z) {
    // 确保配置不为空
    if (config.chestLocations == null || config.chestLocations.isEmpty()) {
        return false;
    }
    
    for (ChestLocation sourceChest : config.chestLocations) {
        // 直接坐标匹配
        if (sourceChest.x == x && sourceChest.y == y && sourceChest.z == z) {
            return true;
        }
        
        // 大箱子配对检查
        try {
            if (isBigChest(sourceChest.x, sourceChest.y, sourceChest.z)) {
                ChestLocation pairedChest = findBigChestPaired(sourceChest.x, sourceChest.y, sourceChest.z);
                if (pairedChest != null && pairedChest.x == x && pairedChest.y == y && pairedChest.z == z) {
                    return true;
                }
            }
        } catch (Exception e) {
            System.err.println("检查大箱子配对时出错: " + e.getMessage());
        }
    }
    return false;
}
```

#### updateCache方法
```java
// 确保排除逻辑在正确的位置
for (ContainerSearcher.ContainerInfo container : containers) {
    if (container.type == ContainerSearcher.ContainerType.CHEST) {
        ChestLocation location = new ChestLocation(container.x, container.y, container.z);
        
        // 1. 检查是否已处理（避免重复）
        if (processedLocations.contains(location)) {
            continue;
        }
        
        // 2. 排除源箱子（关键步骤）
        if (isSourceChest(container.x, container.y, container.z)) {
            continue;
        }
        
        // 3. 其他处理逻辑...
    }
}
```

## 验证修复结果

### ✅ **验证步骤**

#### 1. 检查缓存统计
```bash
itemsorter cachestats
# 应该显示: (已排除X个源箱子)
```

#### 2. 检查缓存内容
```bash
# 查看所有缓存的箱子位置
# 确保源箱子坐标不在其中
```

#### 3. 测试物品分类
```bash
# 运行物品分类
# 确保物品不会被存储到源箱子中
```

#### 4. 查看日志输出
```
# 应该看到类似的日志:
[ChestCache] 排除源箱子: (100, 64, 200)
[ChestCache] onChestOpened: 排除源箱子 (100, 64, 200)
```

## 预防措施

### 🛡️ **最佳实践**

#### 1. 配置验证
- 添加源箱子后立即验证坐标
- 使用 `itemsorter listchests` 确认配置

#### 2. 定期检查
- 定期使用 `itemsorter cachestats` 检查状态
- 观察是否有异常的缓存行为

#### 3. 日志监控
- 关注调试日志输出
- 及时发现和解决问题

#### 4. 测试验证
- 配置完成后进行完整的功能测试
- 确保源箱子和存储箱子功能分离

通过以上调试步骤和修复方案，应该能够解决源箱子被错误加入缓存的问题。如果问题仍然存在，请检查调试日志输出，找出具体的失败原因。
