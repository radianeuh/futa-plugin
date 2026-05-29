# 物品分类模块 (ItemSorterModule) 使用说明

## 概述

物品分类模块是一个全新的独立模块，专门用于自动分类和整理Minecraft中的所有物品。它支持从指定箱子坐标列表中提取物品，根据智能分类规则将物品放入对应的存储容器中。

## 主要功能

### 1. 全物品自动分类
- 支持所有Minecraft物品的自动分类
- 基于物品属性的智能分类（工具类型、材料、用途等）
- 用户自定义分类规则
- 预定义的物品分类组（工具、武器、建筑材料、食物等）

### 2. 灵活的存储系统
- 支持箱子、潜影盒、桶等多种容器类型
- 自动搜索附近容器功能
- 可配置每个分类的专用存储位置
- 支持坐标指定和自动搜索两种模式

### 3. 智能处理流程
- 状态机管理，确保处理流程稳定
- 异步操作，避免阻塞游戏
- 错误恢复机制
- 可配置的处理间隔和超时

## 配置说明

### 基本配置
```json
{
  "enabled": true,                    // 是否启用模块
  "searchRadius": 10,                 // 搜索容器的半径
  "processingIntervalTicks": 60,      // 处理间隔（tick）
  "enableSmartClassification": true,  // 是否启用智能分类
  "defaultCategory": "misc"           // 默认分类名称
}
```

### 源箱子配置
```json
{
  "chestLocations": [
    {"x": 100, "y": 64, "z": 200},
    {"x": 105, "y": 64, "z": 200}
  ]
}
```

### 存储位置配置
```json
{
  "categoryStorageMap": {
    "tools": {"x": 110, "y": 64, "z": 200, "type": "shulker"},
    "weapons_armor": {"x": 115, "y": 64, "z": 200, "type": "chest"},
    "building_blocks": {"x": 120, "y": 64, "z": 200, "type": "auto"},
    "food": {"x": 125, "y": 64, "z": 200, "type": "auto"}
  }
}
```

### 自定义分类配置
```json
{
  "customCategories": {
    "precious_items": ["diamond", "emerald", "netherite_ingot"],
    "farming_tools": ["hoe", "shears", "bucket"],
    "my_favorites": ["elytra", "totem_of_undying"]
  }
}
```

## 命令使用

### 基本命令
```
itemsorter enable          # 启用模块
itemsorter disable         # 禁用模块
itemsorter status          # 查看模块状态
itemsorter help            # 显示帮助信息
```

### 源箱子管理
```
itemsorter addchest <x> <y> <z>     # 添加源箱子坐标
itemsorter removechest <index>      # 移除源箱子坐标
itemsorter listchests               # 列出所有源箱子坐标
```

### 存储位置管理
```
itemsorter setstorage <category> <x> <y> <z> [type]  # 设置分类存储位置
itemsorter liststorage                               # 列出所有存储位置配置
```

存储类型说明：
- `chest`: 指定位置必须是箱子
- `shulker`: 指定位置必须是潜影盒
- `auto`: 自动搜索附近最合适的容器（默认）

### 分类管理
```
itemsorter addcategory <category> <item1> [item2] ...  # 添加自定义分类
itemsorter removecategory <category>                   # 移除自定义分类
itemsorter listcategories                              # 列出所有分类
```

### 容器搜索
```
itemsorter search [radius]  # 搜索附近的容器
```

## 预定义分类

模块包含以下预定义分类，基于ItemRegistry.java中的所有物品ID：

### 1. 工具类 (tools)
- 各种材质的铲、镐、斧、锄（木、石、铁、金、钻石、下界合金）
- 钓鱼竿、打火石、剪刀、指南针、时钟、望远镜、刷子等

### 2. 武器装备类 (weapons_armor)
- 各种材质的剑（木、石、铁、金、钻石、下界合金）
- 远程武器（弓、弩、三叉戟）
- 各种材质的盔甲（皮革、锁链、铁、金、钻石、下界合金）
- 盾牌、海龟壳

### 3. 大宗建筑材料类 (bulk_building)
- 基础石材（石头、圆石、深板岩、花岗岩等）
- 泥土类（泥土、砂土、草方块、粘土等）
- 沙石类（沙子、砂岩、沙砾等）
- 原木和木板（所有种类）
- 下界和末地基础材料

### 4. 装饰建筑材料类 (decorative_blocks)
- 所有颜色的羊毛（16种颜色）
- 所有颜色的混凝土（16种颜色）
- 所有颜色的陶瓦（16种颜色）
- 各种玻璃（普通、遮光、染色玻璃）
- 建筑构件（楼梯、台阶、栅栏、门等）

### 5. 食物类 (food)
- 基础食物（苹果、面包、曲奇、蛋糕等）
- 肉类（各种生肉和熟肉）
- 鱼类（鳕鱼、鲑鱼、热带鱼等）
- 蔬菜水果（土豆、胡萝卜、浆果等）
- 汤类和其他食物

### 6. 矿物资源类 (ores_minerals)
- 基础矿物（煤炭、铁锭、铜锭、金锭等）
- 宝石（钻石、绿宝石、青金石、红石、石英）
- 高级材料（下界合金锭、远古残骸等）
- 各种碎片和粒

### 7. 红石电路类 (redstone)
- 基础红石（红石粉、红石火把、红石块、红石灯）
- 逻辑元件（中继器、比较器、侦测器、标靶）
- 机械装置（活塞、发射器、漏斗等）
- 传感器和开关（各种按钮、压力板等）

### 8. 药水类 (potions)
- 各种药水（普通、喷溅、滞留）
- 相关物品（玻璃瓶、龙息、经验瓶等）

### 9. 附魔书类 (enchanted_books)
- 所有附魔书

### 10. 独特物品类 (unique_items)
- 特殊装备（鞘翅、不死图腾、下界之星、龙蛋等）
- 烟花（烟花火箭、烟花之星）
- 试炼相关（试炼钥匙、宝库、狼牙棒等）
- 陶片（所有种类的陶片）
- 刷怪蛋（所有生物的刷怪蛋）

### 11. 音乐唱片类 (music_discs)
- 所有音乐唱片（13、cat、blocks、chirp等）

### 12. 垃圾类 (trash)
- 低价值物品（腐肉、蜘蛛眼、毒马铃薯等）
- 植物废料（枯萎灌木、草、海草等）
- 动物产品（骨头、火药、线、羽毛等）
- 种子类（小麦种子、甜菜种子等）

### 13. 杂项类 (misc)
- 未分类的其他物品

## 智能分类规则

模块使用多层分类逻辑：

1. **用户自定义分类优先**：首先检查物品是否在自定义分类中
2. **智能属性分类**：基于物品的工具标签、名称模式等进行分类
3. **默认分类**：无法分类的物品归入默认分类

### 智能分类特性
- 基于工具类型自动分类（剑→武器，镐→工具）
- 名称模式匹配（包含"helmet"→装备）
- 附魔物品特殊处理
- 稀有度评分系统

## 使用流程

### 1. 初始设置
```bash
# 启用模块
itemsorter enable

# 添加源箱子（存放待分类物品的箱子）
itemsorter addchest 100 64 200
itemsorter addchest 105 64 200

# 设置各分类的存储位置
itemsorter setstorage tools 110 64 200 chest
itemsorter setstorage weapons_armor 115 64 200 chest
itemsorter setstorage bulk_building 120 64 200 auto
itemsorter setstorage decorative_blocks 125 64 200 auto
itemsorter setstorage unique_items 130 64 200 shulker
itemsorter setstorage music_discs 135 64 200 shulker
```

### 2. 自定义分类（可选）
```bash
# 添加自定义分类
itemsorter addcategory precious_items diamond emerald netherite_ingot
itemsorter setstorage precious_items 130 64 200 shulker
```

### 3. 运行
模块会自动按配置的间隔运行：
1. 从源箱子中提取所有物品到玩家库存
2. 对库存中的物品进行分类
3. 将每个分类的物品存储到对应的容器中
4. 等待下一个处理周期

### 4. 监控
```bash
# 查看模块状态
itemsorter status

# 搜索附近容器
itemsorter search 15
```

## 注意事项

1. **容器准备**：确保目标存储位置有足够的容器空间
2. **权限要求**：机器人需要有打开容器和移动物品的权限
3. **网络延迟**：在高延迟环境下可能需要调整处理间隔
4. **容器冲突**：避免多个模块同时操作相同的容器
5. **备份配置**：重要的分类配置建议定期备份

## 故障排除

### 常见问题

1. **模块不工作**
   - 检查 `itemsorter status` 确认模块已启用
   - 确认已配置源箱子坐标
   - 检查机器人是否在线且有权限

2. **物品分类错误**
   - 检查自定义分类配置
   - 确认智能分类是否启用
   - 查看日志了解分类过程

3. **容器无法打开**
   - 确认容器坐标正确
   - 检查容器是否被其他玩家占用
   - 验证机器人与容器的距离

4. **处理速度慢**
   - 调整 `processingIntervalTicks` 参数
   - 减少搜索半径
   - 优化存储位置配置

### 日志信息
模块会输出详细的日志信息，包括：
- 处理进度和状态变更
- 物品分类结果
- 容器操作结果
- 错误和警告信息

通过查看日志可以了解模块的运行状态和排查问题。

## 扩展功能

模块设计为可扩展的，未来可以添加：
- 更多容器类型支持
- 基于物品数量的智能分配
- 分类优先级系统
- 批量配置导入/导出
- 图形化配置界面
