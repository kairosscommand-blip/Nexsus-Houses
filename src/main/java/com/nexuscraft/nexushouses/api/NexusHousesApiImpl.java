package com.nexuscraft.nexushouses.api;

import com.nexuscraft.nexushouses.House;
import com.nexuscraft.nexushouses.HouseRegistry;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;
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

    @Override
    public UUID houseIdOf(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        House house = registry.houseOf(playerId);
        return house != null ? house.id() : null;
    }

    @Override
    public String houseName(UUID houseId) {
        House house = registry.byId(houseId);
        return house != null ? house.name() : null;
    }

    @Override
    public UUID houseIdByName(String houseName) {
        if (houseName == null) {
            return null;
        }
        House house = registry.byName(houseName);
        return house != null ? house.id() : null;
    }

    @Override
    public Set<UUID> allianceBlockOf(UUID houseId) {
        Set<UUID> block = new LinkedHashSet<>();
        if (houseId == null || registry.byId(houseId) == null) {
            return block;
        }
        Deque<UUID> frontier = new ArrayDeque<>();
        frontier.add(houseId);
        block.add(houseId);
        while (!frontier.isEmpty()) {
            UUID currentId = frontier.poll();
            House current = registry.byId(currentId);
            if (current == null) {
                continue;
            }
            if (current.liege() != null && block.add(current.liege())) {
                frontier.add(current.liege());
            }
            for (UUID vassalId : current.vassals()) {
                if (block.add(vassalId)) {
                    frontier.add(vassalId);
                }
            }
            for (UUID allyId : current.allies()) {
                if (block.add(allyId)) {
                    frontier.add(allyId);
                }
            }
        }
        return block;
    }
}
