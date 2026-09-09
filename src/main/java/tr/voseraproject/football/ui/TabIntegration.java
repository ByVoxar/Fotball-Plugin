package tr.voseraproject.football.ui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import tr.voseraproject.football.FootballPlugin;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;

public final class TabIntegration {
    private final FootballPlugin plugin;

    private Object tabApi;
    private Object headerFooterManager;
    private Object tabListFormatManager;
    private Method getPlayerMethod;
    private boolean initialized;
    private boolean failureLogged;

    public TabIntegration(FootballPlugin plugin) {
        this.plugin = plugin;
        initialize();
    }

    public void refreshConnection() {
        if (!isTabPluginEnabled()) {
            clear();
            return;
        }
        if (!initialized) {
            initialize();
        }
    }

    public boolean handlesHeaderFooter() {
        refreshConnection();
        return initialized && headerFooterManager != null;
    }

    public boolean handlesPlayerFormatting() {
        refreshConnection();
        return initialized && tabListFormatManager != null;
    }

    public boolean setHeaderFooter(Player player, String header, String footer) {
        if (!handlesHeaderFooter()) {
            return false;
        }
        try {
            Object tabPlayer = getTabPlayer(player.getUniqueId());
            if (tabPlayer == null) {
                return false;
            }
            invoke(headerFooterManager, "setHeaderAndFooter", tabPlayer, header, footer);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            handleFailure(exception);
            return false;
        }
    }

    public boolean setPlayerFormat(Player player, String prefix, String name, String suffix) {
        if (!handlesPlayerFormatting()) {
            return false;
        }
        try {
            Object tabPlayer = getTabPlayer(player.getUniqueId());
            if (tabPlayer == null) {
                return false;
            }
            invoke(tabListFormatManager, "setPrefix", tabPlayer, prefix);
            invoke(tabListFormatManager, "setName", tabPlayer, name);
            invoke(tabListFormatManager, "setSuffix", tabPlayer, suffix);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            handleFailure(exception);
            return false;
        }
    }

    public void reset(Player player) {
        if (player == null) {
            return;
        }

        refreshConnection();
        if (!initialized) {
            return;
        }

        try {
            Object tabPlayer = getTabPlayer(player.getUniqueId());
            if (tabPlayer == null) {
                return;
            }

            if (headerFooterManager != null) {
                invoke(headerFooterManager, "setHeaderAndFooter", tabPlayer, null, null);
            }
            if (tabListFormatManager != null) {
                invoke(tabListFormatManager, "setPrefix", tabPlayer, null);
                invoke(tabListFormatManager, "setName", tabPlayer, null);
                invoke(tabListFormatManager, "setSuffix", tabPlayer, null);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            handleFailure(exception);
        }
    }

    private void initialize() {
        clear();
        if (!isTabPluginEnabled()) {
            return;
        }

        try {
            Class<?> apiClass = Class.forName("me.neznamy.tab.api.TabAPI");
            tabApi = apiClass.getMethod("getInstance").invoke(null);
            getPlayerMethod = apiClass.getMethod("getPlayer", UUID.class);
            headerFooterManager = invokeNoArgOptional(apiClass, tabApi, "getHeaderFooterManager");
            tabListFormatManager = invokeNoArgOptional(apiClass, tabApi,
                    "getTabListFormatManager", "getTablistFormatManager");
            initialized = true;
            failureLogged = false;
            plugin.getLogger().info("TAB bulundu: Football arena TAB'i TAB API üzerinden öncelikli uygulanacak.");
        } catch (ReflectiveOperationException | LinkageError exception) {
            clear();
            if (!failureLogged) {
                failureLogged = true;
                plugin.getLogger().warning("TAB bulundu ancak API bağlantısı kurulamadı; Bukkit TAB fallback kullanılacak: "
                        + exception.getClass().getSimpleName() + ": " + exception.getMessage());
            }
        }
    }

    private Object invokeNoArgOptional(Class<?> type, Object target, String... names) throws ReflectiveOperationException {
        for (String name : names) {
            try {
                return type.getMethod(name).invoke(target);
            } catch (NoSuchMethodException ignored) {

            }
        }
        return null;
    }

    private Object getTabPlayer(UUID uuid) throws ReflectiveOperationException {
        return getPlayerMethod.invoke(tabApi, uuid);
    }

    private void invoke(Object target, String methodName, Object... args) throws ReflectiveOperationException {
        Method method = Arrays.stream(target.getClass().getMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .filter(candidate -> candidate.getParameterCount() == args.length)
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodException(target.getClass().getName() + "#" + methodName));
        method.invoke(target, args);
    }

    private void handleFailure(Exception exception) {
        if (!failureLogged) {
            failureLogged = true;
            plugin.getLogger().warning("TAB API çağrısı başarısız oldu, bağlantı yeniden kurulacak: "
                    + exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
        initialized = false;
    }

    private boolean isTabPluginEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled("TAB");
    }

    private void clear() {
        tabApi = null;
        headerFooterManager = null;
        tabListFormatManager = null;
        getPlayerMethod = null;
        initialized = false;
    }
}
