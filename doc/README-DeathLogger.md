## DeathLogger 模块功能总结

我为您创建了一个全新的 **DeathLogger** 模块，用于记录所有玩家死亡信息。以下是该模块的详细功能：

### 🎯 核心功能

1. **监听死亡事件**: 自动捕获所有 `DeathMessageChatEvent` 事件
2. **详细信息记录**: 提取并结构化死亡消息的所有关键信息
3. **双重输出**: 支持控制台打印和本地文件保存
4. **JSON 格式**: 使用标准化的 JSON 格式存储数据

### 📊 记录的数据字段

每条死亡记录包含以下信息：

- **`timestamp`**: ISO 格式的时间戳
- **`victim`**: 死亡玩家名称
- **`killer`**: 杀手名称（如果有）
- **`killerType`**: 杀手类型（PLAYER 或 MOB）
- **`weapon`**: 使用的武器（如果有）
- **`message`**: 格式化的死亡消息
- **`rawMessage`**: 纯文本死亡消息
- **`componentJson`**: 原始组件 JSON 数据
- **`coordinates`**: 坐标信息（可选）
   - x, y, z 坐标
   - 维度信息

### ⚙️ 配置选项

在 `config.json` 中的 `client.extra.deathLogger` 部分：

```json
{
  "enabled": false,              // 启用/禁用模块
  "printToConsole": true,        // 是否打印到控制台
  "saveToFile": true,           // 是否保存到文件
  "fileName": "death_logs.json", // 文件名
  "includeTimestamp": true,      // 包含时间戳
  "includeCoordinates": false,   // 包含坐标信息
  "includeWeaponInfo": true,     // 包含武器信息
  "includeKillerType": true,     // 包含杀手类型
  "prettyPrintJson": true        // 美化 JSON 输出
}
```

### 📁 文件结构

- **存储目录**: `deaths/`
- **默认文件**: `deaths/death_logs.json`
- **格式**: JSON 数组，每个元素是一条死亡记录

### 🖥️ 控制台输出示例

```
=== DEATH MESSAGE LOGGED ===
Victim: PlayerName
Killer: KillerName (PLAYER)
Weapon: Diamond Sword
Message: PlayerName was slain by KillerName using Diamond Sword
Coordinates: 100 64 -200 (OVERWORLD)
Timestamp: 2024-08-05T10:30:45.123Z
JSON: { ... detailed json ... }
============================
```

### 📄 JSON 文件示例

```json
[
  {
    "timestamp": "2024-08-05T10:30:45.123Z",
    "victim": "PlayerName",
    "killer": "KillerName",
    "killerType": "PLAYER",
    "weapon": "Diamond Sword",
    "message": "PlayerName was slain by KillerName using Diamond Sword",
    "rawMessage": "PlayerName was slain by KillerName using Diamond Sword",
    "componentJson": "{...}",
    "coordinates": {
      "x": 100,
      "y": 64,
      "z": -200,
      "dimension": "OVERWORLD"
    }
  }
]
```

### 🔧 技术特性

- **事件驱动**: 基于 ZenithProxy 的事件系统
- **异常处理**: 完善的错误处理和日志记录
- **文件安全**: 自动创建目录，安全的文件读写
- **内存效率**: 增量式文件更新，不重写整个文件
- **配置热更新**: 支持运行时配置更改

### 🚀 使用方法

1. 在配置文件中启用模块：`"enabled": true`
2. 根据需要调整其他配置选项
3. 重启代理或重新加载配置
4. 死亡消息将自动记录到指定位置

这个模块为 ZenithProxy 提供了强大的死亡事件追踪能力，适用于数据分析、PvP 统计、服务器监控等多种用途。
