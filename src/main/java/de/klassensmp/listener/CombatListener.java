package de.klassensmp.listener;

import de.klassensmp.KlassenSMP;
import de.klassensmp.manager.PvpManager;
import de.klassensmp.model.PlayerData;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.List;

/** Kampf, Tod und die daraus folgenden Statistiken. */
public final class CombatListener implements Listener {

    private final KlassenSMP plugin;

    public CombatListener(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    /** Ermittelt den verantwortlichen Spieler hinter einem Angriff. */
    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) {
                return player;
            }
        }
        if (damager instanceof Tameable tameable && tameable.getOwner() instanceof Player owner) {
            return owner;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Player attacker = resolveAttacker(event.getDamager());

        // Spieler gegen Spieler
        if (event.getEntity() instanceof Player victim && attacker != null && !attacker.equals(victim)) {
            PvpManager.DenyReason reason = plugin.getPvpManager().check(attacker, victim);
            if (reason != PvpManager.DenyReason.ALLOWED) {
                event.setCancelled(true);
                sendDenyMessage(attacker, reason);
                return;
            }
            plugin.getPvpManager().tag(attacker, victim);
            plugin.getTeleportManager().handleDamage(victim);
            return;
        }

        // Schutz von Tieren und Villagern in fremden Claims
        if (attacker != null && event.getEntity() instanceof LivingEntity
                && !(event.getEntity() instanceof Player)) {
            if (plugin.getClaimManager().isEnabled()
                    && plugin.getConfigManager().bool("claims.protect-entities", true)
                    && !plugin.getClaimManager().canBuild(attacker, event.getEntity().getLocation())) {
                event.setCancelled(true);
                plugin.getMessages().send(attacker, "claims.no-access");
            }
        }
    }

    private void sendDenyMessage(Player attacker, PvpManager.DenyReason reason) {
        switch (reason) {
            case WORLD_DISABLED -> plugin.getMessages().send(attacker, "pvp.world-disabled");
            case SPAWN_PROTECTED -> plugin.getMessages().send(attacker, "pvp.spawn-protected");
            case CLAIM_PROTECTED -> plugin.getMessages().send(attacker, "pvp.claim-protected");
            case ATTACKER_DISABLED -> plugin.getMessages().send(attacker, "pvp.own-disabled");
            case VICTIM_DISABLED -> plugin.getMessages().send(attacker, "pvp.target-disabled");
            default -> {
                // ALLOWED und EVENT_ONLY benoetigen keine Meldung
            }
        }
    }

    /** Bricht laufende Teleports ab, wenn der Spieler Schaden nimmt. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            plugin.getTeleportManager().handleDamage(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();

        PlayerData victimData = plugin.getPlayerDataManager().get(victim.getUniqueId());
        if (victimData != null) {
            victimData.addDeath();
        }

        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            PlayerData killerData = plugin.getPlayerDataManager().get(killer.getUniqueId());
            if (killerData != null) {
                killerData.addKill();
                plugin.getAchievementManager().checkKills(killer, killerData.getKills());
                plugin.getQuestManager().addProgress(killer,
                        de.klassensmp.model.QuestType.KILL_PLAYERS, "", 1);
            }
        }

        plugin.getPvpManager().clearTag(victim.getUniqueId());
        plugin.getServerEventManager().handleDeath(victim);

        // Grab anlegen, sofern aktiviert und Items vorhanden sind.
        if (plugin.getGraveManager().isEnabled() && !event.getKeepInventory() && !event.getDrops().isEmpty()) {
            List<ItemStack> drops = new ArrayList<>(event.getDrops());
            int experience = event.getDroppedExp();
            if (plugin.getGraveManager().createGrave(victim, drops, experience)) {
                event.getDrops().clear();
                event.setDroppedExp(0);
            }
        }

        if (plugin.getConfigManager().bool("death.custom-messages", true)) {
            String message = killer == null
                    ? plugin.getMessages().plain("death.default", "%player%", victim.getName())
                    : plugin.getMessages().plain("death.killed",
                    "%player%", victim.getName(), "%killer%", killer.getName());
            event.setDeathMessage(message.isBlank() ? null : message);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) {
            return;
        }
        Player killer = entity.getKiller();
        if (killer == null) {
            return;
        }
        PlayerData data = plugin.getPlayerDataManager().get(killer.getUniqueId());
        if (data != null) {
            data.addMobKill();
            plugin.getAchievementManager().checkMobKills(killer, data.getMobKills());
        }
        plugin.getQuestManager().addProgress(killer,
                de.klassensmp.model.QuestType.KILL_MOBS, entity.getType().name(), 1);

        if (entity instanceof EnderDragon) {
            plugin.getAchievementManager().checkDragon(killer);
        }
    }
}
