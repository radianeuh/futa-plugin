package com.github.futa.module;

import com.github.futa.BaseModule;
import com.github.futa.config.BaseFinderConfig;
import com.github.rfresh2.EventConsumer;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.zenith.cache.data.entity.Entity;
import com.zenith.event.client.ClientBotTick;
import com.zenith.event.client.ClientConnectEvent;
import com.zenith.event.client.ClientDisconnectEvent;
import com.zenith.feature.pathfinder.util.BlockOptionalMetaLookup;
import com.zenith.feature.pathfinder.util.WorldScanner;
import com.zenith.mc.block.Block;
import com.zenith.mc.block.BlockPos;
import com.zenith.mc.block.BlockRegistry;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.zenith.Globals.CACHE;

/**
 * @see com.zenith.feature.pathfinder.util.WorldScanner
 */
public class BaseFinder extends BaseModule {
    private final BaseFinderConfig config = PLUGIN_CONFIG.baseFinder;
    // 使用 LRU 缓存，只保留最近扫描的 1000 个区块
    private final Set<String> scannedChunks = Collections.newSetFromMap(
            new LinkedHashMap<>(1000, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > 1000;
                }
            }
    );
    private final Queue<String> chunksToScan = new LinkedList<>();
    private int tickCounter = 0;
    private int entityScanTicks = 0;
    private int spamCooldownTicks = 0;

    // 预定义的 BlockOptionalMetaLookup
    private BlockOptionalMetaLookup portalFilter;
    private BlockOptionalMetaLookup shulkerFilter;
    private BlockOptionalMetaLookup customBlockFilter;
    private BlockOptionalMetaLookup combinedFilter;

    // 检测到的坐标列表（用于保存到文件）
    private final List<String> detectedLocations = new ArrayList<>();

    public BaseFinder() {
        initFilters();
    }

    private void initFilters() {

        // 传送门过滤器
        portalFilter = new BlockOptionalMetaLookup(
                BlockRegistry.NETHER_PORTAL
        );

        IntOpenHashSet blockStateIds = new IntOpenHashSet();

        for (int stateId = BlockRegistry.SHULKER_BOX.minStateId(); stateId <= BlockRegistry.BLACK_SHULKER_BOX.maxStateId(); stateId++) {
            blockStateIds.add(stateId);
        }

        // 潜影盒过滤器
        shulkerFilter = new BlockOptionalMetaLookup(blockStateIds);

        // 自定义方块过滤器
        updateCustomBlockFilter();

        // 合并所有过滤器
        updateCombinedFilter();
    }

    private void updateCombinedFilter() {
        IntOpenHashSet allBlockStateIds = new IntOpenHashSet();

        // 添加传送门方块ID
        for (int stateId = BlockRegistry.NETHER_PORTAL.minStateId(); stateId <= BlockRegistry.NETHER_PORTAL.maxStateId(); stateId++) {
            allBlockStateIds.add(stateId);
        }
        // 添加潜影盒方块ID
        for (int stateId = BlockRegistry.SHULKER_BOX.minStateId(); stateId <= BlockRegistry.BLACK_SHULKER_BOX.maxStateId(); stateId++) {
            allBlockStateIds.add(stateId);
        }

        // 添加自定义方块ID
        if (customBlockFilter != null) {
            allBlockStateIds.addAll(customBlockFilter.getBlockStateIds());
        }

        combinedFilter = new BlockOptionalMetaLookup(allBlockStateIds);
    }

    private void updateCustomBlockFilter() {
        Set<Block> blocks = new HashSet<>();
        for (String blockName : config.blockList) {
            Block block = BlockRegistry.REGISTRY.get(blockName);
            if (block != null) {
                blocks.add(block);
            }
        }
        if (!blocks.isEmpty()) {
            customBlockFilter = new BlockOptionalMetaLookup(blocks);
        } else {
            customBlockFilter = null;
        }
        // 更新合并过滤器
        updateCombinedFilter();
    }

    @Override
    public boolean enabledSetting() {
        return config.enabled;
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
                EventConsumer.of(ClientBotTick.class, this::onTick),
                EventConsumer.of(ClientConnectEvent.class, this::onConnect),
                EventConsumer.of(ClientDisconnectEvent.class, this::onDisconnect)
        );
    }

    @Override
    public void onEnable() {
        if (config.loadOnStart) {
            loadData();
        }
        info("BaseFinder enabled");
    }

    public java.util.List<String> getDetectedLocations() {
        return java.util.Collections.unmodifiableList(detectedLocations);
    }

    @Override
    public void onDisable() {
        if (config.saveToFile) {
            saveData();
        }
        scannedChunks.clear();
        chunksToScan.clear();
        detectedLocations.clear();
        info("BaseFinder disabled");
    }

    private void onTick(ClientBotTick event) {
        tickCounter++;
        entityScanTicks++;

        if (spamCooldownTicks > 0) {
            spamCooldownTicks--;
        }

        // 每 tick 从队列中扫描一个区块
        if (!chunksToScan.isEmpty()) {
            scanOneChunk();
        }

        // 定时添加新的区块到队列
        if (tickCounter >= config.scanIntervalTicks) {
            tickCounter = 0;
            addChunksToScanQueue();
        }

        // 实体扫描
        if (config.itemFrameFinder || config.enderPearlFinder || config.nameTagFinder ||
                config.villagerFinder || config.boatFinder) {
            if (entityScanTicks >= config.entityScanDelay) {
                scanEntities();
                entityScanTicks = 0;
            }
        }
    }

    private void onConnect(ClientConnectEvent event) {
        if (config.loadOnStart) {
            loadData();
        }
    }

    private void onDisconnect(ClientDisconnectEvent event) {
        if (config.saveToFile) {
            saveData();
        }
        scannedChunks.clear();
        chunksToScan.clear();
        detectedLocations.clear();
    }

    private void addChunksToScanQueue() {
        int viewDistance = CACHE.getChunkCache().getServerViewDistance();
        int playerChunkX = (int) (CACHE.getPlayerCache().getX()) >> 4;
        int playerChunkZ = (int) (CACHE.getPlayerCache().getZ()) >> 4;

        // 添加渲染距离内的区块到队列（跳过已扫描的）
        for (int x = -viewDistance; x <= viewDistance; x++) {
            for (int z = -viewDistance; z <= viewDistance; z++) {
                int chunkX = playerChunkX + x;
                int chunkZ = playerChunkZ + z;
                String chunkKey = chunkX + ":" + chunkZ;

                if (!scannedChunks.contains(chunkKey) && !chunksToScan.contains(chunkKey)) {
                    chunksToScan.add(chunkKey);
                }
            }
        }
    }

    private void scanOneChunk() {
        if (chunksToScan.isEmpty()) return;

        String chunkKey = chunksToScan.poll();
        if (chunkKey == null) return;

        String[] parts = chunkKey.split(":");
        if (parts.length != 2) return;

        int chunkX = Integer.parseInt(parts[0]);
        int chunkZ = Integer.parseInt(parts[1]);

        // 标记为已扫描
        scannedChunks.add(chunkKey);

        // 第一步：使用合并过滤器快速扫描，找到一个就停止
        long startTime = System.nanoTime();
        List<BlockPos> quickScan = WorldScanner.scanChunk(combinedFilter, chunkX, chunkZ, 1, Integer.MIN_VALUE);
        long elapsed = (System.nanoTime() - startTime) / 1_000_000;

        if (quickScan.isEmpty()) {
            // 没有找到任何方块，跳过详细扫描
            info("Quick scan chunk [{}, {}] took {}ms, no match", chunkX, chunkZ, elapsed);
            return;
        }

        info("Quick scan chunk [{}, {}] took {}ms, found match", chunkX, chunkZ, elapsed);

        // 第二步：细分扫描，确定具体是哪种方块
        if (config.portalFinder) {
            scanPortals(chunkX, chunkZ);
        }
        if (config.shulkerFinder) {
            scanShulkerBoxes(chunkX, chunkZ);
        }
        if (config.blockListEnabled) {
            scanCustomBlockList(chunkX, chunkZ);
        }
    }

    private void scanPortals(int chunkX, int chunkZ) {
        // 扫描单个区块，max=1，找到一个就停止
        List<BlockPos> positions = WorldScanner.scanChunk(portalFilter, chunkX, chunkZ, 1, Integer.MIN_VALUE);

        for (BlockPos pos : positions) {
            String chunkKey = chunkX + ":" + chunkZ;
            addDetection(chunkX, chunkZ, chunkKey, "Open Portal", pos.x(), pos.y(), pos.z());
        }
    }

    private void scanShulkerBoxes(int chunkX, int chunkZ) {
        // 扫描单个区块，max=1，找到一个就停止
        List<BlockPos> positions = WorldScanner.scanChunk(shulkerFilter, chunkX, chunkZ, 1, Integer.MIN_VALUE);

        for (BlockPos pos : positions) {
            String chunkKey = chunkX + ":" + chunkZ;
            addDetection(chunkX, chunkZ, chunkKey, "Shulker (1)", pos.x(), pos.y(), pos.z());
        }
    }

    private void scanCustomBlockList(int chunkX, int chunkZ) {
        if (customBlockFilter == null) {
            updateCustomBlockFilter();
            if (customBlockFilter == null) return;
        }

        // 扫描单个区块，max=1，找到一个就停止
        List<BlockPos> positions = WorldScanner.scanChunk(customBlockFilter, chunkX, chunkZ, 1, Integer.MIN_VALUE);

        for (BlockPos pos : positions) {
            String chunkKey = chunkX + ":" + chunkZ;
            addDetection(chunkX, chunkZ, chunkKey, "Block List (1)", pos.x(), pos.y(), pos.z());
        }
    }

    private void scanEntities() {
        var entities = CACHE.getEntityCache().getEntities().values();
        Map<String, Integer> chunkEntityCount = new HashMap<>();

        for (var entity : entities) {
            int chunkX = (int) (entity.getX()) >> 4;
            int chunkZ = (int) (entity.getZ()) >> 4;
            String chunkKey = chunkX + ":" + chunkZ;

            // 物品展示框检测
            if (config.itemFrameFinder && isItemFrame(entity)) {
                addDetection(chunkX, chunkZ, chunkKey, "Item Frame",
                        (int) entity.getX(), (int) entity.getY(), (int) entity.getZ());
                continue;
            }

            // 末影珍珠检测
            if (config.enderPearlFinder && isEnderPearl(entity)) {
                addDetection(chunkX, chunkZ, chunkKey, "Ender Pearl",
                        (int) entity.getX(), (int) entity.getY(), (int) entity.getZ());
                continue;
            }

            // 命名牌实体检测
            if (config.nameTagFinder && hasCustomName(entity)) {
                addDetection(chunkX, chunkZ, chunkKey, "NameTagged Entity",
                        (int) entity.getX(), (int) entity.getY(), (int) entity.getZ());
                continue;
            }

            // 村民检测
            if (config.villagerFinder && isLeveledVillager(entity)) {
                addDetection(chunkX, chunkZ, chunkKey, "Leveled Villager",
                        (int) entity.getX(), (int) entity.getY(), (int) entity.getZ());
                continue;
            }

            // 船检测
            if (config.boatFinder && isBoat(entity)) {
                addDetection(chunkX, chunkZ, chunkKey, "Boat",
                        (int) entity.getX(), (int) entity.getY(), (int) entity.getZ());
                continue;
            }
        }
    }

    private void addDetection(int chunkX, int chunkZ, String chunkKey, String reason, int x, int y, int z) {
        if (spamCooldownTicks > 0) return;

        spamCooldownTicks = config.tickDelay;

        String coords = String.format("X=%d, Y=%d, Z=%d", x, y, z);
        info("Found {} at chunk [{}, {}] {}", reason, chunkX, chunkZ, coords);

        // 保存检测到的坐标到列表
        String location = String.format("%s | Chunk[%d, %d] | %s", reason, chunkX, chunkZ, coords);
        detectedLocations.add(location);
    }

    // 实体检测辅助方法
    private boolean isItemFrame(Entity entity) {
        EntityType entityType = entity.getEntityType();
        return entityType == EntityType.ITEM_FRAME || entityType == EntityType.GLOW_ITEM_FRAME;
    }

    private boolean isEnderPearl(Entity entity) {
        EntityType entityType = entity.getEntityType();
        return entityType == EntityType.ENDER_PEARL;
    }

    private boolean hasCustomName(Entity entity) {
        return entity.getMetadata().containsKey(2);
    }

    private boolean isLeveledVillager(Entity entity) {
        EntityType entityType = entity.getEntityType();
        if (entityType != EntityType.VILLAGER) return false;
        var levelData = entity.getMetadata().get(16);
        if (levelData != null && levelData.getValue() instanceof Integer level) {
            return level > 1;
        }
        return false;
    }

    private boolean isBoat(Entity entity) {
        EntityType entityType = entity.getEntityType();
        return entityType.toString().toLowerCase().contains("_boat");
    }

    // 数据持久化
    private void saveData() {
        // 复制当前数据，避免在异步执行时被修改
        List<String> locationsToSave = new ArrayList<>(detectedLocations);

        CompletableFuture.runAsync(() -> {
            try {
                Path dir = Path.of("basefinder");
                Files.createDirectories(dir);
                Path file = dir.resolve("detected_locations.json");

                Map<String, Object> data = new HashMap<>();
                data.put("locations", locationsToSave);

                Gson gson = new Gson();
                String json = gson.toJson(data);
                Files.writeString(file, json);
            } catch (IOException e) {
                error("Failed to save data: " + e.getMessage());
            }
        });
    }

    private void loadData() {
        CompletableFuture.runAsync(() -> {
            try {
                Path file = Path.of("basefinder/detected_locations.json");
                if (!Files.exists(file)) return;

                String json = Files.readString(file);
                Gson gson = new Gson();
                java.lang.reflect.Type type = new TypeToken<Map<String, List<String>>>() {
                }.getType();
                Map<String, List<String>> data = gson.fromJson(json, type);

                if (data != null && data.containsKey("locations")) {
                    detectedLocations.addAll(data.get("locations"));
                }
            } catch (IOException e) {
                error("Failed to load data: " + e.getMessage());
            }
        });
    }
}
