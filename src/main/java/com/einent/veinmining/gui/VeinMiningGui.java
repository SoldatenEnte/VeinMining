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
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;

import javax.annotation.Nonnull;
import java.util.ArrayList;
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
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnKeyAlways",
                EventData.of("Action", "SetKey").put("Value", "always"), false);

        events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnOriBlock",
                EventData.of("Action", "SetOri").put("Value", "block"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnOriPlayer",
                EventData.of("Action", "SetOri").put("Value", "player"), false);

        setInitialValues(ref, ui, events, store);
    }

    private void setInitialValues(Ref<EntityStore> ref, UICommandBuilder ui, UIEventBuilder events, Store<EntityStore> store) {
        updateVisuals(ref, ui, events, store);
    }

    private void updateVisuals(Ref<EntityStore> ref, UICommandBuilder ui, UIEventBuilder events, Store<EntityStore> store) {
        UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());

        if (uuidComp == null || player == null) return;

        String uuid = uuidComp.getUuid().toString();
        VeinMiningConfig cfg = config.get();

        boolean isAdmin = player.hasPermission("veinmining.admin");
        VeinMiningConfig.GroupSettings group = cfg.resolveGroup(player);

        String currentTarget = cfg.getPlayerTargetMode(uuid);
        String currentPattern = cfg.getPlayerPattern(uuid);
        String currentOri = cfg.getPlayerOrientation(uuid);
        String currentKey = cfg.getPlayerActivation(uuid);

        List<String> allowedModes = cfg.getEffectiveAllowedModes(uuid, group, isAdmin);
        int maxVeinSize = cfg.getEffectiveLimit(uuid, group, isAdmin);

        ui.set("#LblBlockLimit.Text", String.valueOf(maxVeinSize));

        updateModeButton(ui, "#BtnModeOres", "ores", currentTarget, allowedModes);
        updateModeButton(ui, "#BtnModeAll", "all", currentTarget, allowedModes);
        updateModeButton(ui, "#BtnModeOff", "off", currentTarget, allowedModes);

        updateButtonState(ui, "#BtnKeyWalk", "walking".equalsIgnoreCase(currentKey));
        updateButtonState(ui, "#BtnKeyCrouch", "crouching".equalsIgnoreCase(currentKey));
        updateButtonState(ui, "#BtnKeyAlways", "always".equalsIgnoreCase(currentKey));

        updateButtonState(ui, "#BtnOriBlock", "block".equalsIgnoreCase(currentOri));
        updateButtonState(ui, "#BtnOriPlayer", "player".equalsIgnoreCase(currentOri));

        buildPatternList(ui, events, currentPattern, cfg, uuid, group, isAdmin, maxVeinSize);
    }

    private void buildPatternList(UICommandBuilder ui, UIEventBuilder events, String current, VeinMiningConfig cfg, String uuid, VeinMiningConfig.GroupSettings group, boolean isAdmin, int maxSize) {
        ui.clear("#ColLeft");
        ui.clear("#ColRight");

        List<PatternDef> patterns = new ArrayList<>();
        patterns.add(new PatternDef("freeform", "Freeform", "IconFreeform", 1));
        patterns.add(new PatternDef("tunnel3", "Tunnel 3x3", "IconTunnel3", 9));
        patterns.add(new PatternDef("cube", "3x3x3 Cube", "IconCube", 27));
        patterns.add(new PatternDef("tunnel2", "Tunnel 2x1", "IconTunnel2", 2));
        patterns.add(new PatternDef("wall3", "Wall 3x3", "IconWall3", 9));
        patterns.add(new PatternDef("tunnel1", "Tunnel 1x1", "IconTunnel1", 1));
        patterns.add(new PatternDef("wall5", "Wall 5x5", "IconWall5", 25));
        patterns.add(new PatternDef("diagonal", "Diagonal", "IconDiagonal", 1));

        patterns.removeIf(p -> !cfg.isPatternAllowed(uuid, p.id, group, isAdmin));

        if (!cfg.isShowPatternsAboveLimit()) {
            patterns.removeIf(p -> maxSize < p.req);
        }

        for (int i = 0; i < patterns.size(); i++) {
            PatternDef p = patterns.get(i);
            String col = (i % 2 == 0) ? "#ColLeft" : "#ColRight";
            int rowIndex = i / 2;
            String selector = col + "[" + rowIndex + "]";

            boolean isActive = p.id.equalsIgnoreCase(current);

            ui.append(col, "Components/PatternBtn.ui");
            ui.set(selector + " #Lbl.Text", p.label);
            ui.set(selector + " #" + p.uiId + ".Visible", true);
            ui.set(selector + " #Btn.Disabled", isActive);

            if (!isActive) {
                events.addEventBinding(CustomUIEventBindingType.Activating, selector + " #Btn",
                        EventData.of("Action", "SetPattern").put("Value", p.id), false);
            }
        }
    }

    private void updateModeButton(UICommandBuilder ui, String elementId, String modeId, String current, List<String> allowed) {
        boolean isAllowed = allowed.isEmpty() || allowed.contains(modeId);
        boolean isActive = modeId.equalsIgnoreCase(current);

        ui.set(elementId + ".Visible", isAllowed);
        ui.set(elementId + ".Disabled", isActive);
    }

    private void updateButtonState(UICommandBuilder ui, String elementId, boolean active) {
        ui.set(elementId + ".Disabled", active);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull GuiData data) {
        UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());

        if (uuidComp == null || player == null || data.action == null) return;

        String uuid = uuidComp.getUuid().toString();
        boolean needsSave = false;
        VeinMiningConfig cfg = config.get();

        boolean isAdmin = player.hasPermission("veinmining.admin");
        VeinMiningConfig.GroupSettings group = cfg.resolveGroup(player);

        if ("SetTarget".equals(data.action)) {
            List<String> allowed = cfg.getEffectiveAllowedModes(uuid, group, isAdmin);
            if ((allowed.isEmpty() || allowed.contains(data.value)) && !cfg.getPlayerTargetMode(uuid).equals(data.value)) {
                cfg.setPlayerTargetMode(uuid, data.value);
                needsSave = true;
            }
        } else if ("SetPattern".equals(data.action)) {
            if (!cfg.getPlayerPattern(uuid).equals(data.value) && cfg.isPatternAllowed(uuid, data.value, group, isAdmin)) {
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
            UIEventBuilder ev = new UIEventBuilder();
            updateVisuals(ref, cmd, ev, store);
            sendUpdate(cmd, ev, false);
        }
    }

    private static class PatternDef {
        String id, label, uiId;
        int req;
        PatternDef(String id, String label, String uiId, int req) {
            this.id = id; this.label = label; this.uiId = uiId; this.req = req;
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