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
import java.util.List;

public class VeinMiningGui extends InteractiveCustomUIPage<VeinMiningGui.GuiData> {

    private final Config<VeinMiningConfig> config;

    public VeinMiningGui(PlayerRef playerRef, Config<VeinMiningConfig> config) {
        super(playerRef, CustomPageLifetime.CanDismiss, GuiData.CODEC);
        this.config = config;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder ui, @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        ui.append("Pages/EineNT_VeinMining_Gui.ui");

        events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnModeOres",
                EventData.of("Action", "SetTarget").put("Value", "ores"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnModeAll",
                EventData.of("Action", "SetTarget").put("Value", "all"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnModeOff",
                EventData.of("Action", "SetTarget").put("Value", "off"), false);

        events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnKeyWalk",
                EventData.of("Action", "SetKey").put("Value", "walking"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnKeyCrouch",
                EventData.of("Action", "SetKey").put("Value", "crouching"), false);

        events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnOriBlock",
                EventData.of("Action", "SetOri").put("Value", "block"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnOriPlayer",
                EventData.of("Action", "SetOri").put("Value", "player"), false);

        events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnPatFree",
                EventData.of("Action", "SetPattern").put("Value", "freeform"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnPatCube",
                EventData.of("Action", "SetPattern").put("Value", "cube"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnPatTun3",
                EventData.of("Action", "SetPattern").put("Value", "tunnel3"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnPatTun2",
                EventData.of("Action", "SetPattern").put("Value", "tunnel2"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnPatTun1",
                EventData.of("Action", "SetPattern").put("Value", "tunnel1"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnPatDiag",
                EventData.of("Action", "SetPattern").put("Value", "diagonal"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnPatWall3",
                EventData.of("Action", "SetPattern").put("Value", "wall3"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnPatWall5",
                EventData.of("Action", "SetPattern").put("Value", "wall5"), false);

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

        String currentTarget = cfg.getPlayerTargetMode(uuid);
        String currentPattern = cfg.getPlayerPattern(uuid);
        String currentOri = cfg.getPlayerOrientation(uuid);
        String currentKey = cfg.getPlayerActivation(uuid);

        List<String> allowedModes = cfg.getAllowedModes();
        List<String> patternBlacklist = cfg.getPatternBlacklist();

        ui.set("#LblBlockLimit.Text", String.valueOf(cfg.getMaxVeinSize()));

        updateModeButton(ui, "#BtnModeOres", "ores", currentTarget, allowedModes);
        updateModeButton(ui, "#BtnModeAll", "all", currentTarget, allowedModes);
        updateModeButton(ui, "#BtnModeOff", "off", currentTarget, allowedModes);

        updateButtonState(ui, "#BtnKeyWalk", "walking".equalsIgnoreCase(currentKey));
        updateButtonState(ui, "#BtnKeyCrouch", "crouching".equalsIgnoreCase(currentKey));

        updateButtonState(ui, "#BtnOriBlock", "block".equalsIgnoreCase(currentOri));
        updateButtonState(ui, "#BtnOriPlayer", "player".equalsIgnoreCase(currentOri));

        updatePatternButton(ui, "#BtnPatFree", "freeform", currentPattern, patternBlacklist);
        updatePatternButton(ui, "#BtnPatCube", "cube", currentPattern, patternBlacklist);
        updatePatternButton(ui, "#BtnPatTun3", "tunnel3", currentPattern, patternBlacklist);
        updatePatternButton(ui, "#BtnPatTun2", "tunnel2", currentPattern, patternBlacklist);
        updatePatternButton(ui, "#BtnPatTun1", "tunnel1", currentPattern, patternBlacklist);
        updatePatternButton(ui, "#BtnPatDiag", "diagonal", currentPattern, patternBlacklist);
        updatePatternButton(ui, "#BtnPatWall3", "wall3", currentPattern, patternBlacklist);
        updatePatternButton(ui, "#BtnPatWall5", "wall5", currentPattern, patternBlacklist);
    }

    private void updateModeButton(UICommandBuilder ui, String elementId, String modeId, String current, List<String> allowed) {
        boolean isAllowed = allowed.isEmpty() || allowed.contains(modeId);
        boolean isActive = modeId.equalsIgnoreCase(current);

        ui.set(elementId + ".Visible", isAllowed);
        ui.set(elementId + ".Disabled", isActive);
    }

    private void updatePatternButton(UICommandBuilder ui, String elementId, String patternId, String current, List<String> blacklist) {
        boolean isBanned = blacklist.contains(patternId);
        boolean isActive = patternId.equalsIgnoreCase(current);

        ui.set(elementId + ".Visible", !isBanned);
        ui.set(elementId + ".Disabled", isActive);
    }

    private void updateButtonState(UICommandBuilder ui, String elementId, boolean active) {
        ui.set(elementId + ".Disabled", active);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull GuiData data) {
        UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComp == null || data.action == null) return;

        String uuid = uuidComp.getUuid().toString();
        boolean needsSave = false;
        VeinMiningConfig cfg = config.get();

        if ("SetTarget".equals(data.action)) {
            List<String> allowed = cfg.getAllowedModes();
            if ((allowed.isEmpty() || allowed.contains(data.value)) && !cfg.getPlayerTargetMode(uuid).equals(data.value)) {
                cfg.setPlayerTargetMode(uuid, data.value);
                needsSave = true;
            }
        } else if ("SetPattern".equals(data.action)) {
            if (!cfg.getPlayerPattern(uuid).equals(data.value) && !cfg.getPatternBlacklist().contains(data.value)) {
                cfg.setPlayerPattern(uuid, data.value);
                needsSave = true;
            }
        } else if ("SetOri".equals(data.action)) {
            if (!cfg.getPlayerOrientation(uuid).equals(data.value)) {
                cfg.setPlayerOrientation(uuid, data.value);
                needsSave = true;
            }
        } else if ("SetKey".equals(data.action)) {
            if (!cfg.getPlayerActivation(uuid).equals(data.value)) {
                cfg.setPlayerActivation(uuid, data.value);
                needsSave = true;
            }
        }

        if (needsSave) {
            config.save();
            UICommandBuilder cmd = new UICommandBuilder();
            updateVisuals(ref, cmd, store);
            sendUpdate(cmd, new UIEventBuilder(), false);
        }
    }

    public static class GuiData {
        public String action;
        public String value;

        public static final BuilderCodec<GuiData> CODEC = BuilderCodec.builder(GuiData.class, GuiData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v, i) -> d.action = v, (d, i) -> d.action).add()
                .append(new KeyedCodec<>("Value", Codec.STRING), (d, v, i) -> d.value = v, (d, i) -> d.value).add()
                .build();
    }
}