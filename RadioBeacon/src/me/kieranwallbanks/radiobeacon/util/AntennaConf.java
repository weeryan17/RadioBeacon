package me.kieranwallbanks.radiobeacon.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import me.kieranwallbanks.radiobeacon.RadioBeacon;

public class AntennaConf {
    // Configuration options
    static int fixedInitialRadius;
    static int fixedRadiusIncreasePerBlock;
    static int fixedLightningAttractRadiusInitial;
    static double fixedLightningAttractRadiusIncreasePerBlock;
    static int fixedLightningAttractRadiusMax;
    public static boolean fixedLightningDamage;
    public static boolean fixedLightningStrikeOne;
    public static boolean fixedWeatherListener;
    public static boolean fixedBlastSetFire;
    static double fixedBlastPowerInitial;
    static double fixedBlastPowerIncreasePerBlock;
    static double fixedBlastPowerMax;
    public static int fixedExplosionReactionDelay;
    static double fixedRadiusStormFactor;
    static double fixedRadiusThunderFactor;
    static double fixedRadiusRelayFactor;
    static int fixedReceptionRadiusDivisor;
    static int fixedMaxHeight;
    public static int fixedBaseMinY;
    static Material fixedBaseMaterial;
    static Material fixedBaseRelayMaterial;
    public static Material fixedAntennaMaterial;
    static boolean fixedRadiateFromTip;
    public static String fixedUnpoweredNagMessage;
    public static String fixedDenyCreateMessage;
    public static boolean fixedDenyAddMessageBreak;
    public static String fixedDenyAddMessageMessage;

    public static int mobileRadioItem;
    static int mobileInitialRadius;
    static int mobileIncreaseRadius;
    static int mobileMaxRadius;
    static double mobileRadiusStormFactor;
    static double mobileRadiusThunderFactor;
    public static int mobileTaskStartDelaySeconds;
    public static int mobileTaskPeriodSeconds;
    public static boolean mobileRightClickTuneDown;
    public static boolean mobileLeftClickTuneUp;
    public static boolean mobileShiftTune;
    public static int mobileScanBonusRadius;
    public static int mobileScanBonusMaxRadius;
    static boolean mobileSetCompassTarget;
    static boolean mobileSignalLock;
    static String mobileNoSignalsMessage;

    public static boolean verbose;


    @SuppressWarnings("deprecation")
	static public boolean load(RadioBeacon plugin) {
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
        plugin.reloadConfig();      // needed for in-file defaults to take effect

        fixedInitialRadius = plugin.getConfig().getInt("fixedInitialRadius", 100);
        fixedRadiusIncreasePerBlock = plugin.getConfig().getInt("fixedRadiusIncreasePerBlock", 100);
        
        fixedLightningAttractRadiusInitial = plugin.getConfig().getInt("fixedLightningAttractRadiusInitial", 3);
        fixedLightningAttractRadiusIncreasePerBlock = plugin.getConfig().getDouble("fixedLightningAttractRadiusIncreasePerBlock", 0.1);
        fixedLightningAttractRadiusMax = plugin.getConfig().getInt("fixedLightningAttractRadiusMax", 6);
        fixedLightningDamage = plugin.getConfig().getBoolean("fixedLightningDamage", true);
        fixedLightningStrikeOne = plugin.getConfig().getBoolean("fixedLightningStrikeOne", true);
        fixedWeatherListener = plugin.getConfig().getBoolean("fixedWeatherListener", true);

        fixedBlastSetFire = plugin.getConfig().getBoolean("fixedBlastSetFire", true);
        fixedBlastPowerInitial = plugin.getConfig().getDouble("fixedBlastPowerInitial", 1.0);
        fixedBlastPowerIncreasePerBlock = plugin.getConfig().getDouble("fixedBlastPowerIncreasePerBlock", 0.4);
        fixedBlastPowerMax = plugin.getConfig().getDouble("fixedBlastPowerMax", 6.0);

        fixedExplosionReactionDelay = plugin.getConfig().getInt("fixedExplosionReactionDelayTicks", 20);

        fixedRadiusStormFactor = plugin.getConfig().getDouble("fixedRadiusStormFactor", 0.7);
        fixedRadiusThunderFactor = plugin.getConfig().getDouble("fixedRadiusThunderFactor", 1.1);
        fixedRadiusRelayFactor = plugin.getConfig().getDouble("fixedRadiusRelayFactor", 1.0);
        fixedReceptionRadiusDivisor = plugin.getConfig().getInt("fixedReceptionRadiusDivisor", 1);

        fixedMaxHeight = plugin.getConfig().getInt("fixedMaxHeightMeters", 0);

        //if (config.getString("fixedBaseMinY") != null && config.getString("fixedBaseMinY").equals("sealevel")) {  
        // TODO: sea level option? but depends on world
        fixedBaseMinY = plugin.getConfig().getInt("fixedBaseMinY", 0);

        fixedBaseMaterial = Material.matchMaterial(plugin.getConfig().getString("fixedBaseMaterial", "iron_block"));
        if (fixedBaseMaterial == null) {
            RadioBeacon.logger.severe("Failed to match fixedBaseMaterial");
            Bukkit.getServer().getPluginManager().disablePlugin(plugin);
            return false;
        }
        fixedBaseRelayMaterial = Material.matchMaterial(plugin.getConfig().getString("fixedBaseRelayMaterial", "gold_block"));
        if (fixedBaseRelayMaterial == null) {
            RadioBeacon.logger.severe("Failed to match fixedBaseRelayMaterial");
            Bukkit.getServer().getPluginManager().disablePlugin(plugin);
            return false;
        }
 
        fixedAntennaMaterial = Material.matchMaterial(plugin.getConfig().getString("fixedAntennaMaterial"));
        if (fixedAntennaMaterial == null) {
            RadioBeacon.logger.severe("Failed to match fixedAntennaMaterial");
            Bukkit.getServer().getPluginManager().disablePlugin(plugin);
            return false;
        }

        String f = plugin.getConfig().getString("fixedRadiateFrom", "tip");
        if (f.equals("tip")) {
            fixedRadiateFromTip = true;
        } else if (f.equals("base")) {
            fixedRadiateFromTip = false;
        } else {
            RadioBeacon.logger.severe("fixedRadiateFrom not 'tip' nor 'base'");
            Bukkit.getServer().getPluginManager().disablePlugin(plugin);
            return false;
        }          

        fixedUnpoweredNagMessage = plugin.getConfig().getString("fixedUnpoweredNagMessage", "Tip: remove and place this block near redstone current to build an antenna");


        mobileRadioItem = plugin.getConfig().getInt("mobileRadioItem", Material.COMPASS.getId());
        mobileInitialRadius = plugin.getConfig().getInt("mobileInitialRadius", 0);
        mobileIncreaseRadius = plugin.getConfig().getInt("mobileIncreaseRadius", 10);
        mobileMaxRadius = plugin.getConfig().getInt("mobileMaxRadius", 10000);
        mobileRadiusStormFactor = plugin.getConfig().getDouble("mobileRadiusStormFactor", 1.0);
        mobileRadiusThunderFactor = plugin.getConfig().getDouble("mobileRadiusThunderFactor", 1.0);

        fixedDenyCreateMessage = plugin.getConfig().getString("fixedDenyCreateMessage", "Sorry, you do not have permission to build radio towers");
        fixedDenyAddMessageBreak = plugin.getConfig().getBoolean("fixedDenyAddMessageBreak", true);
        fixedDenyAddMessageMessage = plugin.getConfig().getString("fixedDenyAddMessageMessage", "Sorry, you do not have permission to add transmission messages");



        int TICKS_PER_SECOND = 20;
        mobileTaskStartDelaySeconds = plugin.getConfig().getInt("mobileTaskStartDelaySeconds", 0) * TICKS_PER_SECOND;
        mobileTaskPeriodSeconds = plugin.getConfig().getInt("mobileTaskPeriodSeconds", 20) * TICKS_PER_SECOND;

        mobileRightClickTuneDown = plugin.getConfig().getBoolean("mobileRightClickTuneDown", true);
        mobileLeftClickTuneUp = plugin.getConfig().getBoolean("mobileLeftClickTuneUp", true);
        mobileShiftTune = plugin.getConfig().getBoolean("mobileShiftTune", false);
        mobileScanBonusRadius = plugin.getConfig().getInt("mobileScanBonusRadius", 0);
        mobileScanBonusMaxRadius = plugin.getConfig().getInt("mobileScanBonusMaxRadius", 0);
        mobileSetCompassTarget = plugin.getConfig().getBoolean("mobileSetCompassTarget", true);
        mobileSignalLock = plugin.getConfig().getBoolean("mobileSignalLock", true);
        mobileNoSignalsMessage = plugin.getConfig().getString("mobileNoSignalsMessage", "No signals within %d m");

        verbose = plugin.getConfig().getBoolean("verbose", true);
        
        loadAntennas(plugin);


        return true;
    }

    static public boolean isFixedBaseMaterial(Material m) {
        return m == AntennaConf.fixedBaseMaterial || m == AntennaConf.fixedBaseRelayMaterial;
    }

    @SuppressWarnings("deprecation")
	static public boolean isFixedBaseMaterial(int id) {
        return id == AntennaConf.fixedBaseMaterial.getId() || id == AntennaConf.fixedBaseRelayMaterial.getId();
    }
   
    static private YamlConfiguration getAntennaConfig(Plugin plugin) {
        String filename = plugin.getDataFolder() + System.getProperty("file.separator") + "antennas.yml";
        File file = new File(filename);
        return YamlConfiguration.loadConfiguration(file);
    }

    // Load saved antenna information
    static public void loadAntennas(RadioBeacon plugin) {
        YamlConfiguration antennaConfig = getAntennaConfig(plugin);

        List<Map<?,?>> all;
    
        Antenna.xz2Ant = new ConcurrentHashMap<AntennaXZ, Antenna>();   // clear existing

        if (antennaConfig == null || !antennaConfig.isSet("antennas")) {
            RadioBeacon.log("No antennas loaded");
            return;
        }

        // TODO: this is broken in 1.1-R6
        // found   : java.util.List<java.util.Map<?,?>>
        // required: java.util.List<java.util.Map<java.lang.String,java.lang.Object>>
        // http://pastebin.com/f3uhqa9F
        all = antennaConfig.getMapList("antennas");

        int i = 0;
        for (Map<?,?> d: all) {
            try {
                new Antenna(d); 
            } catch (Exception e) {
                RadioBeacon.log("Skipping antenna "+d+": exception "+e);
                continue;
            }
            i += 1;
        }

        RadioBeacon.log("Loaded " + i + " antennas");
    }

    static int lastCount = 0;
    // Save existing antennas
    static public void saveAntennas(RadioBeacon plugin) {
        ArrayList<HashMap<String,Object>> all = new ArrayList<HashMap<String,Object>>();
        YamlConfiguration antennaConfig = getAntennaConfig(plugin);

        int count = 0;

        for (Map.Entry<AntennaXZ,Antenna> pair : Antenna.xz2Ant.entrySet()) {
            Antenna ant = pair.getValue();
    
            all.add(ant.dump());
            count += 1;
        }

        antennaConfig.set("antennas", all);

        try {
            antennaConfig.save(plugin.getDataFolder() + System.getProperty("file.separator") + "antennas.yml");
        } catch (IOException e) {
            RadioBeacon.logger.severe("Failed to save antennas.yml");
        }

        if (count != lastCount) {
            RadioBeacon.log("Saved " + count + " antennas");
            lastCount = count;
        }
    }
}