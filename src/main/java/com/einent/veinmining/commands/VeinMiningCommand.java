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
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class VeinMiningCommand extends AbstractAsyncCommand {
    private final Config<VeinMiningConfig> config;
    private final OptionalArg<String> modeArg;
    private final OptionalArg<String> patternArg;
    private final OptionalArg<String> orientationArg;
    private final OptionalArg<String> keyArg;

    public VeinMiningCommand(Config<VeinMiningConfig> config) {
        super("veinmining", "Configure VeinMining settings.");
        this.addAliases("vein", "vm", "veinminer");
        this.config = config;

        this.modeArg = this.withOptionalArg("mode", "ores, all, off", ArgTypes.STRING);
        this.patternArg = this.withOptionalArg("pattern", "freeform, cube, tunnel3, etc", ArgTypes.STRING);
        this.orientationArg = this.withOptionalArg("orientation", "block, player", ArgTypes.STRING);
        this.keyArg = this.withOptionalArg("key", "walk, crouch", ArgTypes.STRING);

        this.setPermissionGroup(GameMode.Adventure);
    }

    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext context) {
        String rawMode = modeArg.get(context);
        String rawPattern = patternArg.get(context);
        String rawOri = orientationArg.get(context);
        String rawKey = keyArg.get(context);

        boolean isGuiRequest = (rawMode != null && (rawMode.equalsIgnoreCase("gui") || rawMode.equalsIgnoreCase("config")));
        boolean hasArgs = (rawMode != null || rawPattern != null || rawOri != null || rawKey != null);

        if (!hasArgs || isGuiRequest) {
            return openGui(context);
        }

        if (!(context.sender() instanceof Player player)) {
            context.sendMessage(Message.raw("Only players can toggle veinmining settings."));
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            Ref<EntityStore> ref = player.getReference();
            if (ref == null || !ref.isValid()) return;

            Store<EntityStore> store = ref.getStore();
            UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());

            if (uuidComp == null) {
                context.sendMessage(Message.raw("Error: Could not determine player UUID."));
                return;
            }

            String uuid = uuidComp.getUuid().toString();
            List<String> updates = new ArrayList<>();
            VeinMiningConfig cfg = config.get();

            if (rawMode != null && !rawMode.equalsIgnoreCase("gui")) {
                String val = rawMode.toLowerCase();
                if (val.equals("ore")) val = "ores";

                if (val.equals("ores") || val.equals("all") || val.equals("off")) {
                    cfg.setPlayerTargetMode(uuid, val);
                    updates.add("Mode: " + val.toUpperCase());
                } else {
                    context.sendMessage(Message.raw("Invalid mode. Use 'ores', 'all', or 'off'."));
                }
            }

            if (rawPattern != null) {
                String val = resolvePattern(rawPattern.toLowerCase());
                if (val != null) {
                    cfg.setPlayerPattern(uuid, val);
                    updates.add("Pattern: " + formatCap(val));
                } else {
                    context.sendMessage(Message.raw("Invalid pattern. Try 'free', 'cube', 'tunnel3', 'wall3', 'diag'."));
                }
            }

            if (rawOri != null) {
                String val = rawOri.toLowerCase();
                if (val.startsWith("p")) val = "player";
                else if (val.startsWith("b")) val = "block";

                if (val.equals("player") || val.equals("block")) {
                    cfg.setPlayerOrientation(uuid, val);
                    updates.add("Orientation: " + formatCap(val));
                } else {
                    context.sendMessage(Message.raw("Invalid orientation. Use 'player' or 'block'."));
                }
            }

            if (rawKey != null) {
                String val = rawKey.toLowerCase();
                if (val.startsWith("c") || val.startsWith("s")) val = "crouching";
                else if (val.startsWith("w") || val.startsWith("a")) val = "walking";

                if (val.equals("crouching") || val.equals("walking")) {
                    cfg.setPlayerActivation(uuid, val);
                    updates.add("Key: " + (val.equals("walking") ? "Walk" : "Crouch"));
                } else {
                    context.sendMessage(Message.raw("Invalid key. Use 'walk' or 'crouch'."));
                }
            }

            if (!updates.isEmpty()) {
                config.save();
                context.sendMessage(Message.raw("VeinMining Updated > " + String.join(", ", updates)));
            }

        }, player.getWorld());
    }

    private String resolvePattern(String input) {
        return switch (input) {
            case "free", "freeform" -> "freeform";
            case "cube", "3x3" -> "cube";
            case "tunnel3", "t3", "3x3t" -> "tunnel3";
            case "tunnel2", "t2", "2x1" -> "tunnel2";
            case "tunnel1", "t1", "1x1" -> "tunnel1";
            case "wall3", "w3" -> "wall3";
            case "wall5", "w5" -> "wall5";
            case "diagonal", "diag" -> "diagonal";
            default -> null;
        };
    }

    private String formatCap(String input) {
        if (input == null || input.isEmpty()) return input;
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }

    private CompletableFuture<Void> openGui(CommandContext context) {
        if (context.sender() instanceof Player player) {
            return CompletableFuture.runAsync(() -> {
                Ref<EntityStore> ref = player.getReference();
                if (ref != null && ref.isValid()) {
                    Store<EntityStore> store = ref.getStore();
                    PlayerRef playerRefComponent = store.getComponent(ref, PlayerRef.getComponentType());

                    if (playerRefComponent != null) {
                        player.getPageManager().openCustomPage(
                                ref,
                                store,
                                new VeinMiningGui(playerRefComponent, config)
                        );
                    }
                }
            }, player.getWorld());
        }

        context.sendMessage(Message.raw("Console must use arguments (e.g. --mode=all)."));
        return CompletableFuture.completedFuture(null);
    }
}