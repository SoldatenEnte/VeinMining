package com.einent.veinmining;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

public class VeinMiningCommand extends AbstractAsyncCommand {
    private final Config<VeinMiningConfig> config;
    private final OptionalArg<String> modeArg;

    public VeinMiningCommand(Config<VeinMiningConfig> config) {
        super("veinmining", "Sets the VeinMining mode or opens GUI");
        this.addAliases("vein", "vm", "veinminer");
        this.config = config;

        this.modeArg = this.withOptionalArg("mode", "ores, all, off, gui", ArgTypes.STRING);

        this.setPermissionGroup(GameMode.Adventure);
    }

    @Nonnull
    @Override
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext context) {
        String arg = modeArg.get(context);

        if (arg == null || arg.isEmpty() || arg.equalsIgnoreCase("gui") || arg.equalsIgnoreCase("config")) {
            return openGui(context);
        }

        String mode = arg.toLowerCase();

        if (mode.equals("ore")) mode = "ores";

        if (mode.equals("ores") || mode.equals("all") || mode.equals("off")) {
            config.get().setMiningMode(mode);
            config.save();
            context.sendMessage(Message.raw("VeinMining mode set to: " + mode.toUpperCase()));
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