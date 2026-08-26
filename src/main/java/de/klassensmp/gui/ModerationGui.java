package de.klassensmp.gui;

import de.klassensmp.KlassenSMP;
import de.klassensmp.model.PlayerData;
import de.klassensmp.util.ItemBuilder;
import de.klassensmp.util.TimeUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Moderationsmenue fuer einen einzelnen Spieler.
 *
 * <p>Jede Aktion prueft die zugehoerige Permission erneut - ein Klick allein
 * berechtigt nie zu einer Massnahme.</p>
 */
public final class ModerationGui extends Gui {

    private final Player target;

    public ModerationGui(KlassenSMP plugin, Player target) {
        super(plugin, plugin.getMessages().plain("moderation.gui-title", "%player%", target.getName()), 5);
        this.target = target;
    }

    @Override
    protected void build(Player staff) {
        PlayerData data = plugin.getPlayerDataManager().get(target.getUniqueId());

        set(4, new ItemBuilder(Material.PLAYER_HEAD)
                .name("&a" + target.getName())
                .lore(plugin.getMessages().list("moderation.gui-head-lore",
                        "%rank%", plugin.getRankManager().getRank(target).displayName(),
                        "%world%", target.getWorld().getName(),
                        "%health%", String.valueOf(Math.round(target.getHealth())),
                        "%food%", String.valueOf(target.getFoodLevel()),
                        "%gamemode%", target.getGameMode().name(),
                        "%playtime%", data == null ? "-" : TimeUtil.formatPlaytime(data.getTotalPlaytime()),
                        "%warnings%", String.valueOf(plugin.getModerationManager().warningCount(target.getUniqueId()))))
                .skullOwner(target)
                .build());

        action(staff, 19, Material.CHEST, "moderation.gui-inventory", "klassensmp.invsee",
                () -> openLater(staff, target.getInventory()));

        action(staff, 20, Material.ENDER_CHEST, "moderation.gui-enderchest", "klassensmp.endersee",
                () -> openLater(staff, target.getEnderChest()));

        action(staff, 21, Material.COMPASS, "moderation.gui-teleport", "klassensmp.teleport", () -> {
            closeLater(staff);
            plugin.getTeleportManager().teleportInstant(staff, target.getLocation(), "moderation.teleported");
        });

        action(staff, 22, Material.PACKED_ICE, "moderation.gui-freeze", "klassensmp.freeze", () -> {
            boolean frozen = plugin.getFreezeManager().toggle(target);
            plugin.getMessages().send(staff, frozen ? "moderation.freeze-on" : "moderation.freeze-off",
                    "%player%", target.getName());
            refresh(staff);
        });

        action(staff, 23, Material.ENDER_EYE, "moderation.gui-vanish", "klassensmp.vanish", () -> {
            boolean vanished = plugin.getVanishManager().toggle(target);
            plugin.getMessages().send(staff, vanished ? "moderation.vanish-on" : "moderation.vanish-off",
                    "%player%", target.getName());
            refresh(staff);
        });

        action(staff, 24, Material.PAPER, "moderation.gui-stats", "klassensmp.stats.others", () -> {
            if (data != null) {
                openLater(staff, new StatsGui(plugin, data));
            }
        });

        action(staff, 29, Material.YELLOW_WOOL, "moderation.gui-warn", "klassensmp.warn", () -> {
            closeLater(staff);
            plugin.getModerationManager().warn(target.getUniqueId(), target.getName(),
                    plugin.getMessages().plain("moderation.gui-default-reason"), staff.getName());
        });

        action(staff, 30, Material.ORANGE_WOOL, "moderation.gui-mute", "klassensmp.mute", () -> {
            closeLater(staff);
            long duration = plugin.getConfigManager().duration("moderation.gui-mute-minutes", 30L) * 60_000L;
            plugin.getModerationManager().mute(target.getUniqueId(), target.getName(),
                    plugin.getMessages().plain("moderation.gui-default-reason"), staff.getName(), duration);
        });

        action(staff, 31, Material.RED_WOOL, "moderation.gui-kick", "klassensmp.kick", () -> {
            closeLater(staff);
            plugin.getModerationManager().kick(target,
                    plugin.getMessages().plain("moderation.gui-default-reason"), staff.getName());
        });

        action(staff, 32, Material.BARRIER, "moderation.gui-ban", "klassensmp.ban", () -> {
            List<String> details = plugin.getMessages().list("moderation.gui-ban-confirm-lore",
                    "%player%", target.getName());
            openLater(staff, new ConfirmGui(plugin,
                    plugin.getMessages().plain("moderation.gui-ban-confirm", "%player%", target.getName()),
                    details,
                    confirmed -> plugin.getModerationManager().ban(target.getUniqueId(), target.getName(),
                            plugin.getMessages().plain("moderation.gui-default-reason"), staff.getName(), 0L),
                    cancelled -> new ModerationGui(plugin, target).open(staff)));
        });

        set(40, backButton(), event -> openLater(staff, new PlayerListGui(plugin)));
        fillEmpty();
    }

    /** Setzt einen Menuepunkt, der nur mit passender Permission nutzbar ist. */
    private void action(Player staff, int slot, Material material, String key, String permission, Runnable runnable) {
        boolean allowed = staff.hasPermission(permission);
        ItemStack item = new ItemBuilder(allowed ? material : Material.GRAY_DYE)
                .name(plugin.getMessages().plain(key))
                .lore(allowed
                        ? plugin.getMessages().list(key + "-lore")
                        : List.of(plugin.getMessages().plain("common.no-permission-short")))
                .hideAttributes()
                .build();
        set(slot, item, event -> {
            if (!staff.hasPermission(permission)) {
                plugin.getMessages().send(staff, "common.no-permission");
                return;
            }
            runnable.run();
        });
    }
}
