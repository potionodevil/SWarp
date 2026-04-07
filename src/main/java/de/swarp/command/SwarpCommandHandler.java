package de.swarp.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.swarp.annotations.RequiresPermission;
import de.swarp.annotations.WarpCommand;
import de.swarp.database.repository.WarpRepository;
import de.swarp.factory.WarpTask;
import de.swarp.factory.WarpWorkerFactory;
import de.swarp.guice.PluginConfig;
import de.swarp.model.PlayerWarp;
import de.swarp.model.WarpCategory;
import de.swarp.service.WarpCacheService;
import de.swarp.service.WarpCooldownService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

@Singleton
public class SwarpCommandHandler {

    private static final String NAME_PATTERN = "^[a-zA-Z0-9_]{3,24}$";

    private final WarpWorkerFactory workerFactory;
    private final WarpCacheService cache;
    private final WarpCooldownService cooldown;
    private final WarpRepository repository;
    private final PluginConfig config;
    private final JavaPlugin plugin;

    @Inject
    public SwarpCommandHandler(WarpWorkerFactory workerFactory,
                                WarpCacheService cache,
                                WarpCooldownService cooldown,
                                WarpRepository repository,
                                PluginConfig config,
                                JavaPlugin plugin) {
        this.workerFactory = workerFactory;
        this.cache = cache;
        this.cooldown = cooldown;
        this.repository = repository;
        this.config = config;
        this.plugin = plugin;
    }


    @WarpCommand(value = "create", minArgs = 1, usage = "create <n>", permission = "swarp.create")
    public void create(Player player, String[] args) {
        String name = args[0];
        if (!name.matches(NAME_PATTERN)) {
            player.sendMessage(Component.text("✗ Ungültiger Name. Nutze 3–24 alphanumerische Zeichen.", NamedTextColor.RED));
            return;
        }
        workerFactory.submit(WarpTask.create(player, name));
    }

    @WarpCommand(value = "delete", minArgs = 1, usage = "delete <n>")
    @RequiresPermission("swarp.delete")
    public void delete(Player player, String[] args) {
        Optional<PlayerWarp> warp = cache.getByOwnerAndName(player.getUniqueId(), args[0]);
        if (warp.isEmpty() && player.hasPermission("swarp.admin")) {
            warp = cache.getPublicByName(args[0]);
        }
        if (warp.isEmpty()) {
            player.sendMessage(Component.text("✗ Kein Warp namens \"" + args[0] + "\" gefunden.", NamedTextColor.RED));
            return;
        }
        workerFactory.submit(WarpTask.delete(player, warp.get()));
    }

    @WarpCommand(value = "tp", minArgs = 1, usage = "tp <n>", permission = "swarp.teleport")
    public void teleport(Player player, String[] args) {
        int cd = config.getInt("warps.cooldown", 10);
        int remaining = cooldown.getRemainingCooldown(player.getUniqueId(), cd);
        if (remaining > 0) {
            player.sendMessage(Component.text("✗ Cooldown! Warte noch " + remaining + " Sekunde(n).", NamedTextColor.RED));
            return;
        }

        Optional<PlayerWarp> warp = cache.getByOwnerAndName(player.getUniqueId(), args[0]);
        if (warp.isEmpty()) warp = cache.getPublicByName(args[0]);
        if (warp.isEmpty()) {
            player.sendMessage(Component.text("✗ Kein öffentlicher Warp \"" + args[0] + "\" gefunden.", NamedTextColor.RED));
            return;
        }

        cooldown.registerTeleport(player.getUniqueId());
        workerFactory.submit(WarpTask.teleport(player, warp.get()));
    }

    @WarpCommand(value = "list", usage = "list [category]", permission = "swarp.list")
    public void list(Player player, String[] args) {
        List<PlayerWarp> warps;

        if (args.length > 0) {
            WarpCategory cat = WarpCategory.fromString(args[0]);
            warps = cache.getAllPublic().stream()
                    .filter(w -> w.category() == cat)
                    .toList();
            player.sendMessage(Component.text("─── " + cat.icon + " " + cat.displayName + " Warps ───", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        } else {
            warps = cache.getAllPublic();
            player.sendMessage(Component.text("─── Öffentliche Warps (" + warps.size() + ") ───", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            player.sendMessage(buildCategoryBar());
        }

        if (warps.isEmpty()) {
            player.sendMessage(Component.text("Keine Warps gefunden.", NamedTextColor.GRAY));
            return;
        }

        warps.stream()
                .sorted((a, b) -> Long.compare(b.visits(), a.visits()))
                .forEach(w -> player.sendMessage(
                        Component.text("  " + w.category().icon + " ", NamedTextColor.GRAY)
                        .append(Component.text(w.name(), NamedTextColor.YELLOW).decorate(TextDecoration.BOLD)
                                .clickEvent(ClickEvent.runCommand("/swarp tp " + w.name()))
                                .hoverEvent(HoverEvent.showText(
                                        Component.text("Klicken zum Teleportieren!\n", NamedTextColor.GREEN)
                                        .append(Component.text("Owner: " + w.ownerName() + "\n", NamedTextColor.GRAY))
                                        .append(Component.text("Besuche: " + w.visits(), NamedTextColor.AQUA))
                                )))
                        .append(Component.text(" by " + w.ownerName(), NamedTextColor.GRAY))
                        .append(Component.text(" [" + w.visits() + "]", NamedTextColor.DARK_GRAY))
                ));
    }

    @WarpCommand(value = "search", minArgs = 1, usage = "search <keyword>", permission = "swarp.list")
    public void search(Player player, String[] args) {
        String keyword = args[0].toLowerCase();
        List<PlayerWarp> results = cache.getAllPublic().stream()
                .filter(w -> w.name().toLowerCase().contains(keyword)
                        || w.description().toLowerCase().contains(keyword))
                .sorted((a, b) -> Long.compare(b.visits(), a.visits()))
                .limit(10)
                .toList();

        if (results.isEmpty()) {
            player.sendMessage(Component.text("✗ Keine Warps für \"" + keyword + "\" gefunden.", NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.text("─── Suchergebnisse: \"" + keyword + "\" (" + results.size() + ") ───", NamedTextColor.GOLD));
        results.forEach(w -> player.sendMessage(
                Component.text("  " + w.category().icon + " ", NamedTextColor.GRAY)
                .append(Component.text(w.name(), NamedTextColor.YELLOW)
                        .clickEvent(ClickEvent.runCommand("/swarp tp " + w.name())))
                .append(Component.text(" — " + w.ownerName(), NamedTextColor.GRAY))
        ));
    }


    @WarpCommand(value = "rename", minArgs = 2, usage = "rename <alt> <neu>")
    public void rename(Player player, String[] args) {
        if (!args[1].matches(NAME_PATTERN)) {
            player.sendMessage(Component.text("✗ Ungültiger Name.", NamedTextColor.RED));
            return;
        }
        Optional<PlayerWarp> warp = cache.getByOwnerAndName(player.getUniqueId(), args[0]);
        if (warp.isEmpty()) {
            player.sendMessage(Component.text("✗ Kein Warp namens \"" + args[0] + "\".", NamedTextColor.RED));
            return;
        }
        String newName = args[1];
        PlayerWarp updated = warp.get().withName(newName);
        CompletableFuture.runAsync(() -> {
            try {
                repository.updateName(updated.id(), newName);
                cache.remove(warp.get());
                cache.put(updated);
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        player.sendMessage(Component.text("✔ Warp umbenannt: ", NamedTextColor.GREEN)
                                .append(Component.text(args[0], NamedTextColor.YELLOW))
                                .append(Component.text(" → ", NamedTextColor.GRAY))
                                .append(Component.text(newName, NamedTextColor.GOLD))));
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Rename fehlgeschlagen", e);
            }
        });
    }

    @WarpCommand(value = "desc", minArgs = 2, usage = "desc <n> <text>")
    public void desc(Player player, String[] args) {
        Optional<PlayerWarp> warp = cache.getByOwnerAndName(player.getUniqueId(), args[0]);
        if (warp.isEmpty()) {
            player.sendMessage(Component.text("✗ Kein eigener Warp namens \"" + args[0] + "\".", NamedTextColor.RED));
            return;
        }
        String desc = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        if (desc.length() > 128) {
            player.sendMessage(Component.text("✗ Beschreibung max. 128 Zeichen.", NamedTextColor.RED));
            return;
        }
        PlayerWarp updated = warp.get().withDescription(desc);
        CompletableFuture.runAsync(() -> {
            try {
                repository.updateDescription(updated.id(), desc);
                cache.remove(warp.get());
                cache.put(updated);
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        player.sendMessage(Component.text("✔ Beschreibung gesetzt.", NamedTextColor.GREEN)));
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Desc update fehlgeschlagen", e);
            }
        });
    }


    @WarpCommand(value = "category", minArgs = 2, usage = "category <n> <shop|farm|pvp|base|public|other>")
    public void category(Player player, String[] args) {
        Optional<PlayerWarp> warp = cache.getByOwnerAndName(player.getUniqueId(), args[0]);
        if (warp.isEmpty()) {
            player.sendMessage(Component.text("✗ Kein eigener Warp namens \"" + args[0] + "\".", NamedTextColor.RED));
            return;
        }
        WarpCategory cat = WarpCategory.fromString(args[1]);
        PlayerWarp updated = warp.get().withCategory(cat);
        CompletableFuture.runAsync(() -> {
            try {
                repository.updateCategory(updated.id(), cat);
                cache.remove(warp.get());
                cache.put(updated);
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        player.sendMessage(Component.text("✔ Kategorie gesetzt: ", NamedTextColor.GREEN)
                                .append(Component.text(cat.icon + " " + cat.displayName, NamedTextColor.GOLD))));
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Category update fehlgeschlagen", e);
            }
        });
    }


    @WarpCommand(value = "expire", minArgs = 2, usage = "expire <n> <on|off>")
    public void expire(Player player, String[] args) {
        Optional<PlayerWarp> warp = cache.getByOwnerAndName(player.getUniqueId(), args[0]);
        if (warp.isEmpty()) {
            player.sendMessage(Component.text("✗ Kein eigener Warp namens \"" + args[0] + "\".", NamedTextColor.RED));
            return;
        }

        String toggle = args[1].toLowerCase();
        if (!toggle.equals("on") && !toggle.equals("off")) {
            player.sendMessage(Component.text("✗ Nutze: /swarp expire <n> <on|off>", NamedTextColor.RED));
            return;
        }

        boolean expires = toggle.equals("on");
        PlayerWarp updated = warp.get().withExpires(expires);

        CompletableFuture.runAsync(() -> {
            try {
                repository.updateExpires(updated.id(), expires);
                cache.remove(warp.get());
                cache.put(updated);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (expires) {
                        player.sendMessage(Component.text("✔ Warp \"" + updated.name() + "\" wird nach " +
                                config.getInt("warps.expire-days", 30) +
                                " Tagen Inaktivität automatisch gelöscht.", NamedTextColor.YELLOW));
                    } else {
                        player.sendMessage(Component.text("✔ Warp \"" + updated.name() + "\" läuft nicht mehr ab.", NamedTextColor.GREEN));
                    }
                });
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Expire toggle fehlgeschlagen", e);
            }
        });
    }

    @WarpCommand(value = "info", minArgs = 1, usage = "info <n>")
    public void info(Player player, String[] args) {
        Optional<PlayerWarp> warp = cache.getPublicByName(args[0]);
        if (warp.isEmpty()) {
            player.sendMessage(Component.text("✗ Warp nicht gefunden.", NamedTextColor.RED));
            return;
        }
        PlayerWarp w = warp.get();
        player.sendMessage(Component.text("─── Warp Info ───", NamedTextColor.GOLD));
        player.sendMessage(field("Name",       w.name()));
        player.sendMessage(field("Owner",      w.ownerName()));
        player.sendMessage(field("Kategorie",  w.category().icon + " " + w.category().displayName));
        player.sendMessage(field("Besuche",    String.valueOf(w.visits())));
        player.sendMessage(field("Welt",       w.location().getWorld().getName()));
        player.sendMessage(field("Erstellt",   w.createdAt().toString().substring(0, 10)));
        player.sendMessage(field("Läuft ab",   w.expires() ? "Ja" : "Nein"));
        if (!w.description().isEmpty()) {
            player.sendMessage(field("Beschreibung", w.description()));
        }
    }

    @WarpCommand(value = "mywarps", usage = "mywarps")
    public void mywarps(Player player, String[] args) {
        List<PlayerWarp> warps = cache.getByOwner(player.getUniqueId());
        if (warps.isEmpty()) {
            player.sendMessage(Component.text("Du hast noch keine Warps. Nutze /swarp create <n>!", NamedTextColor.GRAY));
            return;
        }
        player.sendMessage(Component.text("─── Deine Warps (" + warps.size() + ") ───", NamedTextColor.GOLD));
        warps.forEach(w -> player.sendMessage(
                Component.text("  " + w.category().icon + " ", NamedTextColor.GRAY)
                .append(Component.text(w.name(), NamedTextColor.YELLOW))
                .append(Component.text(" — " + w.visits() + " Besuche", NamedTextColor.GRAY))
                .append(w.expires()
                        ? Component.text(" [läuft ab]", NamedTextColor.YELLOW)
                        : Component.empty())
                .append(Component.text("  [löschen]", NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/swarp delete " + w.name()))
                        .hoverEvent(HoverEvent.showText(Component.text("Klicken zum Löschen", NamedTextColor.RED))))
        ));
    }

    private Component field(String label, String value) {
        return Component.text("  " + label + ": ", NamedTextColor.GRAY)
                .append(Component.text(value, NamedTextColor.WHITE));
    }

    private Component buildCategoryBar() {
        Component bar = Component.text("  Kategorien: ", NamedTextColor.GRAY);
        for (WarpCategory cat : WarpCategory.values()) {
            bar = bar.append(
                    Component.text("[" + cat.icon + " " + cat.displayName + "] ", NamedTextColor.AQUA)
                    .clickEvent(ClickEvent.runCommand("/swarp list " + cat.name().toLowerCase()))
                    .hoverEvent(HoverEvent.showText(Component.text("Nur " + cat.displayName + " anzeigen")))
            );
        }
        return bar;
    }
}
