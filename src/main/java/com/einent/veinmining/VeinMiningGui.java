package com.einent.veinmining;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
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

    public VeinMiningGui(@Nonnull PlayerRef playerRef, Config<VeinMiningConfig> config) {
        super(playerRef, CustomPageLifetime.CanDismiss, GuiData.CODEC);
        this.config = config;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder ui, @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        ui.append("Pages/EineNT_VeinMining_Gui.ui");

        events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnModeOres", EventData.of("Mode", "ores"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnModeAll", EventData.of("Mode", "all"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnModeOff", EventData.of("Mode", "off"), false);

        setInitialValues(ui);
    }

    private void setInitialValues(UICommandBuilder ui) {
        updateVisuals(ui);
    }

    private void updateVisuals(UICommandBuilder ui) {
        VeinMiningConfig cfg = config.get();

        updateButtonState(ui, "#BtnModeOres", cfg.getMiningMode().equalsIgnoreCase("ores"));
        updateButtonState(ui, "#BtnModeAll", cfg.getMiningMode().equalsIgnoreCase("all"));
        updateButtonState(ui, "#BtnModeOff", cfg.getMiningMode().equalsIgnoreCase("off"));
    }

    private void updateButtonState(UICommandBuilder ui, String elementId, boolean active) {
        ui.set(elementId + ".Disabled", active);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull GuiData data) {
        boolean needsSave = false;
        boolean needsUiUpdate = false;
        VeinMiningConfig cfg = config.get();

        if (data.mode != null && !cfg.getMiningMode().equals(data.mode)) {
            cfg.setMiningMode(data.mode);
            needsSave = true;
            needsUiUpdate = true;
        }

        if (needsSave) {
            config.save();
        }

        if (needsUiUpdate) {
            UICommandBuilder cmd = new UICommandBuilder();
            updateVisuals(cmd);
            sendUpdate(cmd, new UIEventBuilder(), false);
        }
    }

    public static class GuiData {
        public String mode;

        public static final BuilderCodec<GuiData> CODEC = BuilderCodec.builder(GuiData.class, GuiData::new)
                .append(new KeyedCodec<>("Mode", Codec.STRING), (d, v) -> d.mode = v, d -> d.mode).add()
                .build();
    }
}