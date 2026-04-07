package de.swarp.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Registered with Bukkit for the {@code /swarp} command.
 * Delegates all logic to {@link CommandDispatcher}.
 */
public class SwarpCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS =
            List.of("create", "delete", "tp", "list", "info", "mywarps", "help");

    private final CommandDispatcher dispatcher;

    public SwarpCommand(CommandDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /swarp.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            dispatcher.sendHelp(player);
            return true;
        }

        String[] subArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subArgs, 0, subArgs.length);
        return dispatcher.dispatch(player, args[0], subArgs);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return SUBS.stream().filter(s -> s.startsWith(partial)).toList();
        }
        return List.of();
    }
}
