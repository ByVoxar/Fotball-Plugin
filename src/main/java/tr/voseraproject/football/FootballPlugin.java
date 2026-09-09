package tr.voseraproject.football;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import tr.voseraproject.football.arena.ArenaManager;
import tr.voseraproject.football.ball.BallPhysicsTask;
import tr.voseraproject.football.command.CommandManager;
import tr.voseraproject.football.listener.ArenaChatListener;
import tr.voseraproject.football.listener.CelebrationFireworkListener;
import tr.voseraproject.football.listener.GoalListener;
import tr.voseraproject.football.listener.GoalSelectionListener;
import tr.voseraproject.football.listener.PlayerListener;
import tr.voseraproject.football.listener.SignListener;
import tr.voseraproject.football.placeholder.PlaceholderExpansion;
import tr.voseraproject.football.setup.GoalSelectionManager;
import tr.voseraproject.football.stats.StatsManager;
import tr.voseraproject.football.ui.ArenaUiManager;

import java.util.Objects;

public final class FootballPlugin extends JavaPlugin {
    private StatsManager statsManager;
    private ArenaManager arenaManager;
    private GoalSelectionManager goalSelectionManager;
    private BallPhysicsTask ballPhysicsTask;
    private ArenaUiManager arenaUiManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveBundledFile("arenas.yml");
        saveBundledFile("stats.yml");

        statsManager = new StatsManager(this);
        arenaManager = new ArenaManager(this, statsManager);
        arenaUiManager = new ArenaUiManager(this, arenaManager);
        arenaManager.setUiManager(arenaUiManager);
        arenaUiManager.start();
        goalSelectionManager = new GoalSelectionManager(this, arenaManager);

        registerCommands();
        registerListeners();
        registerPlaceholders();

        ballPhysicsTask = new BallPhysicsTask(this, arenaManager);
        ballPhysicsTask.runTaskTimer(this, 1L, 1L);

        getLogger().info("Football 1.0.12 aktif. Spigot/Paper 1.21-1.21.1 / Java 21.");
    }

    @Override
    public void onDisable() {
        if (ballPhysicsTask != null) {
            ballPhysicsTask.cancel();
        }
        if (arenaManager != null) {
            arenaManager.shutdown();
        }
        if (arenaUiManager != null) {
            arenaUiManager.shutdown();
        }
        if (statsManager != null) {
            statsManager.save();
        }
    }

    private void registerCommands() {
        CommandManager commandManager = new CommandManager(this, arenaManager, goalSelectionManager, statsManager);
        PluginCommand command = Objects.requireNonNull(getCommand("football"), "football command missing from plugin.yml");
        command.setExecutor(commandManager);
        command.setTabCompleter(commandManager);
    }

    private void registerListeners() {
        var pluginManager = Bukkit.getPluginManager();
        pluginManager.registerEvents(new GoalListener(arenaManager), this);
        pluginManager.registerEvents(new CelebrationFireworkListener(arenaManager), this);
        pluginManager.registerEvents(new ArenaChatListener(this, arenaManager), this);
        pluginManager.registerEvents(new SignListener(arenaManager), this);
        pluginManager.registerEvents(new PlayerListener(arenaManager), this);
        pluginManager.registerEvents(new GoalSelectionListener(goalSelectionManager), this);
    }

    private void registerPlaceholders() {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            boolean registered = new PlaceholderExpansion(this, statsManager).register();
            if (!registered) {
                getLogger().warning("PlaceholderAPI expansion kaydedilemedi.");
            }
        } else {
            getLogger().severe("PlaceholderAPI bulunamadı. plugin.yml depend nedeniyle Football çalıştırılmamalı.");
        }
    }

    private void saveBundledFile(String name) {
        if (!new java.io.File(getDataFolder(), name).exists()) {
            saveResource(name, false);
        }
    }

    public StatsManager getStatsManager() {
        return statsManager;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public ArenaUiManager getArenaUiManager() {
        return arenaUiManager;
    }
}
