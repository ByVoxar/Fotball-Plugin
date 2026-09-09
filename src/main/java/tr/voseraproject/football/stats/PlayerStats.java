package tr.voseraproject.football.stats;

public final class PlayerStats {
    private String name;
    private int wins;
    private int losses;
    private int goals;
    private int played;

    public PlayerStats(String name, int wins, int losses, int goals, int played) {
        this.name = name;
        this.wins = Math.max(0, wins);
        this.losses = Math.max(0, losses);
        this.goals = Math.max(0, goals);
        this.played = Math.max(0, played);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
    }

    public int getWins() {
        return wins;
    }

    public int getLosses() {
        return losses;
    }

    public int getGoals() {
        return goals;
    }

    public int getPlayed() {
        return played;
    }

    public void incrementWins() {
        wins++;
    }

    public void incrementLosses() {
        losses++;
    }

    public void incrementGoals() {
        goals++;
    }

    public void incrementPlayed() {
        played++;
    }
}
