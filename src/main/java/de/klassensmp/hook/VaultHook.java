package de.klassensmp.hook;

import de.klassensmp.KlassenSMP;
import de.klassensmp.util.NumberUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.ServicePriority;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Stellt die KlassenSMP-Economy als Vault-Anbieter bereit.
 *
 * <p>Vault wird nicht als Compile-Abhaengigkeit eingebunden. Stattdessen wird
 * das Interface {@code net.milkbowl.vault.economy.Economy} zur Laufzeit
 * geladen und ueber einen dynamischen Proxy implementiert. Ist Vault nicht
 * installiert, passiert schlicht nichts.</p>
 */
public final class VaultHook {

    private final KlassenSMP plugin;

    private boolean registered;
    private Class<?> economyClass;
    private Constructor<?> responseConstructor;
    private Object successType;
    private Object failureType;
    private Object notImplementedType;
    private Object provider;

    public VaultHook(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void setup() {
        this.registered = false;
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        if (!plugin.getConfigManager().bool("economy.register-in-vault", true)) {
            plugin.getLogger().info("Vault erkannt, Registrierung ist in der Config deaktiviert.");
            return;
        }
        try {
            this.economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            Class<?> responseClass = Class.forName("net.milkbowl.vault.economy.EconomyResponse");
            Class<?> typeClass = Class.forName("net.milkbowl.vault.economy.EconomyResponse$ResponseType");
            this.responseConstructor = responseClass.getConstructor(double.class, double.class, typeClass, String.class);
            this.successType = Enum.valueOf((Class<Enum>) typeClass.asSubclass(Enum.class), "SUCCESS");
            this.failureType = Enum.valueOf((Class<Enum>) typeClass.asSubclass(Enum.class), "FAILURE");
            this.notImplementedType = Enum.valueOf((Class<Enum>) typeClass.asSubclass(Enum.class), "NOT_IMPLEMENTED");

            this.provider = Proxy.newProxyInstance(
                    economyClass.getClassLoader(),
                    new Class<?>[]{economyClass},
                    new EconomyInvocationHandler());

            Bukkit.getServicesManager().register((Class) economyClass, provider, plugin, ServicePriority.Normal);
            this.registered = true;
            plugin.getLogger().info("Vault erkannt - KlassenSMP-Economy wurde als Anbieter registriert.");
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().warning("Vault gefunden, aber die Economy-Registrierung ist fehlgeschlagen: " + ex.getMessage());
            this.registered = false;
        }
    }

    public boolean isRegistered() {
        return registered;
    }

    /** Meldet den Anbieter beim Deaktivieren wieder ab. */
    public void shutdown() {
        if (registered && provider != null) {
            try {
                Bukkit.getServicesManager().unregister(provider);
            } catch (RuntimeException ignored) {
                // Server faehrt bereits herunter
            }
        }
        this.registered = false;
        this.provider = null;
    }

    private Object response(double amount, double balance, Object type, String error) throws ReflectiveOperationException {
        return responseConstructor.newInstance(amount, balance, type, error);
    }

    /** Loest das erste Argument (Name oder OfflinePlayer) in eine UUID auf. */
    private UUID resolve(Object argument) {
        if (argument instanceof OfflinePlayer offlinePlayer) {
            return offlinePlayer.getUniqueId();
        }
        if (argument instanceof String name) {
            return plugin.getPlayerDataManager().findUuidByName(name);
        }
        return null;
    }

    private double amount(Object[] args) {
        if (args == null) {
            return 0.0D;
        }
        for (Object arg : args) {
            if (arg instanceof Double value) {
                return value;
            }
        }
        return 0.0D;
    }

    /**
     * Uebersetzt Vault-Aufrufe auf die KlassenSMP-Economy.
     * Nicht unterstuetzte Methoden liefern definierte Standardwerte statt zu werfen.
     */
    private final class EconomyInvocationHandler implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();

            switch (name) {
                case "equals" -> {
                    return proxy == args[0];
                }
                case "hashCode" -> {
                    return System.identityHashCode(proxy);
                }
                case "toString" -> {
                    return "KlassenSMP-VaultEconomy";
                }
                case "isEnabled" -> {
                    return plugin.isEnabled();
                }
                case "getName" -> {
                    return "KlassenSMP";
                }
                case "hasBankSupport" -> {
                    // KlassenSMP kennt persoenliche Bankkonten, aber keine benannten Vault-Banken.
                    return false;
                }
                case "fractionalDigits" -> {
                    return 2;
                }
                case "format" -> {
                    return NumberUtil.formatMoney(amount(args)) + " " + plugin.getEconomyManager().getCurrencySymbol();
                }
                case "currencyNamePlural" -> {
                    return plugin.getEconomyManager().getCurrencyPlural();
                }
                case "currencyNameSingular" -> {
                    return plugin.getEconomyManager().getCurrencySingular();
                }
                case "hasAccount", "createPlayerAccount" -> {
                    return resolve(args == null || args.length == 0 ? null : args[0]) != null;
                }
                case "getBalance" -> {
                    UUID uuid = resolve(args[0]);
                    return uuid == null ? 0.0D : plugin.getEconomyManager().getBalance(uuid);
                }
                case "has" -> {
                    UUID uuid = resolve(args[0]);
                    return uuid != null && plugin.getEconomyManager().getBalance(uuid) >= amount(args);
                }
                case "withdrawPlayer" -> {
                    UUID uuid = resolve(args[0]);
                    double value = NumberUtil.round(amount(args));
                    if (uuid == null || value <= 0) {
                        return response(0, 0, failureType, "Ungueltiges Konto oder Betrag");
                    }
                    boolean ok = plugin.getEconomyManager().withdraw(uuid, value);
                    double balance = plugin.getEconomyManager().getBalance(uuid);
                    return response(value, balance, ok ? successType : failureType,
                            ok ? null : "Nicht genug Guthaben");
                }
                case "depositPlayer" -> {
                    UUID uuid = resolve(args[0]);
                    double value = NumberUtil.round(amount(args));
                    if (uuid == null || value <= 0) {
                        return response(0, 0, failureType, "Ungueltiges Konto oder Betrag");
                    }
                    plugin.getEconomyManager().deposit(uuid, value);
                    return response(value, plugin.getEconomyManager().getBalance(uuid), successType, null);
                }
                case "getBanks" -> {
                    return new ArrayList<String>();
                }
                default -> {
                    return fallback(method);
                }
            }
        }

        /** Definierter Rueckgabewert fuer nicht unterstuetzte Vault-Methoden. */
        private Object fallback(Method method) throws ReflectiveOperationException {
            Class<?> type = method.getReturnType();
            if (type.getName().equals("net.milkbowl.vault.economy.EconomyResponse")) {
                return response(0, 0, notImplementedType, "Von KlassenSMP nicht unterstuetzt");
            }
            if (type == boolean.class || type == Boolean.class) {
                return false;
            }
            if (type == double.class || type == Double.class) {
                return 0.0D;
            }
            if (type == int.class || type == Integer.class) {
                return 0;
            }
            if (type == String.class) {
                return "";
            }
            if (List.class.isAssignableFrom(type)) {
                return new ArrayList<String>();
            }
            return null;
        }
    }
}
