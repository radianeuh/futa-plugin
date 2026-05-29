# Futa Manager - ZenithProxy 插件合集

一个功能强大的 Minecraft ZenithProxy 插件合集，提供自动化物品管理、附魔、交易、战斗辅助等多种实用功能。

## 📋 目录

- [项目简介](#项目简介)
- [功能特性](#功能特性)
- [安装说明](#安装说明)
- [配置指南](#配置指南)
- [命令列表](#命令列表)
- [模块详解](#模块详解)
- [开发指南](#开发指南)
- [注意事项](#注意事项)

---

## 🎯 项目简介

**Futa Manager** 是一个基于 ZenithProxy 平台的 Minecraft 客户端插件，专为自动化游戏操作设计。该插件提供了丰富的模块和命令，帮助玩家自动完成物品整理、附魔、村民交易、自动战斗等重复性任务。

### 核心特点

- ✅ **高度自动化**：智能状态机驱动，自动处理复杂流程
- ✅ **模块化设计**：25+ 独立模块，可按需启用
- ✅ **灵活配置**：每个模块都有详细的配置文件
- ✅ **多版本支持**：支持 Minecraft 1.21.4 及更高版本
- ✅ **事件驱动**：基于 tick 的高效事件处理机制

---

## ⚡ 功能特性

### 📦 物品管理

#### 1. **自动箱子管理器 (AutoChestManager)**
- 自动从多个箱子提取物品
- 智能分类存储到潜影盒
- 自动丢弃垃圾物品
- 支持轮次延迟和超时保护
- 状态机驱动，稳定可靠

#### 2. **物品分类器 (ItemSorter)**
- 智能物品分类系统
- 支持自定义分类规则
- 自动缓存箱子内容
- 一键整理多个箱子
- 支持普通箱子、桶和潜影盒

#### 3. **自动附魔书排序 (EnchantBookSorter)**
- 自动整理附魔书
- 按附魔类型分类存储
- 智能识别附魔等级
- 优化箱子空间利用

### 🔮 附魔系统

#### 4. **自动附魔模块 (AutoEnchant)**
- 全自动钻石装备附魔
- 智能经验收集（刷怪塔/农场）
- 支持多本附魔书合并
- 剑类装备特殊处理（最多7本书）
- 附魔进度跟踪和缓存
- 自动补充材料和书籍

### 💰 交易系统

#### 5. **村民交易者 (VillagerTrader)**
- 自动与村民交易
- 智能补货系统
- 绿宝石自动合成
- 交易冷却管理
- 支持多个村民

#### 6. **商店模块 (Shop)**
- 自动化商店操作
- 批量购买/出售物品
- 价格监控和提醒

### ⚔️ 战斗辅助

#### 7. **自动水晶放置 (AutoCrystal)**
- 自动放置末地水晶
- 智能目标选择
- 战斗状态检测

#### 8. **珍珠增强 (PearlPlus)**
- 末影珍珠投掷优化
- 精准落点计算

#### 9. **死亡记录器 (DeathLogger)**
- 记录所有玩家死亡信息
- JSON 格式详细日志
- 中文翻译支持
- 包含时间戳、坐标、武器等信息
- 可选控制台输出和文件保存

### 🌾 农场自动化

#### 10. **下界疣农场 (NetherWartFarm)**
- 自动种植和收获下界疣
- 智能生长检测
- 自动补种

#### 11. **自动凋零放置 (AutoWither)**
- 在指定位置自动放置凋零
- 灵魂沙自动补给
- 轮数计数和管理
- 最大凋零数量限制
- 详细的使用文档：[autowither_usage.md](autowither_usage.md)

### 🎒 其他实用功能

#### 12. **自动跟随 (AutoFollow)**
- 自动跟随指定玩家
- 智能路径规划
- 战斗状态检测
- 重生后自动发送坐标
- 床点击支持

#### 13. **自动登录 (AutoLogin)**
- 服务器自动登录
- 会话重连支持

#### 14. **自动开箱 (AutoVaultOpener)**
- 批量打开容器
- 快速提取物品

#### 15. **聊天日志 (ChatLog)**
- 聊天记录保存
- 关键词过滤

#### 16. **可视范围记录器 (VisualRangeLogger)**
- 记录进入视野的玩家
- 距离提醒

#### 17. **固定视角 (FixedAngleView)**
- 锁定视角角度
- 防止视角漂移

#### 18. **末地门传送 (EndGateway)**
- 自动使用末地门
- 快速传送

#### 19. **自动掉落 (AutoDrop)**
- 自动丢弃指定物品
- 背包空间管理

#### 20. ** wandering (Wander)**
- 随机移动
- 防挂机检测

#### 21. **防卡住 (AntiStuck)**
- 检测卡住状态
- 自动脱困

#### 22. **自动酿造 (AutoBrewer)**
- 自动酿造药水

#### 23. **重生处理 (PostRespawn)**
- 重生后自动执行动作

---

## 📥 安装说明

### 前置要求

- **ZenithProxy**: Java 版本（不支持 Linux 版本）
- **Java**: JDK 17 或更高版本
- **Minecraft**: 1.21.4 或兼容版本

### 安装步骤

1. **下载插件**
   ```bash
   # 克隆仓库
   git clone https://github.com/futa/ZenithProxy-futa.git
   
   # 或使用预编译的 JAR 文件
   ```

2. **构建插件**
   ```bash
   # 进入项目目录
   cd ZenithProxy-futa
   
   # 使用 Gradle 构建
   ./gradlew build
   
   # Windows 用户
   gradlew.bat build
   ```

3. **安装插件**
   - 将生成的 `build/libs/FutaManager-*.jar` 复制到 ZenithProxy 的 `plugins` 文件夹
   - 重启 ZenithProxy 以加载插件

4. **验证安装**
   - 启动 ZenithProxy
   - 查看控制台输出，确认插件加载成功
   - 使用 `/futa` 相关命令测试

---

## ⚙️ 配置指南

### 配置文件位置

插件配置文件位于 ZenithProxy 根目录的 `config/futa.json`

### 主要配置项

```json
{
  "autoChest": {
    "enabled": true,
    "interval": 60,
    "chestLocations": [],
    "shulkerLocations": [],
    "trashLocations": []
  },
  "itemSorter": {
    "enabled": true,
    "chestLocations": [],
    "classificationRules": {}
  },
  "autoEnchant": {
    "enabled": true,
    "equipmentChests": [],
    "bookChests": [],
    "resultChests": [],
    "xpFarmLocation": null,
    "delayBetweenActions": 10
  },
  "autoFollow": {
    "enabled": false,
    "targetPlayers": [],
    "updateInterval": 20,
    "maxDistance": 100
  },
  "trader": {
    "enabled": false,
    "restockChest": null,
    "restockEmeraldCountThreshold": 64,
    "restockStacks": 8
  },
  "die": {
    "enabled": true,
    "printToConsole": true,
    "saveToFile": true,
    "prettyPrintJson": true
  },
  "autoWither": {
    "enabled": false,
    "positions": [],
    "soulSandChest": null,
    "minSoulSand": 24,
    "maxWithers": 6,
    "actionDelay": 1,
    "checkInterval": 5
  }
}
```

### 配置示例

详见各模块的使用文档：
- [自动凋零使用说明](autowither_usage.md)
- [方块替换使用说明](block_replace_usage.md)

---

## 💻 命令列表

### 通用命令

| 命令 | 描述 | 权限 |
|------|------|------|
| `/futa` | 显示插件信息 | 所有玩家 |

### 模块控制命令

#### 物品管理
```bash
/autochest <on|off>              # 启用/禁用自动箱子管理
/autochest addChest <x> <y> <z>  # 添加箱子位置
/autochest list                  # 列出所有箱子

/itemsorter <on|off>             # 启用/禁用物品分类
/itemsorter addChest <x> <y> <z> # 添加箱子位置
/itemsorter classify             # 手动触发分类

/enchantbooksorter <on|off>      # 启用/禁用附魔书排序
```

#### 附魔系统
```bash
/autoenchant <on|off>            # 启用/禁用自动附魔
/autoenchant setXpFarm <x> <y> <z>  # 设置经验农场位置
/autoenchant status              # 查看附魔状态
```

#### 交易系统
```bash
/villagertrader <on|off>         # 启用/禁用村民交易
/villagertrader setRestockChest <x> <y> <z>  # 设置补货箱子
/villagertrader restock          # 手动补货

/shop <buy|sell> <item> <amount> # 商店操作
```

#### 战斗辅助
```bash
/autocrystal <on|off>            # 启用/禁用自动水晶
/pearlplus <on|off>              # 启用/禁用珍珠增强

/chatlog <on|off>                # 启用/禁用聊天日志
/chatlog search <keyword>        # 搜索聊天记录
```

#### 农场自动化
```bash
/netherwartfarm <on|off>         # 启用/禁用下界疣农场

/autowither <on|off>             # 启用/禁用自动凋零
/autowither addPosition <x> <y> <z>     # 添加放置位置
/autowither soulSandChest <x> <y> <z>   # 设置灵魂沙箱子
/autowither maxWithers <number>         # 设置最大凋零数量
/autowither listPositions        # 查看所有位置
/autowither resetRound           # 重置轮数计数器
```

#### 其他功能
```bash
/autofollow <on|off>             # 启用/禁用自动跟随
/autofollow addTarget <player>   # 添加跟随目标
/autofollow removeTarget <player># 移除跟随目标

/autologin <on|off>              # 启用/禁用自动登录

/autovault <on|off>              # 启用/禁用自动开箱

/visualrange <on|off>            # 启用/禁用可视范围记录

/fixedangle <on|off>             # 启用/禁用固定视角
/fixedangle set <pitch> <yaw>    # 设置视角角度

/endgateway <on|off>             # 启用/禁用末地门传送

/autodrop <on|off>               # 启用/禁用自动掉落
/autodrop addItem <item>         # 添加自动掉落物品

/wander <on|off>                 # 启用/禁用随机移动

/antistuck <on|off>              # 启用/禁用防卡住

/showentity                      # 显示附近实体信息

/pp                              # 性能分析命令

/loginonce                       # 一次性登录命令
```

---

## 📚 模块详解

### 架构设计

插件采用模块化架构，每个功能都是一个独立的 Module 或 Command：

```
FutaPlugin (主插件)
├── Modules (25个模块)
│   ├── AutoChestManagerModule
│   ├── ItemSorterModule
│   ├── AutoEnchantModule
│   ├── VillagerTrader
│   └── ... (其他模块)
└── Commands (22个命令)
    ├── AutoChestManagerCommand
    ├── ItemSorterCommand
    ├── AutoEnchantCommand
    └── ... (其他命令)
```

### 状态机机制

大多数模块使用状态机模式实现复杂逻辑：

```java
enum ProcessingState {
    IDLE,
    OPENING_CHEST,
    WITHDRAWING_FROM_CHEST,
    CLOSING_CHEST,
    // ... 更多状态
}
```

### 事件驱动

基于 ZenithProxy 的事件系统：

- `ClientBotTick`: 客户端 tick 事件
- `SystemChatEvent`: 系统聊天事件
- `ClientDeathEvent`: 死亡事件
- `ServerPlayerInVisualRangeEvent`: 玩家进入视野事件

---

## 🔧 开发指南

### 技术栈

- **语言**: Java 17+
- **构建工具**: Gradle 8.x
- **框架**: ZenithProxy Plugin API
- **依赖库**:
  - Hutool (工具类库)
  - Gson (JSON 处理)
  - ProtocolLib (协议处理)

### 项目结构

```
ZenithProxy-futa/
├── src/main/java/com/github/futa/
│   ├── FutaPlugin.java          # 主插件类
│   ├── BaseModule.java          # 基础模块类
│   ├── module/                  # 模块实现
│   │   ├── AutoChestManagerModule.java
│   │   ├── ItemSorterModule.java
│   │   └── ...
│   ├── command/                 # 命令实现
│   │   ├── AutoChestManagerCommand.java
│   │   ├── ItemSorterCommand.java
│   │   └── ...
│   ├── config/                  # 配置类
│   │   ├── FutaConfig.java
│   │   ├── AutoChestManagerConfig.java
│   │   └── ...
│   ├── dto/                     # 数据传输对象
│   └── util/                    # 工具类
├── src/main/resources/
│   ├── itemtag/                 # 物品标签
│   ├── mcdata/                  # Minecraft 数据
│   └── recipes/                 # 配方数据
├── doc/                         # 文档
├── build.gradle.kts             # Gradle 配置
└── README.md                    # 本文件
```

### 创建新模块

1. **创建模块类**
```java
public class MyModule extends Module {
    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(ClientBotTick.class, this::onTick)
        );
    }
    
    @Override
    public boolean enabledSetting() {
        return PLUGIN_CONFIG.myModule.enabled;
    }
    
    private void onTick(ClientBotTick event) {
        // 模块逻辑
    }
}
```

2. **创建配置类**
```java
public class MyModuleConfig {
    public boolean enabled = false;
    // 其他配置项
}
```

3. **注册模块**
```java
// 在 FutaPlugin.onLoad() 中
pluginAPI.registerModule(new MyModule());
```

### 创建新命令

```java
@CommandInfo(
    name = "mycommand",
    description = "我的命令"
)
public class MyCommand extends Command {
    @Override
    public void execute(CommandContext context) {
        // 命令逻辑
    }
}
```

### 构建和测试

```bash
# 构建插件
./gradlew build

# 运行测试
./gradlew test

# 本地测试（需要配置 ZenithProxy）
./gradlew run
```

---

## ⚠️ 注意事项

### 使用建议

1. **备份配置**: 使用前备份重要数据和配置文件
2. **逐步启用**: 先启用单个模块测试，确认无误后再启用其他模块
3. **监控日志**: 定期检查控制台日志和错误信息
4. **合理配置**: 根据服务器规则调整模块参数，避免被检测为作弊

### 性能优化

- 调整 `updateInterval` 减少 tick 频率
- 合理设置延迟时间（`delayBetweenActions`）
- 避免同时启用过多模块
- 定期清理日志文件

### 兼容性

- 仅支持 ZenithProxy Java 版本
- 需要 Minecraft 1.21.4 或兼容版本
- 某些模块可能需要特定的服务器环境

### 安全提示

⚠️ **重要声明**: 
- 本插件仅供学习和研究使用
- 在多人服务器上使用自动化脚本可能违反服务器规则
- 请遵守各服务器的使用条款
- 开发者不对因使用本插件导致的任何后果负责

---

## 📖 相关文档

- [自动凋零使用说明](autowither_usage.md)
- [方块替换使用说明](block_replace_usage.md)
- [物品分类完整指南](doc/ItemClassification_Complete.md)
- [物品排序器默认分类](doc/ItemSorter_DefaultClassification_README.md)
- [物品排序器一箱一物](doc/ItemSorter_OneItemOneChest_README.md)
- [死亡记录器说明](doc/README-DeathLogger.md)

---

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

---

## 📄 许可证

本项目遵循 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件

---

## 👥 作者

- **futa** - 初始开发和维护

---

## 🙏 致谢

- [ZenithProxy](https://github.com/rfresh2/ZenithProxy) - 强大的 Minecraft 代理平台
- [Hutool](https://hutool.cn/) - Java 工具类库
- 所有贡献者和使用者

---

## 📞 联系方式

- GitHub: [futa](https://github.com/futa)
- 问题反馈: [Issues](https://github.com/futa/ZenithProxy-futa/issues)

---

**⭐ 如果这个项目对你有帮助，请给个 Star！**
