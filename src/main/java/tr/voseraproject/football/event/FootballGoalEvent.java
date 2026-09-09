package tr.voseraproject.football.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import tr.voseraproject.football.arena.Arena;
import tr.voseraproject.football.arena.Team;

import java.util.UUID;

public final class FootballGoalEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Arena arena;
    private final Team scoringTeam;
    private final UUID lastKicker;

    public FootballGoalEvent(Arena arena, Team scoringTeam, UUID lastKicker) {
        this.arena = arena;
        this.scoringTeam = scoringTeam;
        this.lastKicker = lastKicker;
    }

    public Arena getArena() {
        return arena;
    }

    public Team getScoringTeam() {
        return scoringTeam;
    }

    public UUID getLastKicker() {
        return lastKicker;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
