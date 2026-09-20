package com.nexuscraft.nexushouses;

import org.bukkit.Material;

/** One entry in the treasure loot pool -- a material, a random amount range per stack, and how
 *  often it's picked relative to the rest of the pool. See TreasureLootTable#roll. */
public record TreasureLoot(Material material, int min, int max, int weight) {
}
