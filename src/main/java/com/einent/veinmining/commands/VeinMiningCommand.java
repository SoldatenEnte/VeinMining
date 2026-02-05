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

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class VeinMiningCommand extends AbstractAsyncCommand {
    private final Config<VeinMiningConfig> config;
    private final OptionalArg<String> modeArg;
    private final OptionalArg<String> patternArg;
    private final OptionalArg<String> orientationArg;
    private final OptionalArg<String> keyArg;

    // Admin Args
    private final OptionalArg<String> targetArg;
    private final OptionalArg<Integer> limitArg;
    private final OptionalArg<Boolean> allowGuiArg;
    private final OptionalArg<Boolean> enabledArg;
    private final OptionalArg<String> allowedModesArg;
    private final OptionalArg<String> allowedPatternsArg;

    private static final List<String> VALID_MODES = Arrays.asList("ores", "all", "off");
    private static final List<String> VALID_PATTERNS = Arrays.asList(
            "freeform", "cube", "tunnel3", "tunnel2", "tunnel1", "wall3", "wall5", "diagonal"
    );

    public VeinMiningCommand(Config<VeinMiningConfig> config) {
        super("veinmining", "Configure VeinMining settings.");
        this.addAliases("vein", "vm", "veinminer");
        this.config = config;

        this.modeArg = this.withOptionalArg("mode", "ores, all, off", ArgTypes.STRING);
        this.patternArg = this.withOptionalArg("pattern", "freeform, cube, tunnel3, etc", ArgTypes.STRING);
        this.orientationArg = this.withOptionalArg("orientation", "block, player", ArgTypes.STRING);
        this.keyArg = this.withOptionalArg("key", "walk, crouch", ArgTypes.STRING);

        this.targetArg = this.withOptionalArg("target", "Target player name (Admin)", ArgTypes.STRING);
        this.limitArg = this.withOptionalArg("limit", "Set max blocks (Admin)", ArgTypes.INTEGER);
        this.allowGuiArg = this.withOptionalArg("gui", "Allow/Deny GUI (Admin)", ArgTypes.BOOLEAN);
        this.enabledArg = this.withOptionalArg("enable", "Enable/Disable mod (Admin)", ArgTypes.BOOLEAN);
        this.allowedModesArg = this.withOptionalArg("allowed_modes", "CSV of allowed modes or 'inherit' (Admin)", ArgTypes.STRING);
        this.allowedPatternsArg = this.withOptionalArg("allowed_patterns", "CSV of allowed patterns or 'inherit' (Admin)", ArgTypes.STRING);

        this.setPermissionGroup(GameMode.Adventure);
    }

    @Override
    @Nonnull
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext context) {
        VeinMiningConfig cfg = config.get();

        String targetName = targetArg.get(context);
        if (targetName != null) {
            // Admin Action
            if (context.sender() instanceof Player senderPlayer && !senderPlayer.hasPermission("veinmining.op")) {
                context.sendMessage(Message.raw("You do not have permission to modify other players' settings."));
                return CompletableFuture.completedFuture(null);
            }

            Integer limit = limitArg.get(context);
            Boolean gui = allowGuiArg.get(context);
            Boolean enabled = enabledArg.get(context);
            String allowedModes = allowedModesArg.get(context);
            String allowedPatterns = allowedPatternsArg.get(context);

            if (limit == null && gui == null && enabled == null && allowedModes == null && allowedPatterns == null) {
                context.sendMessage(Message.raw("Please specify setting: --limit, --gui, --enable, --allowed_modes, --allowed_patterns"));
                return CompletableFuture.completedFuture(null);
            }

            // Find player
            String targetUuid = null;
            String targetDisplay = targetName;

            if (context.sender() instanceof Player sender) {
                if (sender.getWorld() != null) {
                    for (PlayerRef pRef : sender.getWorld().getPlayerRefs()) {
                        if (pRef.getUsername().equalsIgnoreCase(targetName)) {
                            targetUuid = pRef.getUuid().toString();
                            targetDisplay = pRef.getUsername();
                            break;
                        }
                    }
                }
            }

            if (targetUuid == null) {
                context.sendMessage(Message.raw("Player '" + targetName + "' not found in your world."));
                return CompletableFuture.completedFuture(null);
            }

            String[] modeArray = null;
            if (allowedModes != null) {
                if (allowedModes.equalsIgnoreCase("inherit")) {
                    modeArray = new String[0];
                } else {
                    // Remove quotes and split
                    String cleanInput = allowedModes.replace("\"", "");
                    modeArray = Arrays.stream(cleanInput.split(","))
                            .map(String::trim)
                            .map(String::toLowerCase)
                            .filter(s -> {
                                if (VALID_MODES.contains(s)) return true;
                                context.sendMessage(Message.raw("Warning: Invalid mode skipped '" + s + "'"));
                                return false;
                            })
                            .toArray(String[]::new);
                }
            }

            String[] patternArray = null;
            if (allowedPatterns != null) {
                if (allowedPatterns.equalsIgnoreCase("inherit")) {
                    patternArray = new String[0];
                } else {
                    String cleanInput = allowedPatterns.replace("\"", "");
                    patternArray = Arrays.stream(cleanInput.split(","))
                            .map(String::trim)
                            .map(String::toLowerCase)
                            .filter(s -> {
                                if (VALID_PATTERNS.contains(s)) return true;
                                context.sendMessage(Message.raw("Warning: Invalid pattern skipped '" + s + "'"));
                                return false;
                            })
                            .toArray(String[]::new);
                }
            }

            cfg.setPlayerOverride(targetUuid, limit, gui, enabled, modeArray, patternArray);
            config.save();
            context.sendMessage(Message.raw("Updated settings for " + targetDisplay));
            return CompletableFuture.completedFuture(null);
        }

        // User Action
        if (context.sender() instanceof Player player) {
            if (cfg.isOpOnlyConfig() && !player.hasPermission("veinmining.op")) {
                context.sendMessage(Message.raw("Only server operators can modify veinmining settings."));
                return CompletableFuture.completedFuture(null);
            }
        }

        String rawMode = modeArg.get(context);
        String rawPattern = patternArg.get(context);
        String rawOri = orientationArg.get(context);
        String rawKey = keyArg.get(context);

        boolean isGuiRequest = (rawMode != null && (rawMode.equalsIgnoreCase("gui") || rawMode.equalsIgnoreCase("config")));
        boolean hasArgs = (rawMode != null || rawPattern != null || rawOri != null || rawKey != null);

        if (!hasArgs || isGuiRequest) return openGui(context);

        if (!(context.sender() instanceof Player player)) {
            context.sendMessage(Message.raw("Only players can toggle veinmining settings."));
            return CompletableFuture.completedFuture(null);
        }

        if (player.getWorld() == null) return CompletableFuture.completedFuture(null);

        return CompletableFuture.runAsync(() -> {
            Ref<EntityStore> ref = player.getReference();
            if (ref == null || !ref.isValid()) return;

            Store<EntityStore> store = ref.getStore();
            UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uuidComp == null) return;

            String uuid = uuidComp.getUuid().toString();

            if (!cfg.isModEnabledForPlayer(uuid)) {
                context.sendMessage(Message.raw("VeinMining is disabled for you."));
                return;
            }

            List<String> updates = new ArrayList<>();

            if (rawMode != null && !rawMode.equalsIgnoreCase("gui")) {
                String val = rawMode.toLowerCase();
                if (val.equals("ore")) val = "ores";

                List<String> allowed = cfg.getEffectiveAllowedModes(uuid);
                if (!allowed.isEmpty() && !allowed.contains(val)) {
                    context.sendMessage(Message.raw("Mode '" + val + "' is restricted. Allowed: " + String.join(", ", allowed)));
                } else if (val.equals("ores") || val.equals("all") || val.equals("off")) {
                    cfg.setPlayerTargetMode(uuid, val);
                    updates.add("Mode: " + val.toUpperCase());
                } else {
                    context.sendMessage(Message.raw("Invalid mode. Allowed: ores, all, off"));
                }
            }

            if (rawPattern != null) {
                String val = resolvePattern(rawPattern.toLowerCase());
                if (val != null) {
                    if (!cfg.isPatternAllowed(uuid, val)) {
                        context.sendMessage(Message.raw("Pattern '" + val + "' is restricted for you."));
                    } else {
                        cfg.setPlayerPattern(uuid, val);
                        updates.add("Pattern: " + formatCap(val));
                    }
                } else {
                    context.sendMessage(Message.raw("Invalid pattern. Try: free, cube, tunnel3, wall3, diag."));
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
            if (player.getWorld() == null) return CompletableFuture.completedFuture(null);

            return CompletableFuture.runAsync(() -> {
                Ref<EntityStore> ref = player.getReference();
                if (ref != null && ref.isValid()) {
                    Store<EntityStore> store = ref.getStore();
                    UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());

                    if (uuidComp != null) {
                        String uuid = uuidComp.getUuid().toString();

                        if (!config.get().isModEnabledForPlayer(uuid)) {
                            context.sendMessage(Message.raw("VeinMining is disabled for you."));
                            return;
                        }

                        if (!config.get().canPlayerOpenGui(uuid)) {
                            context.sendMessage(Message.raw("You are not allowed to open the VeinMining menu."));
                            return;
                        }

                        PlayerRef playerRefComponent = store.getComponent(ref, PlayerRef.getComponentType());
                        if (playerRefComponent != null) {
                            player.getPageManager().openCustomPage(
                                    ref,
                                    store,
                                    new VeinMiningGui(playerRefComponent, config)
                            );
                        }
                    }
                }
            }, player.getWorld());
        }

        context.sendMessage(Message.raw("Console must use arguments (e.g. --mode=all)."));
        return CompletableFuture.completedFuture(null);
    }
}