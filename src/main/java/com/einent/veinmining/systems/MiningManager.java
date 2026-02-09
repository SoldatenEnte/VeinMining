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
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
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

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class MiningManager {

    public static final ThreadLocal<Boolean> IS_VEIN_MINING = ThreadLocal.withInitial(() -> false);
    private static final int PERFORM_BLOCK_UPDATE = 256;
    private static final Set<Vector3i> ACTIVE_VEINS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static Method GET_DROPS_METHOD;
    private static Object ITEM_MODULE_INSTANCE;

    static {
        try {
            Class<?> itemModuleClass = Class.forName("com.hypixel.hytale.server.core.modules.item.ItemModule");
            ITEM_MODULE_INSTANCE = itemModuleClass.getMethod("get").invoke(null);
            GET_DROPS_METHOD = itemModuleClass.getMethod("getRandomItemDrops", String.class);
        } catch (Exception ignored) {}
    }

    private final Config<VeinMiningConfig> config;
    private final PatternCalculator patternCalculator;

    public MiningManager(Config<VeinMiningConfig> config) {
        this.config = config;
        this.patternCalculator = new PatternCalculator();
    }

    public void performVeinMine(Player player, Ref<EntityStore> pRef, Vector3i startPos, String targetId, BlockType startBlockType, Store<EntityStore> store, String uuid, int effectiveLimit) {
        VeinMiningConfig cfg = config.get();
        boolean isAdmin = player.hasPermission("veinmining.admin");

        if (!cfg.isModEnabled(uuid, isAdmin)) return;

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
            boolean isValid = false;
            for (String allowedTool : cfg.getValidTools()) {
                if (toolId.contains(allowedTool)) { isValid = true; break; }
            }
            if (!isValid) return;
        }

        List<String> blockBlacklist = cfg.getBlockBlacklist();
        if (blockBlacklist.contains(targetId)) return;
        List<String> blockWhitelist = cfg.getBlockWhitelist();
        if (!blockWhitelist.isEmpty() && !blockWhitelist.contains(targetId)) return;

        String pattern = cfg.getValidatedPattern(uuid, null, isAdmin);
        String targetMode = cfg.getValidatedTargetMode(uuid, null, isAdmin);
        String oriMode = cfg.getPlayerOrientation(uuid);

        Vector3i hitFace = patternCalculator.getHitFace(startPos, store, pRef);
        Vector3i originStart = patternCalculator.getMultiblockOrigin(world, startPos);

        List<Vector3i> blocksToBreak;
        if ("freeform".equalsIgnoreCase(pattern)) {
            blocksToBreak = patternCalculator.getFreeformBlocks(world, startPos, targetId, effectiveLimit);
        } else {
            blocksToBreak = patternCalculator.getPatternBlocks(world, targetId, store, pRef, originStart, pattern, effectiveLimit, oriMode, hitFace)
                    .stream()
                    .map(pos -> patternCalculator.getMultiblockOrigin(world, pos))
                    .distinct()
                    .collect(Collectors.toList());
        }

        blocksToBreak.remove(startPos);

        blocksToBreak.removeIf(pos -> {
            if (ACTIVE_VEINS.contains(pos)) return true;
            BlockType t = world.getBlockType(pos.x, pos.y, pos.z);
            if (t == null) return true;
            String tid = t.getId();
            if (tid.contains("_Cracked") && targetMode.equals("ores")) return true;
            return !tid.equals(targetId) || blockBlacklist.contains(tid);
        });

        int neighborsLimit = Math.max(0, effectiveLimit - 1);
        if (blocksToBreak.size() > neighborsLimit) {
            blocksToBreak = blocksToBreak.subList(0, neighborsLimit);
        }

        boolean isCreative = player.getGameMode() == GameMode.Creative;
        String dropMode = cfg.getDropMode();
        double userMultiplier = cfg.getDurabilityMultiplier();
        Item toolItem = (tool != null) ? tool.getItem() : null;
        double lossPerHit = (toolItem != null && toolItem.getDurabilityLossOnHit() > 0) ? toolItem.getDurabilityLossOnHit() : 1.0;

        double totalDurabilityCost = 0;
        if (!isCreative && tool != null) {
            totalDurabilityCost += (calculateHitsToBreak(startBlockType, toolItem) * lossPerHit * userMultiplier) / (toolId.contains("Shovel") ? 20.0 : 4.0);
        }

        List<Vector3i> finalBlocks = new ArrayList<>();
        IS_VEIN_MINING.set(true);
        try {
            for (Vector3i pos : blocksToBreak) {
                BlockType type = world.getBlockType(pos.x, pos.y, pos.z);
                if (type == null) continue;
                double blockCost = 0;
                if (!isCreative && tool != null) {
                    blockCost = (calculateHitsToBreak(type, toolItem) * lossPerHit * userMultiplier) / (toolId.contains("Shovel") ? 20.0 : 4.0);
                    if (!tool.isUnbreakable() && (tool.getDurability() - (totalDurabilityCost + blockCost)) <= 0) break;
                }
                totalDurabilityCost += blockCost; finalBlocks.add(pos);
            }
        } finally { IS_VEIN_MINING.set(false); }

        if (finalBlocks.size() < blocksToBreak.size()) {
            Set<Vector3i> scheduled = new HashSet<>(finalBlocks);
            for (Vector3i p : blocksToBreak) {
                if (!scheduled.contains(p)) ACTIVE_VEINS.remove(p);
            }
        }

        if (!isCreative) {
            List<ItemStack> drops = getRealDrops(world, startPos, startBlockType, toolId);
            if (!drops.isEmpty()) {
                spawnDropsAtPos(store, startPos, drops, new Random(), dropMode, startPos, pRef);
            }
        }

        if (!isCreative && tool != null && !tool.isUnbreakable()) {
            player.updateItemStackDurability(pRef, tool, activeContainer, activeSlot, -Math.min(totalDurabilityCost, tool.getDurability()), store);
            player.sendInventory();
        }

        Random rand = new Random();
        PlayerRef playerRefComp = store.getComponent(pRef, PlayerRef.getComponentType());

        if (cfg.isInstantBreak()) {
            IS_VEIN_MINING.set(true);
            try {
                for (Vector3i pos : finalBlocks) {
                    processBlockBreak(world, pos, isCreative, dropMode, store, pRef, tool, toolId, startPos);
                    ACTIVE_VEINS.remove(pos);
                }
            } finally { IS_VEIN_MINING.set(false); }
            if (playerRefComp != null) playSound(playerRefComp, "SFX_Pickaxe_T2_Impact_Nice", 7.0f, 0.7f);
        } else {
            if (playerRefComp != null) playSound(playerRefComp, "SFX_Pickaxe_T2_Impact_Nice", 7.0f, 0.7f);
            scheduleSpreadingBreak(playerRefComp, pRef, world, store, finalBlocks, 0, dropMode, rand, isCreative, tool, toolId, startPos);
        }
    }

    private void processBlockBreak(World world, Vector3i pos, boolean isCreative, String dropMode, Store<EntityStore> store, Ref<EntityStore> entityRef, ItemStack tool, String toolId, Vector3i sourcePos) {
        BlockType type = world.getBlockType(pos.x, pos.y, pos.z);
        if (type == null) return;
        store.invoke(entityRef, new BreakBlockEvent(tool, pos, type));
        if (!isCreative) {
            List<ItemStack> drops = getRealDrops(world, pos, type, toolId);
            if (!drops.isEmpty()) {
                spawnDropsAtPos(store, pos, drops, new Random(), dropMode, sourcePos, entityRef);
            }
        }
        world.setBlock(pos.x, pos.y, pos.z, "Empty", PERFORM_BLOCK_UPDATE);
    }

    private void scheduleSpreadingBreak(PlayerRef playerRef, Ref<EntityStore> entityRef, World world, Store<EntityStore> store, List<Vector3i> blocks, int index, String dropMode, Random rand, boolean isCreative, ItemStack tool, String toolId, Vector3i sourcePos) {
        if (index >= blocks.size()) {
            return;
        }
        int batchSize = 3 + rand.nextInt(3);
        int end = Math.min(index + batchSize, blocks.size());
        for (int i = index; i < end; i++) {
            Vector3i pos = blocks.get(i);
            if (rand.nextFloat() < 0.20f && playerRef != null) {
                playSound(playerRef, "SFX_Stone_Break", 0.5f, 0.8f + rand.nextFloat() * 0.4f);
            }
            IS_VEIN_MINING.set(true);
            try {
                processBlockBreak(world, pos, isCreative, dropMode, store, entityRef, tool, toolId, sourcePos);
            } finally {
                IS_VEIN_MINING.set(false);
            }
            ACTIVE_VEINS.remove(pos);
        }
        CompletableFuture.delayedExecutor(4L + rand.nextInt(5), TimeUnit.MILLISECONDS, world).execute(() -> scheduleSpreadingBreak(playerRef, entityRef, world, store, blocks, end, dropMode, rand, isCreative, tool, toolId, sourcePos));
    }

    @SuppressWarnings("unchecked")
    private List<ItemStack> getRealDrops(World world, Vector3i pos, BlockType type, String toolId) {
        List<ItemStack> res = new ArrayList<>();
        if (type == null || "Empty".equals(type.getId())) return res;

        if (toolId != null && toolId.contains("Shears")) {
            String id = (type.getItem() != null) ? type.getItem().getId() : type.getId();
            if (isValidId(id)) { res.add(new ItemStack(id, 1)); return res; }
        }
        BlockGathering g = type.getGathering(); if (g == null) return res;
        if (g.shouldUseDefaultDropWhenPlaced() && isDecoBlock(world, pos)) {
            String id = (type.getItem() != null) ? type.getItem().getId() : type.getId();
            if (isValidId(id)) res.add(new ItemStack(id, 1)); return res;
        }
        String dlid = null; String iid = null; int qty = 1;
        if (g.getBreaking() != null) { dlid = g.getBreaking().getDropListId(); iid = g.getBreaking().getItemId(); qty = g.getBreaking().getQuantity(); }
        else if (g.getSoft() != null) { dlid = g.getSoft().getDropListId(); iid = g.getSoft().getItemId(); }
        if (dlid != null || iid != null) {
            if (dlid != null && GET_DROPS_METHOD != null && ITEM_MODULE_INSTANCE != null) try {
                for (int i = 0; i < qty; i++) {
                    List<ItemStack> d = (List<ItemStack>) GET_DROPS_METHOD.invoke(ITEM_MODULE_INSTANCE, dlid);
                    if (d != null) {
                        for (ItemStack s : d) {
                            if (s != null && isValidId(s.getItemId())) res.add(s);
                        }
                    }
                }
            } catch (Exception ignored) {}
            if (isValidId(iid)) res.add(new ItemStack(iid, qty)); return res;
        }
        String fid = (type.getItem() != null) ? type.getItem().getId() : type.getId();
        if (isValidId(fid)) res.add(new ItemStack(fid, 1)); return res;
    }

    private boolean isValidId(String id) { return id != null && !id.trim().isEmpty() && !id.equalsIgnoreCase("Empty"); }
    private boolean isDecoBlock(World world, Vector3i pos) {
        try {
            ChunkStore cs = world.getChunkStore(); Ref<ChunkStore> ref = cs.getChunkSectionReference(pos.x, pos.y, pos.z);
            if (ref != null && ref.isValid()) {
                BlockPhysics bp = cs.getStore().getComponent(ref, BlockPhysics.getComponentType());
                return bp != null && bp.isDeco(pos.x, pos.y, pos.z);
            }
        } catch (Exception ignored) {} return false;
    }

    private void spawnDropsAtPos(Store<EntityStore> store, Vector3i blockPos, List<ItemStack> drops, Random rand, String dropMode, Vector3i sourcePos, Ref<EntityStore> playerRef) {
        Vector3d base;
        String mode = dropMode.toLowerCase().trim();

        if (mode.equals("at_player") || mode.equals("player")) {
            TransformComponent t = store.getComponent(playerRef, TransformComponent.getComponentType());
            if (t != null) {
                base = t.getPosition().clone().add(0, 0.2, 0);
            } else {
                base = new Vector3d(sourcePos.x + 0.5, sourcePos.y + 0.5, sourcePos.z + 0.5);
            }
        } else if (mode.equals("at_source") || mode.equals("at_break") || mode.equals("break") || mode.equals("source")) {
            base = new Vector3d(sourcePos.x + 0.5, sourcePos.y + 0.5, sourcePos.z + 0.5);
        } else {
            base = new Vector3d(blockPos.x + 0.5, blockPos.y + 0.5, blockPos.z + 0.5);
        }

        for (ItemStack stack : drops) spawnStack(store, base, stack, rand);
    }

    private void spawnStack(Store<EntityStore> store, Vector3d base, ItemStack stack, Random rand) {
        if (stack == null || !isValidId(stack.getItemId())) return;
        Vector3d pos = base.add(new Vector3d((rand.nextDouble() - 0.5) * 0.5, (rand.nextDouble() - 0.5) * 0.5, (rand.nextDouble() - 0.5) * 0.5));
        Holder<EntityStore> item = ItemComponent.generateItemDrop(store, stack, pos, Vector3f.ZERO, 0, 0.15f, 0);
        if (item != null) store.addEntity(item, AddReason.SPAWN);
    }

    private void playSound(PlayerRef ref, String sound, float vol, float pitch) {
        if (!config.get().isMasterSoundEnabled()) return;
        try { SoundUtil.playSoundEvent2dToPlayer(ref, SoundEvent.getAssetMap().getIndex(sound), SoundCategory.SFX, vol, pitch); } catch (Exception ignored) {}
    }

    private double calculateHitsToBreak(BlockType type, Item tool) {
        if (tool == null) return 5.0; BlockGathering g = type.getGathering(); if (g == null || g.getBreaking() == null) return 1.0;
        BlockBreakingDropType b = g.getBreaking(); String req = (b.getGatherType() != null) ? b.getGatherType() : "pickaxe";
        int qual = (b.getQuality() > 0) ? b.getQuality() : 1; ItemTool tc = tool.getTool(); if (tc == null || tc.getSpecs() == null) return 5.0;
        for (ItemToolSpec s : tc.getSpecs()) { if (s.getGatherType().equalsIgnoreCase(req)) return (s.getPower() > 0) ? (double) qual / s.getPower() : 10.0; }
        return 10.0;
    }
}