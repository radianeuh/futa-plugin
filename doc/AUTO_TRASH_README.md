# 自动扔垃圾插件功能

## 功能概述

自动扔垃圾插件是一个ZenithProxy插件模块，可以自动检测并丢弃库存中的垃圾物品，帮助玩家保持背包整洁。

## 主要功能

### 1. 自动检测垃圾物品
- 定期扫描玩家背包（槽位9-35）
- 根据配置的垃圾物品列表自动识别垃圾物品
- 支持自定义垃圾物品列表

### 2. 安全模式
- 内置安全模式，只丢弃确认的垃圾物品
- 防止误删重要物品
- 可配置的安全物品列表

### 3. 可配置设置
- 启用/禁用自动扔垃圾功能
- 设置延迟时间
- 显示/隐藏操作信息
- 自定义垃圾物品列表

## 命令使用

### 基本命令
```
/autotrash start    - 开始自动扔垃圾
/autotrash stop     - 停止自动扔垃圾
/autotrash status   - 查看当前状态
/autotrash help     - 显示帮助信息
```

### 垃圾物品管理
```
/autotrash add <item_id>     - 添加垃圾物品
/autotrash remove <item_id>   - 移除垃圾物品
/autotrash list              - 列出所有垃圾物品
/autotrash clear             - 清空垃圾物品列表
/autotrash reset             - 重置为默认垃圾物品列表
```

### 配置命令
```
/autotrash delay <seconds>   - 设置延迟时间（1-60秒）
```

## 默认垃圾物品列表

插件包含以下默认垃圾物品：
- 腐肉 (rotten_flesh)
- 骨头 (bone)
- 蜘蛛眼 (spider_eye)
- 毒马铃薯 (poisonous_potato)
- 枯灌木 (dead_bush)
- 草 (grass)
- 高草 (tallgrass)
- 海草 (seagrass)
- 海带 (kelp)
- 海泡菜 (sea_pickle)
- 粘土球 (clay_ball)
- 泥土 (dirt)
- 粗泥 (coarse_dirt)
- 灰化土 (podzol)
- 菌丝体 (mycelium)
- 沙砾 (gravel)
- 沙子 (sand)
- 红沙 (red_sand)
- 灵魂沙 (soul_sand)
- 灵魂土 (soul_soil)
- 下界岩 (netherrack)
- 末地石 (end_stone)
- 石头 (stone)
- 圆石 (cobblestone)
- 苔石 (mossy_cobblestone)
- 安山岩 (andesite)
- 闪长岩 (diorite)
- 花岗岩 (granite)
- 深板岩 (deepslate)
- 凝灰岩 (tuff)
- 方解石 (calcite)
- 平滑玄武岩 (smooth_basalt)
- 黑石 (blackstone)
- 玄武岩 (basalt)
- 黑曜石 (obsidian)
- 萤石粉 (glowstone_dust)
- 红石 (redstone)
- 火药 (gunpowder)
- 糖 (sugar)
- 纸 (paper)
- 木棍 (stick)
- 燧石 (flint)
- 木炭 (charcoal)
- 煤炭 (coal)
- 铁粒 (iron_nugget)
- 金粒 (gold_nugget)
- 铜粒 (copper_nugget)
- 粗铁 (raw_iron)
- 粗金 (raw_gold)
- 粗铜 (raw_copper)
- 粗煤 (raw_coal)
- 石英 (quartz)

## 配置选项

### 基本设置
- `enabled`: 是否启用插件
- `autoTrashEnabled`: 是否启用自动扔垃圾
- `containerTrashEnabled`: 是否在容器中自动扔垃圾
- `trashDelaySeconds`: 延迟时间（秒）
- `showTrashInfo`: 是否显示扔垃圾信息
- `safeMode`: 是否启用安全模式

### 垃圾物品列表
- `trashItems`: 垃圾物品ID列表
- 支持添加/移除自定义物品
- 支持重置为默认列表

## 使用示例

1. **启动自动扔垃圾**：
   ```
   /autotrash start
   ```

2. **添加自定义垃圾物品**：
   ```
   /autotrash add dirt
   /autotrash add minecraft:stone
   ```

3. **查看当前状态**：
   ```
   /autotrash status
   ```

4. **设置延迟时间**：
   ```
   /autotrash delay 5
   ```

5. **停止自动扔垃圾**：
   ```
   /autotrash stop
   ```

## 注意事项

1. **安全第一**：建议启用安全模式，防止误删重要物品
2. **定期检查**：定期查看垃圾物品列表，确保没有误删重要物品
3. **备份重要物品**：将重要物品放在安全的地方
4. **测试环境**：建议先在测试环境中验证配置

## 技术实现

- 使用ZenithProxy的库存管理系统
- 定期扫描玩家背包
- 支持批量丢弃操作
- 线程安全的实现
- 可配置的延迟和频率

## 故障排除

### 常见问题

1. **插件不工作**：
   - 检查是否已启用：`/autotrash status`
   - 确认模块已加载

2. **物品没有被丢弃**：
   - 检查物品是否在垃圾列表中：`/autotrash list`
   - 确认安全模式设置

3. **误删重要物品**：
   - 立即停止插件：`/autotrash stop`
   - 检查垃圾物品列表
   - 移除误添加的物品

### 日志信息

插件会在聊天中显示操作信息：
- 绿色：正常操作信息
- 黄色：调试信息
- 红色：错误信息

## 更新日志

### v1.0.0
- 初始版本
- 基本自动扔垃圾功能
- 可配置垃圾物品列表
- 安全模式支持
- 完整的命令系统 