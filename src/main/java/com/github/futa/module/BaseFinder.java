package com.github.futa.module;

import com.github.futa.BaseModule;
import com.github.futa.config.BaseFinderConfig;
import com.github.rfresh2.EventConsumer;
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
import java.util.concurrent.ConcurrentHashMap;

import static com.zenith.Globals.CACHE;

/**
 * @see com.zenith.feature.pathfinder.util.WorldScanner
 */
public class BaseFinder extends BaseModule {
    private final BaseFinderConfig config;
    private final Set<String> detectedChunks = ConcurrentHashMap.newKeySet();
    private int tickCounter = 0;
    private int entityScanTicks = 0;
    private int spamCooldownTicks = 0;

    // 预定义的 BlockOptionalMetaLookup
    private BlockOptionalMetaLookup portalFilter;
    private BlockOptionalMetaLookup shulkerFilter;
    private BlockOptionalMetaLookup customBlockFilter;

    public BaseFinder() {
        this.config = PLUGIN_CONFIG.baseFinder;
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
        }
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

    @Override
    public void onDisable() {
        if (config.saveToFile) {
            saveData();
        }
        detectedChunks.clear();
        info("BaseFinder disabled");
    }

    private void onTick(ClientBotTick event) {
        tickCounter++;
        entityScanTicks++;

        if (spamCooldownTicks > 0) {
            spamCooldownTicks--;
        }

        // 定时扫描渲染距离内的区块
        if (tickCounter >= config.scanIntervalTicks) {
            tickCounter = 0;
            scanNearbyChunks();
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
        detectedChunks.clear();
    }

    private void scanNearbyChunks() {
        String dimension = CACHE.getChunkCache().getCurrentDimension() != null ?
                CACHE.getChunkCache().getCurrentDimension().name() : "minecraft:overworld";


        // 传送门检测
        if (config.portalFinder) {
            scanPortals(dimension);
        }

        // 潜影盒检测
        if (config.shulkerFinder) {
            scanShulkerBoxes(dimension);
        }

        // 自定义方块列表检测
        if (config.blockListEnabled) {
            scanCustomBlockList(dimension);
        }
    }

    private void scanPortals(String dimension) {
        List<BlockPos> positions = WorldScanner.scanCurrentViewDistance(portalFilter);

        for (BlockPos pos : positions) {
            String chunkKey = dimension + ":" + (pos.x() >> 4) + ":" + (pos.z() >> 4);
            if (detectedChunks.contains(chunkKey)) continue;

            addDetection(pos.x() >> 4, pos.z() >> 4, dimension, chunkKey, "Open Portal", pos.x(), pos.y(), pos.z());
        }
    }

    private void scanShulkerBoxes(String dimension) {
        List<BlockPos> positions = WorldScanner.scanCurrentViewDistance(shulkerFilter);

        // 按 chunk 分组统计
        Map<String, List<BlockPos>> chunkShulkers = new HashMap<>();
        for (BlockPos pos : positions) {
            String chunkKey = dimension + ":" + (pos.x() >> 4) + ":" + (pos.z() >> 4);
            chunkShulkers.computeIfAbsent(chunkKey, k -> new ArrayList<>()).add(pos);
        }

        for (var entry : chunkShulkers.entrySet()) {
            if (detectedChunks.contains(entry.getKey())) continue;

            String[] parts = entry.getKey().split(":");
            int chunkX = Integer.parseInt(parts[1]);
            int chunkZ = Integer.parseInt(parts[2]);
            BlockPos first = entry.getValue().get(0);

            addDetection(chunkX, chunkZ, dimension, entry.getKey(),
                    "Shulker (" + entry.getValue().size() + ")", first.x(), first.y(), first.z());
        }
    }

    private void scanCustomBlockList(String dimension) {
        if (customBlockFilter == null) {
            updateCustomBlockFilter();
            if (customBlockFilter == null) return;
        }

        List<BlockPos> positions = WorldScanner.scanCurrentViewDistance(customBlockFilter);

        // 按 chunk 分组统计
        Map<String, Integer> chunkCounts = new HashMap<>();
        for (BlockPos pos : positions) {
            String chunkKey = dimension + ":" + (pos.x() >> 4) + ":" + (pos.z() >> 4);
            chunkCounts.merge(chunkKey, 1, Integer::sum);
        }

        for (var entry : chunkCounts.entrySet()) {
            if (detectedChunks.contains(entry.getKey())) continue;

            if (entry.getValue() >= config.blockListThreshold) {
                String[] parts = entry.getKey().split(":");
                int chunkX = Integer.parseInt(parts[1]);
                int chunkZ = Integer.parseInt(parts[2]);

                addDetection(chunkX, chunkZ, dimension, entry.getKey(),
                        "Block List (" + entry.getValue() + ")", 0, 0, 0);
            }
        }
    }

    private void scanEntities() {
        var entities = CACHE.getEntityCache().getEntities().values();
        Map<String, Integer> chunkEntityCount = new HashMap<>();
        String dimension = CACHE.getChunkCache().getCurrentDimension() != null ?
                CACHE.getChunkCache().getCurrentDimension().name() : "minecraft:overworld";

        for (var entity : entities) {
            int chunkX = (int) (entity.getX()) >> 4;
            int chunkZ = (int) (entity.getZ()) >> 4;
            String chunkKey = dimension + ":" + chunkX + ":" + chunkZ;

            if (detectedChunks.contains(chunkKey)) continue;

            // 物品展示框检测
            if (config.itemFrameFinder && isItemFrame(entity)) {
                addDetection(chunkX, chunkZ, dimension, chunkKey, "Item Frame",
                        (int) entity.getX(), (int) entity.getY(), (int) entity.getZ());
                continue;
            }

            // 末影珍珠检测
            if (config.enderPearlFinder && isEnderPearl(entity)) {
                addDetection(chunkX, chunkZ, dimension, chunkKey, "Ender Pearl",
                        (int) entity.getX(), (int) entity.getY(), (int) entity.getZ());
                continue;
            }

            // 命名牌实体检测
            if (config.nameTagFinder && hasCustomName(entity)) {
                addDetection(chunkX, chunkZ, dimension, chunkKey, "NameTagged Entity",
                        (int) entity.getX(), (int) entity.getY(), (int) entity.getZ());
                continue;
            }

            // 村民检测
            if (config.villagerFinder && isLeveledVillager(entity)) {
                addDetection(chunkX, chunkZ, dimension, chunkKey, "Leveled Villager",
                        (int) entity.getX(), (int) entity.getY(), (int) entity.getZ());
                continue;
            }

            // 船检测
            if (config.boatFinder && isBoat(entity)) {
                addDetection(chunkX, chunkZ, dimension, chunkKey, "Boat",
                        (int) entity.getX(), (int) entity.getY(), (int) entity.getZ());
                continue;
            }
        }
    }

    private void addDetection(int chunkX, int chunkZ, String dimension, String chunkKey, String reason, int x, int y, int z) {
        if (spamCooldownTicks > 0 && !detectedChunks.isEmpty()) return;

        detectedChunks.add(chunkKey);
        spamCooldownTicks = config.tickDelay;

        String coords = config.displayCoords ? String.format(" near X%d, Y%d, Z%d", x, y, z) : "";
        info("Found {} at chunk [{}, {}]{}", reason, chunkX, chunkZ, coords);
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
        try {
            Path dir = Path.of("plugins/data/basefinder");
            Files.createDirectories(dir);
            Path file = dir.resolve("detected_chunks.json");

            StringBuilder json = new StringBuilder("{\"chunks\":[");
            boolean first = true;
            for (String chunkKey : detectedChunks) {
                if (!first) json.append(",");
                first = false;

                String[] parts = chunkKey.split(":");
                if (parts.length == 3) {
                    json.append("{\"dimension\":\"").append(parts[0])
                            .append("\",\"chunkX\":").append(parts[1])
                            .append(",\"chunkZ\":").append(parts[2])
                            .append("}");
                }
            }
            json.append("]}");

            Files.writeString(file, json.toString());
        } catch (IOException e) {
            error("Failed to save data: " + e.getMessage());
        }
    }

    private void loadData() {
        try {
            Path file = Path.of("plugins/data/basefinder/detected_chunks.json");
            if (!Files.exists(file)) return;

            String json = Files.readString(file);
            if (json.contains("\"chunks\"")) {
                String chunksPart = json.substring(json.indexOf("\"chunks\":[") + 11, json.lastIndexOf("]"));
                if (!chunksPart.isEmpty()) {
                    String[] entries = chunksPart.split("\\},\\{");
                    for (String entry : entries) {
                        String dimension = extractJsonValue(entry, "dimension");
                        String chunkX = extractJsonValue(entry, "chunkX");
                        String chunkZ = extractJsonValue(entry, "chunkZ");

                        if (dimension != null && chunkX != null && chunkZ != null) {
                            detectedChunks.add(dimension + ":" + chunkX + ":" + chunkZ);
                        }
                    }
                }
            }
        } catch (IOException e) {
            error("Failed to load data: " + e.getMessage());
        }
    }

    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1) return null;

        startIndex += searchKey.length();
        while (startIndex < json.length() && json.charAt(startIndex) == ' ') {
            startIndex++;
        }

        if (startIndex >= json.length()) return null;

        if (json.charAt(startIndex) == '"') {
            startIndex++;
            int endIndex = json.indexOf("\"", startIndex);
            if (endIndex == -1) return null;
            return json.substring(startIndex, endIndex);
        }

        int endIndex = startIndex;
        while (endIndex < json.length() && (Character.isDigit(json.charAt(endIndex)) || json.charAt(endIndex) == '-')) {
            endIndex++;
        }
        return json.substring(startIndex, endIndex);
    }
}
