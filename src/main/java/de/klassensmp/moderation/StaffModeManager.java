package de.klassensmp.moderation;

import de.klassensmp.KlassenSMP;
import de.klassensmp.util.Compat;
import de.klassensmp.util.ItemBuilder;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Der Moderationsmodus ({@code /mod}).
 *
 * <p>Beim Aktivieren werden Inventar, Spielmodus, Flugstatus und Leben
 * gesichert und der Spieler erhaelt ein Moderationsinventar. Beim Deaktivieren
 * wird exakt der vorherige Zustand wiederhergestellt - auch dann, wenn der
 * Server zwischendurch neu startet, denn beim Herunterfahren wird der Modus
 * fuer alle Spieler beendet.</p>
 */
public final class StaffModeManager {

    /** Slot-Belegung des Moderationsinventars. */
    public static final int SLOT_PLAYERS = 0;
    public static final int SLOT_TELEPORT = 1;
    public static final int SLOT_FREEZE = 2;
    public static final int SLOT_INSPECT = 3;
    public static final int SLOT_VANISH = 7;
    public static final int SLOT_EXIT = 8;

    private final KlassenSMP plugin;
    private final Map<UUID, SavedState> saved = new ConcurrentHashMap<>();

    public StaffModeManager(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    /** Gesicherter Spielerzustand vor dem Moderationsmodus. */
    private record SavedState(ItemStack[] contents,
                              ItemStack[] armor,
                              ItemStack offHand,
                              GameMode gameMode,
                              boolean allowFlight,
                              boolean flying,
                              boolean invulnerable,
                              double health,
                              int foodLevel,
                              float saturation,
                              boolean wasVanished) {
    }

    public boolean isActive(Player player) {
        return player != null && saved.containsKey(player.getUniqueId());
    }

    public int count() {
        return saved.size();
    }

    /** @return {@code true}, wenn der Moderationsmodus jetzt aktiv ist. */
    public boolean toggle(Player player) {
        if (isActive(player)) {
            disable(player);
            return false;
        }
        enable(player);
        return true;
    }

    public void enable(Player player) {
        if (isActive(player)) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        saved.put(player.getUniqueId(), new SavedState(
                inventory.getContents().clone(),
                inventory.getArmorContents().clone(),
                inventory.getItemInOffHand() == null ? null : inventory.getItemInOffHand().clone(),
                player.getGameMode(),
                player.getAllowFlight(),
                player.isFlying(),
                player.isInvulnerable(),
                player.getHealth(),
                player.getFoodLevel(),
                player.getSaturation(),
                plugin.getVanishManager().isVanished(player)));

        inventory.clear();
        inventory.setArmorContents(null);
        inventory.setItemInOffHand(null);

        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setInvulnerable(true);

        giveItems(player);
        plugin.getVanishManager().hide(player);

        if (plugin.getConfigManager().bool("sounds.enabled", true)) {
            Compat.playSound(player, plugin.getConfigManager().string("sounds.staffmode", "block.beacon.activate"), 0.6F, 1.4F);
        }
    }

    /** Setzt die Moderationsitems neu (z.B. nach einem Klick). */
    public void giveItems(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.setItem(SLOT_PLAYERS, new ItemBuilder(Material.BOOK)
                .name(plugin.getMessages().plain("staffmode.item-players"))
                .lore(plugin.getMessages().list("staffmode.item-players-lore"))
                .build());
        inventory.setItem(SLOT_TELEPORT, new ItemBuilder(Material.COMPASS)
                .name(plugin.getMessages().plain("staffmode.item-teleport"))
                .lore(plugin.getMessages().list("staffmode.item-teleport-lore"))
                .build());
        inventory.setItem(SLOT_FREEZE, new ItemBuilder(Material.PACKED_ICE)
                .name(plugin.getMessages().plain("staffmode.item-freeze"))
                .lore(plugin.getMessages().list("staffmode.item-freeze-lore"))
                .build());
        inventory.setItem(SLOT_INSPECT, new ItemBuilder(Material.CHEST)
                .name(plugin.getMessages().plain("staffmode.item-inspect"))
                .lore(plugin.getMessages().list("staffmode.item-inspect-lore"))
                .build());
        inventory.setItem(SLOT_VANISH, new ItemBuilder(
                plugin.getVanishManager().isVanished(player) ? Material.ENDER_EYE : Material.ENDER_PEARL)
                .name(plugin.getMessages().plain("staffmode.item-vanish"))
                .lore(plugin.getMessages().list("staffmode.item-vanish-lore"))
                .build());
        inventory.setItem(SLOT_EXIT, new ItemBuilder(Material.BARRIER)
                .name(plugin.getMessages().plain("staffmode.item-exit"))
                .lore(plugin.getMessages().list("staffmode.item-exit-lore"))
                .build());
        player.updateInventory();
    }

    public void disable(Player player) {
        SavedState state = saved.remove(player.getUniqueId());
        if (state == null) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setContents(state.contents());
        inventory.setArmorContents(state.armor());
        inventory.setItemInOffHand(state.offHand());

        player.setGameMode(state.gameMode());
        player.setAllowFlight(state.allowFlight());
        player.setFlying(state.allowFlight() && state.flying());
        player.setInvulnerable(state.invulnerable());
        player.setFoodLevel(state.foodLevel());
        player.setSaturation(state.saturation());
        try {
            // Der Wert stammt vom selben Spieler, liegt also im gueltigen Bereich.
            player.setHealth(Math.max(0.5D, state.health()));
        } catch (IllegalArgumentException ex) {
            // Maximales Leben hat sich zwischenzeitlich geaendert - Spieler bleibt am Leben.
            plugin.getLogger().warning("Leben konnte nicht wiederhergestellt werden: " + player.getName());
        }

        if (!state.wasVanished()) {
            plugin.getVanishManager().show(player);
        }
        player.updateInventory();

        if (plugin.getConfigManager().bool("sounds.enabled", true)) {
            Compat.playSound(player, plugin.getConfigManager().string("sounds.staffmode-off", "block.beacon.deactivate"), 0.6F, 1.0F);
        }
    }

    /**
     * Beendet den Moderationsmodus fuer alle Spieler.
     * Wird beim Herunterfahren aufgerufen, damit keine Inventare verloren gehen.
     */
    public void restoreAll() {
        for (UUID uuid : new java.util.ArrayList<>(saved.keySet())) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                disable(player);
            } else {
                saved.remove(uuid);
            }
        }
    }

    public void handleQuit(Player player) {
        disable(player);
    }
}
