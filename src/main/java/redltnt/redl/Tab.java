package redltnt.redl;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import redltnt.redl.RedLTnt;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Tab implements TabCompleter {

    private final RedLTnt plugin;

    public Tab(RedLTnt plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            // Provide tab completion for the first argument
            List<String> completions = new ArrayList<>();
            completions.add("reload");
            completions.add("give");
            return completions.stream()
                    .filter(arg -> arg.startsWith(args[0]))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            // Provide tab completion for player names if "give" is specified
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.startsWith(args[1]))
                    .collect(Collectors.toList());
            // Provide tab completion for custom TNT keys if "give" is specified
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            // Provide tab completion for player names if "give" is specified
            Set<String> customTntKeys = plugin.getConfig().getConfigurationSection("config").getKeys(false);
            return customTntKeys.stream()
                    .filter(key -> key.startsWith(args[2]))
                    .collect(Collectors.toList());
        }

        // Return an empty list if no completions are found
        return new ArrayList<>();
    }
}
