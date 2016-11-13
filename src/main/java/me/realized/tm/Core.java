package me.realized.tm;

import me.realized.tm.commands.TMCommand;
import me.realized.tm.commands.TokenCommand;
import me.realized.tm.configuration.Lang;
import me.realized.tm.data.DataManager;
import me.realized.tm.hooks.TMEconomy;
import me.realized.tm.shop.ShopManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

public class Core extends JavaPlugin {

    private static Core instance = null;
    private static final Logger logger = Logger.getLogger("TokenManager");

    private Lang lang;
    private DataManager dataManager;
    private ShopManager shopManager;

    @Override
    public void onEnable() {
        instance = this;

        lang = new Lang(this);
        lang.load();

        dataManager = new DataManager(this);

        if (!dataManager.load()) {
            warn("DataManager has failed to initialize! Plugin is now disabling...");
            getPluginLoader().disablePlugin(this);
            return;
        }

        dataManager.checkOnline();
        dataManager.checkConnection();
        dataManager.reload();

        shopManager = new ShopManager(this);
        shopManager.load();

        getCommand("token").setExecutor(new TokenCommand(this));
        getCommand("tm").setExecutor(new TMCommand(this));

        PluginManager manager = Bukkit.getPluginManager();

        if (getConfig().getBoolean("vault-hook")) {
            if (!manager.isPluginEnabled("Vault")) {
                warn("Vault Hook was enabled in the config, but Vault was not found on the server! Hook failed.");
                return;
            }

            Bukkit.getServicesManager().register(Economy.class, new TMEconomy(this), this, ServicePriority.Highest);
            info("Successfully hooked into Vault.");
        }
    }

    @Override
    public void onDisable() {
        dataManager.close();

        if (shopManager != null) {
            shopManager.close();
        }
    }

    public void warn(String msg) {
        logger.warning("[TokenManager] " + msg);
    }

    public void info(String msg) {
        logger.info("[TokenManager] " + msg);
    }

    public Lang getLang() {
        return lang;
    }

    public ShopManager getShopManager() {
        return shopManager;
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public static Core getInstance() {
        return instance;
    }
}
