package com.nexuscraft.nexushouses;

import org.bukkit.Location;
import org.bukkit.block.Chest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Every real chest inside a defined treasure region (see TreasureRegistry, {@code /house region})
 * becomes a living treasure chest: stocked with a fresh randomized haul the first moment anyone
 * opens it, and again once restock-hours has passed since it was last found empty. This listener
 * covers the two moments a player is actually there to see it happen (first discovery, and
 * re-opening one that was already due); {@link TreasureRestockTask} covers the rest -- a chest
 * nobody happens to re-open would otherwise just sit empty forever past its timer.
 */
public final class TreasureChestListener implements Listener {

    private final TreasureRegistry registry;
    private final TreasureLootTable lootTable;

    public TreasureChestListener(TreasureRegistry registry, TreasureLootTable lootTable) {
        this.registry = registry;
        this.lootTable = lootTable;
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        if (!lootTable.enabled()) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof Chest chest)) {
            return;
        }
        Location location = chest.getLocation();
        if (location == null || registry.regionContaining(location) == null) {
            return;
        }

        String key = TreasureRegistry.keyFor(location);
        Inventory inventory = event.getInventory();

        if (!registry.isKnownChest(key)) {
            // First time anyone's found this particular chest -- stock it immediately rather than
            // making the very first discovery an empty box.
            registry.registerChest(key);
            fill(inventory);
            return;
        }

        if (registry.isDue(key, System.currentTimeMillis()) && TreasureRegistry.isEmpty(inventory)) {
            fill(inventory);
            registry.clearTimer(key);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!lootTable.enabled()) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof Chest chest)) {
            return;
        }
        Location location = chest.getLocation();
        if (location == null || registry.regionContaining(location) == null) {
            return;
        }
        String key = TreasureRegistry.keyFor(location);
        if (!registry.isKnownChest(key)) {
            return;
        }
        // The countdown only starts once a haul is actually found and taken -- a chest that's
        // closed still full (or still partly full) isn't "looted" yet.
        if (TreasureRegistry.isEmpty(event.getInventory()) && registry.hasNoTimerYet(key)) {
            registry.scheduleRestock(key, System.currentTimeMillis() + lootTable.restockMillis());
        }
    }

    private void fill(Inventory inventory) {
        for (ItemStack item : lootTable.roll()) {
            inventory.addItem(item);
        }
    }
}
