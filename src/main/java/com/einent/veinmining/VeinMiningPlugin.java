package com.einent.veinmining;

import com.einent.veinmining.commands.VeinMiningCommand;
import com.einent.veinmining.config.VeinMiningConfig;
import com.einent.veinmining.systems.VeinMiningSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;

import java.util.logging.Level;

public class VeinMiningPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final Config<VeinMiningConfig> config;

    public VeinMiningPlugin(JavaPluginInit init) {
        super(init);
        this.config = this.withConfig("VeinMining", VeinMiningConfig.CODEC);
    }

    @Override
    protected void setup() {
        this.config.save();
        this.getCommandRegistry().registerCommand(new VeinMiningCommand(config));
        this.getEntityStoreRegistry().registerSystem(new VeinMiningSystem(config));
        LOGGER.at(Level.INFO).log("VeinMining Plugin Loaded.");
    }
}