package de.klassensmp.command;

import de.klassensmp.KlassenSMP;
import de.klassensmp.claim.ClaimManager;
import de.klassensmp.model.Claim;
import de.klassensmp.model.PlayerData;
import de.klassensmp.util.NumberUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Grundstuecke verwalten ({@code /claim}). */
public final class ClaimCommand extends BaseCommand {

    public ClaimCommand(KlassenSMP plugin) {
        super(plugin, "klassensmp.claims", true);
    }

    @Override
    protected void execute(CommandSender sender, String name, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        if (!plugin.getClaimManager().isEnabled()) {
            plugin.getMessages().send(player, "claims.disabled");
            return;
        }
        if (args.length == 0) {
            for (String line : plugin.getMessages().list("claims.help")) {
                player.sendMessage(line);
            }
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "erstellen", "create" -> create(player, args);
            case "loeschen", "delete", "unclaim" -> delete(player);
            case "info" -> info(player);
            case "liste", "list" -> list(player);
            case "vertrauen", "trust" -> trust(player, args, true);
            case "entfernen", "untrust" -> trust(player, args, false);
            default -> plugin.getMessages().send(player, "claims.unknown-subcommand");
        }
    }

    private void create(Player player, String[] args) {
        int radius = args.length > 1
                ? NumberUtil.parseInt(args[1], plugin.getConfigManager().integer("claims.default-radius", 15))
                : plugin.getConfigManager().integer("claims.default-radius", 15);
        if (radius < 1 || radius > 250) {
            plugin.getMessages().send(player, "claims.invalid-radius");
            return;
        }

        ClaimManager.CreateResult result = plugin.getClaimManager().create(player, player.getLocation(), radius,
                claim -> plugin.getMessages().send(player, "claims.created",
                        "%size%", claim.width() + "x" + claim.depth(),
                        "%blocks%", String.valueOf(claim.area())));

        switch (result) {
            case SUCCESS -> {
                // Erfolgsmeldung kommt aus dem Callback, sobald gespeichert wurde.
            }
            case DISABLED -> plugin.getMessages().send(player, "claims.disabled");
            case WORLD_DISABLED -> plugin.getMessages().send(player, "claims.world-disabled");
            case TOO_SMALL -> plugin.getMessages().send(player, "claims.too-small",
                    "%min%", String.valueOf(plugin.getConfigManager().integer("claims.min-size", 5)));
            case TOO_LARGE -> plugin.getMessages().send(player, "claims.too-large",
                    "%max%", String.valueOf(plugin.getConfigManager().integer("claims.max-size", 100)));
            case OVERLAPS -> plugin.getMessages().send(player, "claims.overlaps");
            case LIMIT_REACHED -> plugin.getMessages().send(player, "claims.limit-reached",
                    "%max%", String.valueOf(plugin.getConfigManager().integer("claims.max-per-player", 3)));
            case BLOCK_LIMIT_REACHED -> plugin.getMessages().send(player, "claims.block-limit",
                    "%max%", String.valueOf(plugin.getConfigManager().integer("claims.max-blocks-per-player", 40000)));
        }
    }

    private void delete(Player player) {
        Claim claim = plugin.getClaimManager().getClaimAt(player.getLocation());
        if (claim == null) {
            plugin.getMessages().send(player, "claims.none-here");
            return;
        }
        if (!claim.getOwner().equals(player.getUniqueId()) && !player.hasPermission("klassensmp.claims.admin")) {
            plugin.getMessages().send(player, "claims.not-owner");
            return;
        }
        plugin.getClaimManager().delete(claim);
        plugin.getMessages().send(player, "claims.deleted");
    }

    private void info(Player player) {
        Claim claim = plugin.getClaimManager().getClaimAt(player.getLocation());
        if (claim == null) {
            plugin.getMessages().send(player, "claims.none-here");
            return;
        }
        PlayerData owner = plugin.getPlayerDataManager().get(claim.getOwner());
        List<String> memberNames = new ArrayList<>();
        for (UUID member : claim.getMembers()) {
            PlayerData data = plugin.getPlayerDataManager().get(member);
            memberNames.add(data == null ? member.toString().substring(0, 8) : data.getName());
        }
        for (String line : plugin.getMessages().list("claims.info",
                "%owner%", owner == null ? "?" : owner.getName(),
                "%size%", claim.width() + "x" + claim.depth(),
                "%blocks%", String.valueOf(claim.area()),
                "%world%", claim.getWorld(),
                "%members%", memberNames.isEmpty()
                        ? plugin.getMessages().plain("common.none")
                        : String.join(", ", memberNames))) {
            player.sendMessage(line);
        }
    }

    private void list(Player player) {
        List<Claim> claims = plugin.getClaimManager().claimsOf(player.getUniqueId());
        if (claims.isEmpty()) {
            plugin.getMessages().send(player, "claims.none");
            return;
        }
        plugin.getMessages().send(player, "claims.list-header",
                "%count%", String.valueOf(claims.size()),
                "%blocks%", String.valueOf(plugin.getClaimManager().blocksUsed(player.getUniqueId())));
        for (Claim claim : claims) {
            plugin.getMessages().sendPlain(player, "claims.list-entry",
                    "%id%", String.valueOf(claim.getId()),
                    "%world%", claim.getWorld(),
                    "%x%", String.valueOf(claim.getMinX()),
                    "%z%", String.valueOf(claim.getMinZ()),
                    "%size%", claim.width() + "x" + claim.depth());
        }
    }

    private void trust(Player player, String[] args, boolean add) {
        if (args.length < 2) {
            plugin.getMessages().send(player, add ? "claims.usage-trust" : "claims.usage-untrust");
            return;
        }
        Claim claim = plugin.getClaimManager().getClaimAt(player.getLocation());
        if (claim == null) {
            plugin.getMessages().send(player, "claims.none-here");
            return;
        }
        if (!claim.getOwner().equals(player.getUniqueId()) && !player.hasPermission("klassensmp.claims.admin")) {
            plugin.getMessages().send(player, "claims.not-owner");
            return;
        }
        UUID target = plugin.getPlayerDataManager().findUuidByName(args[1]);
        if (target == null) {
            plugin.getMessages().send(player, "common.player-not-found", "%player%", args[1]);
            return;
        }
        if (add) {
            plugin.getClaimManager().trust(claim, target);
            plugin.getMessages().send(player, "claims.trusted", "%player%", args[1]);
        } else {
            plugin.getClaimManager().untrust(claim, target);
            plugin.getMessages().send(player, "claims.untrusted", "%player%", args[1]);
        }
    }

    @Override
    protected List<String> complete(CommandSender sender, String name, String[] args) {
        if (args.length == 1) {
            return List.of("erstellen", "loeschen", "info", "liste", "vertrauen", "entfernen");
        }
        if (args.length == 2) {
            String first = args[0].toLowerCase(Locale.ROOT);
            if (first.startsWith("vertrau") || first.startsWith("trust")) {
                return visiblePlayerNames(sender);
            }
            if (first.startsWith("entfern") || first.startsWith("untrust")) {
                Player player = asPlayer(sender);
                Claim claim = player == null ? null : plugin.getClaimManager().getClaimAt(player.getLocation());
                if (claim != null) {
                    List<String> names = new ArrayList<>();
                    for (UUID member : claim.getMembers()) {
                        PlayerData data = plugin.getPlayerDataManager().get(member);
                        if (data != null) {
                            names.add(data.getName());
                        }
                    }
                    return names;
                }
            }
            if (first.startsWith("erstell") || first.startsWith("create")) {
                return List.of("10", "15", "25", "50");
            }
        }
        return List.of();
    }
}
