package de.klassensmp.model;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Ein Kit mit Items, Cooldown und optionaler Permission.
 *
 * @param cooldownSeconds 0 = kein Cooldown
 * @param oneTime         Kit darf nur ein einziges Mal genutzt werden
 */
public record Kit(String name,
                  String displayName,
                  String icon,
                  String permission,
                  long cooldownSeconds,
                  boolean oneTime,
                  double price,
                  List<String> description,
                  List<ItemStack> items,
                  List<String> commands) {

    public boolean hasPermission() {
        return permission != null && !permission.isBlank();
    }
}
