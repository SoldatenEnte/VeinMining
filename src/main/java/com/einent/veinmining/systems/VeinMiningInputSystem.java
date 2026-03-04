package com.einent.veinmining.systems;

import com.einent.veinmining.config.VeinMiningConfig;
import com.einent.veinmining.gui.VeinMiningHud;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VeinMiningInputSystem extends EntityTickingSystem<EntityStore> {

    private final Config<VeinMiningConfig> config;
    private final Map<String, InputState> playerStates = new HashMap<>();
    private final Query<EntityStore> query;

    private final List<String> ALL_PATTERNS = Arrays.asList(
            "freeform", "cube", "wall3", "wall5", "tunnel3", "tunnel2", "tunnel1", "diagonal"
    );

    public VeinMiningInputSystem(Config<VeinMiningConfig> config) {
        this.config = config;
        this.query = Query.any();
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        Holder<EntityStore> holder = EntityUtils.toHolder(index, archetypeChunk);
        Player player = holder.getComponent(Player.getComponentType());
        PlayerRef playerRef = holder.getComponent(PlayerRef.getComponentType());
        MovementStatesComponent moveComp = holder.getComponent(MovementStatesComponent.getComponentType());
        UUIDComponent uuidComp = holder.getComponent(UUIDComponent.getComponentType());

        if (player == null || playerRef == null || moveComp == null || uuidComp == null) return;

        String uuid = uuidComp.getUuid().toString();
        VeinMiningConfig cfg = config.get();
        boolean isAdmin = player.hasPermission("veinmining.admin");
        VeinMiningConfig.GroupSettings group = cfg.resolveGroup(player);

        if (!cfg.isModEnabled(uuid, isAdmin)) return;

        String activationMode = cfg.getPlayerActivation(uuid);
        if ("always".equalsIgnoreCase(activationMode)) return;

        MovementStates states = moveComp.getMovementStates();
        boolean isPressed = "crouching".equalsIgnoreCase(activationMode) ? states.crouching : states.walking;

        InputState state = playerStates.computeIfAbsent(uuid, unused -> new InputState());
        long currentTime = System.currentTimeMillis();

        if (isPressed && !state.wasPressed) {
            if (cfg.isQuickSwitchEnabled() && currentTime - state.lastPressTime < 350) {
                List<String> allowed = new ArrayList<>();
                for (String p : ALL_PATTERNS) {
                    if (cfg.isPatternAllowed(uuid, p, group, isAdmin)) {
                        allowed.add(p);
                    }
                }

                if (allowed.size() > 1) {
                    String current = cfg.getPlayerPattern(uuid);
                    int idx = allowed.indexOf(current);
                    String nextPattern = allowed.get((idx + 1) % allowed.size());

                    cfg.setPlayerPattern(uuid, nextPattern);
                    config.save();

                    VeinMiningHud hud = new VeinMiningHud(playerRef);
                    hud.updatePattern(nextPattern);
                    player.getHudManager().setCustomHud(playerRef, hud);
                    state.hudShowTime = currentTime;
                }

                state.lastPressTime = 0;
            } else {
                state.lastPressTime = currentTime;
            }
        }

        if (state.hudShowTime > 0 && currentTime - state.hudShowTime > 2000) {
            player.getHudManager().setCustomHud(playerRef, new EmptyHud(playerRef));
            state.hudShowTime = 0;
        }

        state.wasPressed = isPressed;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    private static class InputState {
        boolean wasPressed = false;
        long lastPressTime = 0;
        long hudShowTime = 0;
    }

    private static class EmptyHud extends CustomUIHud {
        public EmptyHud(@Nonnull PlayerRef playerRef) {
            super(playerRef);
        }
        @Override
        protected void build(@Nonnull UICommandBuilder ui) {}
    }
}