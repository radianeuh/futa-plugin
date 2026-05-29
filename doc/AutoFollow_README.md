# AutoFollow 模块使用说明

## 功能概述

AutoFollow 模块实现了自动跟随玩家的功能，可以根据配置的玩家名列表自动跟随视距内最近的玩家。

## 配置说明

在 `config.json` 文件中的 `autoFollow` 部分进行配置：

```json
{
  "autoFollow": {
    "enabled": false,
    "targetPlayers": [],
    "followDistance": 3.0,
    "maxFollowDistance": 50,
    "updateInterval": 20,
    "avoidObstacles": true,
    "stopInCombat": true
  }
}
```

### 配置项说明

- **enabled**: 是否启用自动跟随功能
- **targetPlayers**: 目标玩家名列表，将自动跟随这些玩家中最近的一个
- **followDistance**: 跟随距离（格数），当距离小于此值时不会移动
- **maxFollowDistance**: 最大跟随距离（格数），超过此距离的玩家不会被考虑
- **updateInterval**: 更新间隔（tick），20 tick = 1秒
- **avoidObstacles**: 是否启用障碍物规避（使用智能路径规划）
- **stopInCombat**: 是否在战斗状态时暂停跟随
- **autoClickBed**: 是否启用自动点击床功能
- **bedSearchRadius**: 床搜索半径（格数）
- **bedClickCooldownMs**: 床点击冷却时间（毫秒）

## 命令使用

模块使用 ZenithProxy 的 Brigadier 命令系统，提供以下命令：

### 基本开关命令
```
/autofollow               - 切换启用/禁用状态（无参数时显示状态）
/autofollow on            - 启用自动跟随
/autofollow off           - 禁用自动跟随
/autofollow status        - 显示详细状态信息
```

### 目标玩家管理
```
/autofollow add <玩家名>       - 添加玩家到跟随目标列表
/autofollow remove <玩家名>    - 从跟随目标列表中移除玩家
/autofollow list             - 显示当前目标玩家列表
```

### 命令别名
```
/af                          - /autofollow 的别名
/af add <玩家名>              - 添加目标
/af remove <玩家名>           - 移除目标
/af list                    - 显示列表
/af status                  - 显示状态
```

## 输出格式

命令使用 ZenithProxy 的 Embed 系统，提供美观的格式化输出：

### 🎨 颜色编码
- **绿色**: 成功操作（添加/移除玩家）
- **蓝色**: 信息展示（状态/列表）
- **黄色**: 警告信息（重复操作）
- **红色**: 错误信息（无效输入）

### 📋 状态显示
```
AutoFollow 状态
─────────────────────────────
启用状态:      ✅ 启用
目标玩家数量:  3
跟随距离:      3.0 格
最大跟随距离:  100 格
更新间隔:      20 tick
规避障碍:      ✅ 启用
战斗暂停:      ✅ 启用
自动点击床:    ✅ 启用
床搜索半径:    4 格
床点击冷却:    5 秒
目标玩家:      
  1. Player1
  2. Player2  
  3. Player3
模块状态:      正在跟随: Player1
─────────────────────────────
```

### 📝 列表展示
```
跟随目标玩家列表 (3)
─────────────────────────────
**1.** Player1
**2.** Player2
**3.** Player3
─────────────────────────────
```

### ✅ 操作确认
```
添加成功
─────────────────────────────
已添加玩家 'NewPlayer' 到跟随目标列表
─────────────────────────────
```

## 工作原理

### 1. 目标选择
- 从配置的目标玩家列表中选择
- 只考虑视距内（maxFollowDistance）的玩家
- 优先选择距离最近的玩家

### 2. 跟随逻辑
- 定期检查目标玩家位置（updateInterval间隔）
- 当距离大于followDistance时开始移动
- 支持两种移动模式：
  - 智能路径规划（avoidObstacles=true）：使用Baritone进行路径规划
  - 直线移动（avoidObstacles=false）：直接向目标移动

### 3. 安全机制
- **战斗检测**：当stopInCombat=true时，检测到战斗状态会暂停跟随
- **生命值检测**：当玩家生命值过低时暂停跟随
- **异常处理**：出现异常时自动停止跟随，避免卡死

### 4. 自动床点击功能
- **智能搜索**：在每个tick周期内，在配置的搜索半径（bedSearchRadius）范围内寻找床方块
- **方块识别**：通过检查方块的注册名称是否包含 "bed" 来识别床方块
- **自动点击**：找到床后，使用 `BARITONE.rightClickBlock()` 方法自动点击床
- **频率限制**：通过配置的冷却时间（bedClickCooldownMs）控制点击频率，避免频繁点击
- **范围控制**：可配置搜索半径（默认4格），适应不同的使用场景
- **开关控制**：通过 `autoClickBed` 配置项可以完全禁用此功能

## 实现细节

### TODO项目（需要用户实现的具体API）

1. **获取玩家位置**：
   ```java
   private Vec3d getCurrentPlayerPosition() {
       // 需要实现：从全局状态或玩家缓存中获取位置
       return null;
   }
   ```

2. **获取玩家名称**：
   ```java
   private String getCurrentPlayerName() {
       // 需要实现：获取当前玩家名称
       return "";
   }
   ```

3. **战斗状态检测**：
   ```java
   private boolean isInCombat() {
       // 需要实现：检测玩家是否在战斗状态
       return false;
   }
   ```

4. **生命值检测**：
   ```java
   private boolean isPlayerLowHealth() {
       // 需要实现：检测玩家生命值是否过低
       return false;
   }
   ```

5. **移动控制**：
   ```java
   private void moveToPosition(Vec3d targetPos, double targetDistance) {
       // 需要实现：调用移动API
       if (PluginDie.PLUGIN_CONFIG.autoFollow.avoidObstacles) {
           // 使用Baritone路径规划
           // BARITONE.pathTo(targetPos.x, targetPos.y, targetPos.z);
       } else {
           // 直接移动
           // moveToDirectly(targetPos);
       }
   }
   ```

6. **停止移动**：
   ```java
   private void stopMovement() {
       // 需要实现：调用停止移动API
       // BARITONE.stop();
   }
   ```

## 使用示例

### 基本使用
1. 添加目标玩家：
   ```
   /af add Player1
   /af add Player2
   ```

2. 启用自动跟随：
   ```
   /af on
   ```

3. 查看状态：
   ```
   /af status
   ```

### 配置文件示例
```json
{
  "autoFollow": {
    "enabled": true,
    "targetPlayers": ["Player1", "Player2", "Player3"],
    "followDistance": 2.0,
    "maxFollowDistance": 64,
    "updateInterval": 10,
    "avoidObstacles": true,
    "stopInCombat": true
  }
}
```

## 注意事项

1. **性能考虑**：updateInterval不宜设置过小，建议10-40之间
2. **距离设置**：maxFollowDistance应小于服务器的视距（通常64-128格）
3. **安全性**：建议始终启用stopInCombat以避免危险情况
4. **API依赖**：需要确保相关的移动和状态检测API已正确实现

## 故障排除

### 常见问题
1. **不移动**：检查目标玩家是否在视距内，以及配置是否正确
2. **移动卡死**：检查avoidObstacles设置，尝试启用智能路径规划
3. **频繁停止**：检查战斗检测逻辑是否过于敏感

### 调试信息
模块会输出详细的调试信息，包括：
- 目标选择过程
- 距离计算结果
- 移动指令执行
- 暂停/恢复原因

启用调试模式可获得更多信息。