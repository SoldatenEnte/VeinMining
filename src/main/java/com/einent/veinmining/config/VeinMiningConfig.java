package com.einent.veinmining.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VeinMiningConfig {

    private int maxVeinSize = 50;
    private double durabilityMultiplier = 1.0;
    private boolean consolidateDrops = true;
    private boolean requireValidTool = true;
    private boolean instantBreak = false;
    private boolean requirePermission = false;
    private boolean opOnlyConfig = false;

    private String[] allowedModes = new String[0];
    private String[] validTools = new String[] { "Pickaxe", "Hatchet", "Shovel", "Shears" };
    private String[] patternBlacklist = new String[0];
    private String[] blockWhitelist = new String[0];
    private String[] blockBlacklist = new String[0];

    private final Map<String, PlayerModeEntry> playerSettingsMap = new HashMap<>();

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
            .append(new KeyedCodec<>("RequirePermission", Codec.BOOLEAN),
                    (config, value, ignored) -> config.requirePermission = value,
                    (config, ignored) -> config.requirePermission).add()
            .append(new KeyedCodec<>("OpOnlyConfig", Codec.BOOLEAN),
                    (config, value, ignored) -> config.opOnlyConfig = value,
                    (config, ignored) -> config.opOnlyConfig).add()
            .append(new KeyedCodec<>("AllowedModes", new ArrayCodec<>(Codec.STRING, String[]::new)),
                    (config, value, ignored) -> config.allowedModes = value,
                    (config, ignored) -> config.allowedModes).add()
            .append(new KeyedCodec<>("ValidTools", new ArrayCodec<>(Codec.STRING, String[]::new)),
                    (config, value, ignored) -> config.validTools = value,
                    (config, ignored) -> config.validTools).add()
            .append(new KeyedCodec<>("PatternBlacklist", new ArrayCodec<>(Codec.STRING, String[]::new)),
                    (config, value, ignored) -> config.patternBlacklist = value,
                    (config, ignored) -> config.patternBlacklist).add()
            .append(new KeyedCodec<>("BlockWhitelist", new ArrayCodec<>(Codec.STRING, String[]::new)),
                    (config, value, ignored) -> config.blockWhitelist = value,
                    (config, ignored) -> config.blockWhitelist).add()
            .append(new KeyedCodec<>("BlockBlacklist", new ArrayCodec<>(Codec.STRING, String[]::new)),
                    (config, value, ignored) -> config.blockBlacklist = value,
                    (config, ignored) -> config.blockBlacklist).add()
            .append(new KeyedCodec<>("PlayerModes", new ArrayCodec<>(PlayerModeEntry.CODEC, PlayerModeEntry[]::new)),
                    (config, value, ignored) -> config.setPlayerModesFromArray(value),
                    (config, ignored) -> config.getPlayerModesAsArray()).add()
            .build();

    public int getMaxVeinSize() { return maxVeinSize; }
    public double getDurabilityMultiplier() { return durabilityMultiplier; }
    public boolean isConsolidateDrops() { return consolidateDrops; }
    public boolean isRequireValidTool() { return requireValidTool; }
    public boolean isInstantBreak() { return instantBreak; }
    public boolean isRequirePermission() { return requirePermission; }
    public boolean isOpOnlyConfig() { return opOnlyConfig; }
    public List<String> getAllowedModes() { return allowedModes != null ? Arrays.asList(allowedModes) : new ArrayList<>(); }
    public List<String> getValidTools() { return validTools != null ? Arrays.asList(validTools) : new ArrayList<>(); }
    public List<String> getPatternBlacklist() { return patternBlacklist != null ? Arrays.asList(patternBlacklist) : new ArrayList<>(); }
    public List<String> getBlockWhitelist() { return blockWhitelist != null ? Arrays.asList(blockWhitelist) : new ArrayList<>(); }
    public List<String> getBlockBlacklist() { return blockBlacklist != null ? Arrays.asList(blockBlacklist) : new ArrayList<>(); }

    public String getPlayerTargetMode(String uuid) {
        PlayerModeEntry entry = playerSettingsMap.get(uuid);
        String mode = (entry != null && entry.targetMode != null) ? entry.targetMode : "all";

        List<String> allowed = getAllowedModes();
        if (!allowed.isEmpty() && !allowed.contains(mode)) {
            return allowed.getFirst();
        }
        return mode;
    }

    public String getPlayerPattern(String uuid) {
        PlayerModeEntry entry = playerSettingsMap.get(uuid);
        String pat = (entry != null && entry.pattern != null) ? entry.pattern : "freeform";
        List<String> blacklist = getPatternBlacklist();
        if (blacklist.contains(pat)) {
            return "freeform";
        }
        return pat;
    }

    public String getPlayerOrientation(String uuid) {
        PlayerModeEntry entry = playerSettingsMap.get(uuid);
        return (entry != null && entry.orientation != null) ? entry.orientation : "block";
    }

    public String getPlayerActivation(String uuid) {
        PlayerModeEntry entry = playerSettingsMap.get(uuid);
        return (entry != null && entry.activationKey != null) ? entry.activationKey : "walking";
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

    public void setPlayerActivation(String uuid, String activation) {
        getEntry(uuid).activationKey = activation;
    }

    private PlayerModeEntry getEntry(String uuid) {
        return playerSettingsMap.computeIfAbsent(uuid, k -> new PlayerModeEntry(k, "all", "freeform", "block", "walking"));
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
        public String activationKey = "walking";

        public PlayerModeEntry() {}

        public PlayerModeEntry(String uuid, String targetMode, String pattern, String orientation, String activationKey) {
            this.uuid = uuid;
            this.targetMode = targetMode;
            this.pattern = pattern;
            this.orientation = orientation;
            this.activationKey = activationKey;
        }

        public static final BuilderCodec<PlayerModeEntry> CODEC = BuilderCodec.builder(PlayerModeEntry.class, PlayerModeEntry::new)
                .append(new KeyedCodec<>("UUID", Codec.STRING), (o, v, i) -> o.uuid = v, (o, i) -> o.uuid).add()
                .append(new KeyedCodec<>("Mode", Codec.STRING), (o, v, i) -> o.targetMode = v, (o, i) -> o.targetMode).add()
                .append(new KeyedCodec<>("Pattern", Codec.STRING), (o, v, i) -> o.pattern = v, (o, i) -> o.pattern).add()
                .append(new KeyedCodec<>("Orientation", Codec.STRING), (o, v, i) -> o.orientation = v, (o, i) -> o.orientation).add()
                .append(new KeyedCodec<>("Activation", Codec.STRING), (o, v, i) -> o.activationKey = v, (o, i) -> o.activationKey).add()
                .build();
    }
}