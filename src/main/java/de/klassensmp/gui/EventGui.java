package de.klassensmp.gui;

import de.klassensmp.event.EventDefinition;
import de.klassensmp.KlassenSMP;
import de.klassensmp.util.Compat;
import de.klassensmp.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Uebersicht aller konfigurierten Events. */
public final class EventGui extends PaginatedGui<EventDefinition> {

    public EventGui(KlassenSMP plugin) {
        super(plugin, plugin.getMessages().plain("events.gui-title"));
    }

    @Override
    protected List<EventDefinition> entries(Player player) {
        return plugin.getServerEventManager().all();
    }

    @Override
    protected ItemStack render(Player player, EventDefinition definition) {
        List<String> lore = new ArrayList<>();
        lore.add(definition.description());
        lore.add("");
        lore.add(plugin.getMessages().plain("events.gui-type", "%type%", definition.type().name()));
        lore.add(plugin.getMessages().plain("events.gui-minplayers",
                "%amount%", String.valueOf(definition.minPlayers())));
        lore.add(plugin.getMessages().plain("events.gui-reward",
                "%money%", plugin.getEconomyManager().format(definition.rewardMoney())));
        lore.add("");
        boolean active = plugin.getServerEventManager().getActive() == definition;
        if (active) {
            lore.add(plugin.getMessages().plain("events.gui-active"));
        } else if (player.hasPermission("klassensmp.event.manage")) {
            lore.add(plugin.getMessages().plain("events.gui-start"));
        }

        ItemBuilder builder = new ItemBuilder(Compat.material(definition.icon(), Material.FIREWORK_ROCKET))
                .name(definition.displayName())
                .lore(lore);
        if (active) {
            builder.glow();
        }
        return builder.build();
    }

    @Override
    protected void onEntryClick(Player player, EventDefinition definition, boolean rightClick) {
        if (!player.hasPermission("klassensmp.event.manage")) {
            plugin.getMessages().send(player, "common.no-permission");
            return;
        }
        closeLater(player);
        switch (plugin.getServerEventManager().start(player, definition.id())) {
            case STARTED -> plugin.getMessages().send(player, "events.start-ok", "%event%", definition.displayName());
            case ALREADY_RUNNING -> plugin.getMessages().send(player, "events.already-running");
            case UNKNOWN_EVENT -> plugin.getMessages().send(player, "events.unknown");
            case NO_LOCATION -> plugin.getMessages().send(player, "events.no-location");
        }
    }
}
