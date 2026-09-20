package com.nexuscraft.nexushouses;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Persists treasure regions and every tracked chest's restock timer to treasure.yml -- same
 * flat-file-registry pattern as HouseRegistry's own houses.yml. A chest's "state" here is
 * deliberately tiny: just whether it's been seen at all, and (once it's been found empty) the real
 * timestamp it's next due to restock. The chest's actual contents live on the chest itself, same
 * as any other block in the world -- nothing here duplicates that.
 *
 * Chest state is stored as a config-driven list of maps (one map per tracked chest, its raw
 * "world,x,y,z" key as a plain string value) rather than as YAML path segments -- a world name
 * containing a literal '.' would otherwise collide with YAML's own path separator. Same pattern
 * this project family already uses for other config-driven lists (see TreasureLootTable's own
 * loot pool).
 */
public final class TreasureRegistry {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, TreasureRegion> regionsById = new LinkedHashMap<>();
    private final Set<String> knownChests = new LinkedHashSet<>();
    private final Map<String, Long> nextRestockAtMillis = new LinkedHashMap<>();

    public TreasureRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "treasure.yml");
    }

    public void load() {
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        regionsById.clear();
        knownChests.clear();
        nextRestockAtMillis.clear();

        ConfigurationSection regions = data.getConfigurationSection("regions");
        if (regions != null) {
            for (String idKey : regions.getKeys(false)) {
                ConfigurationSection s = regions.getConfigurationSection(idKey);
                if (s == null) {
                    continue;
                }
                try {
                    UUID id = UUID.fromString(idKey);
                    String name = s.getString("name", "Unnamed Region");
                    String world = s.getString("world", "world");
                    double x = s.getDouble("x");
                    double y = s.getDouble("y");
                    double z = s.getDouble("z");
                    int radius = s.getInt("radius", 32);
                    regionsById.put(id, new TreasureRegion(id, name, world, x, y, z, radius));
                } catch (Exception e) {
                    plugin.getLogger().warning("[NexusHouses] Skipping a corrupt treasure region (" + idKey + "): " + e.getMessage());
                }
            }
        }

        for (Map<?, ?> raw : data.getMapList("chests")) {
            Object keyObj = raw.get("key");
            if (keyObj == null) {
                continue;
            }
            String key = String.valueOf(keyObj);
            knownChests.add(key);
            long due = 0L;
            Object dueObj = raw.get("next-restock");
            if (dueObj != null) {
                try {
                    due = Long.parseLong(String.valueOf(dueObj));
                } catch (NumberFormatException ignored) {
                    // Malformed -- treat as "no timer scheduled" rather than failing the whole load.
                }
            }
            if (due > 0) {
                nextRestockAtMillis.put(key, due);
            }
        }
    }

    public void save() {
        YamlConfiguration data = new YamlConfiguration();
        data.set("regions", null);
        for (TreasureRegion region : regionsById.values()) {
            String path = "regions." + region.id();
            data.set(path + ".name", region.name());
            data.set(path + ".world", region.world());
            data.set(path + ".x", region.x());
            data.set(path + ".y", region.y());
            data.set(path + ".z", region.z());
            data.set(path + ".radius", region.radiusBlocks());
        }

        List<Map<String, Object>> chestList = new ArrayList<>();
        for (String key : knownChests) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("key", key);
            Long due = nextRestockAtMillis.get(key);
            entry.put("next-restock", due != null ? due : 0L);
            chestList.add(entry);
        }
        data.set("chests", chestList);

        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("[NexusHouses] Could not save treasure.yml: " + e.getMessage());
        }
    }

    // ---- Regions ----

    public TreasureRegion createRegion(String name, Location center, int radiusBlocks) {
        TreasureRegion region = new TreasureRegion(UUID.randomUUID(), name, center.getWorld().getName(),
                center.getX(), center.getY(), center.getZ(), radiusBlocks);
        regionsById.put(region.id(), region);
        save();
        return region;
    }

    public boolean removeRegionByName(String name) {
        TreasureRegion match = byName(name);
        if (match == null) {
            return false;
        }
        regionsById.remove(match.id());
        save();
        return true;
    }

    public TreasureRegion byName(String name) {
        for (TreasureRegion region : regionsById.values()) {
            if (region.name().equalsIgnoreCase(name)) {
                return region;
            }
        }
        return null;
    }

    public Collection<TreasureRegion> allRegions() {
        return regionsById.values();
    }

    public TreasureRegion regionContaining(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        for (TreasureRegion region : regionsById.values()) {
            if (region.contains(location.getWorld().getName(), location.getX(), location.getY(), location.getZ())) {
                return region;
            }
        }
        return null;
    }

    // ---- Chest state ----

    public static String keyFor(Location location) {
        return location.getWorld().getName() + "," + location.getBlockX() + ","
                + location.getBlockY() + "," + location.getBlockZ();
    }

    public boolean isKnownChest(String key) {
        return knownChests.contains(key);
    }

    public void registerChest(String key) {
        if (knownChests.add(key)) {
            save();
        }
    }

    public boolean isDue(String key, long nowMillis) {
        Long due = nextRestockAtMillis.get(key);
        return due != null && due > 0 && nowMillis >= due;
    }

    public boolean hasNoTimerYet(String key) {
        Long due = nextRestockAtMillis.get(key);
        return due == null || due <= 0;
    }

    public void scheduleRestock(String key, long atMillis) {
        nextRestockAtMillis.put(key, atMillis);
        save();
    }

    public void clearTimer(String key) {
        nextRestockAtMillis.remove(key);
        save();
    }

    public Set<String> trackedChestKeys() {
        return knownChests;
    }

    public static boolean isEmpty(Inventory inventory) {
        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() != Material.AIR && item.getAmount() > 0) {
                return false;
            }
        }
        return true;
    }
}
