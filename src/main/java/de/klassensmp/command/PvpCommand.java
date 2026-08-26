package de.klassensmp.command;

import de.klassensmp.KlassenSMP;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** PvP ein- und ausschalten ({@code /pvp}). */
public final class PvpCommand extends BaseCommand {

    public PvpCommand(KlassenSMP plugin) {
        super(plugin, "klassensmp.pvp", true);
    }

    @Override
    protected void execute(CommandSender sender, String name, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        if (plugin.getConfigManager().bool("pvp.force-enabled", false)) {
            plugin.getMessages().send(player, "pvp.forced");
            return;
        }
        if (plugin.getPvpManager().isTagged(player)) {
            plugin.getMessages().send(player, "pvp.combat-blocked",
                    "%time%", plugin.getPvpManager().remainingTagFormatted(player));
            return;
        }
        boolean enabled = plugin.getPvpManager().toggle(player);
        plugin.getMessages().send(player, enabled ? "pvp.enabled" : "pvp.disabled");
    }
}
