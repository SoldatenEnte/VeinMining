package com.einent.veinmining.systems;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;

import java.util.*;
import java.util.stream.Collectors;

public class PatternCalculator {

    public List<Vector3i> getFreeformBlocks(World world, Vector3i startPos, String targetId, int max) {
        List<Vector3i> result = new ArrayList<>();
        Queue<Vector3i> queue = new LinkedList<>();
        Set<Vector3i> visitedPhysical = new HashSet<>();
        Set<Vector3i> visitedOrigins = new HashSet<>();

        Vector3i startOrigin = getMultiblockOrigin(world, startPos);
        visitedOrigins.add(startOrigin);

        visitedPhysical.add(startPos);
        addNeighbors(startPos, queue, visitedPhysical);

        int bufferLimit = Math.min(max * 10, 4096);

        while (!queue.isEmpty() && result.size() < max && visitedPhysical.size() < bufferLimit) {
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

    public List<Vector3i> getPatternBlocks(World world, String targetId, Store<EntityStore> store, Ref<EntityStore> ref, Vector3i start, String pattern, int max, String oriMode, Vector3i hitFace) {
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

        int minW = -(width / 2), maxW = (width / 2);
        int minH, maxH;
        if (height == 1) { minH = 0; maxH = 0; }
        else if (height == 2) { minH = -1; maxH = 0; }
        else if (height == 3) { minH = -1; maxH = 1; }
        else if (height == 5) { minH = -2; maxH = 2; }
        else { minH = 0; maxH = 0; }
        int minD = 0, maxD = depthLimit - 1;

        List<Vector3i> result = new ArrayList<>();
        Queue<Vector3i> queue = new LinkedList<>();
        Set<Vector3i> visited = new HashSet<>();

        queue.add(start);
        visited.add(start);

        Vector3i secondary = null;
        if (pattern.equals("diagonal")) {
            if (oriMode.equalsIgnoreCase("block")) {
                if (fwd.y == 0) secondary = new Vector3i(0, playerLook.y > 0 ? 1 : -1, 0);
                else secondary = getDominantAxis(new Vector3f(playerLook.x, 0, playerLook.z));
            } else {
                int vert = (playerLook.y > 0) ? 1 : -1;
                if (fwd.y != 0) {
                    float hx = (float)-Math.sin(yaw);
                    float hz = (float)-Math.cos(yaw);
                    secondary = getDominantAxis(new Vector3f(hx, 0, hz));
                    fwd = new Vector3i(0, fwd.y, 0);
                } else {
                    secondary = new Vector3i(0, vert, 0);
                }
            }
        }

        while (!queue.isEmpty() && result.size() < max) {
            Vector3i current = queue.poll();
            if (!current.equals(start)) result.add(current);

            if (!pattern.equals("diagonal")) {
                for (int x = -1; x <= 1; x++) {
                    for (int y = -1; y <= 1; y++) {
                        for (int z = -1; z <= 1; z++) {
                            if (x == 0 && y == 0 && z == 0) continue;
                            Vector3i neighbor = new Vector3i(current.x + x, current.y + y, current.z + z);
                            if (visited.contains(neighbor)) continue;

                            Vector3i offset = new Vector3i(neighbor.x - start.x, neighbor.y - start.y, neighbor.z - start.z);
                            int d = dot(offset, fwd);
                            int w = dot(offset, right);
                            int h = dot(offset, up);

                            Vector3i check = new Vector3i(
                                    fwd.x * d + right.x * w + up.x * h,
                                    fwd.y * d + right.y * w + up.y * h,
                                    fwd.z * d + right.z * w + up.z * h
                            );

                            if (check.equals(offset) && d >= minD && d <= maxD && w >= minW && w <= maxW && h >= minH && h <= maxH) {
                                BlockType type = world.getBlockType(neighbor.x, neighbor.y, neighbor.z);
                                if (type != null && type.getId().equals(targetId)) {
                                    visited.add(neighbor);
                                    queue.add(neighbor);
                                }
                            }
                        }
                    }
                }
            } else if (secondary != null) {
                Vector3i step = new Vector3i(fwd.x + secondary.x, fwd.y + secondary.y, fwd.z + secondary.z);
                Vector3i neighbor = new Vector3i(current.x + step.x, current.y + step.y, current.z + step.z);
                if (!visited.contains(neighbor)) {
                    BlockType type = world.getBlockType(neighbor.x, neighbor.y, neighbor.z);
                    if (type != null && type.getId().equals(targetId)) {
                        visited.add(neighbor);
                        queue.add(neighbor);
                    }
                }
            }
        }

        result.sort(Comparator.comparingDouble(pos -> distanceSq(pos, start)));
        return result;
    }

    private int dot(Vector3i v, Vector3i axis) {
        return v.x * axis.x + v.y * axis.y + v.z * axis.z;
    }

    public Vector3i getMultiblockOrigin(World world, Vector3i pos) {
        try {
            ChunkStore chunkStore = world.getChunkStore();
            Store<ChunkStore> store = chunkStore.getStore();
            long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.x, pos.z);
            Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIndex);

            if (chunkRef != null && chunkRef.isValid()) {
                BlockChunk blockChunk = store.getComponent(chunkRef, BlockChunk.getComponentType());
                if (blockChunk != null) {
                    @SuppressWarnings("deprecation")
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

    public Vector3i getHitFace(Vector3i target, Store<EntityStore> store, Ref<EntityStore> ref) {
        TransformComponent trans = store.getComponent(ref, TransformComponent.getComponentType());
        HeadRotation head = store.getComponent(ref, HeadRotation.getComponentType());

        if (trans == null || head == null) return new Vector3i(0, 1, 0);

        Vector3d origin = trans.getPosition().clone().add(0, 1.62, 0);
        Vector3f rot = head.getRotation();
        Vector3f dirF = getForwardVector(rot.getYaw(), rot.getPitch());
        Vector3d dir = new Vector3d(dirF.x, dirF.y, dirF.z);

        double minX = target.x, minY = target.y, minZ = target.z;
        double maxX = target.x + 1.0, maxY = target.y + 1.0, maxZ = target.z + 1.0;

        double tMin = Double.MAX_VALUE;
        Vector3i normal = new Vector3i(0, 1, 0);

        boolean found = false;

        if (Math.abs(dir.x) > 1e-6) {
            double t1 = (minX - origin.x) / dir.x;
            double t2 = (maxX - origin.x) / dir.x;
            if (checkIntersection(t1, tMin, origin, dir, minY, maxY, minZ, maxZ, 1, 2)) {
                tMin = t1; normal = new Vector3i(-1, 0, 0); found = true;
            }
            if (checkIntersection(t2, tMin, origin, dir, minY, maxY, minZ, maxZ, 1, 2)) {
                tMin = t2; normal = new Vector3i(1, 0, 0); found = true;
            }
        }

        if (Math.abs(dir.y) > 1e-6) {
            double t1 = (minY - origin.y) / dir.y;
            double t2 = (maxY - origin.y) / dir.y;
            if (checkIntersection(t1, tMin, origin, dir, minX, maxX, minZ, maxZ, 0, 2)) {
                tMin = t1; normal = new Vector3i(0, -1, 0); found = true;
            }
            if (checkIntersection(t2, tMin, origin, dir, minX, maxX, minZ, maxZ, 0, 2)) {
                tMin = t2; normal = new Vector3i(0, 1, 0); found = true;
            }
        }

        if (Math.abs(dir.z) > 1e-6) {
            double t1 = (minZ - origin.z) / dir.z;
            double t2 = (maxZ - origin.z) / dir.z;
            if (checkIntersection(t1, tMin, origin, dir, minX, maxX, minY, maxY, 0, 1)) {
                tMin = t1; normal = new Vector3i(0, 0, -1); found = true;
            }
            if (checkIntersection(t2, tMin, origin, dir, minX, maxX, minY, maxY, 0, 1)) {
                normal = new Vector3i(0, 0, 1); found = true;
            }
        }

        if (!found) {
            Vector3d center = new Vector3d(target.x + 0.5, target.y + 0.5, target.z + 0.5);
            Vector3d diff = origin.clone().subtract(center);
            double ax = Math.abs(diff.x);
            double ay = Math.abs(diff.y);
            double az = Math.abs(diff.z);

            if (ax >= ay && ax >= az) return new Vector3i(diff.x > 0 ? 1 : -1, 0, 0);
            else if (ay >= ax && ay >= az) return new Vector3i(0, diff.y > 0 ? 1 : -1, 0);
            else return new Vector3i(0, 0, diff.z > 0 ? 1 : -1);
        }

        return normal;
    }

    private boolean checkIntersection(double t, double tMin, Vector3d origin, Vector3d dir, double minA, double maxA, double minB, double maxB, int idxA, int idxB) {
        if (t <= 0 || t >= tMin) return false;
        double a = (idxA == 0 ? origin.x : (idxA == 1 ? origin.y : origin.z)) + (idxA == 0 ? dir.x : (idxA == 1 ? dir.y : dir.z)) * t;
        double b = (idxB == 0 ? origin.x : (idxB == 1 ? origin.y : origin.z)) + (idxB == 0 ? dir.x : (idxB == 1 ? dir.y : dir.z)) * t;
        return a >= minA - 1e-4 && a <= maxA + 1e-4 && b >= minB - 1e-4 && b <= maxB + 1e-4;
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
}