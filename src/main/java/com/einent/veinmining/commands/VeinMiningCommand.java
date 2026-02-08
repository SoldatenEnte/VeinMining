package com.einent.veinmining.commands;

import com.einent.veinmining.config.VeinMiningConfig;
import com.einent.veinmining.gui.VeinMiningGui;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class VeinMiningCommand extends AbstractAsyncCommand {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final Config<VeinMiningConfig> config;
    private final OptionalArg<String> modeArg, patternArg, orientationArg, keyArg, targetArg, allowedModesArg, allowedPatternsArg;
    private final OptionalArg<Integer> limitArg;
    private final OptionalArg<Boolean> allowGuiArg, enabledArg;

    private static final List<String> VALID_MODES = Arrays.asList("ores", "all", "off");
    private static final List<String> VALID_PATTERNS = Arrays.asList("freeform", "cube", "tunnel3", "tunnel2", "tunnel1", "wall3", "wall5", "diagonal");

    public VeinMiningCommand(Config<VeinMiningConfig> config) {
        super("veinmining", "Configure VeinMining settings.");
        this.addAliases("vein", "vm", "veinminer");
        this.config = config;

        this.modeArg = this.withOptionalArg("mode", "ores, all, off", ArgTypes.STRING);
        this.patternArg = this.withOptionalArg("pattern", "freeform, cube, etc", ArgTypes.STRING);
        this.orientationArg = this.withOptionalArg("orientation", "block, player", ArgTypes.STRING);
        this.keyArg = this.withOptionalArg("key", "walk, crouch, always", ArgTypes.STRING);

        this.targetArg = this.withOptionalArg("target", "Player name/UUID (Admin)", ArgTypes.STRING);
        this.limitArg = this.withOptionalArg("limit", "Set max blocks (Admin)", ArgTypes.INTEGER);
        this.allowGuiArg = this.withOptionalArg("gui", "Allow/Deny GUI (Admin)", ArgTypes.BOOLEAN);
        this.enabledArg = this.withOptionalArg("enable", "Enable/Disable mod (Admin)", ArgTypes.BOOLEAN);
        this.allowedModesArg = this.withOptionalArg("allowed_modes", "CSV, 'inherit' or 'none' (Admin)", ArgTypes.STRING);
        this.allowedPatternsArg = this.withOptionalArg("allowed_patterns", "CSV, 'inherit' or 'none' (Admin)", ArgTypes.STRING);

        this.setPermissionGroup(GameMode.Adventure);
    }

    @Override
    public boolean hasPermission(@Nonnull CommandSender sender) {
        return true;
    }

    @Override
    @Nonnull
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext context) {
        if (!(context.sender() instanceof Player player)) {
            context.sendMessage(Message.raw("Only players can use this command."));
            return CompletableFuture.completedFuture(null);
        }

        World world = player.getWorld();
        if (world == null) return CompletableFuture.completedFuture(null);

        return CompletableFuture.runAsync(() -> {
            try {
                VeinMiningConfig cfg = config.get();
                Ref<EntityStore> pRef = player.getReference();
                if (pRef == null || !pRef.isValid()) return;

                String tName = targetArg.get(context);
                boolean isAdmin = player.hasPermission("veinmining.admin");

                if (tName != null) {
                    if (!isAdmin) {
                        context.sendMessage(Message.raw("No permission to modify others."));
                        return;
                    }
                    handleAdmin(context, world, tName);
                    return;
                }

                UUIDComponent uuidComp = pRef.getStore().getComponent(pRef, UUIDComponent.getComponentType());
                if (uuidComp == null) return;
                String uuid = uuidComp.getUuid().toString();

                if (!cfg.isModEnabled(uuid, isAdmin)) {
                    context.sendMessage(Message.raw("VeinMining is disabled for you."));
                    return;
                }

                if (modeArg.get(context) == null && patternArg.get(context) == null && orientationArg.get(context) == null && keyArg.get(context) == null) {
                    openGui(context, player, uuid, isAdmin);
                    return;
                }

                handlePlayer(context, player, uuid, isAdmin);
            } catch (Exception e) {
                LOGGER.at(Level.SEVERE).log("Error in VeinMiningCommand", e);
            }
        }, world);
    }

    private void handleAdmin(CommandContext context, World world, String target) {
        VeinMiningConfig cfg = config.get();
        UUID tUuid = null;
        String tDisplay = target;
        try { tUuid = UUID.fromString(target); } catch (Exception ignored) {}

        if (tUuid == null) {
            for (PlayerRef pRef : world.getPlayerRefs()) {
                if (pRef.getUsername().equalsIgnoreCase(target)) {
                    tUuid = pRef.getUuid();
                    tDisplay = pRef.getUsername();
                    break;
                }
            }
        }
        if (tUuid == null) {
            context.sendMessage(Message.raw("Player not found."));
            return;
        }

        String am = allowedModesArg.get(context);
        String[] modes = parseListArg(am, VALID_MODES);

        String ap = allowedPatternsArg.get(context);
        String[] patterns = parseListArg(ap, VALID_PATTERNS);

        cfg.setPlayerOverride(tUuid.toString(), limitArg.get(context), allowGuiArg.get(context), enabledArg.get(context), modes, patterns);
        config.save();
        context.sendMessage(Message.raw("Admin: Updated settings for " + tDisplay));
    }

    private String[] parseListArg(String arg, List<String> validOptions) {
        if (arg == null) return null;
        if (arg.equalsIgnoreCase("inherit")) return null;
        if (arg.equalsIgnoreCase("none") || arg.isEmpty()) return new String[0];

        return Arrays.stream(arg.replace("\"", "").split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(validOptions::contains)
                .toArray(String[]::new);
    }

    private void handlePlayer(CommandContext context, Player player, String uuid, boolean isAdmin) {
        VeinMiningConfig cfg = config.get();
        VeinMiningConfig.GroupSettings group = cfg.resolveGroup(player);
        List<String> updates = new ArrayList<>();

        String rm = modeArg.get(context);
        if (rm != null) {
            String val = rm.toLowerCase();
            if (cfg.getEffectiveAllowedModes(uuid, group, isAdmin).contains(val)) {
                cfg.setPlayerTargetMode(uuid, val);
                updates.add("Mode: " + val.toUpperCase());
            }
            else context.sendMessage(Message.raw("Mode '" + val + "' is restricted for your rank."));
        }

        String rp = patternArg.get(context);
        if (rp != null) {
            String val = rp.toLowerCase();
            if (cfg.isPatternAllowed(uuid, val, group, isAdmin)) {
                cfg.setPlayerPattern(uuid, val);
                updates.add("Pattern: " + val);
            }
            else context.sendMessage(Message.raw("Pattern '" + val + "' is restricted for your rank."));
        }

        String ori = orientationArg.get(context);
        if (ori != null) {
            if(ori.equalsIgnoreCase("player") || ori.equalsIgnoreCase("block")) {
                cfg.setPlayerOrientation(uuid, ori.toLowerCase());
                updates.add("Orientation Updated");
            }
        }

        String key = keyArg.get(context);
        if (key != null) {
            if(key.equalsIgnoreCase("walking") || key.equalsIgnoreCase("crouching") || key.equalsIgnoreCase("walk") || key.equalsIgnoreCase("crouch") || key.equalsIgnoreCase("always")) {
                String normalized;
                if (key.equalsIgnoreCase("always")) {
                    normalized = "always";
                } else {
                    normalized = key.startsWith("w") ? "walking" : "crouching";
                }
                cfg.setPlayerActivation(uuid, normalized);
                updates.add("Activation Key Updated");
            }
        }

        if (!updates.isEmpty()) {
            config.save();
            context.sendMessage(Message.raw("Updated: " + String.join(", ", updates)));
        }
    }

    private void openGui(CommandContext context, Player player, String uuid, boolean isAdmin) {
        VeinMiningConfig cfg = config.get();
        VeinMiningConfig.GroupSettings group = cfg.resolveGroup(player);

        if (!cfg.canOpenGui(uuid, group, isAdmin)) {
            context.sendMessage(Message.raw("You do not have permission to open the VeinMining GUI."));
            return;
        }

        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) return;

        PlayerRef pr = ref.getStore().getComponent(ref, PlayerRef.getComponentType());
        if (pr != null) {
            player.getPageManager().openCustomPage(ref, ref.getStore(), new VeinMiningGui(pr, config));
        }
    }
}