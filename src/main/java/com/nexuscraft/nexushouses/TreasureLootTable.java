package com.nexuscraft.nexushouses;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Loads {@code treasure.*} from config.yml -- whether treasure chests are on at all, the
 * restock timer, the sweep cadence, how many stacks a restock places, and the weighted loot pool
 * itself. Same defensive parsing stance every config-driven list in this project family takes: a
 * malformed loot entry (unknown material, missing weight) is skipped individually with a logged
 * warning, never fatal to the rest of the list.
 */
public final class TreasureLootTable {

    private final JavaPlugin plugin;
    private final List<TreasureLoot> entries = new ArrayList<>();
    private final Random random = new Random();

    private boolean enabled = true;
    private long restockHours = 8;
    private int sweepIntervalMinutes = 5;
    private int itemsMin = 2;
    private int itemsMax = 5;

    public TreasureLootTable(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        FileConfiguration config = plugin.getConfig();
        this.enabled = config.getBoolean("treasure.enabled", true);
        this.restockHours = Math.max(1, config.getLong("treasure.restock-hours", 8));
        this.sweepIntervalMinutes = Math.max(1, config.getInt("treasure.sweep-interval-minutes", 5));
        this.itemsMin = Math.max(0, config.getInt("treasure.items-min", 2));
        this.itemsMax = Math.max(itemsMin, config.getInt("treasure.items-max", 5));

        entries.clear();
        for (Map<?, ?> raw : config.getMapList("treasure.loot")) {
            TreasureLoot entry = parseOne(raw);
            if (entry != null) {
                entries.add(entry);
            }
        }
        plugin.getLogger().info("NexusHouses: loaded " + entries.size() + " treasure loot entr"
                + (entries.size() == 1 ? "y" : "ies") + (enabled ? "" : " (treasure chests currently disabled)") + ".");
    }

    private TreasureLoot parseOne(Map<?, ?> raw) {
        Material material;
        try {
            material = Material.valueOf(String.valueOf(raw.get("material")).trim().toUpperCase());
        } catch (Exception ex) {
            plugin.getLogger().warning("NexusHouses: skipping a treasure loot entry with an unknown or missing 'material'.");
            return null;
        }
        int min = Math.max(1, intVal(raw, "min", 1));
        int max = Math.max(min, intVal(raw, "max", min));
        int weight = intVal(raw, "weight", 10);
        if (weight < 1) {
            weight = 1;
        }
        return new TreasureLoot(material, min, max, weight);
    }

    private static int intVal(Map<?, ?> raw, String key, int def) {
        Object value = raw.get(key);
        if (value == null) {
            return def;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public long restockMillis() {
        return restockHours * 3600_000L;
    }

    public int sweepIntervalMinutes() {
        return sweepIntervalMinutes;
    }

    /** Rolls a fresh randomized haul -- items-min..items-max independent weighted picks, each its
     *  own stack. Repeats are allowed on purpose (unlike a hatchling's trait roll): a real chest
     *  can hold two stacks of the same thing. Empty if the loot pool itself is empty. */
    public List<ItemStack> roll() {
        List<ItemStack> result = new ArrayList<>();
        if (entries.isEmpty()) {
            return result;
        }
        int count = itemsMin == itemsMax ? itemsMin : itemsMin + random.nextInt(itemsMax - itemsMin + 1);
        int totalWeight = 0;
        for (TreasureLoot entry : entries) {
            totalWeight += entry.weight();
        }
        for (int i = 0; i < count; i++) {
            TreasureLoot picked = weightedPick(totalWeight);
            int amount = picked.min() == picked.max() ? picked.min()
                    : picked.min() + random.nextInt(picked.max() - picked.min() + 1);
            result.add(new ItemStack(picked.material(), amount));
        }
        return result;
    }

    private TreasureLoot weightedPick(int totalWeight) {
        int roll = random.nextInt(Math.max(1, totalWeight));
        int cumulative = 0;
        for (TreasureLoot entry : entries) {
            cumulative += entry.weight();
            if (roll < cumulative) {
                return entry;
            }
        }
        return entries.get(entries.size() - 1);
    }
}
