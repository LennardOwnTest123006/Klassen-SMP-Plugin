package de.klassensmp.gui;

import de.klassensmp.KlassenSMP;
import de.klassensmp.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Oberflaeche mit Seitenblaetterung.
 *
 * <p>Die oberen fuenf Reihen zeigen die Eintraege, die unterste Reihe die
 * Navigation. Dadurch bleibt die Bedienung ueberall im Plugin gleich.</p>
 */
public abstract class PaginatedGui<T> extends Gui {

    protected static final int PAGE_SIZE = 45;

    protected int page;

    protected PaginatedGui(KlassenSMP plugin, String title) {
        super(plugin, title, 6);
    }

    /** Die Eintraege, die angezeigt werden sollen. */
    protected abstract List<T> entries(Player player);

    /** Erzeugt das Item fuer einen Eintrag. */
    protected abstract ItemStack render(Player player, T entry);

    /** Wird bei einem Klick auf einen Eintrag aufgerufen. */
    protected abstract void onEntryClick(Player player, T entry, boolean rightClick);

    /** Optionaler Rueckwaerts-Knopf in der Fusszeile. */
    protected BiConsumer<Player, PaginatedGui<T>> backAction() {
        return null;
    }

    @Override
    protected void build(Player player) {
        List<T> entries = entries(player);
        int pages = Math.max(1, (int) Math.ceil(entries.size() / (double) PAGE_SIZE));
        if (page >= pages) {
            page = pages - 1;
        }
        if (page < 0) {
            page = 0;
        }

        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = start + i;
            if (index >= entries.size()) {
                break;
            }
            T entry = entries.get(index);
            set(i, render(player, entry), event -> onEntryClick(player, entry, event.isRightClick()));
        }

        if (page > 0) {
            set(45, new ItemBuilder(Material.ARROW)
                    .name(plugin.getMessages().plain("gui.previous-page"))
                    .build(), event -> {
                page--;
                refresh(player);
            });
        }
        if (page < pages - 1) {
            set(53, new ItemBuilder(Material.ARROW)
                    .name(plugin.getMessages().plain("gui.next-page"))
                    .build(), event -> {
                page++;
                refresh(player);
            });
        }

        set(49, new ItemBuilder(Material.BOOK)
                .name(plugin.getMessages().plain("gui.page-info",
                        "%page%", String.valueOf(page + 1),
                        "%pages%", String.valueOf(pages),
                        "%total%", String.valueOf(entries.size())))
                .build());

        BiConsumer<Player, PaginatedGui<T>> back = backAction();
        if (back != null) {
            set(48, backButton(), event -> back.accept(player, this));
        }
        set(50, closeButton(), event -> closeLater(player));

        fillEmpty();
    }
}
