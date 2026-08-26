package de.klassensmp;

import de.klassensmp.achievement.AchievementManager;
import de.klassensmp.claim.ClaimManager;
import de.klassensmp.command.CommandManager;
import de.klassensmp.config.ConfigManager;
import de.klassensmp.config.MessageManager;
import de.klassensmp.crate.CrateManager;
import de.klassensmp.database.Database;
import de.klassensmp.economy.EconomyManager;
import de.klassensmp.event.ServerEventManager;
import de.klassensmp.gui.GuiManager;
import de.klassensmp.hook.HookManager;
import de.klassensmp.kit.KitManager;
import de.klassensmp.listener.ListenerRegistry;
import de.klassensmp.manager.BackupManager;
import de.klassensmp.manager.ChatManager;
import de.klassensmp.manager.GraveManager;
import de.klassensmp.manager.HomeManager;
import de.klassensmp.manager.PlayerDataManager;
import de.klassensmp.manager.PvpManager;
import de.klassensmp.manager.RankManager;
import de.klassensmp.manager.SpawnManager;
import de.klassensmp.manager.TeleportManager;
import de.klassensmp.manager.TpaManager;
import de.klassensmp.manager.WarpManager;
import de.klassensmp.manager.WorldManager;
import de.klassensmp.moderation.AntiBotManager;
import de.klassensmp.moderation.AntiSpamManager;
import de.klassensmp.moderation.FreezeManager;
import de.klassensmp.moderation.ModerationManager;
import de.klassensmp.moderation.StaffModeManager;
import de.klassensmp.moderation.VanishManager;
import de.klassensmp.performance.PerformanceManager;
import de.klassensmp.performance.ServerBoostManager;
import de.klassensmp.quest.QuestManager;
import de.klassensmp.scoreboard.BoardManager;
import de.klassensmp.tab.TabManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Hauptklasse des KlassenSMP-Plugins.
 *
 * <p>Die Klasse haelt bewusst nur den Lebenszyklus und die Referenzen auf die
 * einzelnen Manager. Saemtliche Fachlogik liegt in den jeweiligen Paketen.</p>
 *
 * <p>Es werden ausschliesslich Spigot/Bukkit-APIs verwendet. Paper-spezifische
 * Klassen, Events oder Methoden kommen nicht zum Einsatz.</p>
 */
public final class KlassenSMP extends JavaPlugin {

    private static KlassenSMP instance;

    private ConfigManager configManager;
    private MessageManager messageManager;
    private Database database;
    private HookManager hookManager;

    private PlayerDataManager playerDataManager;
    private RankManager rankManager;
    private EconomyManager economyManager;
    private HomeManager homeManager;
    private WarpManager warpManager;
    private SpawnManager spawnManager;
    private TeleportManager teleportManager;
    private TpaManager tpaManager;
    private ChatManager chatManager;
    private PvpManager pvpManager;
    private GraveManager graveManager;
    private WorldManager worldManager;
    private BackupManager backupManager;

    private AntiSpamManager antiSpamManager;
    private AntiBotManager antiBotManager;
    private ModerationManager moderationManager;
    private VanishManager vanishManager;
    private StaffModeManager staffModeManager;
    private FreezeManager freezeManager;

    private ClaimManager claimManager;
    private KitManager kitManager;
    private CrateManager crateManager;
    private QuestManager questManager;
    private AchievementManager achievementManager;
    private ServerEventManager serverEventManager;

    private PerformanceManager performanceManager;
    private ServerBoostManager serverBoostManager;
    private TabManager tabManager;
    private BoardManager boardManager;
    private GuiManager guiManager;

    private CommandManager commandManager;
    private ListenerRegistry listenerRegistry;

    private boolean fullyEnabled;

    @Override
    public void onEnable() {
        instance = this;
        long start = System.currentTimeMillis();

        try {
            // 1. Konfiguration
            this.configManager = new ConfigManager(this);
            configManager.load();

            // 2. Nachrichten
            this.messageManager = new MessageManager(this);
            messageManager.load();

            // 3. Datenbank
            this.database = new Database(this);
            if (!database.connect()) {
                getLogger().severe("Die Datenbank konnte nicht initialisiert werden - KlassenSMP wird deaktiviert.");
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }

            // 4. Manager
            initManagers();

            // 5. Listener
            this.listenerRegistry = new ListenerRegistry(this);
            listenerRegistry.registerAll();

            // 6. Commands
            this.commandManager = new CommandManager(this);
            commandManager.registerAll();

            // 7. Optionale Abhaengigkeiten
            this.hookManager.setup();

            // 8. bis 9. Wiederkehrende Systeme starten
            startSystems();

            // 10. Fertig
            this.fullyEnabled = true;
            getLogger().info("KlassenSMP " + getDescription().getVersion() + " aktiviert ("
                    + (System.currentTimeMillis() - start) + " ms).");
        } catch (RuntimeException ex) {
            getLogger().log(Level.SEVERE, "KlassenSMP konnte nicht gestartet werden", ex);
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    private void initManagers() {
        this.hookManager = new HookManager(this);

        this.rankManager = new RankManager(this);
        this.playerDataManager = new PlayerDataManager(this);
        this.economyManager = new EconomyManager(this);
        this.teleportManager = new TeleportManager(this);
        this.homeManager = new HomeManager(this);
        this.warpManager = new WarpManager(this);
        this.spawnManager = new SpawnManager(this);
        this.tpaManager = new TpaManager(this);
        this.chatManager = new ChatManager(this);
        this.pvpManager = new PvpManager(this);
        this.graveManager = new GraveManager(this);
        this.worldManager = new WorldManager(this);
        this.backupManager = new BackupManager(this);

        this.antiSpamManager = new AntiSpamManager(this);
        this.antiBotManager = new AntiBotManager(this);
        this.moderationManager = new ModerationManager(this);
        this.vanishManager = new VanishManager(this);
        this.staffModeManager = new StaffModeManager(this);
        this.freezeManager = new FreezeManager(this);

        this.claimManager = new ClaimManager(this);
        this.kitManager = new KitManager(this);
        this.crateManager = new CrateManager(this);
        this.questManager = new QuestManager(this);
        this.achievementManager = new AchievementManager(this);
        this.serverEventManager = new ServerEventManager(this);

        this.performanceManager = new PerformanceManager(this);
        this.serverBoostManager = new ServerBoostManager(this);
        this.tabManager = new TabManager(this);
        this.boardManager = new BoardManager(this);
        this.guiManager = new GuiManager(this);

        // Daten laden (asynchron, wo moeglich)
        rankManager.load();
        playerDataManager.load();
        warpManager.load();
        spawnManager.load();
        claimManager.load();
        kitManager.load();
        crateManager.load();
        questManager.load();
        achievementManager.load();
        serverEventManager.load();
        graveManager.load();
    }

    private void startSystems() {
        performanceManager.start();
        serverBoostManager.start();
        tabManager.start();
        boardManager.start();
        playerDataManager.startAutoSave();
        questManager.startAutoSave();
        tpaManager.start();
        graveManager.start();
        moderationManager.start();
        antiSpamManager.start();
        antiBotManager.start();
        backupManager.startScheduler();

        // Spieler, die bereits online sind (z.B. bei /reload), sauber uebernehmen.
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            loadPlayer(player);
            boardManager.handleJoin(player);
            tabManager.handleJoin(player);
        }
    }

    /**
     * Laedt alle Daten eines Spielers.
     *
     * <p>Wird sowohl beim Beitritt als auch beim Aktivieren des Plugins fuer
     * bereits verbundene Spieler verwendet - so gibt es nur einen Ort, an dem
     * die Ladeschritte gepflegt werden muessen.</p>
     */
    public void loadPlayer(org.bukkit.entity.Player player) {
        playerDataManager.handleJoin(player);
        homeManager.loadFor(player.getUniqueId());
        kitManager.loadUses(player.getUniqueId());
        questManager.loadFor(player.getUniqueId());
        achievementManager.loadFor(player.getUniqueId());
        chatManager.loadIgnores(player.getUniqueId());
    }

    @Override
    public void onDisable() {
        if (!fullyEnabled) {
            if (database != null) {
                database.shutdown();
            }
            instance = null;
            return;
        }

        // Alle wiederkehrenden Aufgaben stoppen
        Bukkit.getScheduler().cancelTasks(this);

        try {
            if (guiManager != null) {
                guiManager.closeAll();
            }
            if (staffModeManager != null) {
                staffModeManager.restoreAll();
            }
            if (serverEventManager != null) {
                serverEventManager.stopActiveEvent(null, true);
            }
            if (tabManager != null) {
                tabManager.shutdown();
            }
            if (boardManager != null) {
                boardManager.shutdown();
            }
            if (playerDataManager != null) {
                playerDataManager.saveAllBlocking();
            }
            if (claimManager != null) {
                claimManager.saveAllBlocking();
            }
            if (questManager != null) {
                questManager.saveAllBlocking();
            }
            if (hookManager != null) {
                hookManager.shutdown();
            }
        } catch (RuntimeException ex) {
            getLogger().log(Level.WARNING, "Fehler beim Herunterfahren", ex);
        } finally {
            if (database != null) {
                database.shutdown();
            }
            instance = null;
            fullyEnabled = false;
            getLogger().info("KlassenSMP deaktiviert.");
        }
    }

    /**
     * Laedt Konfiguration, Nachrichten und alle datei-basierten Inhalte neu.
     * Die Datenbankverbindung bleibt bestehen.
     */
    public void reloadEverything() {
        configManager.load();
        messageManager.load();
        rankManager.load();
        kitManager.load();
        crateManager.load();
        questManager.load();
        achievementManager.load();
        serverEventManager.load();
        chatManager.reload();
        tabManager.reload();
        boardManager.reload();
        performanceManager.reload();
        antiSpamManager.reload();
        backupManager.startScheduler();
    }

    public static KlassenSMP getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessages() {
        return messageManager;
    }

    public Database getDatabase() {
        return database;
    }

    public HookManager getHooks() {
        return hookManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public RankManager getRankManager() {
        return rankManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public HomeManager getHomeManager() {
        return homeManager;
    }

    public WarpManager getWarpManager() {
        return warpManager;
    }

    public SpawnManager getSpawnManager() {
        return spawnManager;
    }

    public TeleportManager getTeleportManager() {
        return teleportManager;
    }

    public TpaManager getTpaManager() {
        return tpaManager;
    }

    public ChatManager getChatManager() {
        return chatManager;
    }

    public PvpManager getPvpManager() {
        return pvpManager;
    }

    public GraveManager getGraveManager() {
        return graveManager;
    }

    public WorldManager getWorldManager() {
        return worldManager;
    }

    public BackupManager getBackupManager() {
        return backupManager;
    }

    public AntiSpamManager getAntiSpamManager() {
        return antiSpamManager;
    }

    public AntiBotManager getAntiBotManager() {
        return antiBotManager;
    }

    public ModerationManager getModerationManager() {
        return moderationManager;
    }

    public VanishManager getVanishManager() {
        return vanishManager;
    }

    public StaffModeManager getStaffModeManager() {
        return staffModeManager;
    }

    public FreezeManager getFreezeManager() {
        return freezeManager;
    }

    public ClaimManager getClaimManager() {
        return claimManager;
    }

    public KitManager getKitManager() {
        return kitManager;
    }

    public CrateManager getCrateManager() {
        return crateManager;
    }

    public QuestManager getQuestManager() {
        return questManager;
    }

    public AchievementManager getAchievementManager() {
        return achievementManager;
    }

    public ServerEventManager getServerEventManager() {
        return serverEventManager;
    }

    public PerformanceManager getPerformanceManager() {
        return performanceManager;
    }

    public ServerBoostManager getServerBoostManager() {
        return serverBoostManager;
    }

    public TabManager getTabManager() {
        return tabManager;
    }

    public BoardManager getBoardManager() {
        return boardManager;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }
}
