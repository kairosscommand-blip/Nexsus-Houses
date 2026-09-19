package com.nexuscraft.nexushouses;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class HouseCommandExecutor implements CommandExecutor {

    private final HouseEngine engine;
    private final HouseRegistry registry;
    private final TreasureRegistry treasureRegistry;
    private final NexusHouses nexusHouses;
    private final EconomyBridge economy;
    private final EndeavorsBridge endeavors;

    public HouseCommandExecutor(HouseEngine engine, HouseRegistry registry, TreasureRegistry treasureRegistry,
            NexusHouses nexusHouses, EconomyBridge economy, EndeavorsBridge endeavors) {
        this.engine = engine;
        this.registry = registry;
        this.treasureRegistry = treasureRegistry;
        this.nexusHouses = nexusHouses;
        this.economy = economy;
        this.endeavors = endeavors;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("list")) {
            sendList(sender);
            return true;
        }
        if (sub.equals("info")) {
            sendInfo(sender, args.length > 1 ? args[1] : null);
            return true;
        }
        if (sub.equals("reload")) {
            if (!sender.hasPermission("nexushouses.admin")) {
                sender.sendMessage(color("&cYou don't have permission for that."));
                return true;
            }
            nexusHouses.reloadAll();
            sender.sendMessage(color("&aNexusHouses config reloaded."));
            return true;
        }
        if (sub.equals("admin")) {
            handleAdmin(sender, args);
            return true;
        }
        if (sub.equals("region")) {
            handleRegion(sender, args);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(color("&cOnly a player can do that."));
            return true;
        }

        switch (sub) {
            case "create": {
                if (args.length < 5) {
                    player.sendMessage(color("&6Usage: &f/house create <name> <sigilBeast> <primaryColor> <secondaryColor> [motto...]"));
                    return true;
                }
                String name = args[1];
                String beast = args[2];
                String primary = args[3];
                String secondary = args[4];
                String motto = args.length > 5 ? String.join(" ", java.util.Arrays.copyOfRange(args, 5, args.length)) : "";
                reply(player, engine.createHouse(player, name, motto, beast, primary, secondary),
                        "House " + name + " has been founded. Long may it stand.");
                return true;
            }
            case "disband": {
                reply(player, engine.disbandHouse(player), "Your house has been dissolved.");
                return true;
            }
            case "abdicate": {
                reply(player, engine.abdicate(player), "You have stepped down.");
                return true;
            }
            case "invite": {
                Player target = requireArgPlayer(player, args, 1);
                if (target == null) {
                    return true;
                }
                reply(player, engine.invite(player, target), "Invite sent to " + target.getName() + ".");
                return true;
            }
            case "join": {
                if (args.length < 2) {
                    player.sendMessage(color("&6Usage: &f/house join <houseName>"));
                    return true;
                }
                reply(player, engine.acceptInvite(player, args[1]), "Welcome to House " + args[1] + ".");
                return true;
            }
            case "kick": {
                if (args.length < 2) {
                    player.sendMessage(color("&6Usage: &f/house kick <player>"));
                    return true;
                }
                org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                reply(player, engine.kick(player, target), args[1] + " has been removed from your house.");
                return true;
            }
            case "leave": {
                reply(player, engine.leave(player), "You have left your house.");
                return true;
            }
            case "setheir": {
                Player target = requireArgPlayer(player, args, 1);
                if (target == null) {
                    return true;
                }
                reply(player, engine.setHeir(player, target), target.getName() + " is now your heir.");
                return true;
            }
            case "banner": {
                House house = registry.houseOf(player.getUniqueId());
                if (house == null) {
                    player.sendMessage(color("&cYou don't belong to a house."));
                    return true;
                }
                player.getInventory().addItem(engine.buildBannerItem(house));
                player.sendMessage(color("&aHere is the banner of House " + house.name() + "."));
                return true;
            }
            case "swear": {
                if (args.length < 2) {
                    player.sendMessage(color("&6Usage: &f/house swear <liegeHouseName>"));
                    return true;
                }
                reply(player, engine.proposeVassal(player, args[1]),
                        "Your house has offered fealty to House " + args[1] + ".");
                return true;
            }
            case "acceptvassal": {
                if (args.length < 2) {
                    player.sendMessage(color("&6Usage: &f/house acceptvassal <vassalHouseName>"));
                    return true;
                }
                reply(player, engine.acceptVassal(player, args[1]),
                        "House " + args[1] + " is now sworn to yours.");
                return true;
            }
            case "breakfealty": {
                reply(player, engine.breakFealty(player), "Your house's oath of fealty has been broken.");
                return true;
            }
            case "ally": {
                if (args.length < 2) {
                    player.sendMessage(color("&6Usage: &f/house ally <houseName>"));
                    return true;
                }
                reply(player, engine.proposeAlliance(player, args[1]),
                        "Alliance proposed/confirmed with House " + args[1] + ".");
                return true;
            }
            case "acceptally": {
                if (args.length < 2) {
                    player.sendMessage(color("&6Usage: &f/house acceptally <houseName>"));
                    return true;
                }
                reply(player, engine.acceptAlliance(player, args[1]),
                        "Your house is now allied with House " + args[1] + ".");
                return true;
            }
            case "breakally": {
                if (args.length < 2) {
                    player.sendMessage(color("&6Usage: &f/house breakally <houseName>"));
                    return true;
                }
                reply(player, engine.breakAlliance(player, args[1]),
                        "The alliance with House " + args[1] + " has ended.");
                return true;
            }
            case "rival": {
                if (args.length < 2) {
                    player.sendMessage(color("&6Usage: &f/house rival <houseName>"));
                    return true;
                }
                reply(player, engine.declareRival(player, args[1]),
                        "House " + args[1] + " is now your rival.");
                return true;
            }
            case "peace": {
                if (args.length < 2) {
                    player.sendMessage(color("&6Usage: &f/house peace <houseName>"));
                    return true;
                }
                reply(player, engine.offerPeace(player, args[1]),
                        "Peace offered/made with House " + args[1] + ".");
                return true;
            }
            case "donate": {
                handleDonate(player, args);
                return true;
            }
            default:
                sendUsage(sender);
                return true;
        }
    }

    /** Donates real gold (via Vault) from the caller straight into their own house's treasury --
     *  the "log in and do something" hook NexusEndeavors' "house.donate" objective reports against
     *  (see EndeavorsBridge). Deliberately your OWN house only, no donating into a house you don't
     *  belong to (a house's treasury is meant to reflect its own members' contribution, not be an
     *  open donation box anyone can pad). */
    private void handleDonate(Player player, String[] args) {
        House house = registry.houseOf(player.getUniqueId());
        if (house == null) {
            player.sendMessage(color("&cYou don't belong to a house."));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(color("&6Usage: &f/house donate <amount>"));
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(color("&cAmount must be a number."));
            return;
        }
        if (!Double.isFinite(amount) || amount <= 0) {
            player.sendMessage(color("&cAmount must be a positive, finite number."));
            return;
        }
        if (!economy.isVaultConnected()) {
            player.sendMessage(color("&cNo economy plugin is connected -- house donations need Vault and a"
                    + " Vault-compatible economy plugin installed."));
            return;
        }
        if (!economy.withdraw(player, amount)) {
            player.sendMessage(color("&cYou don't have " + economy.format(amount) + " to donate."));
            return;
        }
        house.deposit(amount);
        registry.save();
        endeavors.report(player.getUniqueId(), player.getName(), "house.donate", (int) Math.round(amount));
        player.sendMessage(color("&aDonated " + economy.format(amount) + " to House " + house.name()
                + "'s treasury (now " + economy.format(house.treasury()) + ")."));
    }

    private void handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nexushouses.admin")) {
            sender.sendMessage(color("&cYou don't have permission for that."));
            return;
        }
        if (args.length < 3 || !args[1].equalsIgnoreCase("disband")) {
            sender.sendMessage(color("&6Usage: &f/house admin disband <houseName>"));
            return;
        }
        House house = registry.byName(args[2]);
        if (house == null) {
            sender.sendMessage(color("&cNo house by that name."));
            return;
        }
        registry.remove(house.id());
        sender.sendMessage(color("&aHouse " + args[2] + " has been forcibly disbanded."));
    }

    private void handleRegion(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nexushouses.admin")) {
            sender.sendMessage(color("&cYou don't have permission for that."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(color("&6Usage: &f/house region <create|remove|list>"));
            return;
        }
        String regionSub = args[1].toLowerCase();

        if (regionSub.equals("list")) {
            if (treasureRegistry.allRegions().isEmpty()) {
                sender.sendMessage(color("&7No treasure regions have been defined yet."));
                return;
            }
            sender.sendMessage(color("&6Treasure regions:"));
            for (TreasureRegion region : treasureRegistry.allRegions()) {
                sender.sendMessage(color("&7- &f" + region.name() + " &7(" + region.world() + ", radius "
                        + region.radiusBlocks() + ")"));
            }
            return;
        }

        if (regionSub.equals("remove")) {
            if (args.length < 3) {
                sender.sendMessage(color("&6Usage: &f/house region remove <name>"));
                return;
            }
            if (treasureRegistry.removeRegionByName(args[2])) {
                sender.sendMessage(color("&aTreasure region " + args[2] + " has been removed."));
            } else {
                sender.sendMessage(color("&cNo treasure region by that name."));
            }
            return;
        }

        if (regionSub.equals("create")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(color("&cOnly a player can do that (the region is centered on where you stand)."));
                return;
            }
            if (args.length < 4) {
                player.sendMessage(color("&6Usage: &f/house region create <name> <radiusBlocks>"));
                return;
            }
            String name = args[2];
            if (treasureRegistry.byName(name) != null) {
                player.sendMessage(color("&cA treasure region by that name already exists."));
                return;
            }
            int radius;
            try {
                radius = Integer.parseInt(args[3]);
            } catch (NumberFormatException ex) {
                player.sendMessage(color("&cRadius must be a whole number of blocks."));
                return;
            }
            if (radius <= 0) {
                player.sendMessage(color("&cRadius must be greater than zero."));
                return;
            }
            TreasureRegion region = treasureRegistry.createRegion(name, player.getLocation(), radius);
            player.sendMessage(color("&aTreasure region " + region.name() + " created here, radius "
                    + region.radiusBlocks() + " blocks. Every chest inside it is now a living treasure chest."));
            return;
        }

        sender.sendMessage(color("&6Usage: &f/house region <create|remove|list>"));
    }

    private void sendList(CommandSender sender) {
        if (registry.size() == 0) {
            sender.sendMessage(color("&7No houses have been founded yet."));
            return;
        }
        sender.sendMessage(color("&6The Great Houses:"));
        for (House house : registry.all()) {
            if (house.liege() != null) {
                continue; // shown nested under their liege below
            }
            sender.sendMessage(color("&7- &f" + house.name() + " &7(" + house.memberCount() + " sworn)"));
            for (UUID vassalId : house.vassals()) {
                House vassal = registry.byId(vassalId);
                if (vassal != null) {
                    sender.sendMessage(color("&7    -> &f" + vassal.name() + " &7(sworn bannerman)"));
                }
            }
        }
    }

    private void sendInfo(CommandSender sender, String houseName) {
        House house = null;
        if (houseName != null) {
            house = registry.byName(houseName);
        } else if (sender instanceof Player player) {
            house = registry.houseOf(player.getUniqueId());
        }
        if (house == null) {
            sender.sendMessage(color("&cNo such house (or you don't belong to one -- try /house info <name>)."));
            return;
        }
        sender.sendMessage(color("&6House " + house.name() + " &7-- sigil: &f" + house.sigilBeast()));
        if (!house.motto().isEmpty()) {
            sender.sendMessage(color("&7\"" + house.motto() + "\""));
        }
        sender.sendMessage(color("&7Head: &f" + nameOf(house.head())
                + (house.heir() != null ? " &7-- Heir: &f" + nameOf(house.heir()) : "")));
        sender.sendMessage(color("&7Members: &f" + house.memberCount()));
        sender.sendMessage(color("&7Treasury: &f" + economy.format(house.treasury())));
        if (house.liege() != null) {
            House liege = registry.byId(house.liege());
            sender.sendMessage(color("&7Sworn to: &f" + (liege != null ? liege.name() : "an unknown house")));
        }
        if (!house.vassals().isEmpty()) {
            sender.sendMessage(color("&7Bannermen: &f" + house.vassals().size()));
        }
        if (!house.allies().isEmpty()) {
            sender.sendMessage(color("&aAllies: &f" + namesOf(house.allies())));
        }
        if (!house.rivals().isEmpty()) {
            sender.sendMessage(color("&cRivals: &f" + namesOf(house.rivals())));
        }
    }

    private String namesOf(java.util.Set<UUID> houseIds) {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (UUID id : houseIds) {
            House h = registry.byId(id);
            names.add(h != null ? h.name() : "an unknown house");
        }
        return String.join(", ", names);
    }

    private String nameOf(UUID playerId) {
        if (playerId == null) {
            return "no one";
        }
        org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(playerId);
        String name = offline != null ? offline.getName() : null;
        return name != null ? name : "unknown";
    }

    private Player requireArgPlayer(Player sender, String[] args, int index) {
        if (args.length <= index) {
            sender.sendMessage(color("&cMissing player name."));
            return null;
        }
        Player target = Bukkit.getPlayer(args[index]);
        if (target == null) {
            sender.sendMessage(color("&cThat player isn't online."));
            return null;
        }
        return target;
    }

    private void reply(Player player, String error, String successMessage) {
        if (error != null) {
            player.sendMessage(color("&c" + error));
        } else {
            player.sendMessage(color("&a" + successMessage));
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(color("&6NexusHouses &7-- /house <create|disband|abdicate|invite|join|kick|leave|"
                + "setheir|banner|info|list|swear|acceptvassal|breakfealty|ally|acceptally|breakally|rival|peace|"
                + "donate|region>"));
        sender.sendMessage(color("&7Sneak-right-click your own tamed horse with your house banner to swear it in."));
        sender.sendMessage(color("&7Admins: &f/house region <create|remove|list> &7-- living treasure chest zones."));
    }

    private String color(String s) {
        return Colors.color(s);
    }
}
