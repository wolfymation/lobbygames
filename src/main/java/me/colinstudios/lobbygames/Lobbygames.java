package me.colinstudios.lobbygames;

import me.colinstudios.lobbygames.cookie.CookieAdminCommand;
import me.colinstudios.lobbygames.cookie.CookieClickerService;
import me.colinstudios.lobbygames.cookie.CookieListener;
import me.colinstudios.lobbygames.cookie.CookiePlaceholderExpansion;
import me.colinstudios.lobbygames.cookie.CookieSpecialItemService;
import me.colinstudios.lobbygames.cookie.CookieStorage;
import org.bukkit.plugin.java.JavaPlugin;

public final class Lobbygames extends JavaPlugin {
    private CookieStorage cookieStorage;
    private CookieClickerService cookieClickerService;
    private CookieSpecialItemService cookieSpecialItemService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.cookieStorage = new CookieStorage(this);
        this.cookieStorage.open();

        this.cookieClickerService = new CookieClickerService(this, cookieStorage);
        this.cookieClickerService.start();
        this.cookieSpecialItemService = new CookieSpecialItemService(this);

        CookieListener listener = new CookieListener(cookieClickerService, cookieSpecialItemService);
        getServer().getPluginManager().registerEvents(listener, this);

        CookieAdminCommand adminCommand = new CookieAdminCommand(this, cookieClickerService, cookieStorage, cookieSpecialItemService);
        getCommand("cookie").setExecutor(adminCommand);
        getCommand("cookie").setTabCompleter(adminCommand);

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new CookiePlaceholderExpansion(this, cookieClickerService).register();
            getLogger().info("PlaceholderAPI hook registered.");
        }
    }

    @Override
    public void onDisable() {
        if (cookieSpecialItemService != null) {
            cookieSpecialItemService.stop();
        }
        if (cookieClickerService != null) {
            cookieClickerService.stop();
        }
        if (cookieSpecialItemService != null) {
            cookieSpecialItemService.restoreTemporaryBlocks();
        }
        if (cookieStorage != null) {
            cookieStorage.close();
        }
    }
}
