# 自动放凋灵模块使用说明

## 功能概述
自动放凋灵模块会在配置的坐标位置依次放置灵魂沙，等待1tick后继续下一个位置。模块会自动检查当前场上存在的凋零数量，当达到最大数量（默认6只）时停止放置，直到凋灵数量归零后继续。

## 基本命令

### 启用/禁用模块
```
/autowither on    # 启用自动放凋灵
/autowither off   # 禁用自动放凋灵
```

### 管理放置位置
```
/autowither addPosition <x> <y> <z>    # 添加放置位置
/autowither removePosition <index>    # 删除指定索引的位置
/autowither listPositions             # 查看所有位置
/autowither clearPositions            # 清空所有位置
```

### 配置箱子
```
/autowither soulSandChest <x> <y> <z> # 设置灵魂沙箱子位置
/autowither minSoulSand <数量>        # 设置最少保留的灵魂沙数量（默认24）
```

### 配置参数
```
/autowither maxWithers <数量>         # 设置最大同时存在的凋零数量（默认6）
/autowither resetRound                # 重置轮数计数器
/autowither actionDelay <ticks>       # 设置每次放置后的延迟时间（默认1tick）
/autowither checkInterval <ticks>     # 设置检查凋零数量的间隔（默认5ticks）
/autowither debug <true/false>        # 启用/禁用调试模式
```

## 工作流程

1. **等待阶段**：模块定期检查当前场上的凋零数量和灵魂沙数量
2. **检查凋零**：如果场上还有凋零，等待它们全部消失
3. **检查轮数**：如果已达到最大轮数（等于最大凋零数量），强制等待
4. **检查灵魂沙**：如果灵魂沙数量少于设定值（默认24），自动从配置的箱子获取
5. **放置阶段**：依次在配置的坐标位置放置灵魂沙
6. **冷却阶段**：每次放置后等待配置的延迟时间
7. **循环阶段**：继续下一个位置，直到所有位置都使用过一遍（完成一轮）
8. **轮数计数**：每完成一轮（放完一只凋灵），轮数计数器+1

## 使用示例

1. 首先添加几个放置位置：
```
/autowither addPosition 100 64 200
/autowither addPosition 105 64 200
/autowither addPosition 110 64 200
```

2. 设置灵魂沙箱子位置：
```
/autowither soulSandChest 100 65 200
```

3. 设置最少保留的灵魂沙数量：
```
/autowither minSoulSand 32
```

4. 查看配置的位置：
```
/autowither listPositions
```

5. 设置最大凋零数量：
```
/autowither maxWithers 3
```

6. 启用模块：
```
/autowither on
```

7. 查看当前轮数和重置：
```
/autowither             # 查看配置，包括当前轮数
/autowither resetRound  # 重置轮数计数器
```

## 注意事项

- 模块会自动检查灵魂沙数量，不足时从配置的箱子获取
- 确保配置的灵魂沙箱子中有足够的灵魂沙
- 放置位置需要是空气方块才能成功放置
- 模块会自动循环使用配置的位置列表
- 箱子支持普通箱子、桶和潜影盒
- 轮数计数器在每完成一轮（放完一只凋灵）后自动增加
- 当达到最大轮数（等于maxWithers设置）时，模块会强制进入等待状态
- 启用调试模式可以看到详细的执行日志，包括轮数信息