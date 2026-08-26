package de.klassensmp.gui;

import de.klassensmp.KlassenSMP;
import de.klassensmp.model.Achievement;
import de.klassensmp.util.Compat;
import de.klassensmp.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Uebersicht aller Erfolge eines Spielers. */
public final class AchievementGui extends PaginatedGui<Achievement> {

    private final UUID target;
    private final String targetName;

    public AchievementGui(KlassenSMP plugin, UUID target, String targetName) {
        super(plugin, plugin.getMessages().plain("achievements.gui-title", "%player%", targetName));
        this.target = target;
        this.targetName = targetName;
    }

    @Override
    protected List<Achievement> entries(Player player) {
        return plugin.getAchievementManager().all();
    }

    @Override
    protected ItemStack render(Player player, Achievement achievement) {
        boolean unlocked = plugin.getAchievementManager().hasUnlocked(target, achievement.id());
        List<String> lore = new ArrayList<>();
        lore.add(achievement.description());
        lore.add("");
        lore.add(unlocked
                ? plugin.getMessages().plain("achievements.gui-unlocked")
                : plugin.getMessages().plain("achievements.gui-locked"));
        if (achievement.moneyReward() > 0) {
            lore.add(plugin.getMessages().plain("achievements.gui-reward-money",
                    "%money%", plugin.getEconomyManager().format(achievement.moneyReward())));
        }
        if (achievement.experienceReward() > 0) {
            lore.add(plugin.getMessages().plain("achievements.gui-reward-xp",
                    "%amount%", String.valueOf(achievement.experienceReward())));
        }

        ItemBuilder builder = new ItemBuilder(unlocked
                ? Compat.material(achievement.icon(), Material.PAPER)
                : Material.GRAY_DYE)
                .name(achievement.displayName())
                .lore(lore);
        if (unlocked) {
            builder.glow();
        }
        return builder.build();
    }

    @Override
    protected void onEntryClick(Player player, Achievement achievement, boolean rightClick) {
        // Erfolge sind reine Anzeige.
    }

    public String getTargetName() {
        return targetName;
    }
}
