
- Ingredient slot: 3 (top right)
- Fuel slot: 4 (top left, Blaze Powder)

Now let me design the module.

Let me also check if there are any potion-related data structures in the codebase.

For the brewing process:

1. Put blaze powder in slot 4 (fuel)
2. Put base potions (water bottles) in slots 0, 1, 2
3. Put ingredient in slot 3
4. Wait for brewing to complete (~400 ticks / 20 seconds)
5. The potions in slots 0, 1, 2 are now brewed
6. For multi-step recipes, put the brewed potions back and add next ingredient

The brewing progress can be detected by checking if the item in slot 3 (ingredient) is consumed (becomes empty).

Now, the key challenge is detecting when brewing is complete. In MC protocol, the server sends window item updates when brewing completes. We can:

1. Wait a fixed time (200 ticks = 10 seconds)
2. Check if the ingredient slot (slot 3) is empty
3. Check if the fuel slot has decreased

Option 2 is more reliable. After placing the ingredient, we wait until the ingredient slot becomes empty.

Let me also think about the recipe system. The user wants to brew specific potions. I'll define recipes as:

- A list of ingredient item IDs to apply in sequence
- Starting from water bottles

For example, a Potion of Healing recipe:

1. Water Bottle + Nether Wart = Awkward Potion
2. Awkward Potion + Glistering Melon Slice = Potion of Healing

So the recipe would be: ["nether_wart", "glistering_melon_slice"]

For the config, I'll have:

- brewingStandPos: BlockPos of the brewing stand
- sourceChests: List<BlockPos> for material chests
- resultChest: BlockPos for output
- recipes: List of recipe definitions
- enabled: boolean

Let me now write the module. I'll follow the patterns from AutoCraftModule and AutoEnchantModule.

Let me also check if there are any potion-related utilities in the codebase.

Actually, let me also look at how the brewing stand is opened - can we right-click it directly?

Let me check if there's a way to detect the brewing stand's state (brewing or not). In MC protocol, the brewing stand has a "brew time" property that indicates the progress. But I'm not sure if the proxy can read this.

For simplicity, I'll use a timer-based approach:

1. Place ingredients
2. Wait for the ingredient to be consumed (check container slots)
3. Or wait a fixed time (200 ticks)

Let me now design the full module structure:

public class AutoBrewerModule extends BaseModule {                                                                                                                                                                                                                                                                    
// States
enum State {                                                                                                                                                                                                                                                                                                      
COLLECT_INGREDIENTS,
OPEN_SOURCE_CHEST,                                                                                                                                                                                                                                                                                            
AWAIT_SOURCE_CHEST,                                                                                                                                                                                                                                                                                           
WITHDRAW_MATERIALS,                                                                                                                                                                                                                                                                                           
AWAIT_WITHDRAW,
MOVE_TO_BREWING_STAND,                                                                                                                                                                                                                                                                                        
AWAIT_BREWING_STAND,
LOAD_FUEL,                                                                                                                                                                                                                                                                                                    
AWAIT_FUEL_LOADED,
LOAD_POTIONS,                                                                                                                                                                                                                                                                                                 
AWAIT_POTIONS_LOADED,                                                                                                                                                                                                                                                                                         
LOAD_INGREDIENT,                                                                                                                                                                                                                                                                                              
AWAIT_INGREDIENT_LOADED,
BREWING_WAIT,                                                                                                                                                                                                                                                                                                 
COLLECT_POTIONS,                                                                                                                                                                                                                                                                                              
AWAIT_COLLECT_COMPLETE,                                                                                                                                                                                                                                                                                       
STORE_RESULT,
AWAIT_STORE_RESULT,                                                                                                                                                                                                                                                                                           
REST                                                                                                                                                                                                                                                                                                          
}

      // Recipe definition                                                                                                                                                                                                                                                                                              
      public static class BrewRecipe {
          public String name;                                                                                                                                                                                                                                                                                           
          public List<String> ingredients; // in order                                                                                                                                                                                                                                                                  
      }                                                                                                                                                                                                                                                                                                                 
}

Wait, I need to think about this more carefully. The brewing stand has specific slots:

- Slots 0-2: Potion bottles (3 slots)
- Slot 3: Ingredient (1 slot)
- Slot 4: Fuel (Blaze Powder, 1 slot)

The brewing process:

1. Put blaze powder in slot 4
2. Put water bottles in slots 0-2
3. Put first ingredient in slot 3
4. Wait for brewing (~200 ticks)
5. Take out brewed potions from slots 0-2
6. If multi-step recipe: put brewed potions back in slots 0-2, add next ingredient
7. Repeat until all ingredients applied

Let me also think about how to handle the container interaction. The brewing stand container has:
The register function for BREWING_STAND has 5 as the last parameter, which is the total slots. So the brewing stand has 5 slots (0-4).

The player inventory starts at slot 5. The container total size is 5 + 36 = 41.

For depositing items into the brewing stand, I need to use specific slot indices:
- Slot 3: Ingredient
- Slot 4: Fuel

I can't use InventoryActionMacros.deposit() because that would put items in the first available slot. I need to use specific slot placement actions.

Let me check what inventory actions are available in the codebase. I need something like "click on inventory slot and click on container slot" to move items.

Let me look at the inventory action system.



AutoBrewer 模块实现

创建的文件

1. AutoBrewerConfig.java - 配置类
   - enabled - 模块开关
   - brewingStand - 酿造台位置
   - sourceChests - 原料箱列表
   - resultChest - 成品箱位置
   - recipes - 配方列表（支持多步酿造）
   - brewWaitTicks - 酿造等待时间（默认420 tick ≈ 21秒）
   - debugMode - 调试模式
2. AutoBrewerModule.java - 主模块
   - 13个状态的状态机
   - 酿造台槽位精确控制（使用 ClickItem 左键操作）
   - 支持多步配方（如：水瓶 → 粗制药水 → 最终药水）
   - 材料自动从原料箱提取
3. AutoBrewerCommand.java - 命令类

酿造流程

1. GATHER_MATERIALS → 从原料箱提取水瓶、烈焰粉、材料
2. OPEN_BREWING_STAND → 打开酿造台
3. LOAD_ITEMS → 加载物品到指定槽位
    - 槽位4: 烈焰粉（燃料）
    - 槽位0-2: 水瓶
    - 槽位3: 材料
4. BREWING_WAIT → 等待酿造完成（固定时间）
5. COLLECT_POTIONS → 取出酿造好的药水
6. NEXT_STEP → 检查是否还有下一步
7. STORE_RESULT → 存放到成品箱

命令使用示例

# 启用模块
/autobrewer on

# 设置酿造台位置
/autobrewer brewingStand 100 64 200

# 添加原料箱
/autobrewer sourceChests add 100 64 195

# 设置成品箱
/autobrewer resultChest 100 64 210

# 添加治疗药水配方（两步：水瓶→粗制药水→治疗药水）
/autobrewer recipes add "治疗药水" nether_wart,glistering_melon_slice

# 查看配置
/autobrewer

支持的材料名称

nether_wart, sugar, spider_eye, fermented_spider_eye, ghast_tear, magma_cream, glistering_melon_slice, blaze_powder, golden_carrot, dragon_breath, glowstone_dust, redstone, gunpowder, phantom_membrane
