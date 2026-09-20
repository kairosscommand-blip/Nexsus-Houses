package com.nexuscraft.nexushouses;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Runs every {@code treasure.sweep-interval-minutes} and proactively restocks any tracked chest
 * whose restock timer has already elapsed. Without this, a chest would only ever refill the next
 * time someone happened to open it again -- which would make "restocks after N hours" a lie for
 * any chest nobody revisits. Only touches a chest whose world is actually loaded right now (an
 * unloaded world's lookup just fails closed, harmlessly skipped -- it catches up next sweep) and
 * that's still genuinely empty (never overwrites whatever a player deliberately left sitting in
 * it -- that chest simply won't restock until it's fully cleared out).
 */
public final class TreasureRestockTask implements Runnable {

    private final TreasureRegistry registry;
    private final TreasureLootTable lootTable;

    public TreasureRestockTask(TreasureRegistry registry, TreasureLootTable lootTable) {
        this.registry = registry;
        this.lootTable = lootTable;
    }

    @Override
    public void run() {
        if (!lootTable.enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (String key : List.copyOf(registry.trackedChestKeys())) {
            if (registry.isDue(key, now)) {
                restockIfLoadedAndEmpty(key);
            }
        }
    }

    private void restockIfLoadedAndEmpty(String key) {
        String[] parts = key.split(",");
        if (parts.length != 4) {
            return;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return;
        }
        int x;
        int y;
        int z;
        try {
            x = Integer.parseInt(parts[1]);
            y = Integer.parseInt(parts[2]);
            z = Integer.parseInt(parts[3]);
        } catch (NumberFormatException ex) {
            return;
        }

        Block block = world.getBlockAt(x, y, z);
        if (!(block.getState() instanceof Chest chest)) {
            return;
        }
        Inventory inventory = chest.getInventory();
        if (!TreasureRegistry.isEmpty(inventory)) {
            return;
        }
        for (ItemStack item : lootTable.roll()) {
            inventory.addItem(item);
        }
        registry.clearTimer(key);
    }
}
