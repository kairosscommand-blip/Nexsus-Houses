package com.nexuscraft.nexushouses;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * All the actual politics: founding and disbanding houses, succession when a head falls (the
 * single most GoT-coded mechanic here -- a real player death is a real political event, not just
 * a respawn), fealty (a house swearing itself vassal to a stronger one), and the alliance/rivalry
 * web between houses. {@link House} itself is a plain data holder; every rule about who can do
 * what, and what happens automatically, lives here.
 *
 * Deliberate asymmetry, on purpose, same as the real thing: starting a rivalry is unilateral (you
 * don't need the other side's permission to be an enemy) and ending one requires both sides to
 * separately offer peace; forming an alliance requires both sides to agree, but either side alone
 * can walk away from it. Swearing fealty needs the liege's consent; breaking it doesn't.
 */
public final class HouseEngine implements Listener {

    private final JavaPlugin plugin;
    private final HouseRegistry registry;
    private final PulseBridge pulse;
    private final HouseKeys keys;

    private boolean enabled = true;
    private int maxNameLength = 24;
    private int maxMottoLength = 64;
    private int successionTriggerChancePercent = 100;
    private int successionCooldownMinutes = 5;
    private final Set<String> allowedSigilBeasts = new HashSet<>();
    private final Set<String> allowedBannerColors = new HashSet<>();

    private final Map<UUID, UUID> pendingInvites = new HashMap<>(); // invitee -> house id
    private final Set<String> pendingVassalRequests = new HashSet<>(); // "vassalId:liegeId"
    private final Set<String> pendingAllianceRequests = new HashSet<>(); // "fromId:toId"
    private final Set<String> pendingPeaceOffers = new HashSet<>(); // "fromId:toId"
    private final Map<UUID, Long> lastSuccessionMillis = new HashMap<>();

    public HouseEngine(JavaPlugin plugin, HouseRegistry registry, HouseKeys keys) {
        this.plugin = plugin;
        this.registry = registry;
        this.keys = keys;
        this.pulse = new PulseBridge(plugin);
    }

    public void loadFromConfig() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("houses");
        if (s == null) {
            return;
        }
        enabled = s.getBoolean("enabled", true);
        maxNameLength = s.getInt("max-name-length", 24);
        maxMottoLength = s.getInt("max-motto-length", 64);
        successionTriggerChancePercent = s.getInt("succession-trigger-chance-percent", 100);
        successionCooldownMinutes = s.getInt("succession-cooldown-minutes", 5);

        allowedSigilBeasts.clear();
        for (String beast : s.getStringList("sigil-beasts")) {
            allowedSigilBeasts.add(beast.trim().toLowerCase());
        }
        if (allowedSigilBeasts.isEmpty()) {
            allowedSigilBeasts.addAll(List.of("wolf", "lion", "stag", "kraken", "dragon", "bear",
                    "falcon", "serpent", "boar", "raven", "fox", "eagle"));
        }

        allowedBannerColors.clear();
        for (String color : s.getStringList("banner-colors")) {
            allowedBannerColors.add(color.trim().toUpperCase());
        }
        if (allowedBannerColors.isEmpty()) {
            allowedBannerColors.addAll(List.of("WHITE", "BLACK", "RED", "BLUE", "GREEN", "YELLOW",
                    "PURPLE", "ORANGE", "GRAY", "CYAN"));
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Set<String> getAllowedSigilBeasts() {
        return allowedSigilBeasts;
    }

    public Set<String> getAllowedBannerColors() {
        return allowedBannerColors;
    }

    // ---- Founding / disbanding ----

    /** @return null on success, or a player-facing error message. */
    public String createHouse(Player founder, String name, String motto, String sigilBeast,
                               String primaryColor, String secondaryColor) {
        if (registry.houseOf(founder.getUniqueId()) != null) {
            return "You already belong to a house.";
        }
        if (name == null || name.isBlank() || name.length() > maxNameLength) {
            return "House names must be 1-" + maxNameLength + " characters.";
        }
        if (registry.byName(name) != null) {
            return "A house by that name already exists.";
        }
        String beast = sigilBeast == null ? "" : sigilBeast.trim().toLowerCase();
        if (!allowedSigilBeasts.contains(beast)) {
            return "Unknown sigil beast. Choose one of: " + String.join(", ", allowedSigilBeasts);
        }
        String primary = normalizeColor(primaryColor);
        String secondary = normalizeColor(secondaryColor);
        if (primary == null || secondary == null) {
            return "Unknown banner color. Choose from: " + String.join(", ", allowedBannerColors);
        }
        String finalMotto = motto == null ? "" : motto.trim();
        if (finalMotto.length() > maxMottoLength) {
            return "Mottos can be at most " + maxMottoLength + " characters.";
        }

        House house = registry.create(name.trim(), finalMotto, beast, primary, secondary, founder.getUniqueId());
        pulse.submit("HOUSE", "House " + house.name() + " has been founded, " + founder.getName() + " at its head");
        return null;
    }

    /** @return null on success, or an error message. Only the current head can disband. */
    public String disbandHouse(Player head) {
        House house = registry.houseOf(head.getUniqueId());
        if (house == null) {
            return "You don't belong to a house.";
        }
        if (!head.getUniqueId().equals(house.head())) {
            return "Only the head of your house can disband it.";
        }
        registry.remove(house.id());
        pulse.submit("HOUSE", "House " + house.name() + " has been dissolved by its own head");
        return null;
    }

    // ---- Membership ----

    public String invite(Player head, Player target) {
        House house = registry.houseOf(head.getUniqueId());
        if (house == null) {
            return "You don't belong to a house.";
        }
        if (!head.getUniqueId().equals(house.head())) {
            return "Only the head of your house can invite someone.";
        }
        if (registry.houseOf(target.getUniqueId()) != null) {
            return target.getName() + " already belongs to a house.";
        }
        pendingInvites.put(target.getUniqueId(), house.id());
        target.sendMessage(Colors.color("&6You've been invited to join House " + house.name()
                + " -- run &f/house join " + house.name() + "&6 to accept."));
        return null;
    }

    public String acceptInvite(Player player, String houseName) {
        UUID pendingHouseId = pendingInvites.get(player.getUniqueId());
        House house = registry.byName(houseName);
        if (house == null) {
            return "No house by that name.";
        }
        if (pendingHouseId == null || !pendingHouseId.equals(house.id())) {
            return "You don't have a pending invite to that house.";
        }
        if (registry.houseOf(player.getUniqueId()) != null) {
            return "You already belong to a house.";
        }
        pendingInvites.remove(player.getUniqueId());
        house.members().put(player.getUniqueId(), HouseRank.SWORN);
        registry.save();
        return null;
    }

    public String kick(Player head, OfflinePlayer target) {
        House house = registry.houseOf(head.getUniqueId());
        if (house == null) {
            return "You don't belong to a house.";
        }
        if (!head.getUniqueId().equals(house.head())) {
            return "Only the head of your house can remove a member.";
        }
        if (target.getUniqueId().equals(head.getUniqueId())) {
            return "You can't remove yourself -- use /house disband or /house abdicate instead.";
        }
        if (!house.isMember(target.getUniqueId())) {
            return (target.getName() != null ? target.getName() : "That player") + " isn't in your house.";
        }
        house.members().remove(target.getUniqueId());
        if (target.getUniqueId().equals(house.heir())) {
            house.setHeir(null);
        }
        registry.save();
        return null;
    }

    public String leave(Player player) {
        House house = registry.houseOf(player.getUniqueId());
        if (house == null) {
            return "You don't belong to a house.";
        }
        if (player.getUniqueId().equals(house.head())) {
            return "The head can't simply leave -- use /house abdicate to pass on leadership, or /house disband.";
        }
        house.members().remove(player.getUniqueId());
        if (player.getUniqueId().equals(house.heir())) {
            house.setHeir(null);
        }
        registry.save();
        return null;
    }

    public String setHeir(Player head, Player target) {
        House house = registry.houseOf(head.getUniqueId());
        if (house == null) {
            return "You don't belong to a house.";
        }
        if (!head.getUniqueId().equals(house.head())) {
            return "Only the head can name an heir.";
        }
        if (!house.isMember(target.getUniqueId())) {
            return target.getName() + " isn't a member of your house.";
        }
        if (target.getUniqueId().equals(head.getUniqueId())) {
            return "The head can't be their own heir.";
        }
        if (house.heir() != null) {
            house.members().put(house.heir(), HouseRank.SWORN);
        }
        house.setHeir(target.getUniqueId());
        house.members().put(target.getUniqueId(), HouseRank.HEIR);
        registry.save();
        pulse.submit("HOUSE", target.getName() + " has been named heir to House " + house.name());
        return null;
    }

    /** The head voluntarily steps down -- runs the exact same succession logic a death would,
     * except the old head survives as an ordinary sworn member afterward rather than the whole
     * "a life was lost" framing. */
    public String abdicate(Player head) {
        House house = registry.houseOf(head.getUniqueId());
        if (house == null) {
            return "You don't belong to a house.";
        }
        if (!head.getUniqueId().equals(house.head())) {
            return "Only the head can abdicate.";
        }
        runSuccession(house, head.getUniqueId(), false);
        return null;
    }

    // ---- Succession on real death ----

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!enabled) {
            return;
        }
        Player victim = event.getEntity();
        House house = registry.houseOf(victim.getUniqueId());
        if (house == null) {
            return;
        }
        if (victim.getUniqueId().equals(house.head())) {
            Long last = lastSuccessionMillis.get(house.id());
            long cooldownMillis = Math.max(1, successionCooldownMinutes) * 60_000L;
            if (last != null && System.currentTimeMillis() - last < cooldownMillis) {
                return;
            }
            if (new java.util.Random().nextInt(100) >= successionTriggerChancePercent) {
                return;
            }
            lastSuccessionMillis.put(house.id(), System.currentTimeMillis());
            runSuccession(house, victim.getUniqueId(), true);
        } else if (victim.getUniqueId().equals(house.heir())) {
            house.setHeir(null);
            house.members().put(victim.getUniqueId(), HouseRank.SWORN);
            registry.save();
            pulse.submit("HOUSE", "House " + house.name() + " has lost its heir apparent");
        }
    }

    /** Shared by both a real death and a voluntary abdication -- promotes the designated heir,
     * or failing that the longest-serving remaining member, or failing THAT dissolves the house
     * entirely (a line with no heir and no bannermen left simply ends). */
    private void runSuccession(House house, UUID outgoingHeadId, boolean died) {
        UUID heir = house.heir();
        if (heir != null && !heir.equals(outgoingHeadId)) {
            house.members().put(outgoingHeadId, HouseRank.SWORN);
            house.members().put(heir, HouseRank.HEAD);
            house.setHeir(null);
            registry.save();
            String heirName = nameOf(heir);
            pulse.submit("HOUSE", (died ? "The head of House " + house.name() + " has fallen. "
                    : "The head of House " + house.name() + " has stepped down. ")
                    + "By right of succession, " + heirName + " now leads");
            return;
        }

        UUID fallback = null;
        for (UUID memberId : house.members().keySet()) {
            if (!memberId.equals(outgoingHeadId)) {
                fallback = memberId;
                break;
            }
        }
        if (fallback != null) {
            house.members().put(outgoingHeadId, HouseRank.SWORN);
            house.members().put(fallback, HouseRank.HEAD);
            registry.save();
            String fallbackName = nameOf(fallback);
            pulse.submit("HOUSE", (died ? "The head of House " + house.name() + " has fallen with no named heir. "
                    : "The head of House " + house.name() + " has stepped down with no named heir. ")
                    + "The bannermen have raised " + fallbackName + " in their stead");
            return;
        }

        // No one left to inherit -- the house's line ends here.
        String houseName = house.name();
        registry.remove(house.id());
        pulse.submit("HOUSE", died
                ? "House " + houseName + " has fallen silent -- its line ends with its last lord"
                : "House " + houseName + " has been dissolved, its last lord stepping down with no one left to inherit it");
    }

    private String nameOf(UUID playerId) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerId);
        String name = offline != null ? offline.getName() : null;
        return name != null ? name : "an unknown heir";
    }

    // ---- Fealty (liege / vassal) ----

    public String proposeVassal(Player vassalHead, String liegeHouseName) {
        House vassal = registry.houseOf(vassalHead.getUniqueId());
        if (vassal == null || !vassalHead.getUniqueId().equals(vassal.head())) {
            return "Only the head of your house can offer fealty.";
        }
        if (vassal.liege() != null) {
            return "Your house is already sworn to a liege.";
        }
        House liege = registry.byName(liegeHouseName);
        if (liege == null) {
            return "No house by that name.";
        }
        if (liege.id().equals(vassal.id())) {
            return "A house can't swear fealty to itself.";
        }
        if (liege.liege() != null && liege.liege().equals(vassal.id())) {
            return "That house is already sworn to yours.";
        }
        pendingVassalRequests.add(vassal.id() + ":" + liege.id());
        return null;
    }

    public String acceptVassal(Player liegeHead, String vassalHouseName) {
        House liege = registry.houseOf(liegeHead.getUniqueId());
        if (liege == null || !liegeHead.getUniqueId().equals(liege.head())) {
            return "Only the head of your house can accept fealty.";
        }
        House vassal = registry.byName(vassalHouseName);
        if (vassal == null) {
            return "No house by that name.";
        }
        String key = vassal.id() + ":" + liege.id();
        if (!pendingVassalRequests.remove(key)) {
            return "That house hasn't offered fealty to yours.";
        }
        vassal.setLiege(liege.id());
        liege.vassals().add(vassal.id());
        registry.save();
        pulse.submit("HOUSE", "House " + vassal.name() + " has sworn fealty to House " + liege.name());
        return null;
    }

    public String breakFealty(Player vassalHead) {
        House vassal = registry.houseOf(vassalHead.getUniqueId());
        if (vassal == null || !vassalHead.getUniqueId().equals(vassal.head())) {
            return "Only the head of your house can break an oath of fealty.";
        }
        if (vassal.liege() == null) {
            return "Your house isn't sworn to anyone.";
        }
        House liege = registry.byId(vassal.liege());
        vassal.setLiege(null);
        if (liege != null) {
            liege.vassals().remove(vassal.id());
        }
        registry.save();
        pulse.submit("HOUSE", "House " + vassal.name() + " has broken its oath of fealty"
                + (liege != null ? " to House " + liege.name() : ""));
        return null;
    }

    // ---- Alliances ----

    public String proposeAlliance(Player head, String otherHouseName) {
        House mine = registry.houseOf(head.getUniqueId());
        if (mine == null || !head.getUniqueId().equals(mine.head())) {
            return "Only the head of your house can propose an alliance.";
        }
        House other = registry.byName(otherHouseName);
        if (other == null) {
            return "No house by that name.";
        }
        if (other.id().equals(mine.id())) {
            return "A house can't ally with itself.";
        }
        if (mine.allies().contains(other.id())) {
            return "Your houses are already allied.";
        }
        String reverseKey = other.id() + ":" + mine.id();
        if (pendingAllianceRequests.remove(reverseKey)) {
            mine.allies().add(other.id());
            other.allies().add(mine.id());
            registry.save();
            pulse.submit("HOUSE", "House " + mine.name() + " and House " + other.name() + " have sworn an alliance");
            return null;
        }
        pendingAllianceRequests.add(mine.id() + ":" + other.id());
        return null;
    }

    public String acceptAlliance(Player head, String otherHouseName) {
        House mine = registry.houseOf(head.getUniqueId());
        if (mine == null || !head.getUniqueId().equals(mine.head())) {
            return "Only the head of your house can accept an alliance.";
        }
        House other = registry.byName(otherHouseName);
        if (other == null) {
            return "No house by that name.";
        }
        String key = other.id() + ":" + mine.id();
        if (!pendingAllianceRequests.remove(key)) {
            return "That house hasn't proposed an alliance with yours.";
        }
        mine.allies().add(other.id());
        other.allies().add(mine.id());
        registry.save();
        pulse.submit("HOUSE", "House " + mine.name() + " and House " + other.name() + " have sworn an alliance");
        return null;
    }

    public String breakAlliance(Player head, String otherHouseName) {
        House mine = registry.houseOf(head.getUniqueId());
        if (mine == null || !head.getUniqueId().equals(mine.head())) {
            return "Only the head of your house can break an alliance.";
        }
        House other = registry.byName(otherHouseName);
        if (other == null) {
            return "No house by that name.";
        }
        if (!mine.allies().remove(other.id())) {
            return "Your houses aren't allied.";
        }
        other.allies().remove(mine.id());
        registry.save();
        pulse.submit("HOUSE", "The alliance between House " + mine.name() + " and House " + other.name() + " has ended");
        return null;
    }

    // ---- Rivalry ----

    public String declareRival(Player head, String otherHouseName) {
        House mine = registry.houseOf(head.getUniqueId());
        if (mine == null || !head.getUniqueId().equals(mine.head())) {
            return "Only the head of your house can declare a rival.";
        }
        House other = registry.byName(otherHouseName);
        if (other == null) {
            return "No house by that name.";
        }
        if (other.id().equals(mine.id())) {
            return "A house can't rival itself.";
        }
        if (mine.rivals().contains(other.id())) {
            return "Your houses are already rivals.";
        }
        mine.allies().remove(other.id());
        other.allies().remove(mine.id());
        mine.rivals().add(other.id());
        other.rivals().add(mine.id());
        registry.save();
        pulse.submit("HOUSE", "House " + mine.name() + " has declared House " + other.name() + " its rival");
        return null;
    }

    public String offerPeace(Player head, String otherHouseName) {
        House mine = registry.houseOf(head.getUniqueId());
        if (mine == null || !head.getUniqueId().equals(mine.head())) {
            return "Only the head of your house can offer peace.";
        }
        House other = registry.byName(otherHouseName);
        if (other == null) {
            return "No house by that name.";
        }
        if (!mine.rivals().contains(other.id())) {
            return "Your houses aren't rivals.";
        }
        String reverseKey = other.id() + ":" + mine.id();
        if (pendingPeaceOffers.remove(reverseKey)) {
            mine.rivals().remove(other.id());
            other.rivals().remove(mine.id());
            registry.save();
            pulse.submit("HOUSE", "Peace has been made between House " + mine.name() + " and House " + other.name());
            return null;
        }
        pendingPeaceOffers.add(mine.id() + ":" + other.id());
        return null;
    }

    // ---- Banner ----

    public ItemStack buildBannerItem(House house) {
        Material bannerMaterial = Material.matchMaterial(house.bannerPrimaryColor() + "_BANNER");
        if (bannerMaterial == null) {
            bannerMaterial = Material.matchMaterial("WHITE_BANNER");
        }
        ItemStack banner = new ItemStack(bannerMaterial);
        Object meta = banner.getItemMeta();
        if (meta instanceof BannerMeta bannerMeta) {
            DyeColor secondary = parseColor(house.bannerSecondaryColor());
            List<Pattern> patterns = new ArrayList<>();
            patterns.add(new Pattern(secondary, PatternType.BORDER));
            patterns.add(new Pattern(secondary, PatternType.STRIPE_BOTTOM));
            bannerMeta.setPatterns(patterns);
            bannerMeta.setDisplayName(Colors.color("&6House " + house.name()));
            // Tagged with the house's real id via PDC -- see HouseKeys' class comment for why this,
            // and not the display name, is what HorseAllegianceListener trusts to identify which
            // house a given banner item actually belongs to.
            bannerMeta.getPersistentDataContainer().set(keys.bannerHouseId, HouseKeys.STRING, house.id().toString());
            banner.setItemMeta(bannerMeta);
        }
        return banner;
    }

    /** Reads the house id a banner item was tagged with, or null if this isn't (or is no longer)
     *  a real house banner -- e.g. a plain vanilla banner, or one from a since-disbanded house. */
    public House houseOfBannerItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(keys.bannerHouseId, HouseKeys.STRING);
        if (id == null) {
            return null;
        }
        try {
            return registry.byId(UUID.fromString(id));
        } catch (IllegalArgumentException badId) {
            return null;
        }
    }

    private DyeColor parseColor(String name) {
        try {
            return DyeColor.valueOf(name.toUpperCase());
        } catch (Exception e) {
            return DyeColor.BLACK;
        }
    }

    private String normalizeColor(String name) {
        if (name == null) {
            return null;
        }
        String upper = name.trim().toUpperCase();
        return allowedBannerColors.contains(upper) ? upper : null;
    }
}
