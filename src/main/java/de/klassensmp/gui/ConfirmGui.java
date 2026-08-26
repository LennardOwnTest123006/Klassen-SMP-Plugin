package de.klassensmp.gui;

import de.klassensmp.KlassenSMP;
import de.klassensmp.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.Consumer;

/** Einfache Ja/Nein-Rueckfrage vor kritischen Aktionen. */
public final class ConfirmGui extends Gui {

    private final String question;
    private final List<String> details;
    private final Consumer<Player> onConfirm;
    private final Consumer<Player> onCancel;

    public ConfirmGui(KlassenSMP plugin, String question, List<String> details,
                      Consumer<Player> onConfirm, Consumer<Player> onCancel) {
        super(plugin, plugin.getMessages().plain("gui.confirm-title"), 3);
        this.question = question;
        this.details = details == null ? List.of() : details;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    @Override
    protected void build(Player player) {
        set(13, new ItemBuilder(Material.PAPER).name(question).lore(details).build());
        set(11, new ItemBuilder(Material.LIME_WOOL)
                .name(plugin.getMessages().plain("gui.confirm-yes"))
                .build(), event -> resolve(player, onConfirm));
        set(15, new ItemBuilder(Material.RED_WOOL)
                .name(plugin.getMessages().plain("gui.confirm-no"))
                .build(), event -> resolve(player, onCancel));
        fillEmpty();
    }

    /**
     * Schliesst die Rueckfrage und fuehrt danach die Aktion aus.
     * Beides passiert im selben Tick nacheinander, damit eine Aktion, die
     * eine neue Oberflaeche oeffnet, nicht sofort wieder geschlossen wird.
     */
    private void resolve(Player player, Consumer<Player> action) {
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            player.closeInventory();
            if (action != null) {
                action.accept(player);
            }
        });
    }
}
