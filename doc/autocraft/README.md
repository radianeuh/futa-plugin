# 自动合成模块 (AutoCraft Module)

## 概述

自动合成模块是一个功能强大的Minecraft自动化工具，可以自动收集原料、合成物品并存储成品。该模块专门为触手可及的容器和工作台设计，无需复杂的路径规划。

## 主要特性

- **多容器原料管理**: 支持从多个容器收集原料
- **智能合成执行**: 支持手合成和工作台合成
- **批量处理**: 高效的批量合成和存储
- **状态机管理**: 可靠的状态控制和错误恢复
- **灵活配置**: 丰富的配置选项和任务管理
- **实时监控**: 详细的状态信息和性能统计

## 系统架构

```
AutoCraftModule (主模块)
├── MultiContainerMaterialManager (原料管理器)
├── LocalContainerOperator (容器操作器)
├── LocalCraftingExecutor (合成执行器)
└── 配置和命令系统
```

## 安装和配置

### 1. 确保模块已注册

模块已自动注册到FutaPlugin中，无需额外配置。

### 2. 配置容器位置

在配置文件中设置以下关键位置：
- `sourceContainers`: 原料容器列表
- `craftingTable`: 工作台位置
- `resultContainer`: 成品容器位置

### 3. 配置合成任务

添加需要自动合成的物品和数量。

## 使用方法

### 基本命令

```bash
# 启用/禁用自动合成
autocraft on
autocraft off

# 查看状态
autocraft status

# 添加合成任务
autocraft add minecraft:stick 64

# 移除合成任务
autocraft remove minecraft:stick

# 列出所有任务
autocraft list

# 查看统计信息
autocraft stats

# 重置状态
autocraft reset
```

### 配置管理

```bash
# 查看配置
autocraft config

# 查看特定配置段
autocraft config crafting

# 设置配置项
autocraft set batchSize 32
autocraft set delayBetweenActions 1000
```

## 配置选项

### 基础配置
- `enabled`: 是否启用模块
- `priority`: 模块优先级
- `delayBetweenActions`: 操作间隔时间(ms)
- `maxRetryAttempts`: 最大重试次数

### 合成配置
- `allowHandCrafting`: 允许手合成
- `batchSize`: 批量合成大小
- `autoSortIngredients`: 自动整理原料
- `waitForMaterialsRestock`: 等待原料补充

### 库存管理
- `maxInventoryUsage`: 最大库存使用率(%)
- `autoCleanInventory`: 自动清理背包
- `keepEssentialItems`: 保留重要物品

## 工作流程

1. **空闲状态**: 检查是否有待处理的任务
2. **计算需求**: 分析配方和原料需求
3. **收集原料**: 从多个容器收集所需原料
4. **准备合成**: 检查合成条件和库存空间
5. **执行合成**: 进行物品合成
6. **存储成品**: 将合成结果存储到指定容器
7. **完成状态**: 准备下一个任务

## 支持的配方

模块内置了常用配方的支持：

### 2x2合成（手合成）
- 木棍 (stick)
- 木板 (planks)
- 工作台 (crafting_table)
- 火把 (torch)

### 3x3合成（工作台）
- 熔炉 (furnace)
- 箱子 (chest)
- 木镐 (wooden_pickaxe)
- 石镐 (stone_pickaxe)
- 铁镐 (iron_pickaxe)
- 高炉 (blast_furnace)
- 烟熏炉 (smoker)

## 性能优化

- **并行处理**: 支持多容器并行操作
- **智能缓存**: 容器内容和配方缓存
- **批量操作**: 高效的批量合成和存储
- **错误恢复**: 自动错误检测和恢复机制

## 故障排除

### 常见问题

1. **容器无法打开**
   - 检查容器位置配置是否正确
   - 确保容器在触手可及的范围内

2. **原料不足**
   - 检查原料容器是否有足够的物品
   - 确认配方配置是否正确

3. **合成失败**
   - 检查工作台位置是否正确
   - 确认玩家背包是否有足够空间

4. **模块无响应**
   - 使用 `autocraft reset` 重置状态
   - 检查模块是否已启用

### 调试信息

启用调试日志：
```bash
autocraft set enableDebugLogging true
```

## 扩展功能

### 自定义配方

可以通过 `CraftingHelper` 类添加自定义配方：

```java
CraftingHelper.addCustomRecipe(
    "minecraft:custom_item", 
    "custom_recipe_id", 
    true,  // 需要工作台
    3, 3   // 3x3配方
);
```

### 自定义容器操作

可以通过扩展 `LocalContainerOperator` 类来支持特殊容器类型。

## 性能统计

模块提供详细的性能统计信息：

- 处理任务数量
- 失败任务数量
- 成功率
- 合成物品总数
- 平均处理时间

使用 `autocraft stats` 命令查看统计信息。

## 注意事项

1. **容器位置**: 确保所有容器和工作台都在触手可及的位置
2. **库存空间**: 保持足够的背包空间用于合成操作
3. **原料充足**: 确保原料容器中有足够的物品
4. **配置正确**: 仔细检查所有配置项是否正确

## 更新日志

### v1.0.0
- 初始版本发布
- 基础自动合成功能
- 多容器原料管理
- 状态机控制
- 命令系统支持

## 贡献

欢迎提交问题报告和功能请求。在贡献代码时，请确保：

1. 遵循现有的代码风格
2. 添加适当的测试
3. 更新相关文档
4. 确保向后兼容性

## 许可证

本模块遵循与主项目相同的许可证。