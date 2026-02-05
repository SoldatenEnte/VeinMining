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
    private final Map<String, PlayerOverride> playerOverrides = new HashMap<>();

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
            .append(new KeyedCodec<>("PlayerOverrides", new ArrayCodec<>(PlayerOverride.CODEC, PlayerOverride[]::new)),
                    (config, value, ignored) -> config.setPlayerOverridesFromArray(value),
                    (config, ignored) -> config.getPlayerOverridesAsArray()).add()
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

    // --- Override Logic ---

    public int getEffectiveMaxVeinSize(String uuid) {
        PlayerOverride override = playerOverrides.get(uuid);
        if (override != null && override.maxVeinSize != -1) {
            return override.maxVeinSize;
        }
        return getMaxVeinSize();
    }

    public List<String> getEffectiveAllowedModes(String uuid) {
        PlayerOverride override = playerOverrides.get(uuid);
        if (override != null && override.allowedModes != null) {
            return Arrays.asList(override.allowedModes);
        }
        return getAllowedModes();
    }

    public boolean isModEnabledForPlayer(String uuid) {
        PlayerOverride override = playerOverrides.get(uuid);
        if (override != null && override.modEnabled != -1) {
            return override.modEnabled == 1;
        }
        return true;
    }

    public boolean canPlayerOpenGui(String uuid) {
        PlayerOverride override = playerOverrides.get(uuid);
        if (override != null && override.canOpenGui != -1) {
            return override.canOpenGui == 1;
        }
        return true;
    }

    public boolean isPatternAllowed(String uuid, String pattern) {
        PlayerOverride override = playerOverrides.get(uuid);
        if (override != null && override.allowedPatterns != null) {
            // Whitelist mode if override is set
            for (String allowed : override.allowedPatterns) {
                if (allowed.equalsIgnoreCase(pattern)) return true;
            }
            return false;
        }

        // Fallback to global blacklist
        List<String> blacklist = getPatternBlacklist();
        return !blacklist.contains(pattern);
    }

    public void setPlayerOverride(String uuid, Integer limit, Boolean allowGui, Boolean enabled, String[] modes, String[] patterns) {
        PlayerOverride override = playerOverrides.computeIfAbsent(uuid, PlayerOverride::new);
        if (limit != null) override.maxVeinSize = limit;
        if (allowGui != null) override.canOpenGui = allowGui ? 1 : 0;
        if (enabled != null) override.modEnabled = enabled ? 1 : 0;
        if (modes != null) override.allowedModes = modes.length == 0 ? null : modes;
        if (patterns != null) override.allowedPatterns = patterns.length == 0 ? null : patterns;
    }

    // --- End Override Logic ---

    public String getPlayerTargetMode(String uuid) {
        PlayerModeEntry entry = playerSettingsMap.get(uuid);
        String mode = (entry != null && entry.targetMode != null) ? entry.targetMode : "all";

        List<String> allowed = getEffectiveAllowedModes(uuid);
        if (!allowed.isEmpty() && !allowed.contains(mode)) {
            return allowed.getFirst();
        }
        return mode;
    }

    public String getPlayerPattern(String uuid) {
        PlayerModeEntry entry = playerSettingsMap.get(uuid);
        String pat = (entry != null && entry.pattern != null) ? entry.pattern : "freeform";

        if (!isPatternAllowed(uuid, pat)) {
            // Try to find a fallback, default to freeform if valid, otherwise first available
            if (isPatternAllowed(uuid, "freeform")) return "freeform";
            // Return nothing/invalid if nothing allowed, handled in manager
            return "none";
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

    private void setPlayerOverridesFromArray(PlayerOverride[] array) {
        this.playerOverrides.clear();
        if (array != null) {
            for (PlayerOverride entry : array) {
                this.playerOverrides.put(entry.uuid, entry);
            }
        }
    }

    private PlayerOverride[] getPlayerOverridesAsArray() {
        return playerOverrides.values().toArray(new PlayerOverride[0]);
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

    public static class PlayerOverride {
        public String uuid;
        public int maxVeinSize = -1;
        public int canOpenGui = -1; // -1: Inherit, 0: False, 1: True
        public int modEnabled = -1; // -1: Inherit, 0: False, 1: True
        public String[] allowedModes = null; // null: Inherit
        public String[] allowedPatterns = null; // null: Inherit

        public PlayerOverride() {}
        public PlayerOverride(String uuid) { this.uuid = uuid; }

        public static final BuilderCodec<PlayerOverride> CODEC = BuilderCodec.builder(PlayerOverride.class, PlayerOverride::new)
                .append(new KeyedCodec<>("UUID", Codec.STRING), (o, v, i) -> o.uuid = v, (o, i) -> o.uuid).add()
                .append(new KeyedCodec<>("MaxVeinSize", Codec.INTEGER), (o, v, i) -> o.maxVeinSize = v, (o, i) -> o.maxVeinSize).add()
                .append(new KeyedCodec<>("CanOpenGui", Codec.INTEGER), (o, v, i) -> o.canOpenGui = v, (o, i) -> o.canOpenGui).add()
                .append(new KeyedCodec<>("ModEnabled", Codec.INTEGER), (o, v, i) -> o.modEnabled = v, (o, i) -> o.modEnabled).add()
                .append(new KeyedCodec<>("AllowedModes", new ArrayCodec<>(Codec.STRING, String[]::new)), (o, v, i) -> o.allowedModes = v, (o, i) -> o.allowedModes).add()
                .append(new KeyedCodec<>("AllowedPatterns", new ArrayCodec<>(Codec.STRING, String[]::new)), (o, v, i) -> o.allowedPatterns = v, (o, i) -> o.allowedPatterns).add()
                .build();
    }
}