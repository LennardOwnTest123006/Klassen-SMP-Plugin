package de.klassensmp.command;

import de.klassensmp.KlassenSMP;
import de.klassensmp.gui.KitGui;
import de.klassensmp.kit.KitManager;
import de.klassensmp.model.Kit;
import de.klassensmp.util.NumberUtil;
import de.klassensmp.util.TimeUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@code /kit} mit Verwaltungsunterbefehlen. */
public final class KitCommand extends BaseCommand {

    public KitCommand(KlassenSMP plugin) {
        super(plugin, "klassensmp.kit", true);
    }

    @Override
    protected void execute(CommandSender sender, String name, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length == 0) {
            new KitGui(plugin).open(player);
            return;
        }

        String first = args[0].toLowerCase(Locale.ROOT);
        if (first.equals("create") || first.equals("erstellen")) {
            create(player, args);
            return;
        }
        if (first.equals("delete") || first.equals("loeschen")) {
            delete(player, args);
            return;
        }
        if (first.equals("edit") || first.equals("bearbeiten")) {
            // Bearbeiten = neu aus dem aktuellen Inventar speichern.
            create(player, args);
            return;
        }
        if (first.equals("list") || first.equals("liste")) {
            list(player);
            return;
        }

        Kit kit = plugin.getKitManager().get(first);
        if (kit == null) {
            plugin.getMessages().send(player, "kits.unknown");
            return;
        }
        KitManager.GiveResult result = plugin.getKitManager().give(player, kit);
        switch (result) {
            case SUCCESS -> plugin.getMessages().send(player, "kits.received", "%kit%", kit.displayName());
            case NO_PERMISSION -> plugin.getMessages().send(player, "common.no-permission");
            case COOLDOWN -> plugin.getMessages().send(player, "kits.cooldown",
                    "%time%", TimeUtil.formatDuration(
                            plugin.getKitManager().remainingCooldown(player.getUniqueId(), kit)));
            case ALREADY_USED -> plugin.getMessages().send(player, "kits.one-time-used");
            case NOT_ENOUGH_MONEY -> plugin.getMessages().send(player, "economy.not-enough-money");
            case INVENTORY_FULL -> plugin.getMessages().send(player, "kits.inventory-full");
            case UNKNOWN -> plugin.getMessages().send(player, "kits.unknown");
        }
    }

    private void create(Player player, String[] args) {
        if (!player.hasPermission("klassensmp.kit.admin")) {
            plugin.getMessages().send(player, "common.no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.getMessages().send(player, "kits.usage-create");
            return;
        }
        long cooldown = args.length > 2 ? Math.max(0, NumberUtil.parseInt(args[2], 0)) : 0L;
        if (plugin.getKitManager().createFromInventory(player, args[1], cooldown)) {
            plugin.getMessages().send(player, "kits.created", "%kit%", args[1]);
        } else {
            plugin.getMessages().send(player, "kits.invalid-name");
        }
    }

    private void delete(Player player, String[] args) {
        if (!player.hasPermission("klassensmp.kit.admin")) {
            plugin.getMessages().send(player, "common.no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.getMessages().send(player, "kits.usage-delete");
            return;
        }
        if (plugin.getKitManager().delete(args[1])) {
            plugin.getMessages().send(player, "kits.deleted", "%kit%", args[1]);
        } else {
            plugin.getMessages().send(player, "kits.unknown");
        }
    }

    private void list(Player player) {
        List<Kit> kits = plugin.getKitManager().availableFor(player);
        if (kits.isEmpty()) {
            plugin.getMessages().send(player, "kits.none");
            return;
        }
        plugin.getMessages().send(player, "kits.list-header", "%count%", String.valueOf(kits.size()));
        for (Kit kit : kits) {
            long remaining = plugin.getKitManager().remainingCooldown(player.getUniqueId(), kit);
            plugin.getMessages().sendPlain(player, "kits.list-entry",
                    "%kit%", kit.name(),
                    "%display%", kit.displayName(),
                    "%status%", remaining == 0
                            ? plugin.getMessages().plain("kits.gui-available")
                            : plugin.getMessages().plain("kits.gui-cooldown",
                            "%time%", remaining == Long.MAX_VALUE ? "-" : TimeUtil.formatDuration(remaining)));
        }
    }

    @Override
    protected List<String> complete(CommandSender sender, String name, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            for (Kit kit : plugin.getKitManager().availableFor(player)) {
                options.add(kit.name());
            }
            options.add("liste");
            if (player.hasPermission("klassensmp.kit.admin")) {
                options.add("erstellen");
                options.add("loeschen");
                options.add("bearbeiten");
            }
            return options;
        }
        if (args.length == 2 && player.hasPermission("klassensmp.kit.admin")) {
            String first = args[0].toLowerCase(Locale.ROOT);
            if (first.startsWith("loesch") || first.startsWith("delete") || first.startsWith("bearbeit")) {
                return plugin.getKitManager().nameList();
            }
        }
        return List.of();
    }
}
