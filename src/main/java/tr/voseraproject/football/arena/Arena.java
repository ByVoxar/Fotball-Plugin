package tr.voseraproject.football.arena;

import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;
import tr.voseraproject.football.ball.FootballBall;
import tr.voseraproject.football.util.LocationUtil;

import java.util.*;

public final class Arena {
    private final String name;
    private int minPlayers;
    private int maxPlayers;

    private Location redSpawn;
    private Location blueSpawn;
    private Location ballSpawn;
    private GoalRegion redGoal;
    private GoalRegion blueGoal;

    private final List<Location> signs = new ArrayList<>();
    private final Set<UUID> players = new LinkedHashSet<>();
    private final Set<UUID> spectators = new LinkedHashSet<>();
    private final Map<UUID, Team> teams = new HashMap<>();
    private final Map<UUID, PlayerSnapshot> snapshots = new HashMap<>();

    private ArenaState state = ArenaState.WAITING;
    private int redScore;
    private int blueScore;
    private int timeLeft;
    private boolean overtime;
    private boolean goalLocked;
    private FootballBall ball;
    private BukkitTask matchTask;
    private BukkitTask startTask;

    public Arena(String name, int minPlayers, int maxPlayers) {
        this.name = Objects.requireNonNull(name, "name");
        this.minPlayers = Math.max(1, minPlayers);
        this.maxPlayers = Math.max(this.minPlayers, maxPlayers);
    }

    public String getName() {
        return name;
    }

    public int getMinPlayers() {
        return minPlayers;
    }

    public void setMinPlayers(int minPlayers) {
        this.minPlayers = Math.max(1, minPlayers);
        if (maxPlayers < this.minPlayers) {
            maxPlayers = this.minPlayers;
        }
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = Math.max(minPlayers, maxPlayers);
    }

    public Location getRedSpawn() {
        return cloneLocation(redSpawn);
    }

    public void setRedSpawn(Location redSpawn) {
        this.redSpawn = cloneLocation(redSpawn);
    }

    public Location getBlueSpawn() {
        return cloneLocation(blueSpawn);
    }

    public void setBlueSpawn(Location blueSpawn) {
        this.blueSpawn = cloneLocation(blueSpawn);
    }

    public Location getBallSpawn() {
        return cloneLocation(ballSpawn);
    }

    public void setBallSpawn(Location ballSpawn) {
        this.ballSpawn = cloneLocation(ballSpawn);
    }

    public GoalRegion getRedGoal() {
        return redGoal;
    }

    public void setRedGoal(GoalRegion redGoal) {
        this.redGoal = redGoal;
    }

    public GoalRegion getBlueGoal() {
        return blueGoal;
    }

    public void setBlueGoal(GoalRegion blueGoal) {
        this.blueGoal = blueGoal;
    }

    public List<Location> getSigns() {
        return signs.stream().map(Location::clone).toList();
    }

    public void addSign(Location location) {
        String key = LocationUtil.blockKey(location);
        boolean exists = signs.stream().anyMatch(existing -> LocationUtil.blockKey(existing).equals(key));
        if (!exists) {
            signs.add(location.clone());
        }
    }

    public void removeSign(Location location) {
        String key = LocationUtil.blockKey(location);
        signs.removeIf(existing -> LocationUtil.blockKey(existing).equals(key));
    }

    public Set<UUID> getPlayers() {
        return Collections.unmodifiableSet(players);
    }

    public Set<UUID> getSpectators() {
        return Collections.unmodifiableSet(spectators);
    }

    public void addPlayer(UUID uuid) {
        players.add(uuid);
    }

    public void removePlayer(UUID uuid) {
        players.remove(uuid);
        teams.remove(uuid);
    }

    public void addSpectator(UUID uuid) {
        spectators.add(uuid);
    }

    public void removeSpectator(UUID uuid) {
        spectators.remove(uuid);
    }

    public Team getTeam(UUID uuid) {
        return teams.get(uuid);
    }

    public void setTeam(UUID uuid, Team team) {
        teams.put(uuid, team);
    }

    public Map<UUID, Team> getTeams() {
        return Collections.unmodifiableMap(teams);
    }

    public int getTeamSize(Team team) {
        return (int) teams.values().stream().filter(value -> value == team).count();
    }

    public Map<UUID, PlayerSnapshot> getSnapshots() {
        return snapshots;
    }

    public ArenaState getState() {
        return state;
    }

    public void setState(ArenaState state) {
        this.state = state;
    }

    public int getRedScore() {
        return redScore;
    }

    public int getBlueScore() {
        return blueScore;
    }

    public void resetScores() {
        redScore = 0;
        blueScore = 0;
    }

    public int addGoal(Team scoringTeam) {
        if (scoringTeam == Team.RED) {
            return ++redScore;
        }
        return ++blueScore;
    }

    public int getTimeLeft() {
        return timeLeft;
    }

    public void setTimeLeft(int timeLeft) {
        this.timeLeft = Math.max(0, timeLeft);
    }

    public int decrementTimeLeft() {
        if (timeLeft > 0) {
            timeLeft--;
        }
        return timeLeft;
    }

    public boolean isOvertime() {
        return overtime;
    }

    public void setOvertime(boolean overtime) {
        this.overtime = overtime;
    }

    public boolean isGoalLocked() {
        return goalLocked;
    }

    public void setGoalLocked(boolean goalLocked) {
        this.goalLocked = goalLocked;
    }

    public FootballBall getBall() {
        return ball;
    }

    public void setBall(FootballBall ball) {
        this.ball = ball;
    }

    public BukkitTask getStartTask() {
        return startTask;
    }

    public void setStartTask(BukkitTask startTask) {
        this.startTask = startTask;
    }

    public BukkitTask getMatchTask() {
        return matchTask;
    }

    public void setMatchTask(BukkitTask matchTask) {
        this.matchTask = matchTask;
    }

    public boolean isSetupComplete() {
        return redSpawn != null
                && blueSpawn != null
                && ballSpawn != null
                && redGoal != null
                && blueGoal != null;
    }

    public void clearRuntime() {
        players.clear();
        spectators.clear();
        teams.clear();
        snapshots.clear();
        resetScores();
        state = ArenaState.WAITING;
        timeLeft = 0;
        overtime = false;
        goalLocked = false;
        ball = null;
        matchTask = null;
        startTask = null;
    }

    private static Location cloneLocation(Location location) {
        return location == null ? null : location.clone();
    }
}
