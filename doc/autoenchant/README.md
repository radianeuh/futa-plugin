# AutoEnchant 自动附魔模块 使用文档

## 概述

AutoEnchant 是一个全自动附魔模块，能够自动收集经验、从箱子中取出钻石装备和附魔书、通过铁砧进行附魔，并将成品存放到指定箱子。

**支持的装备类型：**

| 装备 | 英文命令参数 | 默认附魔策略 |
|------|-------------|-------------|
| 钻石剑 | `sword` | 横扫之刃 III + 抢夺 III + 锋利 V + 火焰附加 II + 耐久 III + 击退 II + 经验修补 |
| 钻石镐 | `pickaxe` | 效率 V + 精准采集 I + 耐久 III + 经验修补 |
| 钻石头盔 | `helmet` | 水下呼吸 III + 保护 IV + 耐久 III + 经验修补 + 水下速掘 I |
| 钻石胸甲 | `chestplate` | 保护 IV + 耐久 III + 经验修补 |
| 钻石护腿 | `leggings` | 爆炸保护 IV + 耐久 III + 经验修补 |
| 钻石靴子 | `boots` | 深海探索者 III + 摔落保护 IV + 保护 IV + 耐久 III + 经验修补 |
| 鞘翅 | `elytra` | 耐久 III + 经验修补 |
| 钻石斧 | `axe` | 效率 V + 耐久 III + 经验修补 |
| 钻石锄 | `hoe` | 效率 V + 耐久 III + 经验修补 |
| 锤 | `mace` | 穿透 I + 耐久 III + 经验修补 |

## 前置条件

1. **钻石装备**：需要附魔的钻石装备放在「装备箱子」中
2. **附魔书**：对应的附魔书放在「附魔书箱子」中
3. **经验**：铁砧附魔消耗经验等级，需要通过经验农场获取
4. **铁砧**：模块会在 bot 附近搜索铁砧（默认搜索半径 26 格）
5. **成品箱子**：附魔完成的装备存放位置

## 命令一览

命令前缀：`/autoenchant`

### 基础控制

| 命令 | 说明 |
|------|------|
| `/autoenchant on` | 启用模块 |
| `/autoenchant off` | 禁用模块 |

### 箱子配置

每个箱子类型都支持 `add`（添加）、`del`（删除）、`clear`（清空）、`list`（列表）操作。

#### 装备箱子（放待附魔的钻石装备）

```
/autoenchant equipmentChest add <x> <y> <z>   # 添加装备箱子
/autoenchant equipmentChest del <index>        # 删除指定索引的装备箱子
/autoenchant equipmentChest clear              # 清空所有装备箱子
/autoenchant equipmentChest list               # 查看所有装备箱子
```

#### 附魔书箱子（放附魔书）

```
/autoenchant enchantBookChest add <x> <y> <z>  # 添加附魔书箱子
/autoenchant enchantBookChest del <index>       # 删除指定索引的附魔书箱子
/autoenchant enchantBookChest clear             # 清空所有附魔书箱子
/autoenchant enchantBookChest list              # 查看所有附魔书箱子
```

#### 成品箱子（存放附魔完成的装备）

```
/autoenchant resultChest add <x> <y> <z>   # 添加成品箱子
/autoenchant resultChest del <index>        # 删除指定索引的成品箱子
/autoenchant resultChest clear              # 清空所有成品箱子
/autoenchant resultChest list               # 查看所有成品箱子
```

#### 失败箱子（附魔不完美的装备存放位置，可选）

```
/autoenchant failChest <x> <y> <z>   # 设置失败箱子位置
```

### 经验农场

```
/autoenchant xpFarm <x> <y> <z>   # 设置经验农场坐标
```

模块会自动判断 bot 是否有足够的经验等级（默认 ≥ 10 级），不足时自动前往经验农场。

### 铁砧搜索半径

```
/autoenchant searchRadius <radius>   # 设置铁砧搜索半径（1-50，默认 26）
```

### 附魔策略配置

每种装备可以独立配置需要附魔的附魔书列表（按附魔顺序）：

```
/autoenchant strategy <equipment> <enchants...>
```

**参数说明：**
- `equipment`：装备类型（`sword`、`pickaxe`、`helmet`、`chestplate`、`leggings`、`boots`、`elytra`、`axe`、`hoe`、`mace`）
- `enchants`：空格分隔的附魔名列表（英文 ID）

**示例：**

```
/autoenchant strategy sword sharpness fire_aspect unbreaking mending
/autoenchant strategy pickaxe efficiency silk_touch unbreaking mending
/autoenchant strategy helmet respiration protection unbreaking mending aqua_affinity
/autoenchant strategy boots depth_strider feather_falling protection unbreaking mending
```

**附魔名 ID 参考（常用）：**

| 中文名 | 英文 ID | 适用装备 |
|--------|---------|---------|
| 锋利 | `sharpness` | 剑、斧 |
| 横扫之刃 | `sweeping_edge` | 剑 |
| 抢夺 | `looting` | 剑 |
| 火焰附加 | `fire_aspect` | 剑 |
| 击退 | `knockback` | 剑 |
| 效率 | `efficiency` | 镐、斧、锄 |
| 精准采集 | `silk_touch` | 镐、斧、锄 |
| 耐久 | `unbreaking` | 所有装备 |
| 经验修补 | `mending` | 所有装备 |
| 保护 | `protection` | 盔甲 |
| 爆炸保护 | `blast_protection` | 盔甲 |
| 摔落保护 | `feather_falling` | 靴子 |
| 深海探索者 | `depth_strider` | 靴子 |
| 水下呼吸 | `respiration` | 头盔 |
| 水下速掘 | `aqua_affinity` | 头盔 |
| 穿透 | `breach` | 锤 |

### KillAura 联动

```
/autoenchant pauseKillAura true    # 附魔期间暂停 KillAura（默认开启）
/autoenchant pauseKillAura false   # 附魔期间保持 KillAura 启用
```

开启后，模块会在开始附魔操作时自动暂停 KillAura，完成后自动恢复。

### 调试模式

```
/autoenchant debug on    # 开启调试日志
/autoenchant debug off   # 关闭调试日志
```

开启后会输出详细的状态转换日志和附魔书缓存信息。

## 工作流程

模块启用后按以下流程自动执行：

```
┌─────────────────────────────────────────────────────────────┐
│  1. 收集经验                                                 │
│     检查经验等级 → 不足则前往 xpFarm → 等待达到 10 级          │
└──────────────────────────┬──────────────────────────────────┘
                           │ 经验足够
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  2. 获取装备                                                 │
│     优先检查玩家背包 → 没有则从装备箱子提取                     │
└──────────────────────────┬──────────────────────────────────┘
                           │ 找到装备
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  3. 获取附魔书                                               │
│     从附魔书箱子中查找并提取所需的附魔书                        │
└──────────────────────────┬──────────────────────────────────┘
                           │ 找到所有附魔书
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  4. 铁砧附魔                                                 │
│     装备放入铁砧 → 附魔书放入铁砧 → 取出附魔后的装备            │
│     （剑类有特殊的合并附魔书流程）                              │
└──────────────────────────┬──────────────────────────────────┘
                           │ 附魔完成
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  5. 检查结果                                                 │
│     还需要更多附魔 → 回到步骤 4 继续                           │
│     已经完美附魔 → 存放到成品箱子                              │
└─────────────────────────────────────────────────────────────┘
```

### 特殊流程

- **经验不足**：附魔过程中如果经验不够，会暂停附魔，先去收集经验
- **附魔书不足**：如果箱子中没有所需附魔书，会跳过当前装备，尝试下一个
- **重复附魔**：同一件装备最多尝试 10 次附魔，超过后跳过进入休息
- **剑类特殊处理**：钻石剑的附魔采用合并策略（先合并附魔书，再附魔到剑上），以减少铁砧惩罚

## 配置示例

一个完整的配置流程：

```
# 1. 添加装备箱子
/autoenchant equipmentChest add 100 64 200
/autoenchant equipmentChest add 100 64 205

# 2. 添加附魔书箱子
/autoenchant enchantBookChest add 110 64 200
/autoenchant enchantBookChest add 110 64 205

# 3. 添加成品箱子
/autoenchant resultChest add 120 64 200

# 4. 设置经验农场
/autoenchant xpFarm 105 64 210

# 5. 设置失败箱子（可选）
/autoenchant failChest 120 64 205

# 6. 调整铁砧搜索范围
/autoenchant searchRadius 10

# 7. 自定义附魔策略（可选）
/autoenchant strategy sword sharpness looting unbreaking mending

# 8. 启用模块
/autoenchant on
```

## 注意事项

1. **箱子排序**：附魔书箱子的顺序会影响搜索效率，建议把最常用的附魔书放在前面的箱子
2. **经验等级**：默认需要 ≥ 10 级经验才会开始附魔，铁砧操作会消耗经验
3. **铁砧惩罚**：同一装备多次使用铁砧会增加惩罚等级，剑类的合并策略可以减少惩罚
4. **装备识别**：模块只识别钻石装备（剑、镐、头盔、胸甲、护腿、靴子），不识别其他材质
5. **附魔书匹配**：附魔书按附魔 ID 精确匹配，需要与策略配置中的名称完全一致
6. **容器记忆**：模块会记住每个附魔书箱子中发现的附魔书类型，加速后续搜索
7. **并发安全**：模块使用优先级 800，会等待其他高优先级操作完成后才执行
8. **重命名**：每次铁砧附魔时会自动给装备随机分配一个中文名（来自预设名称库）

## 故障排查

| 现象 | 可能原因 | 解决方法 |
|------|---------|---------|
| 模块启用后立即进入休息 | 经验等级不足 10 级 | 确保有足够的经验，或配置 xpFarm |
| 找不到装备 | 装备箱子未配置或为空 | 检查 `equipmentChest list` |
| 找不到附魔书 | 附魔书箱子未配置或缺少所需附魔书 | 检查 `enchantBookChest list`，确认箱子中有对应附魔书 |
| 铁砧附魔失败 | 附近没有铁砧 | 增大 `searchRadius` 或在附近放置铁砧 |
| 附魔不完整 | 附魔书数量不足或策略配置不完整 | 检查策略配置，确保箱子中有所有需要的附魔书 |
| 装存到失败箱子 | 附魔未能达到目标 | 检查附魔书是否齐全，经验是否足够 |