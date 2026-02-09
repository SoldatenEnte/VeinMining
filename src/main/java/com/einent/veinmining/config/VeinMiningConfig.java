package com.einent.veinmining.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.permissions.provider.PermissionProvider;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.*;

public class VeinMiningConfig {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private boolean masterModEnabled = true;
    private boolean masterGuiEnabled = true;
    private boolean masterSoundEnabled = true;
    private int masterMaxLimit = 1000;
    private boolean showPatternsAboveLimit = true;

    private String[] globalBlacklistPatterns = new String[0];
    private String[] blockWhitelist = new String[0];
    private String[] blockBlacklist = new String[0];

    private double durabilityMultiplier = 1.0;
    private String dropMode = "break";
    private boolean bundleDrops = false;
    private boolean requireValidTool = true;
    private boolean instantBreak = false;
    private String[] validTools = new String[] { "Pickaxe", "Hatchet", "Shovel", "Shears" };

    private final Map<String, GroupSettings> groups = new HashMap<>();
    private final Map<String, PlayerOverride> playerOverrides = new HashMap<>();
    private final Map<String, PlayerModeEntry> playerSettingsMap = new HashMap<>();

    public VeinMiningConfig() {
        String[] allModes = new String[]{"ores", "all", "off"};
        String[] allPatterns = new String[]{"freeform", "tunnel3", "cube", "tunnel2", "wall3", "tunnel1", "wall5", "diagonal"};
        groups.put("default", new GroupSettings("default", -1, 50, true, true, allModes, allPatterns));
    }

    public static final BuilderCodec<VeinMiningConfig> CODEC = BuilderCodec.builder(VeinMiningConfig.class, VeinMiningConfig::new)
            .append(new KeyedCodec<>("MasterModEnabled", Codec.BOOLEAN), (c, v, i) -> c.masterModEnabled = v, (c, i) -> c.masterModEnabled).add()
            .append(new KeyedCodec<>("MasterGuiEnabled", Codec.BOOLEAN), (c, v, i) -> c.masterGuiEnabled = v, (c, i) -> c.masterGuiEnabled).add()
            .append(new KeyedCodec<>("MasterSoundEnabled", Codec.BOOLEAN), (c, v, i) -> c.masterSoundEnabled = v, (c, i) -> c.masterSoundEnabled).add()
            .append(new KeyedCodec<>("MasterMaxLimit", Codec.INTEGER), (c, v, i) -> c.masterMaxLimit = v, (c, i) -> c.masterMaxLimit).add()
            .append(new KeyedCodec<>("ShowPatternsAboveLimit", Codec.BOOLEAN), (c, v, i) -> c.showPatternsAboveLimit = v, (c, i) -> c.showPatternsAboveLimit).add()
            .append(new KeyedCodec<>("GlobalBlacklistPatterns", new ArrayCodec<>(Codec.STRING, String[]::new)), (c, v, i) -> c.globalBlacklistPatterns = v, (c, i) -> c.globalBlacklistPatterns).add()
            .append(new KeyedCodec<>("BlockWhitelist", new ArrayCodec<>(Codec.STRING, String[]::new)), (c, v, i) -> c.blockWhitelist = v, (c, i) -> c.blockWhitelist).add()
            .append(new KeyedCodec<>("BlockBlacklist", new ArrayCodec<>(Codec.STRING, String[]::new)), (c, v, i) -> c.blockBlacklist = v, (c, i) -> c.blockBlacklist).add()
            .append(new KeyedCodec<>("DurabilityMultiplier", Codec.DOUBLE), (c, v, i) -> c.durabilityMultiplier = v, (c, i) -> c.durabilityMultiplier).add()
            .append(new KeyedCodec<>("DropMode", Codec.STRING), (c, v, i) -> c.dropMode = v, (c, i) -> c.dropMode).add()
            .append(new KeyedCodec<>("BundleDrops", Codec.BOOLEAN), (c, v, i) -> c.bundleDrops = v, (c, i) -> c.bundleDrops).add()
            .append(new KeyedCodec<>("RequireValidTool", Codec.BOOLEAN), (c, v, i) -> c.requireValidTool = v, (c, i) -> c.requireValidTool).add()
            .append(new KeyedCodec<>("InstantBreak", Codec.BOOLEAN), (c, v, i) -> c.instantBreak = v, (c, i) -> c.instantBreak).add()
            .append(new KeyedCodec<>("ValidTools", new ArrayCodec<>(Codec.STRING, String[]::new)), (c, v, i) -> c.validTools = v, (c, i) -> c.validTools).add()
            .append(new KeyedCodec<>("Groups", new ArrayCodec<>(GroupSettings.CODEC, GroupSettings[]::new)), (c, v, i) -> c.setGroupsFromArray(v), (c, i) -> c.getGroupsAsArray()).add()
            .append(new KeyedCodec<>("PlayerOverrides", new ArrayCodec<>(PlayerOverride.CODEC, PlayerOverride[]::new)), (c, v, i) -> c.setOverridesFromArray(v), (c, i) -> c.getOverridesAsArray()).add()
            .append(new KeyedCodec<>("PlayerData", new ArrayCodec<>(PlayerModeEntry.CODEC, PlayerModeEntry[]::new)), (c, v, i) -> c.setDataFromArray(v), (c, i) -> c.getDataAsArray()).add()
            .build();

    public GroupSettings resolveGroup(Player player) {
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) return groups.get("default");
        PlayerRef pr = ref.getStore().getComponent(ref, PlayerRef.getComponentType());
        if (pr == null) return groups.get("default");
        UUID uuid = pr.getUuid();
        PermissionProvider provider = PermissionsModule.get().getFirstPermissionProvider();
        Set<String> playerDirectNodes = provider.getUserPermissions(uuid);
        Set<String> playerGroups = PermissionsModule.get().getGroupsForUser(uuid);
        GroupSettings best = groups.get("default");
        int highestPriority = best.priority;
        for (GroupSettings g : groups.values()) {
            if (g.name == null || g.name.equalsIgnoreCase("default")) continue;
            String rankNode = "veinmining.group." + g.name.toLowerCase();
            boolean hasNode = false;
            if (playerDirectNodes != null && playerDirectNodes.contains(rankNode)) {
                hasNode = true;
            } else {
                for (String groupName : playerGroups) {
                    Set<String> groupNodes = provider.getGroupPermissions(groupName);
                    if (groupNodes != null && groupNodes.contains(rankNode)) {
                        hasNode = true;
                        break;
                    }
                }
            }
            if (hasNode && g.priority > highestPriority) {
                highestPriority = g.priority;
                best = g;
            }
        }
        return best;
    }

    public boolean isModEnabled(String uuid, boolean isAdmin) {
        if (!masterModEnabled) return false;
        PlayerOverride ov = playerOverrides.get(uuid);
        if (ov != null && ov.modEnabled != null) return ov.modEnabled;
        if (isAdmin) return true;
        PermissionProvider provider = PermissionsModule.get().getFirstPermissionProvider();
        try {
            UUID id = UUID.fromString(uuid);
            Set<String> playerGroups = PermissionsModule.get().getGroupsForUser(id);
            GroupSettings best = groups.get("default");
            int highestPriority = best.priority;
            for (GroupSettings g : groups.values()) {
                if (g.name == null || g.name.equalsIgnoreCase("default")) continue;
                String rankNode = "veinmining.group." + g.name.toLowerCase();
                Set<String> playerDirectNodes = provider.getUserPermissions(id);
                boolean hasNode = (playerDirectNodes != null && playerDirectNodes.contains(rankNode));
                if (!hasNode) {
                    for (String groupName : playerGroups) {
                        Set<String> groupNodes = provider.getGroupPermissions(groupName);
                        if (groupNodes != null && groupNodes.contains(rankNode)) {
                            hasNode = true;
                            break;
                        }
                    }
                }
                if (hasNode && g.priority > highestPriority) {
                    highestPriority = g.priority;
                    best = g;
                }
            }
            return best.modEnabled;
        } catch (Exception e) {
            return groups.get("default").modEnabled;
        }
    }

    public int getEffectiveLimit(String uuid, GroupSettings group, boolean isAdmin) {
        PlayerOverride ov = playerOverrides.get(uuid);
        if (ov != null && ov.maxVeinSize != null) return Math.min(ov.maxVeinSize, masterMaxLimit);
        if (group != null) return Math.min(group.maxVeinSize, masterMaxLimit);
        if (isAdmin) return masterMaxLimit;
        return Math.min(groups.get("default").maxVeinSize, masterMaxLimit);
    }

    public boolean canOpenGui(String uuid, GroupSettings group, boolean isAdmin) {
        if (!masterGuiEnabled && !isAdmin) return false;
        PlayerOverride ov = playerOverrides.get(uuid);
        if (ov != null && ov.canOpenGui != null) return ov.canOpenGui;
        if (group != null) return group.canOpenGui;
        if (isAdmin) return true;
        return groups.get("default").canOpenGui;
    }

    public List<String> getEffectiveAllowedModes(String uuid, GroupSettings group, boolean isAdmin) {
        PlayerOverride ov = playerOverrides.get(uuid);
        if (ov != null && ov.allowedModes != null) return Arrays.asList(ov.allowedModes);
        if (group != null && group.allowedModes != null && group.allowedModes.length > 0) return Arrays.asList(group.allowedModes);
        return Arrays.asList("ores", "all", "off");
    }

    public boolean isPatternAllowed(String uuid, String patternId, GroupSettings group, boolean isAdmin) {
        for (String s : globalBlacklistPatterns) {
            if (s.equalsIgnoreCase(patternId)) return false;
        }
        PlayerOverride ov = playerOverrides.get(uuid);
        if (ov != null && ov.allowedPatterns != null) {
            for (String p : ov.allowedPatterns) if (p.equalsIgnoreCase(patternId)) return true;
            return false;
        }
        if (group != null && group.allowedPatterns != null && group.allowedPatterns.length > 0) {
            for (String p : group.allowedPatterns) if (p.equalsIgnoreCase(patternId)) return true;
            return false;
        }
        return true;
    }

    public String getValidatedTargetMode(String uuid, GroupSettings group, boolean isAdmin) {
        String mode = getPlayerTargetMode(uuid);
        List<String> allowed = getEffectiveAllowedModes(uuid, group, isAdmin);
        if (allowed.contains(mode)) return mode;
        if (allowed.contains("off")) return "off";
        return allowed.isEmpty() ? "off" : allowed.get(0);
    }

    public String getValidatedPattern(String uuid, GroupSettings group, boolean isAdmin) {
        String pattern = getPlayerPattern(uuid);
        if (isPatternAllowed(uuid, pattern, group, isAdmin)) return pattern;
        return "freeform";
    }

    public void setPlayerOverride(String uuid, Integer limit, Boolean gui, Boolean enabled, String[] modes, String[] patterns) {
        PlayerOverride ov = playerOverrides.computeIfAbsent(uuid, PlayerOverride::new);
        ov.maxVeinSize = limit;
        ov.canOpenGui = gui;
        ov.modEnabled = enabled;
        ov.allowedModes = modes;
        ov.allowedPatterns = patterns;
    }

    private PlayerModeEntry getEntry(String uuid) { return playerSettingsMap.computeIfAbsent(uuid, PlayerModeEntry::new); }
    public String getPlayerTargetMode(String uuid) { return getEntry(uuid).targetMode; }
    public void setPlayerTargetMode(String uuid, String mode) { getEntry(uuid).targetMode = mode; }
    public String getPlayerPattern(String uuid) { return getEntry(uuid).pattern; }
    public void setPlayerPattern(String uuid, String pattern) { getEntry(uuid).pattern = pattern; }
    public String getPlayerOrientation(String uuid) { return getEntry(uuid).orientation; }
    public void setPlayerOrientation(String uuid, String orientation) { getEntry(uuid).orientation = orientation; }
    public String getPlayerActivation(String uuid) { return getEntry(uuid).activationKey; }
    public void setPlayerActivation(String uuid, String activation) { getEntry(uuid).activationKey = activation; }

    public List<String> getBlockWhitelist() { return blockWhitelist != null ? Arrays.asList(blockWhitelist) : new ArrayList<>(); }
    public List<String> getBlockBlacklist() { return blockBlacklist != null ? Arrays.asList(blockBlacklist) : new ArrayList<>(); }
    public String getDropMode() { return dropMode; }
    public boolean isBundleDrops() { return bundleDrops; }
    public double getDurabilityMultiplier() { return durabilityMultiplier; }
    public boolean isRequireValidTool() { return requireValidTool; }
    public List<String> getValidTools() { return Arrays.asList(validTools); }
    public boolean isInstantBreak() { return instantBreak; }
    public boolean isShowPatternsAboveLimit() { return showPatternsAboveLimit; }
    public boolean isMasterSoundEnabled() { return masterSoundEnabled; }

    private void setGroupsFromArray(GroupSettings[] array) {
        groups.clear();
        String[] allModes = new String[]{"ores", "all", "off"};
        String[] allPatterns = new String[]{"freeform", "tunnel3", "cube", "tunnel2", "wall3", "tunnel1", "wall5", "diagonal"};
        groups.put("default", new GroupSettings("default", -1, 50, true, true, allModes, allPatterns));
        if (array != null) {
            for (GroupSettings g : array) {
                if (g.name != null) groups.put(g.name.toLowerCase(), g);
            }
        }
    }
    private GroupSettings[] getGroupsAsArray() { return groups.values().toArray(new GroupSettings[0]); }

    private void setOverridesFromArray(PlayerOverride[] array) {
        playerOverrides.clear();
        if (array != null) for (PlayerOverride o : array) playerOverrides.put(o.uuid, o);
    }
    private PlayerOverride[] getOverridesAsArray() { return playerOverrides.values().toArray(new PlayerOverride[0]); }

    private void setDataFromArray(PlayerModeEntry[] array) {
        playerSettingsMap.clear();
        if (array != null) for (PlayerModeEntry e : array) playerSettingsMap.put(e.uuid, e);
    }
    private PlayerModeEntry[] getDataAsArray() { return playerSettingsMap.values().toArray(new PlayerModeEntry[0]); }

    public static class GroupSettings {
        public String name;
        public int priority;
        public int maxVeinSize;
        public boolean canOpenGui;
        public boolean modEnabled;
        public String[] allowedModes;
        public String[] allowedPatterns;

        public GroupSettings() {}
        public GroupSettings(String name, int priority, int limit, boolean gui, boolean enabled, String[] modes, String[] patterns) {
            this.name = name; this.priority = priority; this.maxVeinSize = limit; this.canOpenGui = gui; this.modEnabled = enabled; this.allowedModes = modes; this.allowedPatterns = patterns;
        }

        public static final BuilderCodec<GroupSettings> CODEC = BuilderCodec.builder(GroupSettings.class, GroupSettings::new)
                .append(new KeyedCodec<>("Name", Codec.STRING), (o, v, i) -> o.name = v, (o, i) -> o.name).add()
                .append(new KeyedCodec<>("Priority", Codec.INTEGER), (o, v, i) -> o.priority = v, (o, i) -> o.priority).add()
                .append(new KeyedCodec<>("MaxVeinSize", Codec.INTEGER), (o, v, i) -> o.maxVeinSize = v, (o, i) -> o.maxVeinSize).add()
                .append(new KeyedCodec<>("CanOpenGui", Codec.BOOLEAN), (o, v, i) -> o.canOpenGui = v, (o, i) -> o.canOpenGui).add()
                .append(new KeyedCodec<>("ModEnabled", Codec.BOOLEAN), (o, v, i) -> o.modEnabled = v, (o, i) -> o.modEnabled).add()
                .append(new KeyedCodec<>("AllowedModes", new ArrayCodec<>(Codec.STRING, String[]::new)), (o, v, i) -> o.allowedModes = v, (o, i) -> o.allowedModes).add()
                .append(new KeyedCodec<>("AllowedPatterns", new ArrayCodec<>(Codec.STRING, String[]::new)), (o, v, i) -> o.allowedPatterns = v, (o, i) -> o.allowedPatterns).add()
                .build();
    }

    public static class PlayerOverride {
        public String uuid;
        public Integer maxVeinSize = null;
        public Boolean canOpenGui = null;
        public Boolean modEnabled = null;
        public String[] allowedModes = null;
        public String[] allowedPatterns = null;

        public PlayerOverride() {}
        public PlayerOverride(String uuid) { this.uuid = uuid; }

        public static final BuilderCodec<PlayerOverride> CODEC = BuilderCodec.builder(PlayerOverride.class, PlayerOverride::new)
                .append(new KeyedCodec<>("UUID", Codec.STRING), (o, v, i) -> o.uuid = v, (o, i) -> o.uuid).add()
                .append(new KeyedCodec<>("MaxVeinSize", Codec.INTEGER), (o, v, i) -> o.maxVeinSize = v, (o, i) -> o.maxVeinSize).add()
                .append(new KeyedCodec<>("CanOpenGui", Codec.BOOLEAN), (o, v, i) -> o.canOpenGui = v, (o, i) -> o.canOpenGui).add()
                .append(new KeyedCodec<>("ModEnabled", Codec.BOOLEAN), (o, v, i) -> o.modEnabled = v, (o, i) -> o.modEnabled).add()
                .append(new KeyedCodec<>("AllowedModes", new ArrayCodec<>(Codec.STRING, String[]::new)), (o, v, i) -> o.allowedModes = v, (o, i) -> o.allowedModes).add()
                .append(new KeyedCodec<>("AllowedPatterns", new ArrayCodec<>(Codec.STRING, String[]::new)), (o, v, i) -> o.allowedPatterns = v, (o, i) -> o.allowedPatterns).add()
                .build();
    }

    public static class PlayerModeEntry {
        public String uuid;
        public String targetMode = "all";
        public String pattern = "freeform";
        public String orientation = "block";
        public String activationKey = "walking";

        public PlayerModeEntry() {}
        public PlayerModeEntry(String uuid) { this.uuid = uuid; }

        public static final BuilderCodec<PlayerModeEntry> CODEC = BuilderCodec.builder(PlayerModeEntry.class, PlayerModeEntry::new)
                .append(new KeyedCodec<>("UUID", Codec.STRING), (o, v, i) -> o.uuid = v, (o, i) -> o.uuid).add()
                .append(new KeyedCodec<>("Mode", Codec.STRING), (o, v, i) -> o.targetMode = v, (o, i) -> o.targetMode).add()
                .append(new KeyedCodec<>("Pattern", Codec.STRING), (o, v, i) -> o.pattern = v, (o, i) -> o.pattern).add()
                .append(new KeyedCodec<>("Orientation", Codec.STRING), (o, v, i) -> o.orientation = v, (o, i) -> o.orientation).add()
                .append(new KeyedCodec<>("Activation", Codec.STRING), (o, v, i) -> o.activationKey = v, (o, i) -> o.activationKey).add()
                .build();
    }
}