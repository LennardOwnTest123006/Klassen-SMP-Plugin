package de.klassensmp.command;

import de.klassensmp.KlassenSMP;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Moderationsmodus ({@code /mod}). */
public final class StaffModeCommand extends BaseCommand {

    public StaffModeCommand(KlassenSMP plugin) {
        super(plugin, "klassensmp.mod", true);
    }

    @Override
    protected void execute(CommandSender sender, String name, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        boolean active = plugin.getStaffModeManager().toggle(player);
        plugin.getMessages().send(player, active ? "staffmode.enabled" : "staffmode.disabled");
    }
}
