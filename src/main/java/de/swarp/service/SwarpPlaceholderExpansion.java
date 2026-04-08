package de.swarp.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.swarp.model.PlayerWarp;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;

/**
 * PlaceholderAPI expansion for SWarp.
 */
@Singleton
public class SwarpPlaceholderExpansion extends PlaceholderExpansion {

    private final WarpCacheService cache;

    @Inject
    public SwarpPlaceholderExpansion(WarpCacheService cache) {
        this.cache = cache;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "swarps";
    }

    @Override
    public @NotNull String getAuthor() {
        return "SWarpDev";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true; // Don't unregister on /papi reload
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        // %swarps_top_<field>_<rank>%
        if (params.startsWith("top_")) {
            return handleTop(params.substring(4));
        }

        // %swarps_<playername>_<field>_<rank>%
        return handlePlayer(params);
    }

    private String handleTop(String params) {
        int lastUnderscore = params.lastIndexOf('_');
        if (lastUnderscore == -1) return null;

        String field = params.substring(0, lastUnderscore);       // "name"
        String rankStr = params.substring(lastUnderscore + 1);    // "1"

        int rank;
        try { rank = Integer.parseInt(rankStr); }
        catch (NumberFormatException e) { return null; }

        List<PlayerWarp> sorted = cache.getAllPublic().stream()
                .sorted(Comparator.comparingLong(PlayerWarp::visits).reversed())
                .toList();

        if (rank < 1 || rank > sorted.size()) return "-";

        PlayerWarp warp = sorted.get(rank - 1);
        return switch (field) {
            case "name"    -> warp.name();
            case "visits"  -> String.valueOf(warp.visits());
            case "owner"   -> warp.ownerName();
            case "created" -> warp.createdAt().toString().substring(0, 10);
            default        -> null;
        };
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Player Warps
    // ──────────────────────────────────────────────────────────────────────────

    private String handlePlayer(String params) {
        // params = "WesleyxCraft_visits_1"
        // Split from the end: last segment = rank, second-to-last = field, rest = playername
        String[] parts = params.split("_");
        if (parts.length < 3) return null;

        String rankStr = parts[parts.length - 1];
        String field   = parts[parts.length - 2];
        // Player name may contain underscores, so join everything before field+rank
        String playerName = String.join("_",
                java.util.Arrays.copyOfRange(parts, 0, parts.length - 2));

        int rank;
        try { rank = Integer.parseInt(rankStr); }
        catch (NumberFormatException e) { return null; }

        List<PlayerWarp> playerWarps = cache.getAllPublic().stream()
                .filter(w -> w.ownerName().equalsIgnoreCase(playerName))
                .sorted(Comparator.comparingLong(PlayerWarp::visits).reversed())
                .toList();

        if (rank < 1 || rank > playerWarps.size()) return "-";

        PlayerWarp warp = playerWarps.get(rank - 1);
        return switch (field) {
            case "name"    -> warp.name();
            case "visits"  -> String.valueOf(warp.visits());
            case "owner"   -> warp.ownerName();
            case "created" -> warp.createdAt().toString().substring(0, 10);
            default        -> null;
        };
    }
}
