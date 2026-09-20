package com.nexuscraft.nexushouses;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Everything this plugin needs from the economy side of the server, in one place -- the same
 * Vault-soft-integration pattern NexusCreativeSurvival's and NexusCraftersGuild's own EconomyBridge
 * use (copied and trimmed: this plugin only needs to withdraw gold for a house treasury donation,
 * nothing else). Works with any Vault-compatible economy plugin. A safe, silent false/zero when no
 * economy plugin is installed at all -- every other NexusHouses feature (succession, banners,
 * treasure regions, all of it) works exactly as before with no economy plugin present; only
 * {@code /house donate} needs one.
 */
final class EconomyBridge {

    private Economy vaultEconomy;

    boolean isVaultConnected() {
        if (vaultEconomy == null) {
            tryConnect();
        }
        return vaultEconomy != null;
    }

    private void tryConnect() {
        RegisteredServiceProvider<Economy> provider = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (provider != null) {
            vaultEconomy = provider.getProvider();
        }
    }

    boolean has(Player player, double amount) {
        return isVaultConnected() && vaultEconomy.has(player, amount);
    }

    /** Withdraws the amount, returning whether it actually succeeded (mirrors Vault's own
     *  EconomyResponse.transactionSuccess). */
    boolean withdraw(Player player, double amount) {
        if (!isVaultConnected()) {
            return false;
        }
        return vaultEconomy.withdrawPlayer(player, amount).transactionSuccess();
    }

    String format(double amount) {
        if (isVaultConnected()) {
            return vaultEconomy.format(amount);
        }
        return String.format("%,.2f gold", amount);
    }
}
