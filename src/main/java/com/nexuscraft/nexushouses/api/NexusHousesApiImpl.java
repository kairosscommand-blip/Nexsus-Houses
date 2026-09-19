package com.nexuscraft.nexushouses.api;

import com.nexuscraft.nexushouses.House;
import com.nexuscraft.nexushouses.HouseRegistry;

import java.util.UUID;

/** Thin adapter from the public {@link NexusHousesApi} surface onto the real {@link HouseRegistry}. */
public final class NexusHousesApiImpl implements NexusHousesApi {

    private final HouseRegistry registry;

    public NexusHousesApiImpl(HouseRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean areRivals(UUID playerIdA, UUID playerIdB) {
        if (playerIdA == null || playerIdB == null) {
            return false;
        }
        House houseA = registry.houseOf(playerIdA);
        House houseB = registry.houseOf(playerIdB);
        if (houseA == null || houseB == null || houseA.id().equals(houseB.id())) {
            return false;
        }
        return houseA.rivals().contains(houseB.id()) || houseB.rivals().contains(houseA.id());
    }
}
