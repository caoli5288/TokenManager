package me.realized.tm.data;

import com.avaje.ebean.EbeanServer;
import com.mengcraft.simpleorm.DatabaseException;
import com.mengcraft.simpleorm.EbeanHandler;
import me.realized.tm.Core;
import me.realized.tm.events.TokenReceiveEvent;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import javax.persistence.PersistenceException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class DataManager implements Listener {

    private final Core instance;
    private final FileConfiguration config;
    private final boolean sql;

    private File file;
    private FileConfiguration dataConfig;
    private Map<UUID, Integer> data = new ConcurrentHashMap<>();

    private boolean connected;

    private final List<String> top = new ArrayList<>();
    private long lastUpdate;

    private List<BukkitTask> tasks = new ArrayList<>();
    private EbeanServer server;

    private ExecutorService pool;

    public DataManager(Core instance) {
        this.instance = instance;
        this.config = instance.getConfig();
        this.sql = config.getBoolean("mysql.enabled");
        Bukkit.getPluginManager().registerEvents(this, instance);
    }

    public boolean load() {
        instance.info("Data Storage: " + (sql ? "MySQL" : "Flatfile"));

        if (sql) {
            pool = (new ThreadPoolExecutor(1, 1,
                    0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<Runnable>()));

            boolean success = connect();

            if (success) {
                instance.info("Successfully connected to the database.");
                return true;
            } else {
                instance.warn("Connection failed.");
                return false;
            }
        } else {
            data.clear();
            file = new File(instance.getDataFolder(), "data.yml");

            try {
                boolean generated = file.createNewFile();

                if (generated) {
                    instance.info("Generated data file!");
                }
            } catch (IOException e) {
                instance.warn("Error caught while generating file! (" + e.getMessage() + ")");
                return false;
            }

            dataConfig = YamlConfiguration.loadConfiguration(file);

            if (dataConfig.isConfigurationSection("Players")) {
                for (String key : dataConfig.getConfigurationSection("Players").getKeys(false)) {
                    UUID uuid = UUID.fromString(key);
                    int amount = dataConfig.getInt("Players." + key);
                    data.put(uuid, amount);
                }
            }

            instance.info("Loaded from flatfile storage!");
            return true;
        }
    }

    private boolean connect() {
        String host = config.getString("mysql.hostname");
        String port = config.getString("mysql.port");
        String database = config.getString("mysql.database");
        String user = config.getString("mysql.username");
        String password = config.getString("mysql.password");
        instance.info("Loaded credentials for SQL database.");

        EbeanHandler handler = new EbeanHandler(instance);
        handler.setUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
        handler.setDriver("com.mysql.jdbc.Driver");
        handler.setUserName(user);
        handler.setPassword(password);

        handler.define(Token.class);
        try {
            handler.initialize();

            server = handler.getServer();
            connected = true;
        } catch (DatabaseException e) {
            e.printStackTrace();
        }

        return connected;
    }

    public void close() {
        Bukkit.getScheduler().cancelTasks(instance);
        if (!nil(pool)) {
            pool.shutdown();
            try {
                pool.awaitTermination(1, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void reload() {
        for (BukkitTask task : tasks) {
            task.cancel();
        }

        loadTopBalance();
        initLocalSaver();
    }

    private void loadTopBalance() {

    }

    public void checkOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            executeAction(Action.CREATE, player.getUniqueId(), 0);
        }
    }

    private void initLocalSaver() {
        if (!sql) {
            tasks.add(Bukkit.getScheduler().runTaskTimerAsynchronously(instance, new Runnable() {
                @Override
                public void run() {
                    instance.info("Auto-saving...");
                    saveLocalData();
                }
            }, 0L, 20L * 60L * config.getInt("auto-save")));
        }
    }

    public void checkConnection() {
    }

    private boolean saveLocalData() {
        if (!data.isEmpty()) {
            for (UUID key : data.keySet()) {
                dataConfig.set("Players." + key, data.get(key));
            }
        }

        try {
            dataConfig.save(file);
            instance.info("Saving to local file was completed.");
            return true;
        } catch (IOException e) {
            instance.warn("Error caught while saving file! (" + e.getMessage() + ")");
            return false;
        }
    }

    public boolean hasSQLEnabled() {
        return sql;
    }

    public boolean isConnected() {
        return connected;
    }

    private Token newToken(UUID id) {
        Token token = server.createEntityBean(Token.class);
        token.setId(id);
        token.setName(instance.getServer().getPlayer(id).getName());
        token.setAmount(config.getInt("default-balance"));
        return token;
    }

    private boolean create(UUID uuid) {
        if (sql) return true;
        if (data.get(uuid) == null) {
            data.put(uuid, config.getInt("default-balance"));
            return true;
        }
        return true;
    }

    private int balance(UUID id) {
        if (sql) {
            Token token = server.find(Token.class, id);
            if (token == null) return 0;
            return token.getAmount();
        } else {
            if (!data.containsKey(id)) {
                create(id);
            }
            return data.get(id);
        }
    }

    private boolean nil(Object i) {
        return i == null;
    }

    private boolean set(UUID id, int amount) {
        if (sql) {
            Token token = server.find(Token.class, id);
            if (nil(token)) {
                token = newToken(id);
            }
            token.setAmount(amount);
            server.save(token);
            return true;
        } else {
            data.put(id, amount);
            return true;
        }
    }

    private boolean add(UUID id, int amount) {
        if (sql) {
            Token token = server.find(Token.class, id);
            if (nil(token)) {
                token = newToken(id);
            }
            token.setAmount(token.getAmount() + amount);
            try {
                server.save(token);
            } catch (PersistenceException e) {
                return false;
            }
            return true;
        }
        return set(id, balance(id) + amount);
    }

    private boolean remove(UUID id, int amount) {
        return set(id, Math.max(0, balance(id) - amount));
    }

    private Object callByAction(final Action action, final UUID target, final int amount) {
        switch (action) {
            case CREATE:
                return create(target);
            case BALANCE:
                return balance(target);
            case SET:
                return set(target, amount);
            case ADD:
                return add(target, amount);
            case REMOVE:
                return remove(target, amount);
        }

        return null;
    }

    public Object executeAction(final Action action, final UUID target, int amount) {
        if (action == Action.ADD) {
            TokenReceiveEvent event = new TokenReceiveEvent(target, amount);
            Bukkit.getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                return true;
            }

            amount = event.getAmount();
        }

        if (sql) {
            final int actualAmount = amount;
            Future<Object> future = pool.submit(() -> callByAction(action, target, actualAmount));

            try {
                return future.get(3, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                instance.warn("Error caught while executing action " + action + "! (" + e.getMessage() + ")");
                return action == Action.BALANCE ? 0 : false;
            }
        } else {
            return callByAction(action, target, amount);
        }
    }

    public List<String> getTopBalances() {
        return top;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        executeAction(Action.CREATE, event.getPlayer().getUniqueId(), 0);
    }

}
