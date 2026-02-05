package com.einent.veinmining.systems;

import com.einent.veinmining.config.VeinMiningConfig;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemTool;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemToolSpec;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.blocktype.component.BlockPhysics;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class MiningManager {

    public static final ThreadLocal<Boolean> IS_VEIN_MINING = ThreadLocal.withInitial(() -> false);
    private static final int PERFORM_BLOCK_UPDATE = 256;
    private static final Set<Vector3i> ACTIVE_VEINS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final Config<VeinMiningConfig> config;
    private final PatternCalculator patternCalculator;

    public MiningManager(Config<VeinMiningConfig> config) {
        this.config = config;
        this.patternCalculator = new PatternCalculator();
    }

    public void performVeinMine(Player player, Ref<EntityStore> pRef, Vector3i startPos, String targetId, Store<EntityStore> store, String uuid) {
        VeinMiningConfig cfg = config.get();

        if (cfg.isRequirePermission() && !player.hasPermission("veinmining.use")) {
            return;
        }

        World world = player.getWorld();
        Inventory inv = player.getInventory();

        boolean usingToolBelt = inv.usingToolsItem();
        ItemContainer activeContainer = usingToolBelt ? inv.getTools() : inv.getHotbar();
        short activeSlot = (short) (usingToolBelt ? inv.getActiveToolsSlot() : inv.getActiveHotbarSlot());

        ItemStack tool = activeContainer.getItemStack(activeSlot);
        if (tool == null || tool.isEmpty()) tool = null;

        String toolId = (tool != null) ? tool.getItem().getId() : "";

        if (cfg.isRequireValidTool()) {
            if (tool == null) return;
            List<String> allowed = cfg.getValidTools();
            boolean isValid = false;
            for (String allowedTool : allowed) {
                if (toolId.contains(allowedTool)) {
                    isValid = true;
                    break;
                }
            }
            if (!isValid) return;
        }

        List<String> blockWhitelist = cfg.getBlockWhitelist();
        if (!blockWhitelist.isEmpty() && !blockWhitelist.contains(targetId)) {
            return;
        }

        List<String> blockBlacklist = cfg.getBlockBlacklist();
        if (blockBlacklist.contains(targetId)) {
            return;
        }

        int maxBlocks = Math.max(0, cfg.getMaxVeinSize() - 1);
        String pattern = cfg.getPlayerPattern(uuid);
        String oriMode = cfg.getPlayerOrientation(uuid);

        Vector3i hitFace = patternCalculator.getHitFace(startPos, store, pRef);
        Vector3i originStart = patternCalculator.getMultiblockOrigin(world, startPos);

        List<Vector3i> blocksToBreak;
        if ("freeform".equalsIgnoreCase(pattern)) {
            blocksToBreak = patternCalculator.getFreeformBlocks(world, startPos, targetId, maxBlocks);
        } else {
            blocksToBreak = patternCalculator.getPatternBlocks(world, targetId, store, pRef, originStart, pattern, maxBlocks, oriMode, hitFace)
                    .stream()
                    .map(pos -> patternCalculator.getMultiblockOrigin(world, pos))
                    .distinct()
                    .collect(Collectors.toList());
        }

        blocksToBreak.removeIf(pos -> {
            if (ACTIVE_VEINS.contains(pos)) return true;
            BlockType t = world.getBlockType(pos.x, pos.y, pos.z);
            if (t == null) return true;
            String tid = t.getId();
            return !tid.equals(targetId) || blockBlacklist.contains(tid);
        });

        if (blocksToBreak.isEmpty()) return;

        ACTIVE_VEINS.addAll(blocksToBreak);

        boolean isCreative = player.getGameMode() == GameMode.Creative;
        boolean consolidate = cfg.isConsolidateDrops();
        boolean isShovel = toolId.contains("Shovel");
        double efficiencyDivisor = isShovel ? 20.0 : 4.0;
        double userMultiplier = cfg.getDurabilityMultiplier();
        Item toolItem = (tool != null) ? tool.getItem() : null;
        double lossPerHit = (toolItem != null && toolItem.getDurabilityLossOnHit() > 0) ? toolItem.getDurabilityLossOnHit() : 1.0;

        double totalDurabilityCost = 0;
        List<Vector3i> finalBlocks = new ArrayList<>();

        IS_VEIN_MINING.set(true);
        try {
            for (Vector3i pos : blocksToBreak) {
                BlockType type = world.getBlockType(pos.x, pos.y, pos.z);
                if (type == null) continue;

                double blockCost = 0;
                if (!isCreative && tool != null) {
                    blockCost = (calculateHitsToBreak(type, toolItem) * lossPerHit * userMultiplier) / efficiencyDivisor;
                    if (!tool.isUnbreakable() && (tool.getDurability() - (totalDurabilityCost + blockCost)) <= 0) break;
                }
                totalDurabilityCost += blockCost;
                finalBlocks.add(pos);
            }
        } finally {
            IS_VEIN_MINING.set(false);
        }

        if (finalBlocks.isEmpty()) {
            ACTIVE_VEINS.removeAll(blocksToBreak);
            return;
        }

        if (!isCreative && tool != null && !tool.isUnbreakable()) {
            player.updateItemStackDurability(pRef, tool, activeContainer, activeSlot, -Math.min(totalDurabilityCost, tool.getDurability()), store);
            player.sendInventory();
        }

        Map<String, Integer> consolidatedMap = new HashMap<>();
        Random rand = new Random();
        PlayerRef playerRefComp = store.getComponent(pRef, PlayerRef.getComponentType());

        if (cfg.isInstantBreak()) {
            IS_VEIN_MINING.set(true);
            try {
                for (Vector3i pos : finalBlocks) {
                    processBlockBreak(world, pos, isCreative, consolidate, consolidatedMap, store, pRef, tool, toolId);
                    ACTIVE_VEINS.remove(pos);
                }
            } finally {
                IS_VEIN_MINING.set(false);
            }

            if (playerRefComp != null) playSound(playerRefComp, "SFX_Pickaxe_T2_Impact_Nice", 7.0f, 0.7f);
            if (!isCreative && consolidate && !consolidatedMap.isEmpty()) {
                spawnConsolidatedDrops(store, finalBlocks.getFirst(), consolidatedMap, rand);
            }
        } else {
            if (playerRefComp != null) playSound(playerRefComp, "SFX_Pickaxe_T2_Impact_Nice", 7.0f, 0.7f);
            scheduleSpreadingBreak(playerRefComp, pRef, world, store, finalBlocks, 0, consolidate, consolidatedMap, rand, isCreative, tool, toolId);
        }
    }

    private void processBlockBreak(World world, Vector3i pos, boolean isCreative, boolean consolidate, Map<String, Integer> consolidatedMap, Store<EntityStore> store, Ref<EntityStore> entityRef, ItemStack tool, String toolId) {
        BlockType type = world.getBlockType(pos.x, pos.y, pos.z);
        if (type == null) return;

        store.invoke(entityRef, new BreakBlockEvent(tool, pos, type));

        if (!isCreative) {
            List<ItemStack> drops = getRealDrops(world, pos, type, toolId);
            if (consolidate) {
                drops.forEach(d -> consolidatedMap.merge(d.getItemId(), d.getQuantity(), Integer::sum));
            } else if (!drops.isEmpty()) {
                Random rand = new Random();
                spawnDropsAtPos(store, pos, drops, rand);
            }
        }
        world.setBlock(pos.x, pos.y, pos.z, "Empty", PERFORM_BLOCK_UPDATE);
    }

    private void scheduleSpreadingBreak(PlayerRef playerRef, Ref<EntityStore> entityRef, World world, Store<EntityStore> store, List<Vector3i> blocks, int index, boolean consolidate, Map<String, Integer> consolidatedMap, Random rand, boolean isCreative, ItemStack tool, String toolId) {
        if (index >= blocks.size()) {
            if (!isCreative && consolidate && !consolidatedMap.isEmpty()) {
                spawnConsolidatedDrops(store, blocks.getFirst(), consolidatedMap, rand);
            }
            return;
        }

        int batchSize = 3 + rand.nextInt(3);
        int end = Math.min(index + batchSize, blocks.size());

        for (int i = index; i < end; i++) {
            Vector3i pos = blocks.get(i);
            BlockType type = world.getBlockType(pos.x, pos.y, pos.z);
            if (type == null) {
                ACTIVE_VEINS.remove(pos);
                continue;
            }

            if (rand.nextFloat() < 0.20f && playerRef != null) {
                playSound(playerRef, "SFX_Stone_Break", 0.5f, 0.8f + rand.nextFloat() * 0.4f);
            }

            IS_VEIN_MINING.set(true);
            try {
                store.invoke(entityRef, new BreakBlockEvent(tool, pos, type));
            } finally {
                IS_VEIN_MINING.set(false);
            }

            if (!isCreative) {
                List<ItemStack> drops = getRealDrops(world, pos, type, toolId);
                if (consolidate) {
                    drops.forEach(d -> consolidatedMap.merge(d.getItemId(), d.getQuantity(), Integer::sum));
                } else {
                    spawnDropsAtPos(store, pos, drops, rand);
                }
            }

            world.setBlock(pos.x, pos.y, pos.z, "Empty", PERFORM_BLOCK_UPDATE);
            ACTIVE_VEINS.remove(pos);
        }

        long nextDelay = 4L + rand.nextInt(5);
        CompletableFuture.delayedExecutor(nextDelay, TimeUnit.MILLISECONDS, world).execute(() -> scheduleSpreadingBreak(playerRef, entityRef, world, store, blocks, end, consolidate, consolidatedMap, rand, isCreative, tool, toolId));
    }

    @SuppressWarnings("unchecked")
    private List<ItemStack> getRealDrops(World world, Vector3i pos, BlockType type, String toolId) {
        List<ItemStack> res = new ArrayList<>();

        if (toolId != null && toolId.contains("Shears")) {
            String id = (type.getItem() != null) ? type.getItem().getId() : type.getId();
            if (isValidId(id)) {
                res.add(new ItemStack(id, 1));
                return res;
            }
        }

        BlockGathering g = type.getGathering();
        if (g == null) return res;

        if (g.shouldUseDefaultDropWhenPlaced() && isDecoBlock(world, pos)) {
            String id = (type.getItem() != null) ? type.getItem().getId() : type.getId();
            if (isValidId(id)) res.add(new ItemStack(id, 1));
            return res;
        }

        String dropListId = null;
        String itemId = null;
        int quantity = 1;

        if (g.getBreaking() != null) {
            dropListId = g.getBreaking().getDropListId();
            itemId = g.getBreaking().getItemId();
            quantity = g.getBreaking().getQuantity();
        } else if (g.getSoft() != null) {
            dropListId = g.getSoft().getDropListId();
            itemId = g.getSoft().getItemId();
        }

        if (dropListId != null || itemId != null) {
            if (dropListId != null) {
                try {
                    Class<?> itemModuleClass = Class.forName("com.hypixel.hytale.server.core.modules.item.ItemModule");
                    Object instance = itemModuleClass.getMethod("get").invoke(null);
                    for (int i = 0; i < quantity; i++) {
                        List<ItemStack> drops = (List<ItemStack>) itemModuleClass.getMethod("getRandomItemDrops", String.class).invoke(instance, dropListId);
                        if (drops != null) res.addAll(drops);
                    }
                } catch (Exception ignored) {}
            }
            if (isValidId(itemId)) res.add(new ItemStack(itemId, quantity));
            return res;
        }

        String fallbackId = (type.getItem() != null) ? type.getItem().getId() : type.getId();
        if (isValidId(fallbackId)) res.add(new ItemStack(fallbackId, 1));

        return res;
    }

    private boolean isValidId(String id) {
        return id != null && !id.isEmpty() && !id.equalsIgnoreCase("Empty");
    }

    private boolean isDecoBlock(World world, Vector3i pos) {
        try {
            ChunkStore chunkStore = world.getChunkStore();
            Ref<ChunkStore> sectionRef = chunkStore.getChunkSectionReference(pos.x, pos.y, pos.z);
            if (sectionRef != null && sectionRef.isValid()) {
                BlockPhysics blockPhysics = chunkStore.getStore().getComponent(sectionRef, BlockPhysics.getComponentType());
                return blockPhysics != null && blockPhysics.isDeco(pos.x, pos.y, pos.z);
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void spawnDropsAtPos(Store<EntityStore> store, Vector3i pos, List<ItemStack> drops, Random rand) {
        Vector3d spawnPosBase = new Vector3d(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5);
        for (ItemStack stack : drops) spawnStack(store, spawnPosBase, stack, rand);
    }

    private void spawnConsolidatedDrops(Store<EntityStore> store, Vector3i pos, Map<String, Integer> consolidatedMap, Random rand) {
        Vector3d spawnPosBase = new Vector3d(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5);
        consolidatedMap.forEach((id, qty) -> {
            int remaining = qty;
            while (remaining > 0) {
                int amount = Math.min(remaining, 64);
                spawnStack(store, spawnPosBase, new ItemStack(id, amount), rand);
                remaining -= amount;
            }
        });
    }

    private void spawnStack(Store<EntityStore> store, Vector3d basePos, ItemStack stack, Random rand) {
        Vector3d offset = new Vector3d((rand.nextDouble() - 0.5) * 0.5, (rand.nextDouble() - 0.5) * 0.5, (rand.nextDouble() - 0.5) * 0.5);
        Vector3d finalPos = basePos.add(offset);
        Holder<EntityStore> itemHolder = ItemComponent.generateItemDrop(store, stack, finalPos, Vector3f.ZERO, 0, 0.15f, 0);
        if (itemHolder != null) store.addEntity(itemHolder, AddReason.SPAWN);
    }

    private void playSound(PlayerRef ref, String sound, float vol, float pitch) {
        try {
            SoundUtil.playSoundEvent2dToPlayer(ref, SoundEvent.getAssetMap().getIndex(sound), SoundCategory.SFX, vol, pitch);
        } catch (Exception ignored) {}
    }

    private double calculateHitsToBreak(BlockType blockType, Item toolItem) {
        if (toolItem == null) return 5.0;
        BlockGathering gathering = blockType.getGathering();
        if (gathering == null || gathering.getBreaking() == null) return 1.0;
        BlockBreakingDropType breaking = gathering.getBreaking();
        String requiredType = (breaking.getGatherType() != null) ? breaking.getGatherType() : "pickaxe";
        int blockQuality = (breaking.getQuality() > 0) ? breaking.getQuality() : 1;
        ItemTool toolConfig = toolItem.getTool();
        if (toolConfig == null || toolConfig.getSpecs() == null) return 5.0;
        for (ItemToolSpec spec : toolConfig.getSpecs()) {
            if (spec.getGatherType().equalsIgnoreCase(requiredType)) {
                float toolPower = spec.getPower();
                return (toolPower > 0) ? (double) blockQuality / toolPower : 10.0;
            }
        }
        return 10.0;
    }
}