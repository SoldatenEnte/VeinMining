package com.einent.veinmining.gui;

import com.einent.veinmining.config.VeinMiningConfig;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;

import javax.annotation.Nonnull;

public class VeinMiningGui extends InteractiveCustomUIPage<VeinMiningGui.GuiData> {

    private final Config<VeinMiningConfig> config;

    public VeinMiningGui(PlayerRef playerRef, Config<VeinMiningConfig> config) {
        super(playerRef, CustomPageLifetime.CanDismiss, GuiData.CODEC);
        this.config = config;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder ui, @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        ui.append("Pages/EineNT_VeinMining_Gui.ui");

        events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnModeOres", EventData.of("Mode", "ores"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnModeAll", EventData.of("Mode", "all"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnModeOff", EventData.of("Mode", "off"), false);

        setInitialValues(ref, ui, store);
    }

    private void setInitialValues(Ref<EntityStore> ref, UICommandBuilder ui, Store<EntityStore> store) {
        updateVisuals(ref, ui, store);
    }

    private void updateVisuals(Ref<EntityStore> ref, UICommandBuilder ui, Store<EntityStore> store) {
        UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComp == null) return;

        String uuid = uuidComp.getUuid().toString();
        VeinMiningConfig cfg = config.get();
        String currentMode = cfg.getPlayerMode(uuid);

        updateButtonState(ui, "#BtnModeOres", currentMode.equalsIgnoreCase("ores"));
        updateButtonState(ui, "#BtnModeAll", currentMode.equalsIgnoreCase("all"));
        updateButtonState(ui, "#BtnModeOff", currentMode.equalsIgnoreCase("off"));
    }

    private void updateButtonState(UICommandBuilder ui, String elementId, boolean active) {
        ui.set(elementId + ".Disabled", active);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull GuiData data) {
        UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComp == null) return;

        String uuid = uuidComp.getUuid().toString();
        boolean needsSave = false;
        boolean needsUiUpdate = false;
        VeinMiningConfig cfg = config.get();
        String currentMode = cfg.getPlayerMode(uuid);

        if (data.mode != null && !currentMode.equals(data.mode)) {
            cfg.setPlayerMode(uuid, data.mode);
            needsSave = true;
            needsUiUpdate = true;
        }

        if (needsSave) {
            config.save();
        }

        if (needsUiUpdate) {
            UICommandBuilder cmd = new UICommandBuilder();
            updateVisuals(ref, cmd, store);
            sendUpdate(cmd, new UIEventBuilder(), false);
        }
    }

    public static class GuiData {
        public String mode;

        public static final BuilderCodec<GuiData> CODEC = BuilderCodec.builder(GuiData.class, GuiData::new)
                .append(new KeyedCodec<>("Mode", Codec.STRING), (d, v, i) -> d.mode = v, (d, i) -> d.mode).add()
                .build();
    }
}