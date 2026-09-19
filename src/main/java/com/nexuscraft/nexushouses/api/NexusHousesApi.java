package com.nexuscraft.nexushouses.api;

import java.util.UUID;

/**
 * Small, stable public surface other plugins can query without needing NexusHouses as a compile
 * dependency. Registered with Bukkit's ServicesManager on enable:
 *
 *   RegisteredServiceProvider&lt;NexusHousesApi&gt; reg =
 *       Bukkit.getServicesManager().getRegistration(NexusHousesApi.class);
 *
 * A plugin without this interface on its own classpath can still reach it via reflection:
 * Class.forName("com.nexuscraft.nexushouses.api.NexusHousesApi"), pull the registration off
 * Bukkit.getServicesManager(), and invoke areRivals(UUID, UUID) by Method.invoke -- the same
 * Class.forName + ServicesManager pattern this whole project uses everywhere else (see
 * NexusRealmsApi's own javadoc, which documents the convention this follows). Added for
 * NexusThreshold (a separate plugin whose "rivalry scars" subsystem weighs real PvP much more
 * heavily when it happens between two players from actual declared-rival houses) -- read-only,
 * additive, and safe for any other consumer to ignore.
 */
public interface NexusHousesApi {

    /**
     * True if playerIdA and playerIdB currently belong to houses that consider each other rivals
     * -- checked both directions, since declaring a rival in this plugin is deliberately
     * unilateral (see HouseEngine#declareRival), so only one house's rivals set may actually
     * contain the other's id. False if either player has no house at all, or both belong to the
     * same house.
     */
    boolean areRivals(UUID playerIdA, UUID playerIdB);
}
