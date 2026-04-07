package de.swarp.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.swarp.annotations.RequiresPermission;
import de.swarp.annotations.WarpCommand;
import de.swarp.factory.WarpTask;
import de.swarp.factory.WarpWorkerFactory;
import de.swarp.model.PlayerWarp;
import de.swarp.service.WarpCacheService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/**
 * Contains all /swarp sub-commands as annotated methods.
 * Discovered and routed automatically by {@link CommandDispatcher}.
 */
@Singleton
public class SwarpCommandHandler {

    private static final String NAME_PATTERN = "^[a-zA-Z0-9_]{3,24}$";

    private final WarpWorkerFactory workerFactory;
    private final WarpCacheService cache;

    @Inject
    public SwarpCommandHandler(WarpWorkerFactory workerFactory, WarpCacheService cache) {
        this.workerFactory = workerFactory;
        this.cache = cache;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // /swarp create <name>
    // ──────────────────────────────────────────────────────────────────────────

    @WarpCommand(value = "create", minArgs = 1, usage = "create <name>", permission = "swarp.create")
    public void create(Player player, String[] args) {
        String name = args[0];

        if (!name.matches(NAME_PATTERN)) {
            player.sendMessage(Component.text(
                    "✗ Invalid name. Use 3–24 alphanumeric characters.", NamedTextColor.RED));
            return;
        }

        workerFactory.submit(WarpTask.create(player, name));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // /swarp delete <name>
    // ──────────────────────────────────────────────────────────────────────────

    @WarpCommand(value = "delete", minArgs = 1, usage = "delete <name>")
    @RequiresPermission("swarp.delete")
    public void delete(Player player, String[] args) {
        String name = args[0];
        Optional<PlayerWarp> warp = cache.getByOwnerAndName(player.getUniqueId(), name);

        // Admins can delete any warp
        if (warp.isEmpty() && player.hasPermission("swarp.admin")) {
            warp = cache.getPublicByName(name);
        }

        if (warp.isEmpty()) {
            player.sendMessage(Component.text("✗ No warp named \"" + name + "\" found.", NamedTextColor.RED));
            return;
        }

        workerFactory.submit(WarpTask.delete(player, warp.get()));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // /swarp tp <name>
    // ──────────────────────────────────────────────────────────────────────────

    @WarpCommand(value = "tp", minArgs = 1, usage = "tp <name>", permission = "swarp.teleport")
    public void teleport(Player player, String[] args) {
        String name = args[0];

        // Check own warps first, then global
        Optional<PlayerWarp> warp = cache.getByOwnerAndName(player.getUniqueId(), name);
        if (warp.isEmpty()) warp = cache.getPublicByName(name);

        if (warp.isEmpty()) {
            player.sendMessage(Component.text("✗ No public warp named \"" + name + "\" found.", NamedTextColor.RED));
            return;
        }

        workerFactory.submit(WarpTask.teleport(player, warp.get()));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // /swarp list [player]
    // ──────────────────────────────────────────────────────────────────────────

    @WarpCommand(value = "list", usage = "list [player]", permission = "swarp.list")
    public void list(Player player, String[] args) {
        List<PlayerWarp> warps = cache.getAllPublic();

        if (warps.isEmpty()) {
            player.sendMessage(Component.text("No public warps exist yet. Create one with /swarp create!", NamedTextColor.GRAY));
            return;
        }

        player.sendMessage(Component.text("─── Public Warps (" + warps.size() + ") ───", NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));

        warps.stream()
                .sorted((a, b) -> Long.compare(b.getVisits(), a.getVisits()))
                .forEach(w -> {
                    Component line = Component.text("  ✦ ", NamedTextColor.GOLD)
                            .append(Component.text(w.getName(), NamedTextColor.YELLOW)
                                    .decorate(TextDecoration.BOLD)
                                    .clickEvent(ClickEvent.runCommand("/swarp tp " + w.getName()))
                                    .hoverEvent(HoverEvent.showText(
                                            Component.text("Click to teleport!\n", NamedTextColor.GREEN)
                                            .append(Component.text("Owner: " + w.getOwnerName() + "\n", NamedTextColor.GRAY))
                                            .append(Component.text("Visits: " + w.getVisits(), NamedTextColor.AQUA))
                                    )))
                            .append(Component.text(" by " + w.getOwnerName(), NamedTextColor.GRAY))
                            .append(Component.text(" [" + w.getVisits() + " visits]", NamedTextColor.DARK_GRAY));
                    player.sendMessage(line);
                });
    }

    // ──────────────────────────────────────────────────────────────────────────
    // /swarp info <name>
    // ──────────────────────────────────────────────────────────────────────────

    @WarpCommand(value = "info", minArgs = 1, usage = "info <name>")
    public void info(Player player, String[] args) {
        Optional<PlayerWarp> warp = cache.getPublicByName(args[0]);

        if (warp.isEmpty()) {
            player.sendMessage(Component.text("✗ Warp not found.", NamedTextColor.RED));
            return;
        }

        PlayerWarp w = warp.get();
        player.sendMessage(Component.text("─── Warp Info ───", NamedTextColor.GOLD));
        player.sendMessage(info("Name",    w.getName()));
        player.sendMessage(info("Owner",   w.getOwnerName()));
        player.sendMessage(info("Visits",  String.valueOf(w.getVisits())));
        player.sendMessage(info("World",   w.getLocation().getWorld().getName()));
        player.sendMessage(info("Created", w.getCreatedAt().toString().substring(0, 10)));
        if (!w.getDescription().isEmpty()) {
            player.sendMessage(info("Desc", w.getDescription()));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // /swarp mywarps
    // ──────────────────────────────────────────────────────────────────────────

    @WarpCommand(value = "mywarps", usage = "mywarps")
    public void mywarps(Player player, String[] args) {
        List<PlayerWarp> warps = cache.getByOwner(player.getUniqueId());

        if (warps.isEmpty()) {
            player.sendMessage(Component.text(
                    "You have no warps yet. Use /swarp create <name> to make one!", NamedTextColor.GRAY));
            return;
        }

        player.sendMessage(Component.text("─── Your Warps (" + warps.size() + ") ───", NamedTextColor.GOLD));
        warps.forEach(w -> player.sendMessage(
                Component.text("  ✦ ", NamedTextColor.GOLD)
                .append(Component.text(w.getName(), NamedTextColor.YELLOW))
                .append(Component.text(" — " + w.getVisits() + " visits", NamedTextColor.GRAY))
                .append(Component.text("  [delete]", NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/swarp delete " + w.getName()))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to delete this warp", NamedTextColor.RED))))
        ));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helper
    // ──────────────────────────────────────────────────────────────────────────

    private Component info(String label, String value) {
        return Component.text("  " + label + ": ", NamedTextColor.GRAY)
                .append(Component.text(value, NamedTextColor.WHITE));
    }
}
