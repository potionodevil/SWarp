package de.swarp.listener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.swarp.factory.WarpTask;
import de.swarp.factory.WarpWorkerFactory;
import de.swarp.service.WarpCacheService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * Allows players to create and click warp signs.
 *
 * Sign format:
 *   Line 1: [SWarp]
 *   Line 2: <warpName>
 *   Line 3: (optional description)
 *   Line 4: (empty)
 */
@Singleton
public class WarpSignListener implements Listener {

    private static final String HEADER = "[SWarp]";

    private final WarpCacheService cache;
    private final WarpWorkerFactory workerFactory;

    @Inject
    public WarpSignListener(WarpCacheService cache, WarpWorkerFactory workerFactory) {
        this.cache = cache;
        this.workerFactory = workerFactory;
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        String line1 = event.line(0) != null ? event.line(0).toString() : "";
        if (!line1.equalsIgnoreCase(HEADER)) return;
        String warpName = event.line(1) != null ? event.line(1).toString().trim() : "";
        if (cache.getPublicByName(warpName).isEmpty()) {
            event.getPlayer().sendMessage(
                    Component.text("✗ No public warp named \"" + warpName + "\" found.", NamedTextColor.RED));
            event.setCancelled(true);
            return;
        }
        event.line(0, Component.text(HEADER, NamedTextColor.DARK_GREEN));
        event.getPlayer().sendMessage(
                Component.text("✔ Warp sign created for \"" + warpName + "\"!", NamedTextColor.GREEN));
    }

    @EventHandler
    public void onSignClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        if (!(event.getClickedBlock().getState() instanceof Sign sign)) return;

        String line1 = sign.getSide(Side.FRONT).line(0).toString();
        if (!line1.contains(HEADER)) return;

        String warpName = sign.getSide(Side.FRONT).line(1).toString().trim();
        Player player = event.getPlayer();

        if (!player.hasPermission("swarp.teleport")) {
            player.sendMessage(Component.text("✗ No permission.", NamedTextColor.RED));
            return;
        }

        cache.getPublicByName(warpName).ifPresentOrElse(
                warp -> workerFactory.submit(WarpTask.teleport(player, warp)),
                () -> player.sendMessage(
                        Component.text("✗ Warp \"" + warpName + "\" no longer exists.", NamedTextColor.RED))
        );

        event.setCancelled(true);
    }
}
