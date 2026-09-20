package com.nexuscraft.nexushouses;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One noble house: a name, a motto, a sigil (a flavor "beast" plus the two colors its banner is
 * built from), a membership roll with rank, an optional line of succession (a single designated
 * heir), and this house's place in the wider political web -- an optional liege it's sworn to,
 * whatever houses are sworn to IT, and who it's allied with or at odds with. Deliberately a plain
 * data holder; all the actual rules (who can do what, what happens when a head falls) live in
 * {@link HouseEngine}.
 */
public final class House {

    private final UUID id;
    private String name;
    private String motto;
    private String sigilBeast;
    private String bannerPrimaryColor;
    private String bannerSecondaryColor;
    private final UUID founder;
    private final long foundedAtMillis;

    private final Map<UUID, HouseRank> members = new LinkedHashMap<>();
    private UUID heir;
    private UUID liege;
    private final Set<UUID> vassals = new LinkedHashSet<>();
    private final Set<UUID> allies = new LinkedHashSet<>();
    private final Set<UUID> rivals = new LinkedHashSet<>();
    private double treasury;

    public House(UUID id, String name, String motto, String sigilBeast, String bannerPrimaryColor,
                 String bannerSecondaryColor, UUID founder, long foundedAtMillis) {
        this.id = id;
        this.name = name;
        this.motto = motto;
        this.sigilBeast = sigilBeast;
        this.bannerPrimaryColor = bannerPrimaryColor;
        this.bannerSecondaryColor = bannerSecondaryColor;
        this.founder = founder;
        this.foundedAtMillis = foundedAtMillis;
        members.put(founder, HouseRank.HEAD);
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String motto() {
        return motto;
    }

    public void setMotto(String motto) {
        this.motto = motto;
    }

    public String sigilBeast() {
        return sigilBeast;
    }

    public String bannerPrimaryColor() {
        return bannerPrimaryColor;
    }

    public String bannerSecondaryColor() {
        return bannerSecondaryColor;
    }

    public void setSigil(String sigilBeast, String bannerPrimaryColor, String bannerSecondaryColor) {
        this.sigilBeast = sigilBeast;
        this.bannerPrimaryColor = bannerPrimaryColor;
        this.bannerSecondaryColor = bannerSecondaryColor;
    }

    public UUID founder() {
        return founder;
    }

    public long foundedAtMillis() {
        return foundedAtMillis;
    }

    public Map<UUID, HouseRank> members() {
        return members;
    }

    public HouseRank rankOf(UUID playerId) {
        return members.get(playerId);
    }

    public boolean isMember(UUID playerId) {
        return members.containsKey(playerId);
    }

    public UUID head() {
        for (Map.Entry<UUID, HouseRank> entry : members.entrySet()) {
            if (entry.getValue() == HouseRank.HEAD) {
                return entry.getKey();
            }
        }
        return null;
    }

    public UUID heir() {
        return heir;
    }

    public void setHeir(UUID heir) {
        this.heir = heir;
    }

    public UUID liege() {
        return liege;
    }

    public void setLiege(UUID liege) {
        this.liege = liege;
    }

    public Set<UUID> vassals() {
        return vassals;
    }

    public Set<UUID> allies() {
        return allies;
    }

    public Set<UUID> rivals() {
        return rivals;
    }

    public int memberCount() {
        return members.size();
    }

    public double treasury() {
        return treasury;
    }

    /** Adds real gold (already withdrawn from a donor via Vault -- see HouseCommandExecutor's
     *  "donate" handling) to this house's standing treasury. Deliberately no withdrawal method
     *  yet -- see README's roadmap note; v1.4.0 is deposit-only, a shared house "bank account" with
     *  no spend side is still a real number worth having (bragging rights, a future perks system to
     *  gate on) even before anything can be spent from it. */
    public void deposit(double amount) {
        if (amount > 0) {
            this.treasury += amount;
        }
    }
}
