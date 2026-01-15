package com.einent.veinmining.systems;

import com.einent.veinmining.config.VeinMiningConfig;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
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
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.item.ItemModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class VeinMiningSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("VeinMining");
    private final Config<VeinMiningConfig> config;
    private static final ThreadLocal<Boolean> IS_VEIN_MINING = ThreadLocal.withInitial(() -> false);
    private static final int PERFORM_BLOCK_UPDATE = 256;

    public VeinMiningSystem(Config<VeinMiningConfig> config) {
        super(BreakBlockEvent.class);
        this.config = config;
    }

    @Override
    public void handle(final int index, final ArchetypeChunk<EntityStore> archetypeChunk, final Store<EntityStore> store, final CommandBuffer<EntityStore> commandBuffer, final BreakBlockEvent event) {
        if (IS_VEIN_MINING.get()) return;

        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComp == null) return;

        VeinMiningConfig cfg = config.get();
        String mode = cfg.getPlayerMode(uuidComp.getUuid().toString());
        if ("off".equalsIgnoreCase(mode)) return;

        MovementStatesComponent moveComp = store.getComponent(ref, MovementStatesComponent.getComponentType());
        if (moveComp == null || !moveComp.getMovementStates().walking) return;

        String blockId = event.getBlockType().getId();
        if (blockId == null || blockId.equals("Empty")) return;

        boolean isAll = mode.equalsIgnoreCase("all");
        boolean isOre = blockId.startsWith("Ore_");

        if (!isAll && !isOre) return;

        PlayerRef playerRefComp = store.getComponent(ref, PlayerRef.getComponentType());
        performVeinMine(player, playerRefComp, ref, event.getTargetBlock(), blockId, store, commandBuffer);
    }

    private void performVeinMine(Player player, PlayerRef playerRef, Ref<EntityStore> pRef, Vector3i startPos, String targetId, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {
        World world = player.getWorld();
        Inventory inv = player.getInventory();
        boolean usingToolBelt = inv.usingToolsItem();
        ItemContainer activeContainer = usingToolBelt ? inv.getTools() : inv.getHotbar();
        short activeSlot = (short) (usingToolBelt ? inv.getActiveToolsSlot() : inv.getActiveHotbarSlot());

        ItemStack tool = activeContainer.getItemStack(activeSlot);
        if (tool == null || tool.isEmpty()) tool = null;

        String toolId = (tool != null) ? tool.getItem().getId() : "";
        VeinMiningConfig cfg = config.get();
        if (cfg.isRequireValidTool()) {
            if (tool == null) return;
            boolean isValid = toolId.contains("Pickaxe") || toolId.contains("Hatchet") || toolId.contains("Shovel");
            if (!isValid) return;
        }

        boolean isShovel = toolId.contains("Shovel");
        double efficiencyDivisor = isShovel ? 20.0 : 4.0;
        int maxBlocks = Math.max(0, cfg.getMaxVeinSize() - 1);
        double userMultiplier = cfg.getDurabilityMultiplier();
        boolean isCreative = player.getGameMode() == GameMode.Creative;
        boolean consolidate = cfg.isConsolidateDrops();

        List<Vector3i> blocksToBreak = new ArrayList<>();
        Queue<Vector3i> queue = new LinkedList<>();
        Set<Vector3i> visited = new HashSet<>();

        visited.add(startPos);
        addNeighbors(startPos, queue, visited);

        Item toolItem = (tool != null) ? tool.getItem() : null;
        double lossPerHit = (toolItem != null && toolItem.getDurabilityLossOnHit() > 0) ? toolItem.getDurabilityLossOnHit() : 1.0;

        double totalDurabilityCost = 0;
        Map<String, Integer> consolidatedMap = new HashMap<>();

        IS_VEIN_MINING.set(true);
        try {
            while (!queue.isEmpty() && blocksToBreak.size() < maxBlocks) {
                Vector3i pos = queue.poll();
                BlockType type = world.getBlockType(pos.x, pos.y, pos.z);
                if (type == null || !type.getId().equals(targetId)) continue;

                double blockCost = 0;
                if (!isCreative && tool != null) {
                    blockCost = (calculateHitsToBreak(type, toolItem) * lossPerHit * userMultiplier) / efficiencyDivisor;
                    if (!tool.isUnbreakable() && (tool.getDurability() - (totalDurabilityCost + blockCost)) <= 0) break;
                }

                totalDurabilityCost += blockCost;
                blocksToBreak.add(pos);
                addNeighbors(pos, queue, visited);
            }
        } finally {
            IS_VEIN_MINING.set(false);
        }

        if (blocksToBreak.isEmpty()) return;

        if (!isCreative && tool != null && !tool.isUnbreakable()) {
            player.updateItemStackDurability(pRef, tool, activeContainer, activeSlot, -Math.min(totalDurabilityCost, tool.getDurability()), store);
            player.sendInventory();
        }

        try {
            SoundUtil.playSoundEvent2dToPlayer(playerRef, SoundEvent.getAssetMap().getIndex("SFX_Pickaxe_T2_Impact_Nice"), SoundCategory.UI, 7.0f, 0.8f);
        } catch (Exception ignored) {}

        if (cfg.isInstantBreak()) {
            Random rand = new Random();
            for (Vector3i pos : blocksToBreak) {
                BlockType type = world.getBlockType(pos.x, pos.y, pos.z);
                if (type == null) continue;

                if (!isCreative) {
                    List<ItemStack> drops = getRealDrops(type);
                    if (consolidate) {
                        drops.forEach(d -> consolidatedMap.merge(d.getItemId(), d.getQuantity(), Integer::sum));
                    } else {
                        spawnDropsAtPos(store, commandBuffer, pos, drops, rand);
                    }
                }
                world.setBlock(pos.x, pos.y, pos.z, "Empty", PERFORM_BLOCK_UPDATE);
            }
            try {
                SoundUtil.playSoundEvent2dToPlayer(playerRef, SoundEvent.getAssetMap().getIndex("SFX_Stone_Break"), SoundCategory.SFX, 1.0f, 0.8f);
            } catch (Exception ignored) {}

            if (!isCreative && consolidate && !consolidatedMap.isEmpty()) {
                spawnConsolidatedDrops(store, commandBuffer, blocksToBreak.get(0), consolidatedMap, rand);
            }
        } else {
            scheduleSpreadingBreak(player, playerRef, world, store, commandBuffer, blocksToBreak, 0, consolidate, consolidatedMap, new Random(), isCreative);
        }
    }

    private void scheduleSpreadingBreak(Player player, PlayerRef playerRef, World world, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer, List<Vector3i> blocks, int index, boolean consolidate, Map<String, Integer> consolidatedMap, Random rand, boolean isCreative) {
        if (index >= blocks.size()) {
            if (!isCreative && consolidate && !consolidatedMap.isEmpty()) {
                spawnConsolidatedDrops(store, commandBuffer, blocks.get(0), consolidatedMap, rand);
            }
            return;
        }

        int batchSize = 5 + rand.nextInt(5);
        int end = Math.min(index + batchSize, blocks.size());

        for (int i = index; i < end; i++) {
            Vector3i pos = blocks.get(i);
            BlockType type = world.getBlockType(pos.x, pos.y, pos.z);
            if (type == null) continue;

            if (rand.nextFloat() < 0.30f) {
                try {
                    float pitch = 0.8f + (rand.nextFloat() * 0.5f);
                    SoundUtil.playSoundEvent2dToPlayer(playerRef, SoundEvent.getAssetMap().getIndex("SFX_Stone_Break"), SoundCategory.SFX, 0.6f, pitch);
                } catch (Exception ignored) {}
            }

            if (!isCreative) {
                List<ItemStack> drops = getRealDrops(type);
                if (consolidate) {
                    drops.forEach(d -> consolidatedMap.merge(d.getItemId(), d.getQuantity(), Integer::sum));
                } else {
                    spawnDropsAtPos(store, commandBuffer, pos, drops, rand);
                }
            }

            world.setBlock(pos.x, pos.y, pos.z, "Empty", PERFORM_BLOCK_UPDATE);
        }

        if (index == 0) {
            try {
                SoundUtil.playSoundEvent2dToPlayer(playerRef, SoundEvent.getAssetMap().getIndex("SFX_Stone_Break"), SoundCategory.SFX, 1.0f, 0.8f);
            } catch (Exception ignored) {}
        }

        long nextDelay = 5L + rand.nextInt(10);
        CompletableFuture.delayedExecutor(nextDelay, TimeUnit.MILLISECONDS, world).execute(() -> {
            scheduleSpreadingBreak(player, playerRef, world, store, commandBuffer, blocks, end, consolidate, consolidatedMap, rand, isCreative);
        });
    }

    private void spawnDropsAtPos(Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer, Vector3i pos, List<ItemStack> drops, Random rand) {
        Vector3d spawnPosBase = new Vector3d(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5);
        for (ItemStack stack : drops) {
            spawnStack(store, commandBuffer, spawnPosBase, stack, rand);
        }
    }

    private void spawnConsolidatedDrops(Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer, Vector3i pos, Map<String, Integer> consolidatedMap, Random rand) {
        Vector3d spawnPosBase = new Vector3d(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5);
        consolidatedMap.forEach((id, qty) -> {
            int remaining = qty;
            while (remaining > 0) {
                int amount = Math.min(remaining, 64);
                spawnStack(store, commandBuffer, spawnPosBase, new ItemStack(id, amount), rand);
                remaining -= amount;
            }
        });
    }

    private void spawnStack(Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer, Vector3d basePos, ItemStack stack, Random rand) {
        Vector3d offset = new Vector3d((rand.nextDouble() - 0.5) * 0.4, (rand.nextDouble() - 0.5) * 0.4, (rand.nextDouble() - 0.5) * 0.4);
        Vector3d finalPos = basePos.add(offset);
        Holder<EntityStore> itemHolder = ItemComponent.generateItemDrop(store, stack, finalPos, Vector3f.ZERO, 0, 0.15f, 0);
        if (itemHolder != null) commandBuffer.addEntity(itemHolder, AddReason.SPAWN);
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

    private List<ItemStack> getRealDrops(BlockType type) {
        List<ItemStack> res = new ArrayList<>();
        BlockGathering g = type.getGathering();
        if (g != null && g.getBreaking() != null) {
            BlockBreakingDropType b = g.getBreaking();
            if (b.getDropListId() != null) {
                res.addAll(ItemModule.get().getRandomItemDrops(b.getDropListId()));
            } else if (b.getItemId() != null) {
                res.add(new ItemStack(b.getItemId(), b.getQuantity()));
            }
        }
        if (res.isEmpty()) {
            res.add(new ItemStack((type.getItem() != null) ? type.getItem().getId() : type.getId(), 1));
        }
        return res;
    }

    private void addNeighbors(Vector3i pos, Queue<Vector3i> queue, Set<Vector3i> visited) {
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    Vector3i next = new Vector3i(pos.x + x, pos.y + y, pos.z + z);
                    if (!visited.contains(next)) {
                        visited.add(next);
                        queue.add(next);
                    }
                }
            }
        }
    }

    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }
}