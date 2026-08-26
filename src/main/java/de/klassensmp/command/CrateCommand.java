package de.klassensmp.command;

import de.klassensmp.KlassenSMP;
import de.klassensmp.gui.CrateGui;
import de.klassensmp.gui.CratePreviewGui;
import de.klassensmp.model.Crate;
import de.klassensmp.model.CrateReward;
import de.klassensmp.util.NumberUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Crates oeffnen und Schluessel vergeben ({@code /crate}). */
public final class CrateCommand extends BaseCommand {

    public CrateCommand(KlassenSMP plugin) {
        super(plugin, "klassensmp.crate", false);
    }

    @Override
    protected void execute(CommandSender sender, String name, String[] args) {
        if (args.length == 0) {
            list(sender);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "oeffnen", "open" -> open(sender, args);
            case "vorschau", "preview" -> preview(sender, args);
            case "key", "schluessel" -> giveKey(sender, args);
            case "liste", "list" -> list(sender);
            default -> plugin.getMessages().send(sender, "crates.usage");
        }
    }

    private void list(CommandSender sender) {
        List<Crate> crates = plugin.getCrateManager().all();
        if (crates.isEmpty()) {
            plugin.getMessages().send(sender, "crates.none");
            return;
        }
        plugin.getMessages().send(sender, "crates.list-header", "%count%", String.valueOf(crates.size()));
        Player player = asPlayer(sender);
        for (Crate crate : crates) {
            int keys = player == null ? 0 : plugin.getCrateManager().countKeys(player, crate);
            plugin.getMessages().sendPlain(sender, "crates.list-entry",
                    "%crate%", crate.id(),
                    "%display%", crate.displayName(),
                    "%keys%", String.valueOf(keys),
                    "%rewards%", String.valueOf(crate.rewards().size()));
        }
    }

    private void open(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            plugin.getMessages().send(sender, "common.player-only");
            return;
        }
        if (args.length < 2) {
            plugin.getMessages().send(player, "crates.usage");
            return;
        }
        Crate crate = plugin.getCrateManager().get(args[1]);
        if (crate == null) {
            plugin.getMessages().send(player, "crates.unknown", "%crate%", args[1]);
            return;
        }
        if (!plugin.getCrateManager().consumeKey(player, crate)) {
            plugin.getMessages().send(player, "crates.no-key", "%crate%", crate.displayName());
            return;
        }
        CrateReward reward = plugin.getCrateManager().randomReward(crate);
        new CrateGui(plugin, crate, reward).play(player);
    }

    private void preview(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            plugin.getMessages().send(sender, "common.player-only");
            return;
        }
        if (args.length < 2) {
            plugin.getMessages().send(player, "crates.usage");
            return;
        }
        Crate crate = plugin.getCrateManager().get(args[1]);
        if (crate == null) {
            plugin.getMessages().send(player, "crates.unknown", "%crate%", args[1]);
            return;
        }
        new CratePreviewGui(plugin, crate).open(player);
    }

    private void giveKey(CommandSender sender, String[] args) {
        if (!sender.hasPermission("klassensmp.crate.admin")) {
            plugin.getMessages().send(sender, "common.no-permission");
            return;
        }
        if (args.length < 3) {
            plugin.getMessages().send(sender, "crates.usage-key");
            return;
        }
        Crate crate = plugin.getCrateManager().get(args[1]);
        if (crate == null) {
            plugin.getMessages().send(sender, "crates.unknown", "%crate%", args[1]);
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            plugin.getMessages().send(sender, "common.player-not-found", "%player%", args[2]);
            return;
        }
        int amount = args.length > 3 ? NumberUtil.clamp(NumberUtil.parseInt(args[3], 1), 1, 64) : 1;

        ItemStack key = plugin.getCrateManager().createKey(crate, amount);
        Map<Integer, ItemStack> overflow = target.getInventory().addItem(key);
        for (ItemStack rest : overflow.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), rest);
        }
        plugin.getMessages().send(sender, "crates.key-given",
                "%amount%", String.valueOf(amount), "%player%", target.getName(), "%crate%", crate.displayName());
        plugin.getMessages().send(target, "crates.key-received",
                "%amount%", String.valueOf(amount), "%crate%", crate.displayName());
    }

    @Override
    protected List<String> complete(CommandSender sender, String name, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("oeffnen", "vorschau", "liste"));
            if (sender.hasPermission("klassensmp.crate.admin")) {
                options.add("key");
            }
            return options;
        }
        if (args.length == 2) {
            return plugin.getCrateManager().nameList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("key")) {
            return visiblePlayerNames(sender);
        }
        return List.of();
    }
}
