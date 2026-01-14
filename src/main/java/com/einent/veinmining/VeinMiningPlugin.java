package com.einent.veinmining;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;

import javax.annotation.Nonnull;
import java.util.logging.Level;

public class VeinMiningPlugin extends JavaPlugin {

    private final Config<VeinMiningConfig> config;

    public VeinMiningPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        this.config = this.withConfig("VeinMining", VeinMiningConfig.CODEC);
    }

    @Override
    protected void setup() {
        super.setup();
        this.config.save();
        this.getCommandRegistry().registerCommand(new VeinMiningCommand(config));
        this.getEntityStoreRegistry().registerSystem(new VeinMiningSystem(config));
        this.getLogger().at(Level.INFO).log("VeinMining Plugin Loaded.");
    }
}