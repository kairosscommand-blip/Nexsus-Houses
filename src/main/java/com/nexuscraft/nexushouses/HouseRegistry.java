package com.nexuscraft.nexushouses;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persists every house to houses.yml -- same flat-file-registry pattern as
 * NexusRealms' TeamManager and this project's other non-PDC-attached records. */
public final class HouseRegistry {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, House> housesById = new LinkedHashMap<>();

    public HouseRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "houses.yml");
    }

    public void load() {
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        housesById.clear();

        ConfigurationSection root = data.getConfigurationSection("houses");
        if (root == null) {
            return;
        }
        for (String idKey : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(idKey);
            if (section == null) {
                continue;
            }
            try {
                UUID id = UUID.fromString(idKey);
                String name = section.getString("name", "Unnamed House");
                String motto = section.getString("motto", "");
                String sigilBeast = section.getString("sigil-beast", "Wolf");
                String primary = section.getString("banner-primary", "GRAY");
                String secondary = section.getString("banner-secondary", "BLACK");
                UUID founder = UUID.fromString(section.getString("founder"));
                long foundedAt = section.getLong("founded-at", System.currentTimeMillis());

                House house = new House(id, name, motto, sigilBeast, primary, secondary, founder, foundedAt);
                house.members().clear();
                for (String memberLine : section.getStringList("members")) {
                    String[] parts = memberLine.split(":");
                    if (parts.length < 2) {
                        continue;
                    }
                    house.members().put(UUID.fromString(parts[0]), HouseRank.valueOf(parts[1]));
                }
                if (section.contains("heir")) {
                    house.setHeir(UUID.fromString(section.getString("heir")));
                }
                if (section.contains("liege")) {
                    house.setLiege(UUID.fromString(section.getString("liege")));
                }
                for (String vassal : section.getStringList("vassals")) {
                    house.vassals().add(UUID.fromString(vassal));
                }
                for (String ally : section.getStringList("allies")) {
                    house.allies().add(UUID.fromString(ally));
                }
                for (String rival : section.getStringList("rivals")) {
                    house.rivals().add(UUID.fromString(rival));
                }
                house.deposit(section.getDouble("treasury", 0));

                housesById.put(id, house);
            } catch (Exception e) {
                plugin.getLogger().warning("[NexusHouses] Skipping a corrupt house entry (" + idKey + "): " + e.getMessage());
            }
        }
    }

    public void save() {
        YamlConfiguration data = new YamlConfiguration();
        data.set("houses", null);
        for (House house : housesById.values()) {
            String path = "houses." + house.id();
            data.set(path + ".name", house.name());
            data.set(path + ".motto", house.motto());
            data.set(path + ".sigil-beast", house.sigilBeast());
            data.set(path + ".banner-primary", house.bannerPrimaryColor());
            data.set(path + ".banner-secondary", house.bannerSecondaryColor());
            data.set(path + ".founder", house.founder().toString());
            data.set(path + ".founded-at", house.foundedAtMillis());

            List<String> memberLines = new ArrayList<>();
            for (Map.Entry<UUID, HouseRank> entry : house.members().entrySet()) {
                memberLines.add(entry.getKey() + ":" + entry.getValue().name());
            }
            data.set(path + ".members", memberLines);

            if (house.heir() != null) {
                data.set(path + ".heir", house.heir().toString());
            }
            if (house.liege() != null) {
                data.set(path + ".liege", house.liege().toString());
            }
            data.set(path + ".vassals", toStringList(house.vassals()));
            data.set(path + ".allies", toStringList(house.allies()));
            data.set(path + ".rivals", toStringList(house.rivals()));
            data.set(path + ".treasury", house.treasury());
        }
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("[NexusHouses] Could not save houses.yml: " + e.getMessage());
        }
    }

    private List<String> toStringList(Collection<UUID> ids) {
        List<String> result = new ArrayList<>();
        for (UUID id : ids) {
            result.add(id.toString());
        }
        return result;
    }

    public House create(String name, String motto, String sigilBeast, String primaryColor,
                         String secondaryColor, UUID founder) {
        House house = new House(UUID.randomUUID(), name, motto, sigilBeast, primaryColor, secondaryColor,
                founder, System.currentTimeMillis());
        housesById.put(house.id(), house);
        save();
        return house;
    }

    public House byId(UUID id) {
        return id != null ? housesById.get(id) : null;
    }

    public House byName(String name) {
        for (House house : housesById.values()) {
            if (house.name().equalsIgnoreCase(name)) {
                return house;
            }
        }
        return null;
    }

    /** The house this player currently belongs to, or null. A player belongs to at most one
     * house at a time -- same one-affiliation-at-a-time rule NexusRealms' teams use. */
    public House houseOf(UUID playerId) {
        for (House house : housesById.values()) {
            if (house.isMember(playerId)) {
                return house;
            }
        }
        return null;
    }

    public boolean remove(UUID id) {
        boolean removed = housesById.remove(id) != null;
        if (removed) {
            // A disbanded house can't remain anyone else's liege, vassal, ally, or rival.
            for (House other : housesById.values()) {
                other.vassals().remove(id);
                other.allies().remove(id);
                other.rivals().remove(id);
                if (id.equals(other.liege())) {
                    other.setLiege(null);
                }
            }
            save();
        }
        return removed;
    }

    public Collection<House> all() {
        return housesById.values();
    }

    public int size() {
        return housesById.size();
    }
}
