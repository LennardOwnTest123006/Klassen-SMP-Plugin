package de.klassensmp.manager;

import de.klassensmp.KlassenSMP;
import de.klassensmp.model.PlayerData;
import de.klassensmp.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Steuert PvP: Ein-/Ausschalten pro Spieler, geschuetzte Zonen,
 * Combat-Tag und Combat-Log-Behandlung.
 */
public final class PvpManager {

    private final KlassenSMP plugin;

    /** UUID -> Zeitpunkt, bis zu dem der Spieler im Kampf ist. */
    private final Map<UUID, Long> combatTags = new ConcurrentHashMap<>();
    /** UUID -> letzter Gegner, fuer aussagekraeftige Meldungen. */
    private final Map<UUID, UUID> lastOpponent = new ConcurrentHashMap<>();

    public PvpManager(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    /** Gruende, warum ein Angriff verhindert wurde. */
    public enum DenyReason {
        ALLOWED,
        WORLD_DISABLED,
        SPAWN_PROTECTED,
        CLAIM_PROTECTED,
        ATTACKER_DISABLED,
        VICTIM_DISABLED,
        EVENT_ONLY
    }

    /**
     * Prueft, ob ein Angriff erlaubt ist.
     *
     * <p>Reihenfolge: Weltregel, Spawnschutz, Claimschutz, dann die
     * persoenlichen PvP-Einstellungen beider Spieler.</p>
     */
    public DenyReason check(Player attacker, Player victim) {
        if (attacker == null || victim == null) {
            return DenyReason.ALLOWED;
        }
        if (plugin.getServerEventManager().isParticipant(attacker) && plugin.getServerEventManager().isParticipant(victim)
                && plugin.getServerEventManager().isPvpEvent()) {
            return DenyReason.ALLOWED;
        }
        if (!plugin.getConfigManager().bool("pvp.enabled", true)) {
            return DenyReason.WORLD_DISABLED;
        }
        if (plugin.getWorldManager().isPvpDisabled(victim.getWorld().getName())) {
            return DenyReason.WORLD_DISABLED;
        }
        if (plugin.getConfigManager().bool("pvp.protect-spawn", true)
                && (plugin.getSpawnManager().isInSpawnArea(victim.getLocation())
                || plugin.getSpawnManager().isInSpawnArea(attacker.getLocation()))) {
            return DenyReason.SPAWN_PROTECTED;
        }
        if (plugin.getConfigManager().bool("pvp.protect-claims", true)
                && plugin.getClaimManager().getClaimAt(victim.getLocation()) != null) {
            return DenyReason.CLAIM_PROTECTED;
        }
        if (plugin.getConfigManager().bool("pvp.force-enabled", false)) {
            return DenyReason.ALLOWED;
        }

        PlayerData attackerData = plugin.getPlayerDataManager().get(attacker.getUniqueId());
        PlayerData victimData = plugin.getPlayerDataManager().get(victim.getUniqueId());
        if (attackerData != null && !attackerData.isPvpEnabled()) {
            return DenyReason.ATTACKER_DISABLED;
        }
        if (victimData != null && !victimData.isPvpEnabled()) {
            return DenyReason.VICTIM_DISABLED;
        }
        return DenyReason.ALLOWED;
    }

    public boolean isPvpEnabled(Player player) {
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        return data == null || data.isPvpEnabled();
    }

    /** Schaltet PvP fuer einen Spieler um. */
    public boolean toggle(Player player) {
        PlayerData data = plugin.getPlayerDataManager().get(player);
        boolean next = !data.isPvpEnabled();
        data.setPvpEnabled(next);
        plugin.getPlayerDataManager().save(data);
        return next;
    }

    // ------------------------------------------------------------------
    // Combat-Tag
    // ------------------------------------------------------------------

    public void tag(Player attacker, Player victim) {
        if (!plugin.getConfigManager().bool("pvp.combat-tag.enabled", true)) {
            return;
        }
        long seconds = Math.max(1L, plugin.getConfigManager().duration("pvp.combat-tag.seconds", 15L));
        long until = System.currentTimeMillis() + seconds * 1000L;

        boolean attackerWasFree = !isTagged(attacker);
        boolean victimWasFree = !isTagged(victim);

        combatTags.put(attacker.getUniqueId(), until);
        combatTags.put(victim.getUniqueId(), until);
        lastOpponent.put(attacker.getUniqueId(), victim.getUniqueId());
        lastOpponent.put(victim.getUniqueId(), attacker.getUniqueId());

        if (attackerWasFree) {
            plugin.getMessages().send(attacker, "pvp.combat-enter", "%seconds%", String.valueOf(seconds));
        }
        if (victimWasFree) {
            plugin.getMessages().send(victim, "pvp.combat-enter", "%seconds%", String.valueOf(seconds));
        }
    }

    public boolean isTagged(Player player) {
        if (player == null) {
            return false;
        }
        Long until = combatTags.get(player.getUniqueId());
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() >= until) {
            combatTags.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    public long remainingTag(Player player) {
        Long until = combatTags.get(player.getUniqueId());
        return until == null ? 0L : Math.max(0L, until - System.currentTimeMillis());
    }

    public String remainingTagFormatted(Player player) {
        return TimeUtil.formatDuration(remainingTag(player));
    }

    public void clearTag(UUID uuid) {
        combatTags.remove(uuid);
        lastOpponent.remove(uuid);
    }

    /**
     * Behandelt einen Spieler, der waehrend des Combat-Tags den Server verlaesst.
     *
     * <p>Was passiert, entscheidet die Config: nur melden, den Spieler toeten
     * oder gar nichts tun. Es wird niemals automatisch gebannt.</p>
     */
    public void handleCombatLog(Player player) {
        if (!isTagged(player)) {
            clearTag(player.getUniqueId());
            return;
        }
        String action = plugin.getConfigManager().enumString("pvp.combat-tag.on-logout", "KILL");
        UUID opponentId = lastOpponent.get(player.getUniqueId());
        Player opponent = opponentId == null ? null : Bukkit.getPlayer(opponentId);

        if (plugin.getConfigManager().bool("pvp.combat-tag.broadcast-logout", true)) {
            Bukkit.broadcastMessage(plugin.getMessages().get("pvp.combat-log-broadcast",
                    "%player%", player.getName()));
        }
        if (opponent != null) {
            plugin.getMessages().send(opponent, "pvp.combat-log-opponent", "%player%", player.getName());
        }

        if (action.equals("KILL")) {
            // Der Spieler stirbt regulaer; das Grab-System uebernimmt die Items.
            player.setHealth(0.0D);
        }
        clearTag(player.getUniqueId());
    }

    public void handleQuit(Player player) {
        handleCombatLog(player);
    }
}
