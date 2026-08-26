package de.klassensmp.gui;

import de.klassensmp.KlassenSMP;
import de.klassensmp.model.Crate;
import de.klassensmp.model.CrateReward;
import de.klassensmp.util.ItemBuilder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

/** Zeigt alle moeglichen Belohnungen einer Crate samt Wahrscheinlichkeit. */
public final class CratePreviewGui extends PaginatedGui<CrateReward> {

    private final Crate crate;

    public CratePreviewGui(KlassenSMP plugin, Crate crate) {
        super(plugin, plugin.getMessages().plain("crates.preview-title", "%crate%", crate.displayName()));
        this.crate = crate;
    }

    @Override
    protected List<CrateReward> entries(Player player) {
        return crate.rewards();
    }

    @Override
    protected ItemStack render(Player player, CrateReward reward) {
        double total = crate.totalWeight();
        double percent = total <= 0 ? 0 : reward.chance() * 100.0D / total;
        return new ItemBuilder(plugin.getCrateManager().previewItem(reward))
                .addLore(plugin.getMessages().plain("crates.preview-chance",
                        "%chance%", String.format(Locale.GERMANY, "%.2f", percent)))
                .build();
    }

    @Override
    protected void onEntryClick(Player player, CrateReward reward, boolean rightClick) {
        // Reine Vorschau.
    }
}
