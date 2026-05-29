# 物品分类模块示例配置

这是一个完整的物品分类模块配置示例，展示如何设置所有分类的存储位置。

## 基础配置命令

```bash
# 启用模块
itemsorter enable

# 添加源箱子（存放待分类物品的箱子）
itemsorter addchest 100 64 200
itemsorter addchest 105 64 200
itemsorter addchest 110 64 200
```

## 存储位置配置

### 高价值物品（使用潜影盒）
```bash
# 独特物品 - 最稀有的物品
itemsorter setstorage unique_items 150 64 200 shulker

# 音乐唱片 - 收藏品
itemsorter setstorage music_discs 155 64 200 shulker

# 附魔书 - 重要的附魔资源
itemsorter setstorage enchanted_books 160 64 200 shulker

# 矿物资源 - 贵重材料
itemsorter setstorage ores_minerals 165 64 200 shulker
```

### 常用物品（使用箱子）
```bash
# 工具 - 日常使用
itemsorter setstorage tools 170 64 200 chest

# 武器装备 - 战斗装备
itemsorter setstorage weapons_armor 175 64 200 chest

# 食物 - 生存必需品
itemsorter setstorage food 180 64 200 chest

# 药水 - 增益物品
itemsorter setstorage potions 185 64 200 chest

# 红石电路 - 机械装置
itemsorter setstorage redstone 190 64 200 chest
```

### 大宗物品（自动搜索容器）
```bash
# 大宗建筑材料 - 大量使用的基础材料
itemsorter setstorage bulk_building 200 64 200 auto

# 装饰建筑材料 - 彩色装饰方块
itemsorter setstorage decorative_blocks 205 64 200 auto

# 垃圾物品 - 低价值物品
itemsorter setstorage trash 210 64 200 auto

# 杂项 - 未分类物品
itemsorter setstorage misc 215 64 200 auto
```

## 自定义分类示例

### 创建贵重物品分类
```bash
# 添加最贵重的物品到特殊分类
itemsorter addcategory precious dragon_egg elytra totem_of_undying nether_star mace heavy_core
itemsorter setstorage precious 220 64 200 shulker
```

### 创建农业分类
```bash
# 农业相关物品
itemsorter addcategory farming wheat_seeds carrot potato beetroot_seeds bone_meal
itemsorter setstorage farming 225 64 200 chest
```

### 创建建筑工具分类
```bash
# 建筑时常用的工具和材料
itemsorter addcategory building_tools scaffolding ladder torch lantern
itemsorter setstorage building_tools 230 64 200 chest
```

### 创建PVP装备分类
```bash
# PVP相关的装备
itemsorter addcategory pvp_gear diamond_sword diamond_helmet diamond_chestplate diamond_leggings diamond_boots shield bow crossbow arrow
itemsorter setstorage pvp_gear 235 64 200 shulker
```

## 完整配置脚本

将以下命令保存为脚本文件，一次性配置所有分类：

```bash
#!/bin/bash
# ItemSorter 完整配置脚本

# 启用模块
itemsorter enable

# 添加源箱子
itemsorter addchest 100 64 200
itemsorter addchest 105 64 200
itemsorter addchest 110 64 200

# 高价值物品存储（潜影盒）
itemsorter setstorage unique_items 150 64 200 shulker
itemsorter setstorage music_discs 155 64 200 shulker
itemsorter setstorage enchanted_books 160 64 200 shulker
itemsorter setstorage ores_minerals 165 64 200 shulker

# 常用物品存储（箱子）
itemsorter setstorage tools 170 64 200 chest
itemsorter setstorage weapons_armor 175 64 200 chest
itemsorter setstorage food 180 64 200 chest
itemsorter setstorage potions 185 64 200 chest
itemsorter setstorage redstone 190 64 200 chest

# 大宗物品存储（自动搜索）
itemsorter setstorage bulk_building 200 64 200 auto
itemsorter setstorage decorative_blocks 205 64 200 auto
itemsorter setstorage trash 210 64 200 auto
itemsorter setstorage misc 215 64 200 auto

# 自定义分类
itemsorter addcategory precious dragon_egg elytra totem_of_undying nether_star
itemsorter setstorage precious 220 64 200 shulker

itemsorter addcategory farming wheat_seeds carrot potato beetroot_seeds bone_meal
itemsorter setstorage farming 225 64 200 chest

echo "物品分类模块配置完成！"
echo "使用 'itemsorter status' 查看配置状态"
```

## 存储布局建议

### 仓库区域布局
```
Y=64层布局示例：

[150,64,200] 独特物品(潜影盒)    [170,64,200] 工具(箱子)         [200,64,200] 大宗建材(自动)
[155,64,200] 音乐唱片(潜影盒)    [175,64,200] 武器装备(箱子)     [205,64,200] 装饰方块(自动)  
[160,64,200] 附魔书(潜影盒)      [180,64,200] 食物(箱子)         [210,64,200] 垃圾(自动)
[165,64,200] 矿物(潜影盒)        [185,64,200] 药水(箱子)         [215,64,200] 杂项(自动)
                                [190,64,200] 红石(箱子)         

自定义分类区域：
[220,64,200] 贵重物品(潜影盒)
[225,64,200] 农业物品(箱子)
[230,64,200] 建筑工具(箱子)
```

### 容器类型选择建议

1. **潜影盒** - 用于稀有、贵重物品
   - 独特物品、音乐唱片、附魔书、矿物资源
   - 优点：便携、高价值物品集中管理

2. **箱子** - 用于常用物品
   - 工具、武器装备、食物、药水、红石
   - 优点：容量大、访问方便

3. **自动搜索** - 用于大宗物品
   - 建筑材料、装饰方块、垃圾、杂项
   - 优点：灵活、可以利用现有容器

## 使用技巧

### 1. 分阶段配置
```bash
# 第一阶段：配置基础分类
itemsorter setstorage tools 170 64 200 chest
itemsorter setstorage food 180 64 200 chest
itemsorter setstorage trash 210 64 200 auto

# 第二阶段：配置高价值分类
itemsorter setstorage unique_items 150 64 200 shulker
itemsorter setstorage ores_minerals 165 64 200 shulker

# 第三阶段：配置自定义分类
itemsorter addcategory my_favorites elytra totem_of_undying
```

### 2. 测试配置
```bash
# 查看当前状态
itemsorter status

# 搜索附近容器
itemsorter search 15

# 列出所有存储配置
itemsorter liststorage

# 列出所有分类
itemsorter listcategories
```

### 3. 调整优化
```bash
# 根据实际使用情况调整存储位置
itemsorter setstorage tools 175 64 200 chest

# 添加新的物品到现有分类
itemsorter addcategory farming pumpkin_seeds melon_seeds

# 创建临时分类用于特殊需求
itemsorter addcategory temp_storage diamond_pickaxe diamond_sword
itemsorter setstorage temp_storage 240 64 200 shulker
```

这个配置示例涵盖了所有13个预定义分类，并提供了自定义分类的示例。用户可以根据自己的需求调整坐标和容器类型。
