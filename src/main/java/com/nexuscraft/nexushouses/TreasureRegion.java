package com.nexuscraft.nexushouses;

import java.util.UUID;

/** A named, admin-defined sphere -- a kingdom, a city, a ruin, wherever -- inside which every real
 *  chest becomes a living treasure chest (see TreasureRegistry, TreasureChestListener). */
public record TreasureRegion(UUID id, String name, String world, double x, double y, double z, int radiusBlocks) {

    public boolean contains(String worldName, double px, double py, double pz) {
        if (!world.equalsIgnoreCase(worldName)) {
            return false;
        }
        double dx = px - x;
        double dy = py - y;
        double dz = pz - z;
        return dx * dx + dy * dy + dz * dz <= (double) radiusBlocks * radiusBlocks;
    }
}
