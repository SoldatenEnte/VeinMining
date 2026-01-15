package com.einent.veinmining;

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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.logging.Level;

public class VeinMiningSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("VeinMining");
    private final Config<VeinMiningConfig> config;
    private static final ThreadLocal<Boolean> IS_VEIN_MINING = ThreadLocal.withInitial(() -> false);

    private static final int PERFORM_BLOCK_UPDATE = 256;

    private record PendingDrop(Vector3d position, ItemStack itemStack) {}

    public VeinMiningSystem(Config<VeinMiningConfig> config) {
        super(BreakBlockEvent.class);
        this.config = config;
    }

    @Override
    public void handle(final int index, @Nonnull final ArchetypeChunk<EntityStore> archetypeChunk, @Nonnull final Store<EntityStore> store, @Nonnull final CommandBuffer<EntityStore> commandBuffer, @Nonnull final BreakBlockEvent event) {
        if (IS_VEIN_MINING.get()) return;

        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        VeinMiningConfig cfg = config.get();
        String mode = cfg.getMiningMode();
        if ("off".equalsIgnoreCase(mode)) return;

        MovementStatesComponent moveComp = store.getComponent(ref, MovementStatesComponent.getComponentType());
        if (moveComp == null || !moveComp.getMovementStates().walking) return;

        String blockId = event.getBlockType().getId();
        if (blockId == null || blockId.equals("Empty")) return;

        boolean isAll = mode.equalsIgnoreCase("all");
        boolean isOre = blockId.startsWith("Ore_");
        boolean isWhitelisted = false;

        if (cfg.getWhitelistedBlocks() != null) {
            for (String wb : cfg.getWhitelistedBlocks()) {
                if (wb.equals(blockId)) {
                    isWhitelisted = true;
                    break;
                }
            }
        }

        if (!isAll && !isOre && !isWhitelisted) return;

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
        if (config.get().isRequireValidTool()) {
            if (tool == null) return;
            boolean isValid = toolId.contains("Pickaxe") || toolId.contains("Hatchet") || toolId.contains("Shovel");
            if (!isValid) return;
        }

        boolean isShovel = toolId.contains("Shovel");
        double efficiencyDivisor = isShovel ? 20.0 : 4.0;

        int maxBlocks = Math.max(0, config.get().getMaxVeinSize() - 1);
        double userMultiplier = config.get().getDurabilityMultiplier();
        boolean isCreative = player.getGameMode() == GameMode.Creative;
        boolean consolidate = config.get().isConsolidateDrops();

        Queue<Vector3i> queue = new LinkedList<>();
        Set<Vector3i> visited = new HashSet<>();

        Map<String, Integer> consolidatedMap = new HashMap<>();
        List<PendingDrop> dropsToSpawn = new ArrayList<>();

        visited.add(startPos);
        addNeighbors(startPos, queue, visited);

        Item toolItem = (tool != null) ? tool.getItem() : null;
        double lossPerHit = (toolItem != null && toolItem.getDurabilityLossOnHit() > 0) ? toolItem.getDurabilityLossOnHit() : 1.0;

        int blocksFound = 0;
        double rawLossAccumulated = 0;
        IS_VEIN_MINING.set(true);

        try {
            while (!queue.isEmpty() && blocksFound < maxBlocks) {
                Vector3i pos = queue.poll();

                try {
                    BlockType type = world.getBlockType(pos.x, pos.y, pos.z);
                    if (type == null) continue;

                    if (type.getId().equals(targetId)) {
                        double blockCost = 0;
                        if (!isCreative && tool != null) {
                            double hitsToBreak = calculateHitsToBreak(type, toolItem);
                            blockCost = (hitsToBreak * lossPerHit * userMultiplier) / efficiencyDivisor;

                            if (!tool.isUnbreakable() && (tool.getDurability() - rawLossAccumulated) <= 0) break;
                        }

                        blocksFound++;
                        rawLossAccumulated += blockCost;

                        if (!isCreative) {
                            List<ItemStack> drops = getRealDrops(type);
                            if (consolidate) {
                                drops.forEach(d -> consolidatedMap.merge(d.getItemId(), d.getQuantity(), Integer::sum));
                            } else {
                                Vector3d blockPos = new Vector3d(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5);
                                drops.forEach(d -> dropsToSpawn.add(new PendingDrop(blockPos, d)));
                            }
                        }

                        world.setBlock(pos.x, pos.y, pos.z, "Empty", PERFORM_BLOCK_UPDATE);
                        addNeighbors(pos, queue, visited);
                    }
                } catch (Exception ex) {
                    LOGGER.at(Level.WARNING).log("Error mining block at %s: %s", pos, ex.getMessage());
                }
            }
        } catch (Exception e) {
            LOGGER.at(Level.SEVERE).log("VeinMining loop crashed: %s", e.getMessage());
        } finally {
            IS_VEIN_MINING.set(false);
        }

        if (blocksFound > 0) {
            if (!isCreative && tool != null && !tool.isUnbreakable()) {
                try {
                    player.updateItemStackDurability(pRef, tool, activeContainer, activeSlot, -Math.min(rawLossAccumulated, tool.getDurability()), store);
                    player.sendInventory();
                } catch (Exception e) {
                    LOGGER.at(Level.WARNING).log("Failed to update durability: %s", e.getMessage());
                }
            }

            if (consolidate && !consolidatedMap.isEmpty()) {
                Vector3d basePos = new Vector3d(startPos.x + 0.5, startPos.y + 0.5, startPos.z + 0.5);
                consolidatedMap.forEach((id, qty) -> dropsToSpawn.add(new PendingDrop(basePos, new ItemStack(id, qty))));
            }

            if (!dropsToSpawn.isEmpty()) {
                Random random = new Random();
                for (PendingDrop drop : dropsToSpawn) {
                    int remaining = drop.itemStack.getQuantity();
                    String id = drop.itemStack.getItemId();

                    while (remaining > 0) {
                        int amount = Math.min(remaining, 64);
                        double offsetX = (random.nextDouble() - 0.5) * 0.5;
                        double offsetY = (random.nextDouble() - 0.5) * 0.5;
                        double offsetZ = (random.nextDouble() - 0.5) * 0.5;

                        Vector3d spawnPos = new Vector3d(
                                drop.position.x + offsetX,
                                drop.position.y + offsetY,
                                drop.position.z + offsetZ
                        );

                        Holder<EntityStore> itemHolder = ItemComponent.generateItemDrop(store, new ItemStack(id, amount), spawnPos, Vector3f.ZERO, 0, 0.15f, 0);
                        if (itemHolder != null) commandBuffer.addEntity(itemHolder, AddReason.SPAWN);
                        remaining -= amount;
                    }
                }
            }

            try {
                SoundUtil.playSoundEvent2dToPlayer(playerRef, SoundEvent.getAssetMap().getIndex("SFX_Item_Break"), SoundCategory.SFX, 1.0f, 1.0f);
            } catch (Exception ignored) {}
        }
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

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }
}