package tr.voseraproject.football.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import tr.voseraproject.football.setup.GoalSelectionManager;

public final class GoalSelectionListener implements Listener {
    private final GoalSelectionManager selectionManager;

    public GoalSelectionListener(GoalSelectionManager selectionManager) {
        this.selectionManager = selectionManager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (selectionManager.getSession(event.getPlayer()) == null) {
            return;
        }
        if (!selectionManager.isWand(event.getItem()) || event.getClickedBlock() == null) {
            return;
        }

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            selectionManager.setFirst(event.getPlayer(), event.getClickedBlock().getLocation());
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            selectionManager.setSecond(event.getPlayer(), event.getClickedBlock().getLocation());
        }
    }
}
