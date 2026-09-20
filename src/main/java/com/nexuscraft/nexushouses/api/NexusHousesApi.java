package com.nexuscraft.nexushouses.api;

import java.util.Set;
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
 * Bukkit.getServicesManager(), and invoke a method by Method.invoke -- the same Class.forName +
 * ServicesManager pattern this whole project uses everywhere else (see NexusRealmsApi's own
 * javadoc, which documents the convention this follows).
 *
 * <p>{@code areRivals} was added for NexusThreshold (real PvP between two declared-rival houses'
 * players weighs more heavily toward scarring the ground). {@code houseIdOf}/{@code houseName}/
 * {@code allianceBlockOf} were added in v1.5.0 for NexusWarfare's territory-control system, which
 * needs to know which "side" a player's house is actually fighting on -- see
 * {@code allianceBlockOf}'s own javadoc for exactly what that means. All four are read-only,
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

    /** The id of the house playerId currently belongs to, or null if they belong to none. */
    UUID houseIdOf(UUID playerId);

    /** That house's current display name, or null if houseId doesn't match a real house. */
    String houseName(UUID houseId);

    /** The id of the house currently named houseName (case-insensitive), or null if no house has
     *  that name. Added alongside the other v1.5.0 methods so a consumer can resolve a house by the
     *  name an admin typed (e.g. NexusWarfare's {@code /warfare keep createhome}) without needing
     *  its own separate house registry. */
    UUID houseIdByName(String houseName);

    /**
     * The full set of house ids that make up houseId's "side" for anything that cares which
     * houses stand together -- houseId itself, its liege (if it's sworn to one), every house
     * sworn to IT, and every house it's directly allied with, walked transitively until no new
     * house is reachable (so two independently-allied liege trees merge into one block the moment
     * either side allies the other). Declared rivalry is deliberately NOT part of this walk --
     * NexusHouses doesn't mechanically enforce rivalry today, so a house could in principle end up
     * in the same alliance block as a declared rival by way of a shared ally; that's an existing
     * property of the political web this reads, not something this method changes.
     *
     * <p>Returns a set containing only houseId itself if it has no liege, no vassals, and no
     * allies -- i.e. every house is at minimum its own one-house alliance block. Returns an empty
     * set if houseId doesn't match a real house at all.
     */
    Set<UUID> allianceBlockOf(UUID houseId);
}
