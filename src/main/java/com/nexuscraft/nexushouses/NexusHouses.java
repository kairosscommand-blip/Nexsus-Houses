package com.nexuscraft.nexushouses;

import com.nexuscraft.nexushouses.api.NexusHousesApi;
import com.nexuscraft.nexushouses.api.NexusHousesApiImpl;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class NexusHouses extends JavaPlugin {

    private HouseRegistry houseRegistry;
    private HouseEngine houseEngine;
    private TreasureRegistry treasureRegistry;
    private TreasureLootTable treasureLootTable;
    private BukkitTask treasureSweepTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        HouseKeys keys = new HouseKeys(this);

        this.houseRegistry = new HouseRegistry(this);
        houseRegistry.load();

        this.houseEngine = new HouseEngine(this, houseRegistry, keys);
        houseEngine.loadFromConfig();
        getServer().getPluginManager().registerEvents(houseEngine, this);

        // Horse allegiance banners -- sneak-right-click your own tamed horse with your house's
        // banner. See HorseAllegianceListener's own doc comment.
        getServer().getPluginManager().registerEvents(new HorseAllegianceListener(houseEngine, keys), this);

        // Treasure regions -- named admin-defined zones where every real chest becomes a living,
        // self-restocking treasure chest. See TreasureRegistry/TreasureChestListener/
        // TreasureRestockTask's own doc comments.
        this.treasureRegistry = new TreasureRegistry(this);
        treasureRegistry.load();
        this.treasureLootTable = new TreasureLootTable(this);
        treasureLootTable.load();
        getServer().getPluginManager().registerEvents(
                new TreasureChestListener(treasureRegistry, treasureLootTable), this);
        startTreasureSweepTask();

        EconomyBridge economy = new EconomyBridge();
        EndeavorsBridge endeavors = new EndeavorsBridge(this);
        HouseCommandExecutor executor = new HouseCommandExecutor(houseEngine, houseRegistry, treasureRegistry,
                this, economy, endeavors);
        getCommand("house").setExecutor(executor);

        // Publishes areRivals(UUID, UUID) for the rest of the Nexus family to query without a
        // compile-time dependency (v1.1.0) -- added for NexusThreshold's rivalry-scars
        // subsystem, which weighs real PvP much more heavily between two players from actual
        // declared-rival houses. Same soft Class.forName + ServicesManager pattern as every
        // other cross-plugin surface in this project.
        getServer().getServicesManager().register(NexusHousesApi.class,
                new NexusHousesApiImpl(houseRegistry), this, ServicePriority.Normal);

        getLogger().info("NexusHouses enabled. " + houseRegistry.size() + " house(s) loaded. "
                + (houseEngine.isEnabled() ? "Succession on death: ON" : "Succession on death: OFF") + ". "
                + treasureRegistry.allRegions().size() + " treasure region(s).");
    }

    private void startTreasureSweepTask() {
        if (treasureSweepTask != null) {
            treasureSweepTask.cancel();
        }
        long intervalTicks = 20L * 60 * Math.max(1, treasureLootTable.sweepIntervalMinutes());
        this.treasureSweepTask = getServer().getScheduler().runTaskTimer(this,
                new TreasureRestockTask(treasureRegistry, treasureLootTable), intervalTicks, intervalTicks);
    }

    @Override
    public void onDisable() {
        if (houseRegistry != null) {
            houseRegistry.save();
        }
        if (treasureSweepTask != null) {
            treasureSweepTask.cancel();
        }
        if (treasureRegistry != null) {
            treasureRegistry.save();
        }
        getLogger().info("NexusHouses disabled.");
    }

    /** Reloads config.yml and re-applies settings to the house engine and treasure system. */
    public void reloadAll() {
        reloadConfig();
        houseEngine.loadFromConfig();
        treasureLootTable.load();
        startTreasureSweepTask();
    }
}
