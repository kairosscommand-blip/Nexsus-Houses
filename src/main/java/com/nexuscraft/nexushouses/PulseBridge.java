package com.nexuscraft.nexushouses;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

/**
 * A soft, reflection-based, OUTBOUND bridge to NexusPulse's NexusPulseApi -- the same
 * Class.forName + ServicesManager pattern this project already uses for inbound bridges (see
 * NexusCitizens' RealmsBridge), just pointed the other way: this plugin hands NexusPulse a
 * headline the moment real house politics happen (founding, succession, fealty, alliance, war),
 * instead of querying anything back.
 *
 * Deliberately not a compile-time dependency: NexusHouses works exactly as before if NexusPulse
 * isn't installed at all -- every submit() call below just becomes a silent no-op.
 */
final class PulseBridge {

    private static final String API_CLASS_NAME = "com.nexuscraft.nexuspulse.api.NexusPulseApi";

    private final JavaPlugin plugin;
    private Class<?> apiClass;
    private Object apiInstance;
    private boolean attemptedResolve;
    private boolean warned;

    PulseBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void submit(String category, String message, Location location) {
        if (!ensureResolved()) {
            return;
        }
        try {
            if (location != null && location.getWorld() != null) {
                Method method = apiClass.getMethod("submit", String.class, String.class,
                        String.class, double.class, double.class, double.class);
                method.invoke(apiInstance, category, message, location.getWorld().getName(),
                        location.getX(), location.getY(), location.getZ());
            } else {
                Method method = apiClass.getMethod("submit", String.class, String.class);
                method.invoke(apiInstance, category, message);
            }
        } catch (Exception e) {
            warnOnce(e);
        }
    }

    void submit(String category, String message) {
        submit(category, message, null);
    }

    private boolean ensureResolved() {
        if (apiInstance != null) {
            return true;
        }
        if (attemptedResolve) {
            return false;
        }
        attemptedResolve = true;
        try {
            Class<?> found = Class.forName(API_CLASS_NAME);
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(found);
            if (registration != null) {
                apiClass = found;
                apiInstance = registration.getProvider();
            }
            return apiInstance != null;
        } catch (ClassNotFoundException e) {
            return false; // NexusPulse isn't installed -- perfectly fine, nothing to feed
        } catch (Exception e) {
            warnOnce(e);
            return false;
        }
    }

    private void warnOnce(Exception e) {
        if (warned) {
            return;
        }
        warned = true;
        plugin.getLogger().warning("[NexusHouses] NexusPulse is installed, but couldn't accept an "
                + "event the way this version expects -- headlines just won't appear there. ("
                + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
    }
}
