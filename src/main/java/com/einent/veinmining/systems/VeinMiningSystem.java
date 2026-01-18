package com.einent.veinmining.systems;

import com.einent.veinmining.config.VeinMiningConfig;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemTool;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemToolSpec;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.blocktype.component.BlockPhysics;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkColumn;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class VeinMiningSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    private final Config<VeinMiningConfig> config;
    private static final ThreadLocal<Boolean> IS_VEIN_MINING = ThreadLocal.withInitial(() -> false);
    private static final int PERFORM_BLOCK_UPDATE = 256;

    public VeinMiningSystem(Config<VeinMiningConfig> config) {
        super(BreakBlockEvent.class);
        this.config = config;
    }

    @Override
    public void handle(int index, ArchetypeChunk<EntityStore> archetypeChunk, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer, BreakBlockEvent event) {
        if (IS_VEIN_MINING.get()) return;

        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComp == null) return;

        VeinMiningConfig cfg = config.get();
        String uuid = uuidComp.getUuid().toString();
        String targetMode = cfg.getPlayerTargetMode(uuid);
        if ("off".equalsIgnoreCase(targetMode)) return;

        MovementStatesComponent moveComp = store.getComponent(ref, MovementStatesComponent.getComponentType());
        if (moveComp == null) return;

        MovementStates states = moveComp.getMovementStates();
        String activationMode = cfg.getPlayerActivation(uuid);
        boolean isActive;
        if ("crouching".equalsIgnoreCase(activationMode)) {
            isActive = states.crouching;
        } else {
            isActive = states.walking;
        }

        if (!isActive) return;

        if (event.getBlockType() == null) return;
        String blockId = event.getBlockType().getId();
        if (blockId.equals("Empty")) return;

        if ("ores".equalsIgnoreCase(targetMode) && !blockId.contains("Ore_")) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            if (player.getWorld() == null) return;
            performVeinMine(player, ref, event.getTargetBlock(), blockId, store, cfg, uuid);
        }, player.getWorld());
    }

    private void performVeinMine(Player player, Ref<EntityStore> pRef, Vector3i startPos, String targetId, Store<EntityStore> store, VeinMiningConfig cfg, String uuid) {
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
            boolean isValid = toolId.contains("Pickaxe") || toolId.contains("Hatchet") || toolId.contains("Shovel");
            if (!isValid) return;
        }

        int maxBlocks = Math.max(0, cfg.getMaxVeinSize() - 1);
        String pattern = cfg.getPlayerPattern(uuid);
        String oriMode = cfg.getPlayerOrientation(uuid);

        Vector3i hitFace = getHitFace(startPos, store, pRef);
        Vector3i originStart = getMultiblockOrigin(world, startPos);

        List<Vector3i> blocksToBreak;
        if ("freeform".equalsIgnoreCase(pattern)) {
            blocksToBreak = getFreeformBlocks(world, startPos, targetId, maxBlocks);
        } else {
            blocksToBreak = getPatternBlocks(store, pRef, originStart, pattern, maxBlocks, oriMode, hitFace)
                    .stream()
                    .map(pos -> getMultiblockOrigin(world, pos))
                    .distinct()
                    .collect(Collectors.toList());
        }

        blocksToBreak.removeIf(pos -> {
            BlockType t = world.getBlockType(pos.x, pos.y, pos.z);
            return t == null || !t.getId().equals(targetId);
        });

        if (blocksToBreak.isEmpty()) return;

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

        if (finalBlocks.isEmpty()) return;

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
                    processBlockBreak(world, pos, isCreative, consolidate, consolidatedMap, store, pRef, tool);
                }
            } finally {
                IS_VEIN_MINING.set(false);
            }

            if (playerRefComp != null) playSound(playerRefComp, "SFX_Pickaxe_T2_Impact_Nice", 7.0f, 0.7f);
            if (!isCreative && consolidate && !consolidatedMap.isEmpty()) {
                spawnConsolidatedDrops(store, finalBlocks.get(0), consolidatedMap, rand);
            }
        } else {
            if (playerRefComp != null) playSound(playerRefComp, "SFX_Pickaxe_T2_Impact_Nice", 7.0f, 0.7f);
            scheduleSpreadingBreak(playerRefComp, pRef, world, store, finalBlocks, 0, consolidate, consolidatedMap, rand, isCreative, tool);
        }
    }

    private List<Vector3i> getFreeformBlocks(World world, Vector3i startPos, String targetId, int max) {
        List<Vector3i> result = new ArrayList<>();
        Queue<Vector3i> queue = new LinkedList<>();
        Set<Vector3i> visitedPhysical = new HashSet<>();
        Set<Vector3i> visitedOrigins = new HashSet<>();

        Vector3i startOrigin = getMultiblockOrigin(world, startPos);
        visitedOrigins.add(startOrigin);
        result.add(startOrigin);

        visitedPhysical.add(startPos);
        addNeighbors(startPos, queue, visitedPhysical);

        int bufferLimit = Math.min(max * 10, 4096);

        while (!queue.isEmpty() && result.size() < bufferLimit) {
            Vector3i pos = queue.poll();
            BlockType type = world.getBlockType(pos.x, pos.y, pos.z);

            if (type != null && type.getId().equals(targetId)) {
                Vector3i origin = getMultiblockOrigin(world, pos);

                if (!visitedOrigins.contains(origin)) {
                    visitedOrigins.add(origin);
                    result.add(origin);
                }

                addNeighbors(pos, queue, visitedPhysical);
            }
        }

        result.sort(Comparator.<Vector3i>comparingInt(vec -> {
            int dx = Math.abs(vec.x - startOrigin.x);
            int dy = Math.abs(vec.y - startOrigin.y);
            int dz = Math.abs(vec.z - startOrigin.z);
            return Math.max(dx, Math.max(dy, dz));
        }).thenComparingDouble(vec -> {
            double dx = vec.x - startOrigin.x;
            double dy = vec.y - startOrigin.y;
            double dz = vec.z - startOrigin.z;
            return dx * dx + dy * dy + dz * dz;
        }));

        if (result.size() > max) {
            return result.subList(0, max);
        }
        return result;
    }

    private Vector3i getMultiblockOrigin(World world, Vector3i pos) {
        try {
            ComponentAccessor<ChunkStore> chunkStoreAccessor = world.getChunkStore().getStore();
            ChunkStore chunkStoreData = chunkStoreAccessor.getExternalData();
            long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.x, pos.z);
            Ref<ChunkStore> chunkRef = chunkStoreData.getChunkReference(chunkIndex);

            if (chunkRef != null && chunkRef.isValid()) {
                com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk blockChunk =
                        chunkStoreAccessor.getComponent(chunkRef, com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk.getComponentType());

                if (blockChunk != null) {
                    BlockSection section = blockChunk.getSectionAtBlockY(pos.y);
                    int filler = section.getFiller(pos.x, pos.y, pos.z);
                    int fx = FillerBlockUtil.unpackX(filler);
                    int fy = FillerBlockUtil.unpackY(filler);
                    int fz = FillerBlockUtil.unpackZ(filler);
                    if (fx != 0 || fy != 0 || fz != 0) {
                        return new Vector3i(pos.x - fx, pos.y - fy, pos.z - fz);
                    }
                }
            }
        } catch (Exception ignored) {}
        return pos;
    }

    private List<Vector3i> getPatternBlocks(Store<EntityStore> store, Ref<EntityStore> ref, Vector3i start, String pattern, int max, String oriMode, Vector3i hitFace) {
        List<Vector3i> candidates = new ArrayList<>();

        HeadRotation headRot = store.getComponent(ref, HeadRotation.getComponentType());
        TransformComponent trans = store.getComponent(ref, TransformComponent.getComponentType());

        float yaw = 0, pitch = 0;
        if (headRot != null) {
            Vector3f rotVec = headRot.getRotation();
            yaw = rotVec.getYaw();
            pitch = rotVec.getPitch();
        } else if (trans != null) {
            Vector3f rot = trans.getRotation();
            yaw = rot.getYaw();
            pitch = rot.getPitch();
        }

        Vector3f playerLook = getForwardVector(yaw, pitch);
        Vector3i fwd;
        Vector3i up;
        Vector3i right;

        if (oriMode.equalsIgnoreCase("block")) {
            fwd = new Vector3i(-hitFace.x, -hitFace.y, -hitFace.z);

            if (fwd.y != 0) {
                float hx = (float)-Math.sin(yaw);
                float hz = (float)-Math.cos(yaw);
                Vector3f hFwdF = new Vector3f(hx, 0, hz);
                Vector3i gridUp = getDominantAxis(hFwdF);

                if (gridUp.x == 0 && gridUp.z == 0) gridUp = new Vector3i(0, 0, -1);

                if (fwd.y > 0) {
                    up = new Vector3i(-gridUp.x, -gridUp.y, -gridUp.z);
                } else {
                    up = gridUp;
                }
                right = crossProduct(up, fwd);
            } else {
                up = new Vector3i(0, 1, 0);
                right = crossProduct(up, fwd);
            }
        } else {
            fwd = getDominantAxis(playerLook);

            float hx = (float)-Math.sin(yaw);
            float hz = (float)-Math.cos(yaw);
            Vector3i horizontalFwd = getDominantAxis(new Vector3f(hx, 0, hz));
            Vector3i horizontalRight = new Vector3i(-horizontalFwd.z, 0, horizontalFwd.x);

            if (fwd.y != 0) {
                right = horizontalRight;
                up = (fwd.y > 0) ? new Vector3i(-horizontalFwd.x, 0, -horizontalFwd.z) : horizontalFwd;
            } else {
                if (Math.abs(fwd.z) > Math.abs(fwd.x)) {
                    right = (fwd.z > 0) ? new Vector3i(-1, 0, 0) : new Vector3i(1, 0, 0);
                } else {
                    right = (fwd.x > 0) ? new Vector3i(0, 0, 1) : new Vector3i(0, 0, -1);
                }
                up = new Vector3i(0, 1, 0);
            }
        }

        int width = 1, height = 1, depthLimit = max + 1;

        if (pattern.equals("cube")) {
            width = 3; height = 3; depthLimit = 3;
        } else if (pattern.equals("wall3")) {
            width = 3; height = 3; depthLimit = 1;
        } else if (pattern.equals("wall5")) {
            width = 5; height = 5; depthLimit = 1;
        } else if (pattern.startsWith("tunnel")) {
            if (pattern.endsWith("3")) {
                width = 3; height = 3;
            } else if (pattern.endsWith("2")) {
                width = 1; height = 2;
            }
        }

        if (!pattern.equals("diagonal")) {
            for (int d = 0; d < depthLimit; d++) {
                for (int w = -(width/2); w <= (width/2); w++) {
                    for (int h = 0; h < height; h++) {
                        int dy = h;
                        if (height == 2) dy = -h;
                        if (height == 3) dy = h - 1;
                        if (height == 5) dy = h - 2;

                        if (d == 0 && w == 0 && dy == 0) continue;

                        Vector3i offset = new Vector3i(
                                fwd.x * d + right.x * w + up.x * dy,
                                fwd.y * d + right.y * w + up.y * dy,
                                fwd.z * d + right.z * w + up.z * dy
                        );
                        candidates.add(add(start, offset));
                    }
                }
            }
        } else {
            for (int i = 1; i <= max; i++) {
                Vector3i offset;
                if (oriMode.equalsIgnoreCase("block")) {
                    offset = new Vector3i((fwd.x + up.x) * i, (fwd.y + up.y) * i, (fwd.z + up.z) * i);
                } else {
                    int vert = (playerLook.y > 0) ? 1 : -1;
                    if (fwd.y != 0) {
                        float hx = (float)-Math.sin(yaw);
                        float hz = (float)-Math.cos(yaw);
                        Vector3i hFwd = getDominantAxis(new Vector3f(hx, 0, hz));
                        offset = new Vector3i(hFwd.x * i, fwd.y * i, hFwd.z * i);
                    } else {
                        offset = new Vector3i(fwd.x * i, (fwd.y + vert) * i, fwd.z * i);
                    }
                }
                candidates.add(add(start, offset));
            }
        }

        candidates.sort(Comparator.comparingDouble(pos -> distanceSq(pos, start)));
        return candidates.stream().limit(max).collect(Collectors.toList());
    }

    private Vector3i getHitFace(Vector3i target, Store<EntityStore> store, Ref<EntityStore> ref) {
        TransformComponent trans = store.getComponent(ref, TransformComponent.getComponentType());
        HeadRotation head = store.getComponent(ref, HeadRotation.getComponentType());

        if (trans == null || head == null) return new Vector3i(0, 1, 0);

        Vector3d origin = trans.getPosition().add(0, 1.62, 0);
        Vector3f rot = head.getRotation();
        Vector3f dirF = getForwardVector(rot.getYaw(), rot.getPitch());
        Vector3d dir = new Vector3d(dirF.x, dirF.y, dirF.z);

        double minX = target.x, minY = target.y, minZ = target.z;
        double maxX = target.x + 1.0, maxY = target.y + 1.0, maxZ = target.z + 1.0;

        double tMin = Double.MAX_VALUE;
        Vector3i normal = new Vector3i(0, 1, 0);

        if (Math.abs(dir.x) > 1e-6) {
            double t1 = (minX - origin.x) / dir.x;
            double t2 = (maxX - origin.x) / dir.x;
            if (checkIntersection(t1, tMin, origin, dir, minY, maxY, minZ, maxZ, 1, 2)) {
                tMin = t1; normal = new Vector3i(-1, 0, 0);
            }
            if (checkIntersection(t2, tMin, origin, dir, minY, maxY, minZ, maxZ, 1, 2)) {
                tMin = t2; normal = new Vector3i(1, 0, 0);
            }
        }

        if (Math.abs(dir.y) > 1e-6) {
            double t1 = (minY - origin.y) / dir.y;
            double t2 = (maxY - origin.y) / dir.y;
            if (checkIntersection(t1, tMin, origin, dir, minX, maxX, minZ, maxZ, 0, 2)) {
                tMin = t1; normal = new Vector3i(0, -1, 0);
            }
            if (checkIntersection(t2, tMin, origin, dir, minX, maxX, minZ, maxZ, 0, 2)) {
                tMin = t2; normal = new Vector3i(0, 1, 0);
            }
        }

        if (Math.abs(dir.z) > 1e-6) {
            double t1 = (minZ - origin.z) / dir.z;
            double t2 = (maxZ - origin.z) / dir.z;
            if (checkIntersection(t1, tMin, origin, dir, minX, maxX, minY, maxY, 0, 1)) {
                tMin = t1; normal = new Vector3i(0, 0, -1);
            }
            if (checkIntersection(t2, tMin, origin, dir, minX, maxX, minY, maxY, 0, 1)) {
                tMin = t2; normal = new Vector3i(0, 0, 1);
            }
        }
        return normal;
    }

    private boolean checkIntersection(double t, double tMin, Vector3d origin, Vector3d dir, double minA, double maxA, double minB, double maxB, int idxA, int idxB) {
        if (t <= 0 || t >= tMin) return false;
        double a = (idxA == 0 ? origin.x : (idxA == 1 ? origin.y : origin.z)) + (idxA == 0 ? dir.x : (idxA == 1 ? dir.y : dir.z)) * t;
        double b = (idxB == 0 ? origin.x : (idxB == 1 ? origin.y : origin.z)) + (idxB == 0 ? dir.x : (idxB == 1 ? dir.y : dir.z)) * t;
        return a >= minA && a <= maxA && b >= minB && b <= maxB;
    }

    private double distanceSq(Vector3i a, Vector3i b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        double dz = a.z - b.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private Vector3f getForwardVector(float yaw, float pitch) {
        float x = (float)(-Math.sin(yaw) * Math.cos(pitch));
        float y = (float)Math.sin(pitch);
        float z = (float)-(Math.cos(yaw) * Math.cos(pitch));
        return new Vector3f(x, y, z);
    }

    private Vector3i getDominantAxis(Vector3f v) {
        float ax = Math.abs(v.x);
        float ay = Math.abs(v.y);
        float az = Math.abs(v.z);
        if (ax > ay && ax > az) return new Vector3i((v.x > 0 ? 1 : -1), 0, 0);
        if (ay > ax && ay > az) return new Vector3i(0, (v.y > 0 ? 1 : -1), 0);
        return new Vector3i(0, 0, (v.z > 0 ? 1 : -1));
    }

    private Vector3i crossProduct(Vector3i a, Vector3i b) {
        return new Vector3i(
                a.y * b.z - a.z * b.y,
                a.z * b.x - a.x * b.z,
                a.x * b.y - a.y * b.x
        );
    }

    private Vector3i add(Vector3i a, Vector3i b) {
        return new Vector3i(a.x + b.x, a.y + b.y, a.z + b.z);
    }

    private void processBlockBreak(World world, Vector3i pos, boolean isCreative, boolean consolidate, Map<String, Integer> consolidatedMap, Store<EntityStore> store, Ref<EntityStore> entityRef, ItemStack tool) {
        BlockType type = world.getBlockType(pos.x, pos.y, pos.z);
        if (type == null) return;

        store.invoke(entityRef, new BreakBlockEvent(tool, pos, type));

        if (!isCreative) {
            List<ItemStack> drops = getRealDrops(world, pos, type);
            if (consolidate) {
                drops.forEach(d -> consolidatedMap.merge(d.getItemId(), d.getQuantity(), Integer::sum));
            }
        }
        world.setBlock(pos.x, pos.y, pos.z, "Empty", PERFORM_BLOCK_UPDATE);
    }

    private void scheduleSpreadingBreak(PlayerRef playerRef, Ref<EntityStore> entityRef, World world, Store<EntityStore> store, List<Vector3i> blocks, int index, boolean consolidate, Map<String, Integer> consolidatedMap, Random rand, boolean isCreative, ItemStack tool) {
        if (index >= blocks.size()) {
            if (!isCreative && consolidate && !consolidatedMap.isEmpty()) {
                spawnConsolidatedDrops(store, blocks.get(0), consolidatedMap, rand);
            }
            return;
        }

        int batchSize = 3 + rand.nextInt(3);
        int end = Math.min(index + batchSize, blocks.size());

        for (int i = index; i < end; i++) {
            Vector3i pos = blocks.get(i);
            BlockType type = world.getBlockType(pos.x, pos.y, pos.z);
            if (type == null) continue;

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
                List<ItemStack> drops = getRealDrops(world, pos, type);
                if (consolidate) {
                    drops.forEach(d -> consolidatedMap.merge(d.getItemId(), d.getQuantity(), Integer::sum));
                } else {
                    spawnDropsAtPos(store, pos, drops, rand);
                }
            }

            world.setBlock(pos.x, pos.y, pos.z, "Empty", PERFORM_BLOCK_UPDATE);
        }

        long nextDelay = 4L + rand.nextInt(5);
        CompletableFuture.delayedExecutor(nextDelay, TimeUnit.MILLISECONDS, world).execute(() -> {
            scheduleSpreadingBreak(playerRef, entityRef, world, store, blocks, end, consolidate, consolidatedMap, rand, isCreative, tool);
        });
    }

    private void spawnDropsAtPos(Store<EntityStore> store, Vector3i pos, List<ItemStack> drops, Random rand) {
        Vector3d spawnPosBase = new Vector3d(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5);
        for (ItemStack stack : drops) {
            spawnStack(store, spawnPosBase, stack, rand);
        }
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

    private boolean isDecoBlock(World world, Vector3i pos) {
        try {
            ComponentAccessor<ChunkStore> chunkStoreAccessor = world.getChunkStore().getStore();
            ChunkStore chunkStoreData = chunkStoreAccessor.getExternalData();
            long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.x, pos.z);
            Ref<ChunkStore> chunkRef = chunkStoreData.getChunkReference(chunkIndex);
            if (chunkRef == null || !chunkRef.isValid()) return false;
            ChunkColumn chunkColumn = chunkStoreAccessor.getComponent(chunkRef, ChunkColumn.getComponentType());
            if (chunkColumn == null) return false;
            Ref<ChunkStore> sectionRef = chunkColumn.getSection(ChunkUtil.chunkCoordinate(pos.y));
            if (sectionRef == null || !sectionRef.isValid()) return false;
            BlockPhysics blockPhysics = chunkStoreAccessor.getComponent(sectionRef, BlockPhysics.getComponentType());
            return blockPhysics != null && blockPhysics.isDeco(pos.x, pos.y, pos.z);
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private List<ItemStack> getRealDrops(World world, Vector3i pos, BlockType type) {
        List<ItemStack> res = new ArrayList<>();
        BlockGathering g = type.getGathering();

        if (g != null && g.shouldUseDefaultDropWhenPlaced() && isDecoBlock(world, pos)) {
            if (type.getItem() != null) {
                res.add(new ItemStack(type.getItem().getId(), 1));
            } else {
                res.add(new ItemStack(type.getId(), 1));
            }
            return res;
        }

        String dropListId = null;
        String itemId = null;
        int quantity = 1;

        if (g != null) {
            if (g.getBreaking() != null) {
                dropListId = g.getBreaking().getDropListId();
                itemId = g.getBreaking().getItemId();
                quantity = g.getBreaking().getQuantity();
            } else if (g.getSoft() != null) {
                dropListId = g.getSoft().getDropListId();
                itemId = g.getSoft().getItemId();
            }
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
            if (itemId != null) {
                res.add(new ItemStack(itemId, quantity));
            }
            return res;
        }

        if (type.getItem() != null) {
            res.add(new ItemStack(type.getItem().getId(), 1));
        } else {
            res.add(new ItemStack(type.getId(), 1));
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