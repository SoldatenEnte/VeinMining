package com.einent.veinmining;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.util.Config;

import javax.annotation.Nonnull;

public class VeinMiningCommand extends CommandBase {
    private final Config<VeinMiningConfig> config;
    private final RequiredArg<String> modeArg;

    public VeinMiningCommand(Config<VeinMiningConfig> config) {
        super("veinmining", "Sets the VeinMining mode (ores, all, or off)");
        this.addAliases(new String[]{"vein", "vm", "veinminer"});

        this.config = config;
        // Updated description to indicate both ore and ores are valid
        this.modeArg = this.withRequiredArg("mode", "ores, all or off", ArgTypes.STRING);
        this.setPermissionGroup(GameMode.Adventure);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        String mode = modeArg.get(context).toLowerCase();

        // Normalize "ore" to "ores"
        if (mode.equals("ore")) {
            mode = "ores";
        }

        if (mode.equals("ores") || mode.equals("all") || mode.equals("off")) {
            config.get().setMiningMode(mode);
            config.save();
            context.sendMessage(Message.raw("VeinMining mode set to: " + mode.toUpperCase()));
        } else {
            context.sendMessage(Message.raw("Invalid mode! Use 'ores', 'all', or 'off'."));
        }
    }
}