package com.einent.veinmining.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;

import java.util.HashMap;
import java.util.Map;

public class VeinMiningConfig {

    private int maxVeinSize = 50;
    private double durabilityMultiplier = 1.0;
    private Map<String, PlayerModeEntry> playerSettingsMap = new HashMap<>();
    private boolean consolidateDrops = true;
    private boolean requireValidTool = true;
    private boolean instantBreak = false;

    public static final BuilderCodec<VeinMiningConfig> CODEC = BuilderCodec.builder(VeinMiningConfig.class, VeinMiningConfig::new)
            .append(new KeyedCodec<>("MaxVeinSize", Codec.INTEGER),
                    (config, value, ignored) -> config.maxVeinSize = value,
                    (config, ignored) -> config.maxVeinSize).add()
            .append(new KeyedCodec<>("DurabilityMultiplier", Codec.DOUBLE),
                    (config, value, ignored) -> config.durabilityMultiplier = value,
                    (config, ignored) -> config.durabilityMultiplier).add()
            .append(new KeyedCodec<>("ConsolidateDrops", Codec.BOOLEAN),
                    (config, value, ignored) -> config.consolidateDrops = value,
                    (config, ignored) -> config.consolidateDrops).add()
            .append(new KeyedCodec<>("RequireValidTool", Codec.BOOLEAN),
                    (config, value, ignored) -> config.requireValidTool = value,
                    (config, ignored) -> config.requireValidTool).add()
            .append(new KeyedCodec<>("InstantBreak", Codec.BOOLEAN),
                    (config, value, ignored) -> config.instantBreak = value,
                    (config, ignored) -> config.instantBreak).add()
            .append(new KeyedCodec<>("PlayerModes", new ArrayCodec<>(PlayerModeEntry.CODEC, PlayerModeEntry[]::new)),
                    (config, value, ignored) -> config.setPlayerModesFromArray(value),
                    (config, ignored) -> config.getPlayerModesAsArray()).add()
            .build();

    public int getMaxVeinSize() { return maxVeinSize; }
    public double getDurabilityMultiplier() { return durabilityMultiplier; }
    public boolean isConsolidateDrops() { return consolidateDrops; }
    public boolean isRequireValidTool() { return requireValidTool; }
    public boolean isInstantBreak() { return instantBreak; }

    public String getPlayerTargetMode(String uuid) {
        PlayerModeEntry entry = playerSettingsMap.get(uuid);
        return (entry != null && entry.targetMode != null) ? entry.targetMode : "all";
    }

    public String getPlayerPattern(String uuid) {
        PlayerModeEntry entry = playerSettingsMap.get(uuid);
        return (entry != null && entry.pattern != null) ? entry.pattern : "freeform";
    }

    public String getPlayerOrientation(String uuid) {
        PlayerModeEntry entry = playerSettingsMap.get(uuid);
        return (entry != null && entry.orientation != null) ? entry.orientation : "block";
    }

    public void setPlayerTargetMode(String uuid, String mode) {
        getEntry(uuid).targetMode = mode;
    }

    public void setPlayerPattern(String uuid, String pattern) {
        getEntry(uuid).pattern = pattern;
    }

    public void setPlayerOrientation(String uuid, String orientation) {
        getEntry(uuid).orientation = orientation;
    }

    private PlayerModeEntry getEntry(String uuid) {
        return playerSettingsMap.computeIfAbsent(uuid, k -> new PlayerModeEntry(k, "all", "freeform", "block"));
    }

    private void setPlayerModesFromArray(PlayerModeEntry[] array) {
        this.playerSettingsMap.clear();
        if (array != null) {
            for (PlayerModeEntry entry : array) {
                this.playerSettingsMap.put(entry.uuid, entry);
            }
        }
    }

    private PlayerModeEntry[] getPlayerModesAsArray() {
        return playerSettingsMap.values().toArray(new PlayerModeEntry[0]);
    }

    public static class PlayerModeEntry {
        public String uuid;
        public String targetMode = "all";
        public String pattern = "freeform";
        public String orientation = "block";

        public PlayerModeEntry() {}

        public PlayerModeEntry(String uuid, String targetMode, String pattern, String orientation) {
            this.uuid = uuid;
            this.targetMode = targetMode;
            this.pattern = pattern;
            this.orientation = orientation;
        }

        public static final BuilderCodec<PlayerModeEntry> CODEC = BuilderCodec.builder(PlayerModeEntry.class, PlayerModeEntry::new)
                .append(new KeyedCodec<>("UUID", Codec.STRING), (o, v, i) -> o.uuid = v, (o, i) -> o.uuid).add()
                .append(new KeyedCodec<>("Mode", Codec.STRING), (o, v, i) -> o.targetMode = v, (o, i) -> o.targetMode).add()
                .append(new KeyedCodec<>("Pattern", Codec.STRING), (o, v, i) -> o.pattern = v, (o, i) -> o.pattern).add()
                .append(new KeyedCodec<>("Orientation", Codec.STRING), (o, v, i) -> o.orientation = v, (o, i) -> o.orientation).add()
                .build();
    }
}