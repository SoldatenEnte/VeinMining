package com.einent.veinmining.systems;

import com.einent.veinmining.config.VeinMiningConfig;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

public class VeinMiningSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    private final Config<VeinMiningConfig> config;
    private final MiningManager miningManager;

    public VeinMiningSystem(Config<VeinMiningConfig> config) {
        super(BreakBlockEvent.class);
        this.config = config;
        this.miningManager = new MiningManager(config);
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull BreakBlockEvent event) {
        if (MiningManager.IS_VEIN_MINING.get()) return;

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
        boolean isActive = "crouching".equalsIgnoreCase(activationMode) ? states.crouching : states.walking;

        if (!isActive) return;

        String blockId = event.getBlockType().getId();
        if (blockId.equals("Empty")) return;

        if ("ores".equalsIgnoreCase(targetMode)) {
            if (!blockId.contains("Ore_") || blockId.contains("_Cracked")) {
                return;
            }
        }

        Vector3i targetPos = event.getTargetBlock();

        if (player.getWorld() != null) {
            CompletableFuture.runAsync(() -> {
                if (player.getWorld() == null) return;
                miningManager.performVeinMine(player, ref, targetPos, blockId, store, uuid);
            }, player.getWorld());
        }
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }
}