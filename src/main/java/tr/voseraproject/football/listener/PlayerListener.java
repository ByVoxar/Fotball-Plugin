package tr.voseraproject.football.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import tr.voseraproject.football.arena.Arena;
import tr.voseraproject.football.arena.ArenaManager;
import tr.voseraproject.football.arena.ArenaState;
import tr.voseraproject.football.arena.Team;

public final class PlayerListener implements Listener {
    private final ArenaManager arenaManager;

    public PlayerListener(ArenaManager arenaManager) {
        this.arenaManager = arenaManager;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        arenaManager.handleDisconnect(event.getPlayer());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        arenaManager.handleDisconnect(event.getPlayer());
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && arenaManager.getArenaOf(player.getUniqueId()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && arenaManager.getArenaOf(player.getUniqueId()) != null) {
            event.setCancelled(true);
            player.setFoodLevel(20);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (arenaManager.isTeamArmorInventoryLocked() && arenaManager.isInArena(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player
                && arenaManager.isTeamArmorInventoryLocked()
                && arenaManager.isInArena(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (arenaManager.isTeamArmorInventoryLocked()
                && arenaManager.isInArena(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player
                && arenaManager.isTeamArmorInventoryLocked()
                && arenaManager.isInArena(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (arenaManager.isTeamArmorInventoryLocked()
                && arenaManager.isInArena(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Arena arena = arenaManager.getArenaOf(event.getPlayer().getUniqueId());
        if (arena == null || !arena.getPlayers().contains(event.getPlayer().getUniqueId())) {
            return;
        }
        Team team = arena.getTeam(event.getPlayer().getUniqueId());
        if (team == null) {
            return;
        }
        if (arena.getState() == ArenaState.RUNNING || arena.getState() == ArenaState.WAITING) {
            event.setRespawnLocation(team == Team.RED ? arena.getRedSpawn() : arena.getBlueSpawn());
        }
    }

    @EventHandler
    public void onSpectatorTeleport(PlayerTeleportEvent event) {
        Arena arena = arenaManager.getArenaOf(event.getPlayer().getUniqueId());
        if (arena == null || !arena.getSpectators().contains(event.getPlayer().getUniqueId())) {
            return;
        }

        if (event.getTo() != null && arena.getBallSpawn() != null
                && event.getTo().getWorld() != arena.getBallSpawn().getWorld()
                && event.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN) {
            event.setCancelled(true);
        }
    }
}
