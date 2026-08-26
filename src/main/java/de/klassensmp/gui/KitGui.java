package de.klassensmp.gui;

import de.klassensmp.KlassenSMP;
import de.klassensmp.kit.KitManager;
import de.klassensmp.model.Kit;
import de.klassensmp.util.Compat;
import de.klassensmp.util.ItemBuilder;
import de.klassensmp.util.TimeUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Uebersicht aller Kits mit Cooldown-Anzeige. */
public final class KitGui extends PaginatedGui<Kit> {

    public KitGui(KlassenSMP plugin) {
        super(plugin, plugin.getMessages().plain("kits.gui-title"));
    }

    @Override
    protected List<Kit> entries(Player player) {
        return plugin.getKitManager().all();
    }

    @Override
    protected ItemStack render(Player player, Kit kit) {
        boolean allowed = !kit.hasPermission() || player.hasPermission(kit.permission());
        long remaining = plugin.getKitManager().remainingCooldown(player.getUniqueId(), kit);

        List<String> lore = new ArrayList<>(kit.description());
        lore.add("");
        if (!allowed) {
            lore.add(plugin.getMessages().plain("kits.gui-no-permission"));
        } else if (remaining == Long.MAX_VALUE) {
            lore.add(plugin.getMessages().plain("kits.gui-one-time-used"));
        } else if (remaining > 0) {
            lore.add(plugin.getMessages().plain("kits.gui-cooldown",
                    "%time%", TimeUtil.formatDuration(remaining)));
        } else {
            lore.add(plugin.getMessages().plain("kits.gui-available"));
        }
        if (kit.price() > 0) {
            lore.add(plugin.getMessages().plain("kits.gui-price",
                    "%price%", plugin.getEconomyManager().format(kit.price())));
        }
        lore.add(plugin.getMessages().plain("kits.gui-items", "%amount%", String.valueOf(kit.items().size())));

        ItemBuilder builder = new ItemBuilder(Compat.material(kit.icon(), Material.CHEST))
                .name(kit.displayName())
                .lore(lore);
        if (allowed && remaining == 0) {
            builder.glow();
        }
        return builder.build();
    }

    @Override
    protected void onEntryClick(Player player, Kit kit, boolean rightClick) {
        KitManager.GiveResult result = plugin.getKitManager().give(player, kit);
        switch (result) {
            case SUCCESS -> {
                plugin.getMessages().send(player, "kits.received", "%kit%", kit.displayName());
                closeLater(player);
            }
            case NO_PERMISSION -> plugin.getMessages().send(player, "common.no-permission");
            case COOLDOWN -> plugin.getMessages().send(player, "kits.cooldown",
                    "%time%", TimeUtil.formatDuration(
                            plugin.getKitManager().remainingCooldown(player.getUniqueId(), kit)));
            case ALREADY_USED -> plugin.getMessages().send(player, "kits.one-time-used");
            case NOT_ENOUGH_MONEY -> plugin.getMessages().send(player, "economy.not-enough-money");
            case INVENTORY_FULL -> plugin.getMessages().send(player, "kits.inventory-full");
            case UNKNOWN -> plugin.getMessages().send(player, "kits.unknown");
        }
        refresh(player);
    }
}
