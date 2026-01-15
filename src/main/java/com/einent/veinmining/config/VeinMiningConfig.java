package com.einent.veinmining.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

public class VeinMiningConfig {

    private int maxVeinSize = 50;
    private double durabilityMultiplier = 1.0;
    private String miningMode = "all";
    private boolean consolidateDrops = true;
    private boolean requireValidTool = true;
    private boolean instantBreak = false;

    public static final BuilderCodec<VeinMiningConfig> CODEC = BuilderCodec.builder(VeinMiningConfig.class, VeinMiningConfig::new)
            .append(new KeyedCodec<>("MaxVeinSize", Codec.INTEGER),
                    (config, value, extra) -> config.maxVeinSize = value,
                    (config, extra) -> config.maxVeinSize).add()
            .append(new KeyedCodec<>("MiningMode", Codec.STRING),
                    (config, value, extra) -> config.miningMode = value,
                    (config, extra) -> config.miningMode).add()
            .append(new KeyedCodec<>("DurabilityMultiplier", Codec.DOUBLE),
                    (config, value, extra) -> config.durabilityMultiplier = value,
                    (config, extra) -> config.durabilityMultiplier).add()
            .append(new KeyedCodec<>("ConsolidateDrops", Codec.BOOLEAN),
                    (config, value, extra) -> config.consolidateDrops = value,
                    (config, extra) -> config.consolidateDrops).add()
            .append(new KeyedCodec<>("RequireValidTool", Codec.BOOLEAN),
                    (config, value, extra) -> config.requireValidTool = value,
                    (config, extra) -> config.requireValidTool).add()
            .append(new KeyedCodec<>("InstantBreak", Codec.BOOLEAN),
                    (config, value, extra) -> config.instantBreak = value,
                    (config, extra) -> config.instantBreak).add()
            .build();

    public int getMaxVeinSize() { return maxVeinSize; }
    public String getMiningMode() { return miningMode; }
    public double getDurabilityMultiplier() { return durabilityMultiplier; }
    public boolean isConsolidateDrops() { return consolidateDrops; }
    public boolean isRequireValidTool() { return requireValidTool; }
    public boolean isInstantBreak() { return instantBreak; }

    public void setMaxVeinSize(int maxVeinSize) { this.maxVeinSize = maxVeinSize; }
    public void setMiningMode(String miningMode) { this.miningMode = miningMode; }
    public void setDurabilityMultiplier(double durabilityMultiplier) { this.durabilityMultiplier = durabilityMultiplier; }
    public void setConsolidateDrops(boolean consolidateDrops) { this.consolidateDrops = consolidateDrops; }
    public void setRequireValidTool(boolean requireValidTool) { this.requireValidTool = requireValidTool; }
    public void setInstantBreak(boolean instantBreak) { this.instantBreak = instantBreak; }
}