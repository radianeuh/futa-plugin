# 自动喂海龟模块 (AutoTurtleFeed)

## 功能说明

自动寻找附近的海龟并使用海草进行喂食。每个海龟被喂食后会标记10分钟（可配置）内不再重复喂食。

## 特性

- ✅ 自动检测附近未喂食的海龟
- ✅ 自动切换到海草
- ✅ 右键点击喂食海龟
- ✅ 喂食冷却时间管理（默认10分钟）
- ✅ 可配置最大交互距离
- ✅ 调试模式支持

## 使用方法

### 启用/禁用模块

```
autoturtlefeed on    # 启用自动喂海龟
autoturtlefeed off   # 禁用自动喂海龟
```

### 配置参数

#### 1. 最大距离 (maxDistance)
设置可以喂食的海龟的最大距离（单位：方块）

```
autoturtlefeed maxDistance <1.0-50.0>
```

示例：
```
autoturtlefeed maxDistance 10.0
```

#### 2. 喂食冷却时间 (cooldownMinutes)
设置同一只海龟两次喂食之间的最小间隔时间（单位：分钟）

```
autoturtlefeed cooldownMinutes <1-60>
```

示例：
```
autoturtlefeed cooldownMinutes 10
```

#### 3. 调试模式 (debug)
开启或关闭调试信息输出

```
autoturtlefeed debug <on/off>
```

示例：
```
autoturtlefeed debug on
```

### 查看当前配置

直接运行命令不带参数即可查看当前配置：

```
autoturtlefeed
```

输出示例：
```
Auto Turtle Feed Configuration
- Enabled: ON
- Max Distance: 10.0 blocks
- Feed Cooldown: 10 minutes
- Debug Mode: OFF
```

## 使用要求

1. **背包中必须有海草** - 模块会自动从背包中切换海草到主手
2. **海龟必须在配置的距离范围内** - 默认10个方块
3. **必须能够看到海龟** - 不能有方块遮挡视线
4. **海龟未被标记为已喂食** - 或者已超过冷却时间

## 工作流程

1. 扫描附近存活的海龟
2. 过滤掉最近已喂食的海龟（在冷却时间内）
3. 过滤掉超出距离范围的海龟
4. 过滤掉无法射线命中的海龟（有遮挡）
5. 选择最近的海龟作为目标
6. 切换到海草
7. 瞄准并右键点击喂食
8. 标记该海龟已喂食，开始冷却计时

## 注意事项

- 冷却记录会保存在内存中，重启后会重置
- 超过冷却时间两倍的记录会自动清理，避免内存泄漏
- 如果背包中没有海草，模块会在调试模式下发出警告
- 模块优先级为500，不会干扰高优先级的操作

## 配置示例

### 基础配置
```
autoturtlefeed on
autoturtlefeed maxDistance 10.0
autoturtlefeed cooldownMinutes 10
```

### 大范围喂食
```
autoturtlefeed on
autoturtlefeed maxDistance 20.0
autoturtlefeed cooldownMinutes 15
```

### 调试模式
```
autoturtlefeed on
autoturtlefeed debug on
```

## 技术细节

- **继承**: `AbstractInventoryModule`
- **优先级**: 500
- **物品**: 海草 (SEAGRASS)
- **实体类型**: TURTLE
- **交互方式**: 右键点击实体
- **转头机制**: 使用 `RotationHelper.shortestRotationTo()` 计算最短路径转角
- **射线检测**: 使用 `RaycastHelper.playerEyeRaycastThroughToTarget()` 验证可见性
