package tr.voseraproject.football.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import tr.voseraproject.football.arena.ArenaManager;
import tr.voseraproject.football.event.FootballGoalEvent;

public final class GoalListener implements Listener {
    private final ArenaManager arenaManager;

    public GoalListener(ArenaManager arenaManager) {
        this.arenaManager = arenaManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGoal(FootballGoalEvent event) {
        arenaManager.handleGoal(event.getArena(), event.getScoringTeam(), event.getLastKicker());
    }
}
