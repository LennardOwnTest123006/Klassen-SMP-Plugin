package de.klassensmp.gui;

import de.klassensmp.KlassenSMP;
import de.klassensmp.model.Quest;
import de.klassensmp.model.QuestPeriod;
import de.klassensmp.model.QuestProgress;
import de.klassensmp.quest.QuestManager;
import de.klassensmp.util.Compat;
import de.klassensmp.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Uebersicht der taeglichen und woechentlichen Aufgaben. */
public final class QuestGui extends PaginatedGui<Quest> {

    public QuestGui(KlassenSMP plugin) {
        super(plugin, plugin.getMessages().plain("quests.gui-title"));
    }

    @Override
    protected List<Quest> entries(Player player) {
        return plugin.getQuestManager().activeQuests(player.getUniqueId());
    }

    @Override
    protected ItemStack render(Player player, Quest quest) {
        QuestProgress progress = plugin.getQuestManager().getProgress(player.getUniqueId(), quest);
        int current = progress == null ? 0 : progress.getProgress();
        boolean claimed = progress != null && progress.isClaimed();
        boolean complete = progress != null && progress.isComplete();

        List<String> lore = new ArrayList<>();
        lore.add(quest.description());
        lore.add("");
        lore.add(plugin.getMessages().plain("quests.gui-period",
                "%period%", quest.period() == QuestPeriod.WEEKLY
                        ? plugin.getMessages().plain("quests.weekly")
                        : plugin.getMessages().plain("quests.daily")));
        lore.add(plugin.getMessages().plain("quests.gui-progress",
                "%progress%", String.valueOf(current),
                "%target%", String.valueOf(quest.amount()),
                "%percent%", String.valueOf(progress == null ? 0 : progress.percent())));
        lore.add(progressBar(progress));
        lore.add("");
        if (quest.moneyReward() > 0) {
            lore.add(plugin.getMessages().plain("quests.gui-reward-money",
                    "%money%", plugin.getEconomyManager().format(quest.moneyReward())));
        }
        if (quest.experienceReward() > 0) {
            lore.add(plugin.getMessages().plain("quests.gui-reward-xp",
                    "%amount%", String.valueOf(quest.experienceReward())));
        }
        lore.add("");
        if (claimed) {
            lore.add(plugin.getMessages().plain("quests.gui-claimed"));
        } else if (complete) {
            lore.add(plugin.getMessages().plain("quests.gui-claim"));
        } else {
            lore.add(plugin.getMessages().plain("quests.gui-in-progress"));
        }

        ItemBuilder builder = new ItemBuilder(claimed
                ? Material.GRAY_DYE
                : Compat.material(quest.icon(), Material.PAPER))
                .name(quest.displayName())
                .lore(lore);
        if (complete && !claimed) {
            builder.glow();
        }
        return builder.build();
    }

    /** Einfache Fortschrittsleiste aus 20 Bloecken. */
    private String progressBar(QuestProgress progress) {
        int percent = progress == null ? 0 : progress.percent();
        int filled = percent / 5;
        StringBuilder bar = new StringBuilder("&8[&a");
        for (int i = 0; i < 20; i++) {
            if (i == filled) {
                bar.append("&7");
            }
            bar.append('|');
        }
        bar.append("&8]");
        return de.klassensmp.util.Text.color(bar.toString());
    }

    @Override
    protected void onEntryClick(Player player, Quest quest, boolean rightClick) {
        QuestManager.ClaimResult result = plugin.getQuestManager().claim(player, quest);
        switch (result) {
            case SUCCESS -> plugin.getMessages().send(player, "quests.claimed", "%quest%", quest.displayName());
            case ALREADY_CLAIMED -> plugin.getMessages().send(player, "quests.already-claimed");
            case NOT_COMPLETE -> plugin.getMessages().send(player, "quests.not-complete");
            case UNKNOWN -> plugin.getMessages().send(player, "quests.unknown");
        }
        refresh(player);
    }
}
