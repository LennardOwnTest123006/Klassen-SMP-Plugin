package de.klassensmp.gui;

import de.klassensmp.KlassenSMP;
import de.klassensmp.model.Crate;
import de.klassensmp.model.CrateReward;
import de.klassensmp.util.Compat;
import de.klassensmp.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

/**
 * Crate-Oberflaeche mit Ziehungsanimation.
 *
 * <p>Die Belohnung wird <b>vor</b> der Animation ausgewuerfelt und erst am
 * Ende uebergeben. Ein vorzeitiges Schliessen der Oberflaeche kann die
 * Belohnung daher weder verhindern noch verdoppeln.</p>
 */
public final class CrateGui extends Gui {

    private static final int[] ANIMATION_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int RESULT_SLOT = 13;

    private final Crate crate;
    private final CrateReward result;
    private boolean finished;

    public CrateGui(KlassenSMP plugin, Crate crate, CrateReward result) {
        super(plugin, plugin.getMessages().plain("crates.gui-title", "%crate%", crate.displayName()), 3);
        this.crate = crate;
        this.result = result;
    }

    @Override
    protected void build(Player player) {
        for (int slot : ANIMATION_SLOTS) {
            set(slot, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name("&7...").build());
        }
        fillEmpty();
    }

    /** Startet die Animation und uebergibt danach die Belohnung. */
    public void play(Player player) {
        open(player);
        List<CrateReward> rewards = crate.rewards();
        int steps = Math.max(12, plugin.getConfigManager().integer("crates.animation-steps", 24));

        new BukkitRunnable() {
            private int step;

            @Override
            public void run() {
                if (!player.isOnline() || finished) {
                    finish(player);
                    cancel();
                    return;
                }
                for (int slot : ANIMATION_SLOTS) {
                    CrateReward random = rewards.get((int) (Math.random() * rewards.size()));
                    inventory().setItem(slot, plugin.getCrateManager().previewItem(random));
                }
                if (plugin.getConfigManager().bool("sounds.enabled", true)) {
                    Compat.playSound(player,
                            plugin.getConfigManager().string("sounds.crate-tick", "block.note_block.hat"), 0.5F, 1.4F);
                }
                step++;
                if (step >= steps) {
                    finish(player);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 5L, 3L);
    }

    private void finish(Player player) {
        if (finished) {
            return;
        }
        finished = true;

        for (int slot : ANIMATION_SLOTS) {
            inventory().setItem(slot, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name("&r").build());
        }
        ItemStack preview = plugin.getCrateManager().previewItem(result);
        inventory().setItem(RESULT_SLOT, preview);
        if (player.isOnline()) {
            player.updateInventory();
        }

        plugin.getCrateManager().giveReward(player, crate, result);
        if (plugin.getConfigManager().bool("sounds.enabled", true)) {
            Compat.playSound(player,
                    plugin.getConfigManager().string("sounds.crate-win", "entity.player.levelup"), 0.8F, 1.2F);
        }
        // Die Oberflaeche bleibt kurz offen, damit der Gewinn sichtbar ist.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && plugin.getGuiManager().openGui(player) == this) {
                player.closeInventory();
            }
        }, 60L);
    }
}
