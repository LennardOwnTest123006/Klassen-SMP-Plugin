package de.klassensmp.command;

import de.klassensmp.KlassenSMP;
import de.klassensmp.economy.TransferResult;
import de.klassensmp.gui.BaltopGui;
import de.klassensmp.model.PlayerData;
import de.klassensmp.util.NumberUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Economy-Befehle: {@code /money}, {@code /pay}, {@code /baltop}, {@code /bank}, {@code /eco}. */
public final class EconomyCommands extends BaseCommand {

    public EconomyCommands(KlassenSMP plugin) {
        super(plugin, null, false);
    }

    @Override
    protected void execute(CommandSender sender, String name, String[] args) {
        switch (name) {
            case "pay" -> pay(sender, args);
            case "baltop" -> baltop(sender);
            case "bank" -> bank(sender, args);
            case "eco" -> admin(sender, args);
            default -> balance(sender, args);
        }
    }

    private void balance(CommandSender sender, String[] args) {
        if (!sender.hasPermission("klassensmp.economy")) {
            plugin.getMessages().send(sender, "common.no-permission");
            return;
        }
        PlayerData data;
        if (args.length > 0) {
            if (!sender.hasPermission("klassensmp.economy.others")) {
                plugin.getMessages().send(sender, "common.no-permission");
                return;
            }
            data = plugin.getPlayerDataManager().findByName(args[0]);
            if (data == null) {
                plugin.getMessages().send(sender, "common.player-not-found", "%player%", args[0]);
                return;
            }
        } else {
            Player player = asPlayer(sender);
            if (player == null) {
                plugin.getMessages().send(sender, "common.player-only");
                return;
            }
            data = plugin.getPlayerDataManager().get(player.getUniqueId());
        }
        if (data == null) {
            plugin.getMessages().send(sender, "common.data-not-loaded");
            return;
        }
        plugin.getMessages().send(sender, "economy.balance",
                "%player%", data.getName(),
                "%money%", plugin.getEconomyManager().format(data.getMoney()),
                "%bank%", plugin.getEconomyManager().format(data.getBank()));
    }

    private void pay(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            plugin.getMessages().send(sender, "common.player-only");
            return;
        }
        if (!player.hasPermission("klassensmp.economy.pay")) {
            plugin.getMessages().send(player, "common.no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.getMessages().send(player, "economy.usage-pay");
            return;
        }
        double amount = NumberUtil.parseAmount(args[1]);
        if (amount <= 0) {
            plugin.getMessages().send(player, "economy.invalid-amount");
            return;
        }
        UUID target = plugin.getPlayerDataManager().findUuidByName(args[0]);
        if (target == null) {
            plugin.getMessages().send(player, "common.player-not-found", "%player%", args[0]);
            return;
        }

        TransferResult result = plugin.getEconomyManager().transfer(player.getUniqueId(), target, amount);
        PlayerData targetData = plugin.getPlayerDataManager().get(target);
        String targetName = targetData == null ? args[0] : targetData.getName();

        switch (result) {
            case SUCCESS -> {
                plugin.getMessages().send(player, "economy.pay-sent",
                        "%player%", targetName, "%money%", plugin.getEconomyManager().format(amount));
                Player online = plugin.getServer().getPlayer(target);
                if (online != null) {
                    plugin.getMessages().send(online, "economy.pay-received",
                            "%player%", player.getName(), "%money%", plugin.getEconomyManager().format(amount));
                }
            }
            case NOT_ENOUGH_MONEY -> plugin.getMessages().send(player, "economy.not-enough-money");
            case SAME_PLAYER -> plugin.getMessages().send(player, "economy.pay-self");
            case BELOW_MINIMUM -> plugin.getMessages().send(player, "economy.below-minimum",
                    "%minimum%", plugin.getEconomyManager().format(
                            plugin.getConfigManager().number("economy.minimum-payment", 1.0D)));
            case TARGET_LIMIT_REACHED -> plugin.getMessages().send(player, "economy.target-limit");
            case UNKNOWN_TARGET -> plugin.getMessages().send(player, "common.player-not-found", "%player%", args[0]);
            case INVALID_AMOUNT -> plugin.getMessages().send(player, "economy.invalid-amount");
        }
    }

    private void baltop(CommandSender sender) {
        if (!sender.hasPermission("klassensmp.economy")) {
            plugin.getMessages().send(sender, "common.no-permission");
            return;
        }
        Player player = asPlayer(sender);
        if (player != null && plugin.getConfigManager().bool("economy.baltop-gui", true)) {
            new BaltopGui(plugin).open(player);
            return;
        }
        List<PlayerData> top = plugin.getPlayerDataManager().topBalances(10);
        plugin.getMessages().send(sender, "economy.baltop-header");
        int position = 1;
        for (PlayerData data : top) {
            plugin.getMessages().sendPlain(sender, "economy.baltop-entry",
                    "%position%", String.valueOf(position++),
                    "%player%", data.getName(),
                    "%money%", plugin.getEconomyManager().format(data.getMoney() + data.getBank()));
        }
    }

    private void bank(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            plugin.getMessages().send(sender, "common.player-only");
            return;
        }
        if (!player.hasPermission("klassensmp.economy.bank")) {
            plugin.getMessages().send(player, "common.no-permission");
            return;
        }
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        if (data == null) {
            plugin.getMessages().send(player, "common.data-not-loaded");
            return;
        }
        if (args.length < 2) {
            plugin.getMessages().send(player, "economy.bank-info",
                    "%money%", plugin.getEconomyManager().format(data.getMoney()),
                    "%bank%", plugin.getEconomyManager().format(data.getBank()));
            return;
        }
        double amount = NumberUtil.parseAmount(args[1]);
        if (amount <= 0) {
            plugin.getMessages().send(player, "economy.invalid-amount");
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "einzahlen", "deposit" -> {
                if (plugin.getEconomyManager().depositToBank(player.getUniqueId(), amount)) {
                    plugin.getMessages().send(player, "economy.bank-deposit",
                            "%money%", plugin.getEconomyManager().format(amount));
                } else {
                    plugin.getMessages().send(player, "economy.not-enough-money");
                }
            }
            case "abheben", "withdraw" -> {
                if (plugin.getEconomyManager().withdrawFromBank(player.getUniqueId(), amount)) {
                    plugin.getMessages().send(player, "economy.bank-withdraw",
                            "%money%", plugin.getEconomyManager().format(amount));
                } else {
                    plugin.getMessages().send(player, "economy.bank-not-enough");
                }
            }
            default -> plugin.getMessages().send(player, "economy.usage-bank");
        }
    }

    /** Adminbefehl zum Setzen, Geben und Abziehen von Geld. */
    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("klassensmp.economy.admin")) {
            plugin.getMessages().send(sender, "common.no-permission");
            return;
        }
        if (args.length < 3) {
            plugin.getMessages().send(sender, "economy.usage-eco");
            return;
        }
        UUID target = plugin.getPlayerDataManager().findUuidByName(args[1]);
        PlayerData data = target == null ? null : plugin.getPlayerDataManager().get(target);
        if (data == null) {
            plugin.getMessages().send(sender, "common.player-not-found", "%player%", args[1]);
            return;
        }
        double amount = NumberUtil.parseAmount(args[2]);
        if (amount <= 0 && !args[0].equalsIgnoreCase("set")) {
            plugin.getMessages().send(sender, "economy.invalid-amount");
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give", "geben" -> {
                plugin.getEconomyManager().deposit(target, amount);
                plugin.getMessages().send(sender, "economy.admin-give",
                        "%player%", data.getName(), "%money%", plugin.getEconomyManager().format(amount));
            }
            case "take", "nehmen" -> {
                if (plugin.getEconomyManager().withdraw(target, amount)) {
                    plugin.getMessages().send(sender, "economy.admin-take",
                            "%player%", data.getName(), "%money%", plugin.getEconomyManager().format(amount));
                } else {
                    plugin.getMessages().send(sender, "economy.not-enough-money");
                }
            }
            case "set", "setzen" -> {
                double value = NumberUtil.parseAmount(args[2]);
                plugin.getEconomyManager().setBalance(target, Math.max(0.0D, value < 0 ? 0 : value));
                plugin.getMessages().send(sender, "economy.admin-set",
                        "%player%", data.getName(),
                        "%money%", plugin.getEconomyManager().format(plugin.getEconomyManager().getBalance(target)));
            }
            default -> plugin.getMessages().send(sender, "economy.usage-eco");
        }
        plugin.getPlayerDataManager().save(data);
    }

    @Override
    protected List<String> complete(CommandSender sender, String name, String[] args) {
        switch (name) {
            case "pay" -> {
                if (args.length == 1) {
                    return visiblePlayerNames(sender);
                }
            }
            case "bank" -> {
                if (args.length == 1) {
                    return List.of("einzahlen", "abheben");
                }
            }
            case "eco" -> {
                if (!sender.hasPermission("klassensmp.economy.admin")) {
                    return List.of();
                }
                if (args.length == 1) {
                    return List.of("give", "take", "set");
                }
                if (args.length == 2) {
                    return knownPlayerNames();
                }
            }
            case "money", "balance" -> {
                if (args.length == 1 && sender.hasPermission("klassensmp.economy.others")) {
                    return knownPlayerNames();
                }
            }
            default -> {
                return List.of();
            }
        }
        return List.of();
    }
}
