# 物品分类模块 - 一物一箱系统

## 设计理念

这个物品分类系统遵循"**一种物品一个箱子**"的原则：

1. **单独存储** - 每种物品都有自己专门的存储箱子
2. **颜色合并** - 不同颜色的同种物品放同一个箱子（如所有颜色羊毛放一个箱子）
3. **特殊合并** - 只有稀有/特殊物品才放在一起（如刷怪蛋、音乐唱片、陶片）

## 分类系统

### 🥕 单独物品分类（每种一个箱子）

#### 农作物和食物
- `gunpowder` - 火药专用箱子
- `sugar_cane` - 甘蔗专用箱子
- `wheat` - 小麦专用箱子
- `carrot` - 胡萝卜专用箱子
- `potato` - 土豆专用箱子（包括烤土豆、毒马铃薯）
- `beetroot` - 甜菜根专用箱子
- `apple` - 苹果专用箱子（包括金苹果、附魔金苹果）
- `bread` - 面包专用箱子

#### 肉类
- `beef` - 牛肉专用箱子（生牛肉+熟牛肉）
- `porkchop` - 猪肉专用箱子（生猪肉+熟猪肉）
- `chicken` - 鸡肉专用箱子（生鸡肉+熟鸡肉）
- `mutton` - 羊肉专用箱子（生羊肉+熟羊肉）
- `rabbit` - 兔肉专用箱子（生兔肉+熟兔肉）
- `cod` - 鳕鱼专用箱子（生鳕鱼+熟鳕鱼）
- `salmon` - 鲑鱼专用箱子（生鲑鱼+熟鲑鱼）

#### 矿物资源
- `coal` - 煤炭专用箱子（煤炭+木炭）
- `iron` - 铁专用箱子（铁锭+粗铁+铁粒）
- `gold` - 金专用箱子（金锭+粗金+金粒）
- `copper` - 铜专用箱子（铜锭+粗铜）
- `diamond` - 钻石专用箱子
- `emerald` - 绿宝石专用箱子
- `redstone` - 红石专用箱子
- `lapis_lazuli` - 青金石专用箱子
- `quartz` - 石英专用箱子
- `netherite` - 下界合金专用箱子（锭+碎片+远古残骸）

#### 建筑材料
- `stone` - 石头专用箱子（石头+圆石）
- `dirt` - 泥土专用箱子（泥土+砂土+草方块）
- `sand` - 沙子专用箱子（沙子+红沙）
- `gravel` - 沙砾专用箱子
- `cobblestone` - 圆石专用箱子（圆石+苔石圆石）

#### 原木（每种木材单独存储）
- `oak_log` - 橡木原木箱子
- `spruce_log` - 云杉原木箱子
- `birch_log` - 白桦原木箱子
- `jungle_log` - 丛林原木箱子
- `acacia_log` - 金合欢原木箱子
- `dark_oak_log` - 深色橡木原木箱子

#### 木板（每种木材单独存储）
- `oak_planks` - 橡木木板箱子
- `spruce_planks` - 云杉木板箱子
- `birch_planks` - 白桦木板箱子
- `jungle_planks` - 丛林木板箱子
- `acacia_planks` - 金合欢木板箱子
- `dark_oak_planks` - 深色橡木木板箱子

### 🎨 颜色变种合并（同种不同颜色放一起）

- `wool` - 羊毛箱子（所有16种颜色的羊毛）
- `concrete` - 混凝土箱子（所有16种颜色的混凝土）
- `terracotta` - 陶瓦箱子（所有16种颜色的陶瓦）
- `glass` - 玻璃箱子（普通玻璃+遮光玻璃+所有颜色染色玻璃）
- `carpet` - 地毯箱子（所有16种颜色的地毯）
- `bed` - 床箱子（所有16种颜色的床）

### ⭐ 特殊稀有物品合并

- `spawn_eggs` - 刷怪蛋箱子（所有生物的刷怪蛋）
- `music_discs` - 音乐唱片箱子（所有音乐唱片）
- `pottery_sherds` - 陶片箱子（所有陶片）
- `ultra_rare` - 超稀有物品箱子（龙蛋、鞘翅、图腾、下界之星、信标、潮涌核心）
- `enchanted_books` - 附魔书箱子
- `potions` - 药水箱子（所有类型药水）
- `trash` - 垃圾箱子（低价值物品）

## 配置示例

### 基础配置
```bash
# 启用模块
itemsorter enable

# 添加源箱子
itemsorter addchest 100 64 200
itemsorter addchest 105 64 200
```

### 单独物品存储配置
```bash
# 农作物
itemsorter setstorage gunpowder 100 64 210 auto
itemsorter setstorage sugar_cane 101 64 210 auto
itemsorter setstorage wheat 102 64 210 auto
itemsorter setstorage carrot 103 64 210 auto
itemsorter setstorage potato 104 64 210 auto

# 肉类
itemsorter setstorage beef 110 64 210 auto
itemsorter setstorage porkchop 111 64 210 auto
itemsorter setstorage chicken 112 64 210 auto

# 矿物
itemsorter setstorage coal 120 64 210 auto
itemsorter setstorage iron 121 64 210 auto
itemsorter setstorage gold 122 64 210 auto
itemsorter setstorage diamond 124 64 210 auto
```

### 颜色变种存储配置
```bash
# 所有颜色的羊毛放一个箱子
itemsorter setstorage wool 160 64 210 auto

# 所有颜色的混凝土放一个箱子
itemsorter setstorage concrete 161 64 210 auto

# 所有颜色的玻璃放一个箱子
itemsorter setstorage glass 163 64 210 auto
```

### 特殊物品存储配置
```bash
# 稀有物品用潜影盒
itemsorter setstorage ultra_rare 170 64 210 shulker
itemsorter setstorage music_discs 171 64 210 shulker
itemsorter setstorage spawn_eggs 172 64 210 shulker
itemsorter setstorage enchanted_books 174 64 210 shulker
```

## 仓库布局建议

### 区域规划
```
农作物区域 (100-109, 64, 210):
[100] 火药  [101] 甘蔗  [102] 小麦  [103] 胡萝卜  [104] 土豆
[105] 甜菜  [106] 苹果  [107] 面包  [108] 预留   [109] 预留

肉类区域 (110-119, 64, 210):
[110] 牛肉  [111] 猪肉  [112] 鸡肉  [113] 羊肉  [114] 兔肉
[115] 鳕鱼  [116] 鲑鱼  [117] 预留  [118] 预留  [119] 预留

矿物区域 (120-129, 64, 210):
[120] 煤炭  [121] 铁    [122] 金    [123] 铜    [124] 钻石
[125] 绿宝石 [126] 红石  [127] 青金石 [128] 石英  [129] 下界合金

建筑材料区域 (130-139, 64, 210):
[130] 石头  [131] 泥土  [132] 沙子  [133] 沙砾  [134] 圆石
[135] 预留  [136] 预留  [137] 预留  [138] 预留  [139] 预留

原木区域 (140-149, 64, 210):
[140] 橡木原木    [141] 云杉原木    [142] 白桦原木
[143] 丛林原木    [144] 金合欢原木  [145] 深色橡木原木

木板区域 (150-159, 64, 210):
[150] 橡木木板    [151] 云杉木板    [152] 白桦木板
[153] 丛林木板    [154] 金合欢木板  [155] 深色橡木木板

颜色变种区域 (160-169, 64, 210):
[160] 羊毛  [161] 混凝土  [162] 陶瓦  [163] 玻璃  [164] 地毯  [165] 床

特殊物品区域 (170-179, 64, 210) - 使用潜影盒:
[170] 超稀有  [171] 音乐唱片  [172] 刷怪蛋  [173] 陶片  [174] 附魔书
```

## 使用优势

### ✅ 优点
1. **精确分类** - 每种物品都有固定位置，易于查找
2. **扩展性强** - 可以轻松添加新物品的专用存储
3. **管理简单** - 不需要记忆复杂的分类规则
4. **效率高** - 直接知道每种物品的存储位置

### ⚠️ 注意事项
1. **空间需求大** - 需要大量箱子和存储空间
2. **初期配置复杂** - 需要为每种物品配置存储位置
3. **箱子管理** - 需要合理规划箱子布局

## 快速配置脚本

```bash
#!/bin/bash
# 一物一箱快速配置脚本

# 启用模块
itemsorter enable

# 添加源箱子
itemsorter addchest 100 64 200

# 农作物配置
itemsorter setstorage gunpowder 100 64 210 auto
itemsorter setstorage sugar_cane 101 64 210 auto
itemsorter setstorage wheat 102 64 210 auto
itemsorter setstorage carrot 103 64 210 auto
itemsorter setstorage potato 104 64 210 auto
itemsorter setstorage beetroot 105 64 210 auto
itemsorter setstorage apple 106 64 210 auto
itemsorter setstorage bread 107 64 210 auto

# 肉类配置
itemsorter setstorage beef 110 64 210 auto
itemsorter setstorage porkchop 111 64 210 auto
itemsorter setstorage chicken 112 64 210 auto
itemsorter setstorage mutton 113 64 210 auto
itemsorter setstorage rabbit 114 64 210 auto
itemsorter setstorage cod 115 64 210 auto
itemsorter setstorage salmon 116 64 210 auto

# 矿物配置
itemsorter setstorage coal 120 64 210 auto
itemsorter setstorage iron 121 64 210 auto
itemsorter setstorage gold 122 64 210 auto
itemsorter setstorage copper 123 64 210 auto
itemsorter setstorage diamond 124 64 210 auto
itemsorter setstorage emerald 125 64 210 auto
itemsorter setstorage redstone 126 64 210 auto
itemsorter setstorage lapis_lazuli 127 64 210 auto
itemsorter setstorage quartz 128 64 210 auto
itemsorter setstorage netherite 129 64 210 auto

# 颜色变种配置
itemsorter setstorage wool 160 64 210 auto
itemsorter setstorage concrete 161 64 210 auto
itemsorter setstorage terracotta 162 64 210 auto
itemsorter setstorage glass 163 64 210 auto

# 特殊物品配置（潜影盒）
itemsorter setstorage ultra_rare 170 64 210 shulker
itemsorter setstorage music_discs 171 64 210 shulker
itemsorter setstorage spawn_eggs 172 64 210 shulker
itemsorter setstorage enchanted_books 174 64 210 shulker

echo "一物一箱配置完成！"
```

这个系统确保每种物品都有自己的专用存储位置，同时合理合并了颜色变种和特殊稀有物品，实现了高效的物品管理。
