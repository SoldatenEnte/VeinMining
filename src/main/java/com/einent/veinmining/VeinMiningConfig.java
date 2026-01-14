package com.einent.veinmining;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

public class VeinMiningConfig {

    private int maxVeinSize = 50;
    private double durabilityMultiplier = 1.0;
    private String miningMode = "ores";
    private String[] whitelistedBlocks = new String[]{};

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
            .build();

    public int getMaxVeinSize() { return maxVeinSize; }
    public String getMiningMode() { return miningMode; }
    public void setMiningMode(String mode) { this.miningMode = mode; }
    public double getDurabilityMultiplier() { return durabilityMultiplier; }
    public String[] getWhitelistedBlocks() { return whitelistedBlocks; }
}