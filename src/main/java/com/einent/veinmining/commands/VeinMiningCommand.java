package com.einent.veinmining.commands;

import com.einent.veinmining.config.VeinMiningConfig;
import com.einent.veinmining.gui.VeinMiningGui;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;

import java.util.concurrent.CompletableFuture;

public class VeinMiningCommand extends AbstractAsyncCommand {
    private final Config<VeinMiningConfig> config;
    private final OptionalArg<String> modeArg;

    public VeinMiningCommand(Config<VeinMiningConfig> config) {
        super("veinmining", "Instantly mine connected ores and blocks by holding the Walk key (LEFT ALT). Run /veinmining to configure.");
        this.addAliases("vein", "vm", "veinminer");
        this.config = config;

        this.modeArg = this.withOptionalArg("mode", "ores, all, off, gui", ArgTypes.STRING);

        this.setPermissionGroup(GameMode.Adventure);
    }

    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext context) {
        String arg = modeArg.get(context);

        if (arg == null || arg.isEmpty() || arg.equalsIgnoreCase("gui") || arg.equalsIgnoreCase("config")) {
            return openGui(context);
        }

        if (!(context.sender() instanceof Player player)) {
            context.sendMessage(Message.raw("Only players can toggle veinmining modes directly."));
            return CompletableFuture.completedFuture(null);
        }

        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) return CompletableFuture.completedFuture(null);

        Store<EntityStore> store = ref.getStore();
        UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());

        if (uuidComp == null) {
            context.sendMessage(Message.raw("Error: Could not determine player UUID."));
            return CompletableFuture.completedFuture(null);
        }

        String mode = arg.toLowerCase();
        if (mode.equals("ore")) mode = "ores";

        if (mode.equals("ores") || mode.equals("all") || mode.equals("off")) {
            config.get().setPlayerTargetMode(uuidComp.getUuid().toString(), mode);
            config.save();
            context.sendMessage(Message.raw("VeinMining target mode set to: " + mode.toUpperCase()));
        } else {
            context.sendMessage(Message.raw("Invalid mode! Use --mode='ores', 'all', or 'off'."));
        }

        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> openGui(CommandContext context) {
        if (context.sender() instanceof Player player) {
            Ref<EntityStore> ref = player.getReference();
            if (ref != null && ref.isValid()) {
                Store<EntityStore> store = ref.getStore();
                World world = store.getExternalData().getWorld();

                return CompletableFuture.runAsync(() -> {
                    PlayerRef playerRefComponent = store.getComponent(ref, PlayerRef.getComponentType());
                    if (playerRefComponent != null) {
                        player.getPageManager().openCustomPage(
                                ref,
                                store,
                                new VeinMiningGui(playerRefComponent, config)
                        );
                    }
                }, world);
            }
        }

        context.sendMessage(Message.raw("Only players can open the GUI."));
        return CompletableFuture.completedFuture(null);
    }
}