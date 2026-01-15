package com.einent.veinmining.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class VeinMiningConfig {

    private int maxVeinSize = 50;
    private double durabilityMultiplier = 1.0;
    private Map<String, String> playerModesMap = new HashMap<>();
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

    public String getPlayerMode(String uuid) {
        return playerModesMap.getOrDefault(uuid, "all");
    }

    public void setPlayerMode(String uuid, String mode) {
        this.playerModesMap.put(uuid, mode);
    }

    private void setPlayerModesFromArray(PlayerModeEntry[] array) {
        this.playerModesMap.clear();
        if (array != null) {
            for (PlayerModeEntry entry : array) {
                this.playerModesMap.put(entry.uuid, entry.mode);
            }
        }
    }

    private PlayerModeEntry[] getPlayerModesAsArray() {
        return playerModesMap.entrySet().stream()
                .map(e -> new PlayerModeEntry(e.getKey(), e.getValue()))
                .toArray(PlayerModeEntry[]::new);
    }

    public void setMaxVeinSize(int maxVeinSize) { this.maxVeinSize = maxVeinSize; }
    public void setDurabilityMultiplier(double durabilityMultiplier) { this.durabilityMultiplier = durabilityMultiplier; }
    public void setConsolidateDrops(boolean consolidateDrops) { this.consolidateDrops = consolidateDrops; }
    public void setRequireValidTool(boolean requireValidTool) { this.requireValidTool = requireValidTool; }
    public void setInstantBreak(boolean instantBreak) { this.instantBreak = instantBreak; }

    public static class PlayerModeEntry {
        public String uuid;
        public String mode;

        public PlayerModeEntry() {}

        public PlayerModeEntry(String uuid, String mode) {
            this.uuid = uuid;
            this.mode = mode;
        }

        public static final BuilderCodec<PlayerModeEntry> CODEC = BuilderCodec.builder(PlayerModeEntry.class, PlayerModeEntry::new)
                .append(new KeyedCodec<>("UUID", Codec.STRING), (o, v, i) -> o.uuid = v, (o, i) -> o.uuid).add()
                .append(new KeyedCodec<>("Mode", Codec.STRING), (o, v, i) -> o.mode = v, (o, i) -> o.mode).add()
                .build();
    }
}