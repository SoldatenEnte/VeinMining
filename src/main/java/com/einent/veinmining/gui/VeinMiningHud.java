package com.einent.veinmining.gui;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

public class VeinMiningHud extends CustomUIHud {

    private String currentPattern = "freeform";

    public VeinMiningHud(@Nonnull PlayerRef playerRef) {
        super(playerRef);
    }

    public void updatePattern(String pattern) {
        this.currentPattern = pattern;
    }

    @Override
    protected void build(@Nonnull UICommandBuilder ui) {
        ui.append("Pages/EineNT_VeinMining_Hud.ui");
        ui.set("#ModeLabel.Text", getDisplayName(currentPattern));
        String iconId = "#Icon" + getIconName(currentPattern);
        ui.set(iconId + ".Visible", true);
    }

    private String getDisplayName(String pattern) {
        return switch (pattern.toLowerCase()) {
            case "tunnel3" -> "Tunnel 3x3";
            case "cube" -> "3x3x3 Cube";
            case "tunnel2" -> "Tunnel 2x1";
            case "wall3" -> "Wall 3x3";
            case "tunnel1" -> "Tunnel 1x1";
            case "wall5" -> "Wall 5x5";
            case "diagonal" -> "Diagonal";
            default -> "Freeform";
        };
    }

    private String getIconName(String pattern) {
        return switch (pattern.toLowerCase()) {
            case "tunnel3" -> "Tunnel3";
            case "cube" -> "Cube";
            case "tunnel2" -> "Tunnel2";
            case "wall3" -> "Wall3";
            case "tunnel1" -> "Tunnel1";
            case "wall5" -> "Wall5";
            case "diagonal" -> "Diagonal";
            default -> "Freeform";
        };
    }
}