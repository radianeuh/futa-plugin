# Futa Manager - ZenithProxy 插件合集

> **[🌐 English Documentation (英文文档)](README.md)**

一个功能强大的 Minecraft ZenithProxy 插件合集，提供自动化珍珠管理、附魔、交易、合成辅助等多种实用功能。

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

**Futa Manager** 是一个基于 ZenithProxy 平台的 Minecraft 客户端插件，专为自动化游戏操作设计。该插件提供珍珠管理、自动完成物品整理、附魔、村民交易、自动合成等重复性任务。

该项目最初是我在 2024 年写的，主要是为了我自己在一个服务器使用。后来觉得功能越来越多，也越来越完善，我就决定把它开源出来。

现在代码里面有很多硬编码的地方，如果觉得有问题可以提出来。

另外，插件包含的大部分功能模块默认都是关闭的，不会影响到默认的行为，可以放心使用。

非常欢迎大家参与协作，提交 PR。

### 核心特点

- ✅ **高度自动化**：智能状态机驱动，自动处理复杂流程
- ✅ **模块化设计**：25+ 独立模块，可按需启用
- ✅ **灵活配置**：每个模块都有详细的配置文件
- ✅ **多版本支持**：支持 Minecraft 1.21.4 及更高版本
- ✅ **事件驱动**： 高效事件处理机制

---

## ⚡ 功能特性

### 📦 物品管理

#### 1. **自动箱子管理器 (AutoChestManager)**
- 自动从多个箱子提取物品
- 智能分类存储到潜影盒
- 自动丢弃垃圾物品
- 支持轮次延迟和超时保护

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

#### 6. **商店广告模块 (Shop)**
- 自动化商店广告操作


### ⚔️ 战斗辅助

#### 7. **自动水晶放置 (AutoCrystal)**
- 自动放置末地水晶


#### 8. **珍珠增强 (PearlPlus)**
- 自动加载和使用末影珍珠
- 支持通过私信命令远程控制（发送"拉"请求）
- 智能搜索附近牌子获取玩家珍珠位置
- 自动检测距离并返回状态信息
- 支持配置多个预设珍珠点
- 使用后可自动返回起始位置并断开连接

#### 9. **死亡记录器 (DeathLogger)**
- 记录所有玩家死亡信息
- JSON 格式详细日志
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
- 批量打开宝库
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
- 主要用户末影人农场

#### 18. **末地门传送 (EndGateway)**
- 自动进入末地门
- 快速传送

#### 19. **自动丢弃 (AutoDrop)**
- 自动丢弃指定物品
- 背包空间管理

#### 21. **防卡住 (AntiStuck)**
- 检测卡住状态
- 自动脱困

#### 22. **自动酿造 (AutoBrewer)**
- 自动酿造药水

#### 23. **重生处理 (PostRespawn)**
- 重生后自动执行动作

#### 24. **鞘翅飞行 (ElytraFly)**
- 自动鞘翅飞行控制
- Pitch 振荡飞行（无需烟花）
- 智能边界重置
- 目标坐标导航与自动下线
- 低 Y 轴自动下线安全功能
- 速度：30-40 格/秒，不消耗鞘翅耐久

---

## 📥 安装说明

### 前置要求

- **ZenithProxy**: Java 版本（不支持 Linux 版本）
- **Java**: JDK 25 或更高版本
- **Minecraft**: 1.21.4 或兼容版本

### 安装步骤

1. **下载插件**

   release

3. **安装插件**
   - 将 `.jar` 复制到 ZenithProxy 的 `plugins` 文件夹
   - 重启 ZenithProxy 以加载插件

4. **验证安装**

   - 启动 ZenithProxy
   - 查看控制台输出，确认插件加载成功
   - 使用相关命令测试

---

## ⚙️ 配置指南

### 配置文件位置

插件配置文件位于 ZenithProxy 根目录的 `config/futa.json`

### 配置示例

详见各模块的使用文档：
- [自动凋零使用说明](autowither_usage.md)
- [方块替换使用说明](block_replace_usage.md)

---

## 💻 命令列表

### 

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
/pp <on|off>                     # 启用/禁用珍珠增强（私信远程控制）

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

/pp                              # 珍珠

/loginonce                       # 一次性登录命令

/elytrafly <on|off>              # 启用/禁用鞘翅飞行
/elytrafly upper <height>        # 设置上边界高度
/elytrafly lower <height>        # 设置下边界高度
/elytrafly speed <degrees>       # 设置 pitch 旋转速度
/elytrafly gap <blocks>          # 设置边界间距
/elytrafly goto <x> <z>          # 导航到目标坐标
/elytrafly disconnect on|off     # 到达目标自动下线
/elytrafly disconnectDistance <blocks>  # 设置下线判定距离
/elytrafly lowY <height>         # 低于 Y 坐标自动下线
/elytrafly debug on|off          # 启用调试日志
/elytrafly debugPeriod <seconds> # 设置调试日志间隔
```

---

## 📚 模块详解

### 珍珠增强 (PearlPlus)

**功能概述：**
PearlPlus 是一个智能化的末影珍珠管理系统，支持自动加载珍珠和通过私信远程控制。

**核心功能：**

1. **自动珍珠加载**
   - 根据预设 ID 自动前往指定位置加载珍珠
   - 加载完成后可选择返回起始位置并断开连接
   - 适用于需要定期使用珍珠的场景

2. **私信远程控制**
   - 其他玩家可通过私信发送"拉"来请求你帮助加载他们的珍珠
   - 系统会自动搜索附近 64 格范围内的牌子获取玩家珍珠位置
   - 智能检测距离，超过 120 格会拒绝请求并告知距离
   - 自动回复状态信息（正在路上、距离太远、珍珠不存在等）

3. **智能位置管理**
   - 自动识别带有玩家名字的牌子（需配合陷阱门）
   - 将找到的珍珠位置保存到配置中
   - 支持多个玩家的珍珠位置存储

4. **状态反馈**
   - Discord 和游戏内通知
   - 详细的距离计算和路径规划
   - 错误处理和友好提示

**配置项：**
```json
{
   "pearlPlus": {
      "enabled": true,        // 是否启用模块
      "auto": false,          // 是否自动执行（触发后自动重置为 false）
      "autoId": "",           // 自动加载的珍珠 ID
      "server": ""            // 服务器名称
   }
}
```



**注意事项：**
- 确保珍珠位置附近有可交互方块（如陷阱门、拉杆等）
- 牌子上需要包含玩家名字以便自动识别
- 距离限制为 120 格（曼哈顿距离）
- 使用时需要足够的末影珍珠库存

---

### 鞘翅飞行 (ElytraFly)

**功能概述：**
ElytraFly 是一个自动鞘翅飞行控制模块，实现 pitch 振荡飞行，无需烟花且不消耗鞘翅耐久。机器人自动在 -40°（抬头）和 +40°（低头）之间调整 pitch，在设定的高度范围内持续飞行。

**核心功能：**

1. **自动 Pitch 控制**
   - 在 -40° 和 +40° 之间振荡实现持续飞行
   - 可配置旋转速度（度/tick）
   - 根据玩家位置自动重置边界

2. **高度管理**
   - 上下边界定义飞行高度范围
   - 玩家低于阈值时自动重置边界
   - 智能检测最高点进行边界调整

3. **目标导航**
   - 设置目标 X/Z 坐标进行导航
   - 自动计算 yaw 朝向目标
   - 可配置下线判定距离

4. **安全功能**
   - 低 Y 轴自动下线（可配置阈值）
   - 未加载区块检测防止卡住
   - 可配置间隔的调试日志

**配置项：**
```json
{
   "elytraFly": {
      "enabled": false,
      "pitch40LowerBounds": 80,
      "pitch40UpperBounds": 120,
      "pitch40RotationSpeed": 4,
      "boundGap": 60,
      "targetX": 0,
      "targetZ": 0,
      "disconnectOnReach": false,
      "disconnectDistance": 5,
      "disconnectOnLowY": 0,
      "debug": false,
      "debugLogPeriod": 2
   }
}
```

**使用方法：**
1. 穿戴鞘翅并手动起飞
2. 启用模块：`.elytrafly on`
3. （可选）设置目标：`.elytrafly goto 1000 2000`
4. （可选）启用自动下线：`.elytrafly disconnect on`
5. 模块将自动控制 pitch 并导航

**性能：**
- 速度：30-40 格/秒
- 无需烟花
- 不消耗鞘翅耐久
- 建议同时启用 ElytraUnbreak 模块

---

### 架构设计

插件采用模块化架构，每个功能都是一个独立的 Module 或 Command：

```
FutaPlugin (主插件)
├── Modules (25个模块)
│   ├── AutoChestManagerModule
│   ├── ItemSorterModule
│   ├── AutoEnchantModule
│   ├── VillagerTrader
│   ├── PearlPlusModule
│   └── ... (其他模块)
└── Commands (22个命令)
    ├── AutoChestManagerCommand
    ├── ItemSorterCommand
    ├── AutoEnchantCommand
    ├── PPCommand
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

- **语言**: Java 25+
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
- 所有贡献者和使用者

---

**⭐ 如果这个项目对你有帮助，请给个 Star！**
