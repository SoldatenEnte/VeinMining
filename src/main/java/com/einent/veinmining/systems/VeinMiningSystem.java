package com.einent.veinmining.systems;

import com.einent.veinmining.config.VeinMiningConfig;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
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
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;

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
    public void handle(final int index, final ArchetypeChunk<EntityStore> archetypeChunk, final Store<EntityStore> store, final CommandBuffer<EntityStore> commandBuffer, final BreakBlockEvent event) {
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
        if (moveComp == null || !moveComp.getMovementStates().walking) return;

        String blockId = event.getBlockType().getId();
        if (blockId == null || blockId.equals("Empty")) return;

        PlayerRef playerRefComp = store.getComponent(ref, PlayerRef.getComponentType());
        performVeinMine(player, playerRefComp, ref, event.getTargetBlock(), blockId, store, commandBuffer, cfg, targetMode, uuid);
    }

    private void performVeinMine(Player player, PlayerRef playerRef, Ref<EntityStore> pRef, Vector3i startPos, String targetId, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer, VeinMiningConfig cfg, String targetMode, String uuid) {
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

        List<Vector3i> blocksToBreak;
        if ("freeform".equalsIgnoreCase(pattern)) {
            blocksToBreak = getFreeformBlocks(world, startPos, targetId, maxBlocks);
        } else {
            blocksToBreak = getPatternBlocks(store, pRef, startPos, pattern, maxBlocks, oriMode, hitFace);
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

        if (cfg.isInstantBreak()) {
            for (Vector3i pos : finalBlocks) {
                processBlockBreak(world, pos, isCreative, consolidate, consolidatedMap);
            }
            playSound(playerRef, "SFX_Stone_Break", 1.0f, 0.8f);
            if (!isCreative && consolidate && !consolidatedMap.isEmpty()) {
                spawnConsolidatedDrops(store, commandBuffer, finalBlocks.get(0), consolidatedMap, rand);
            }
        } else {
            playSound(playerRef, "SFX_Pickaxe_T2_Impact_Nice", 7.0f, 0.8f);
            scheduleSpreadingBreak(playerRef, world, store, commandBuffer, finalBlocks, 0, consolidate, consolidatedMap, rand, isCreative);
        }
    }

    private List<Vector3i> getFreeformBlocks(World world, Vector3i startPos, String targetId, int max) {
        List<Vector3i> result = new ArrayList<>();
        Queue<Vector3i> queue = new LinkedList<>();
        Set<Vector3i> visited = new HashSet<>();

        visited.add(startPos);
        addNeighbors(startPos, queue, visited);

        while (!queue.isEmpty() && result.size() < max) {
            Vector3i pos = queue.poll();
            BlockType type = world.getBlockType(pos.x, pos.y, pos.z);
            if (type == null) continue;

            if (type.getId().equals(targetId)) {
                result.add(pos);
                addNeighbors(pos, queue, visited);
            }
        }
        return result;
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
            Vector3i fwdAxis = getDominantAxis(playerLook);
            fwd = fwdAxis;

            float hx = (float)-Math.sin(yaw);
            float hz = (float)-Math.cos(yaw);
            Vector3i horizontalFwd = getDominantAxis(new Vector3f(hx, 0, hz));
            Vector3i horizontalRight = new Vector3i(-horizontalFwd.z, 0, horizontalFwd.x);

            if (fwd.y != 0) {
                right = horizontalRight;
                up = (fwd.y > 0) ? new Vector3i(-horizontalFwd.x, 0, -horizontalFwd.z) : horizontalFwd;
            } else {
                right = horizontalRight;
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

    private void processBlockBreak(World world, Vector3i pos, boolean isCreative, boolean consolidate, Map<String, Integer> consolidatedMap) {
        BlockType type = world.getBlockType(pos.x, pos.y, pos.z);
        if (type == null) return;

        if (!isCreative) {
            List<ItemStack> drops = getRealDrops(type);
            if (consolidate) {
                drops.forEach(d -> consolidatedMap.merge(d.getItemId(), d.getQuantity(), Integer::sum));
            }
        }
        world.setBlock(pos.x, pos.y, pos.z, "Empty", PERFORM_BLOCK_UPDATE);
    }

    private void scheduleSpreadingBreak(PlayerRef playerRef, World world, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer, List<Vector3i> blocks, int index, boolean consolidate, Map<String, Integer> consolidatedMap, Random rand, boolean isCreative) {
        if (index >= blocks.size()) {
            if (!isCreative && consolidate && !consolidatedMap.isEmpty()) {
                spawnConsolidatedDrops(store, commandBuffer, blocks.get(0), consolidatedMap, rand);
            }
            return;
        }

        int batchSize = 3 + rand.nextInt(3);
        int end = Math.min(index + batchSize, blocks.size());

        for (int i = index; i < end; i++) {
            Vector3i pos = blocks.get(i);
            BlockType type = world.getBlockType(pos.x, pos.y, pos.z);
            if (type == null) continue;

            if (rand.nextFloat() < 0.20f) {
                playSound(playerRef, "SFX_Stone_Break", 0.5f, 0.8f + rand.nextFloat() * 0.4f);
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

        long nextDelay = 4L + rand.nextInt(5);
        CompletableFuture.delayedExecutor(nextDelay, TimeUnit.MILLISECONDS, world).execute(() -> {
            scheduleSpreadingBreak(playerRef, world, store, commandBuffer, blocks, end, consolidate, consolidatedMap, rand, isCreative);
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
        Vector3d offset = new Vector3d((rand.nextDouble() - 0.5) * 0.5, (rand.nextDouble() - 0.5) * 0.5, (rand.nextDouble() - 0.5) * 0.5);
        Vector3d finalPos = basePos.add(offset);
        Holder<EntityStore> itemHolder = ItemComponent.generateItemDrop(store, stack, finalPos, Vector3f.ZERO, 0, 0.15f, 0);
        if (itemHolder != null) commandBuffer.addEntity(itemHolder, AddReason.SPAWN);
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

    @SuppressWarnings("unchecked")
    private List<ItemStack> getRealDrops(BlockType type) {
        List<ItemStack> res = new ArrayList<>();
        BlockGathering g = type.getGathering();
        if (g != null && g.getBreaking() != null) {
            BlockBreakingDropType b = g.getBreaking();

            if (b.getDropListId() != null) {
                try {
                    Class<?> itemModuleClass = Class.forName("com.hypixel.hytale.server.core.modules.item.ItemModule");
                    Object instance = itemModuleClass.getMethod("get").invoke(null);
                    List<ItemStack> drops = (List<ItemStack>) itemModuleClass.getMethod("getRandomItemDrops", String.class).invoke(instance, b.getDropListId());
                    if (drops != null) res.addAll(drops);
                } catch (Exception ignored) {}
            }

            if (res.isEmpty() && b.getItemId() != null) {
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