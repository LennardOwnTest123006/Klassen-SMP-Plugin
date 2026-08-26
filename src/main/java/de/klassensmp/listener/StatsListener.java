package de.klassensmp.listener;

import de.klassensmp.KlassenSMP;
import de.klassensmp.model.PlayerData;
import de.klassensmp.model.QuestType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Zaehlt Spielerstatistiken und meldet Fortschritt an Aufgaben und Erfolge.
 *
 * <p>Es wird bewusst am Ende der Ereigniskette gelauscht ({@code MONITOR}),
 * damit nur tatsaechlich durchgefuehrte Aktionen gezaehlt werden.</p>
 */
public final class StatsListener implements Listener {

    private final KlassenSMP plugin;

    public StatsListener(KlassenSMP plugin) {
        this.plugin = plugin;
        startPlaytimeCheck();
    }

    /** Prueft regelmaessig die Spielzeit-Erfolge. */
    private void startPlaytimeCheck() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    plugin.getAchievementManager().checkPlaytime(player);
                    plugin.getQuestManager().addProgress(player, QuestType.PLAY_MINUTES, "", 1);
                }
            }
        }.runTaskTimer(plugin, 20L * 60L, 20L * 60L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Material type = event.getBlock().getType();

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        if (data != null) {
            data.addBlockBroken();
            plugin.getAchievementManager().checkBlocksBroken(player, data.getBlocksBroken());
        }
        plugin.getQuestManager().addProgress(player, QuestType.BREAK_BLOCKS, type.name(), 1);

        if (isOre(type)) {
            plugin.getQuestManager().addProgress(player, QuestType.MINE_ORE, type.name(), 1);
        }
        if (type == Material.DIAMOND_ORE || type == Material.DEEPSLATE_DIAMOND_ORE) {
            plugin.getAchievementManager().checkDiamond(player);
        }
    }

    private boolean isOre(Material material) {
        String name = material.name();
        return name.endsWith("_ORE") || name.equals("ANCIENT_DEBRIS");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        if (data != null) {
            data.addBlockPlaced();
            plugin.getAchievementManager().checkBlocksPlaced(player, data.getBlocksPlaced());
        }
        plugin.getQuestManager().addProgress(player, QuestType.PLACE_BLOCKS,
                event.getBlock().getType().name(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            plugin.getQuestManager().addProgress(event.getPlayer(), QuestType.FISH_ITEMS, "", 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || event.getRecipe() == null) {
            return;
        }
        var result = event.getRecipe().getResult();
        plugin.getQuestManager().addProgress(player, QuestType.CRAFT_ITEMS,
                result.getType().name(), Math.max(1, result.getAmount()));
    }

    /** Schatzsuche: aufgesammelte Marker-Items zaehlen. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            plugin.getServerEventManager().handleTreasurePickup(player, event.getItem().getItemStack());
        }
    }

    /** Sorgt dafuer, dass die Statistik auch beim Tod sofort gespeichert wird. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        PlayerData data = plugin.getPlayerDataManager().get(event.getEntity().getUniqueId());
        if (data != null) {
            plugin.getPlayerDataManager().save(data);
        }
    }
}
