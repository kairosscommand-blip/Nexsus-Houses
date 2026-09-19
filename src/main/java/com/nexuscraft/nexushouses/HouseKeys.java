package com.nexuscraft.nexushouses;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * PersistentDataContainer keys used across this plugin. A banner item is tagged with the house
 * id it was issued for the instant it's created ({@link HouseEngine#buildBannerItem}) -- never
 * identified by its display name or lore, which either could be renamed to spoof (same
 * unspoofable-tag philosophy every other Nexus plugin uses). A horse carries the same house id,
 * plus the real Player who bound it, once it's sworn to that house's colors
 * ({@link HorseAllegianceListener}).
 */
public final class HouseKeys {

    public final NamespacedKey bannerHouseId;
    public final NamespacedKey horseHouseId;
    public final NamespacedKey horseSwornBy;

    public HouseKeys(JavaPlugin plugin) {
        this.bannerHouseId = new NamespacedKey(plugin, "banner-house-id");
        this.horseHouseId = new NamespacedKey(plugin, "horse-house-id");
        this.horseSwornBy = new NamespacedKey(plugin, "horse-sworn-by");
    }

    public static final PersistentDataType<String, String> STRING = PersistentDataType.STRING;
}
