package de.klassensmp.model;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Eine moegliche Belohnung aus einer Crate.
 *
 * @param chance relatives Gewicht (nicht zwingend Prozent)
 */
public record CrateReward(String displayName,
                          ItemStack item,
                          double money,
                          int experience,
                          List<String> commands,
                          double chance,
                          boolean broadcast) {
}
