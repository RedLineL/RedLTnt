package redltnt.redl.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import redltnt.redl.RedLTnt;

public class reloadCommand implements CommandExecutor {
    private final RedLTnt plugin;

    public reloadCommand(RedLTnt plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (plugin == null) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.plugin-not-initialized")));
                return true; // Plugin not initialized, stop here
            }

            if (args.length > 0) {
                if (args[0].equals("reload")) {
                    if (!sender.hasPermission("rltnt.command.reload")) {
                        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.no-permission")));
                        return true; // Permission denied, stop here
                    }
                    plugin.reloadConfig(); // Reload the plugin's configuration
                    plugin.reloadRecipes(); // Reload custom TNT recipes

                    String reloadSuccessMessage = plugin.getConfig().getString("messages.reload-success");
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', reloadSuccessMessage));
                    return true; // Command executed successfully
                } else if (args[0].equalsIgnoreCase("give")) {
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.no-permission")));
                        return true;
                    }

                    Player player = (Player) sender;

                    // Check permission
                    if (!player.hasPermission("rltnt.command.give")) {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.no-permission")));
                        return true;
                    }

                    // Check command syntax
                    if (args.length < 4) {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.give-usage")));
                        return true;
                    }

                    Player target = Bukkit.getPlayer(args[1]);
                    if (target == null || !target.isOnline()) {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.player-not-found")));
                        return true;
                    }

                    String key = args[2]; // TNT name from config
                    int amount;
                    try {
                        amount = Integer.parseInt(args[3]);
                        if (amount <= 0) {
                            player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.positive-amount")));
                            return true;
                        }
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.invalid-amount")));
                        return true;
                    }

                    // Get custom TNT ItemStack
                    ItemStack customTnt = plugin.getCustomTntItem(key);
                    if (customTnt == null) {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.tnt-not-exist").replace("%key%", key)));
                        return true;
                    }

                    customTnt.setAmount(amount);

                    // Add custom TNT to player's inventory
                    target.getInventory().addItem(customTnt);

                    // Inform sender and target about the action
                    String givenMessage = plugin.getConfig().getString("messages.given")
                            .replace("%amount%", String.valueOf(amount))
                            .replace("%tnt_name%", customTnt.getItemMeta().getDisplayName())
                            .replace("%player%", target.getName());
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', givenMessage));

                    String receivedMessage = plugin.getConfig().getString("messages.received")
                            .replace("%amount%", String.valueOf(amount))
                            .replace("%tnt_name%", customTnt.getItemMeta().getDisplayName())
                            .replace("%player%", player.getName());
                    target.sendMessage(ChatColor.translateAlternateColorCodes('&', receivedMessage));

                    return true;
                }
            }

            return false; // Incorrect usage, show command usage
        } catch (Exception e) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.plugin-not-initialized")));
            e.printStackTrace(); // Print the stack trace to the server log
            return true;
        }
    }
}