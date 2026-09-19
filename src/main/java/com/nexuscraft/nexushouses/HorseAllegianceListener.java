package com.nexuscraft.nexushouses;

import org.bukkit.Material;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Sneak-right-click a tamed horse you own while holding your house's banner and it's sworn to
 * your colors from then on: a custom name naming the house, visible to anyone nearby, tagged the
 * same unspoofable PersistentDataContainer way every other identity marker in this project is
 * (see {@link HouseKeys}). Sneak-right-click it again with an empty hand and you take the banner
 * back down.
 *
 * Deliberately identification only, the same "flavor, not a mechanical edge" choice this plugin
 * already made for a house's own banner item and its sigil beast -- a warhorse doesn't fight any
 * harder for flying the right colors, it just makes unmistakably clear whose colors it's flying,
 * which is the entire point of a real banner. A horse can carry at most one house's banner at a
 * time; binding a new one simply overwrites whichever it carried before.
 */
public final class HorseAllegianceListener implements Listener {

    private final HouseEngine engine;
    private final HouseKeys keys;

    public HorseAllegianceListener(HouseEngine engine, HouseKeys keys) {
        this.engine = engine;
        this.keys = keys;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Horse horse)) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }

        ItemStack inHand = player.getInventory().getItemInMainHand();
        House bannerHouse = engine.houseOfBannerItem(inHand);

        if (bannerHouse != null) {
            bindBanner(event, player, horse, inHand, bannerHouse);
            return;
        }

        if (inHand == null || inHand.getType() == Material.AIR) {
            unbindBanner(event, player, horse);
        }
    }

    private void bindBanner(PlayerInteractEntityEvent event, Player player, Horse horse,
                             ItemStack banner, House bannerHouse) {
        AnimalTamer owner = horse.getOwner();
        if (!horse.isTamed() || owner == null || !owner.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(Colors.color("&cYou can only swear allegiance for a horse you've tamed yourself."));
            event.setCancelled(true);
            return;
        }

        horse.getPersistentDataContainer().set(keys.horseHouseId, HouseKeys.STRING, bannerHouse.id().toString());
        horse.getPersistentDataContainer().set(keys.horseSwornBy, HouseKeys.STRING, player.getUniqueId().toString());
        horse.setCustomName(Colors.color("&6" + bannerHouse.name() + "'s Warhorse"));
        horse.setCustomNameVisible(true);

        int remaining = banner.getAmount() - 1;
        if (remaining <= 0) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        } else {
            banner.setAmount(remaining);
        }

        player.sendMessage(Colors.color("&aThis horse now rides under the banner of House " + bannerHouse.name() + "."));
        event.setCancelled(true);
    }

    private void unbindBanner(PlayerInteractEntityEvent event, Player player, Horse horse) {
        if (!horse.getPersistentDataContainer().has(keys.horseHouseId, HouseKeys.STRING)) {
            return;
        }
        AnimalTamer owner = horse.getOwner();
        if (owner == null || !owner.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(Colors.color("&cOnly this horse's owner can take down its banner."));
            event.setCancelled(true);
            return;
        }

        horse.getPersistentDataContainer().remove(keys.horseHouseId);
        horse.getPersistentDataContainer().remove(keys.horseSwornBy);
        horse.setCustomName(null);
        horse.setCustomNameVisible(false);

        player.sendMessage(Colors.color("&7You've taken down this horse's banner."));
        event.setCancelled(true);
    }
}
