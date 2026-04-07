package de.swarp.command;

import de.swarp.annotations.RequiresPermission;
import de.swarp.annotations.WarpCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Scans a handler class for {@link WarpCommand}-annotated methods and
 * dispatches {@code /swarp <sub>} calls to the correct method.
 *
 * Reflection happens once at startup (scan), never on each command call —
 * so runtime overhead is a single HashMap lookup.
 */
public class CommandDispatcher {

    private record HandlerEntry(Method method, Object instance, WarpCommand meta) {}

    private final Map<String, HandlerEntry> handlers = new HashMap<>();
    private final Logger logger;

    public CommandDispatcher(Logger logger) {
        this.logger = logger;
    }

    /**
     * Scans the given object for {@link WarpCommand}-annotated methods and
     * registers them. Call once during plugin enable.
     */
    public void register(Object handler) {
        for (Method method : handler.getClass().getDeclaredMethods()) {
            WarpCommand meta = method.getAnnotation(WarpCommand.class);
            if (meta == null) continue;

            method.setAccessible(true);
            handlers.put(meta.value().toLowerCase(), new HandlerEntry(method, handler, meta));
            logger.info("[SWarp] Registered sub-command: /" + meta.value());
        }
    }

    /**
     * Routes a command invocation. Returns {@code false} if no handler found.
     */
    public boolean dispatch(Player sender, String sub, String[] args) {
        HandlerEntry entry = handlers.get(sub.toLowerCase());

        if (entry == null) {
            sender.sendMessage(Component.text("✗ Unknown sub-command. Try /swarp help", NamedTextColor.RED));
            return true;
        }

        // Permission check via @RequiresPermission or WarpCommand#permission
        String perm = resolvePermission(entry);
        if (!perm.isEmpty() && !sender.hasPermission(perm)) {
            sender.sendMessage(Component.text("✗ No permission.", NamedTextColor.RED));
            return true;
        }

        // Arg count check
        if (args.length < entry.meta().minArgs()) {
            sender.sendMessage(Component.text("Usage: /swarp " + entry.meta().usage(), NamedTextColor.YELLOW));
            return true;
        }

        try {
            entry.method().invoke(entry.instance(), sender, args);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error dispatching /" + sub, e);
            sender.sendMessage(Component.text("✗ Internal error executing command.", NamedTextColor.RED));
        }
        return true;
    }

    private String resolvePermission(HandlerEntry entry) {
        // Prefer @RequiresPermission if present on the method
        RequiresPermission rp = entry.method().getAnnotation(RequiresPermission.class);
        if (rp != null) return rp.value();
        return entry.meta().permission();
    }

    public void sendHelp(Player player) {
        player.sendMessage(Component.text("─── SWarp Commands ───", NamedTextColor.GOLD));
        handlers.values().stream()
                .sorted((a, b) -> a.meta().value().compareTo(b.meta().value()))
                .forEach(entry -> player.sendMessage(
                        Component.text("  /swarp " + entry.meta().usage(), NamedTextColor.YELLOW)
                ));
    }
}
