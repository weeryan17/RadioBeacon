
package com.exphc.RadioBeacon;

import java.util.Map;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.*;
import org.bukkit.Material.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.command.*;
import org.bukkit.inventory.*;
import org.bukkit.*;

// Integral location (unlike Bukkit Location)
class AntennaLocation implements Comparable {
    static Logger log = Logger.getLogger("Minecraft");
    World world;
    int x, y, z;

    public AntennaLocation(World w, int x0, int y0, int z0) {
        world = w;
        x = x0;
        y = y0;
        z = z0;
    }

    public AntennaLocation(Location loc) {
        world = loc.getWorld();
        x = loc.getBlockX();
        y = loc.getBlockY();
        z = loc.getBlockZ();
    }

    public Location getLocation() {
        return new Location(world, x + 0.5, y + 0.5, z + 0.5);
    }

    public String toString() {
        return x + "," + y + "," + z;
    }

    public int compareTo(Object obj) {
        if (!(obj instanceof AntennaLocation)) {
            return -1;
        }
        AntennaLocation rhs = (AntennaLocation)obj;

        // TODO: also compare world
        if (x - rhs.x != 0) {
            return x - rhs.x;
        } else if (y - rhs.y != 0) {
            return y - rhs.y;
        } else if (z - rhs.z != 0) {
            return z - rhs.z;
        }

        return 0;
    }

    public boolean equals(Object obj) {
        return compareTo(obj) == 0;      // why do I have to do this myself?
    }

    public int hashCode() {
        // lame hashing TODO: improve?
        return x * y * z;   
    }
}

class Antenna {
    static Logger log = Logger.getLogger("Minecraft");

    // TODO: change location to store x,z for lookup, and tip/base (y) separately. 
    // (Then can detect destroying middle of antenna)
    static ConcurrentHashMap<AntennaLocation, Antenna> tipsAt = new ConcurrentHashMap<AntennaLocation, Antenna>();
    static ConcurrentHashMap<AntennaLocation, Antenna> basesAt = new ConcurrentHashMap<AntennaLocation, Antenna>();

    AntennaLocation tipAt;      // broadcast tip
    AntennaLocation baseAt;     // control station
    String message;


    public Antenna(Location loc) {
        tipAt = new AntennaLocation(loc);
        baseAt = new AntennaLocation(loc);

        tipsAt.put(tipAt, this);
        basesAt.put(baseAt, this);
        log.info("New antenna at " + tipAt);
    }

    public static Antenna getAntennaByTip(Location loc) {
        return tipsAt.get(new AntennaLocation(loc));
    }
    
    public static Antenna getAntennaByBase(Location loc) {
        return basesAt.get(new AntennaLocation(loc));
    }

    // Get an antenna by base directly adjacent to given location
    public static Antenna getAntennaByBaseAdjacent(Location loc) {
        for (int x = -1; x <= 1; x += 1) {
            for (int z = -1; z <= 1; z += 1) {
                Antenna ant = getAntennaByBase(loc.clone().add(x+0.5, 0, z+0.5));
                if (ant != null) {
                    return ant;
                }
            }
        }
        return null;
    }

    public static void destroy(Antenna ant) {
        destroyTip(ant);
        destroyBase(ant);
    }

    public static void destroyTip(Antenna ant) {
        if (tipsAt.remove(ant.tipAt) == null) {
            throw new RuntimeException("No antenna tip found to destroy at " + ant.tipAt);
        }
    }

    public static void destroyBase(Antenna ant) {
        if (basesAt.remove(ant.baseAt) == null) {
            throw new RuntimeException("No antenna base found to destroy at " + ant.baseAt);
        }
    }

    // Extend or shrink size of the antenna, updating the new center location
    public void setTipLocation(Location newLoc) {
        log.info("Move tip from "+tipAt+" to + " + newLoc);
        destroyTip(this);

        tipAt = new AntennaLocation(newLoc);

        tipsAt.put(tipAt, this);
    }

    public Location getTipLocation() {
        return tipAt.getLocation();
    }
    
    public Location getBaseLocation() {
        return baseAt.getLocation();
    }

    public int getHeight() {
        return tipAt.y - baseAt.y;
    }

    public int getBroadcastRadius() {
        // TODO: configurable, tweak
        // TODO: configurable base distance (+)
        // TODO: exponential not multiplicative?
        return 100 + getHeight() * 100;  
    }

    public String toString() {
        return "<Antenna r="+getBroadcastRadius()+", height="+getHeight()+", tip="+tipAt+", base="+baseAt+">";
    }

    public boolean withinRange(Location receptionLoc, int receptionRadius) {
        // Sphere intersection of broadcast range from tip
        return getTipLocation().distanceSquared(receptionLoc) < square(getBroadcastRadius() + receptionRadius);
    }

    private static int square(int x) {
        return x * x;
    }

    public int getDistance(Location receptionLoc) {
        return (int)Math.sqrt(getTipLocation().distanceSquared(receptionLoc));
    }

    // Receive antenna signals (to this antenna) and show to player
    public void receiveSignals(Player player) {
        player.sendMessage("Antenna range: " + getBroadcastRadius() + " m");

        receiveSignals(player, getTipLocation(), getBroadcastRadius());
    }

    // Receive signals at any location
    static public void receiveSignals(Player player, Location receptionLoc, int receptionRadius) {
        Iterator it = Antenna.tipsAt.entrySet().iterator();
        int count = 0;
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            Antenna otherAnt = (Antenna)pair.getValue();

            if (otherAnt.withinRange(receptionLoc, receptionRadius)) {
                log.info("Received transmission from " + otherAnt);
                String message = "";
                if (otherAnt.message != null) {
                    message = ": " + otherAnt.message;
                }
    
                int distance = otherAnt.getDistance(receptionLoc);
                if (distance == 0) {
                    // Squelch self-transmissions to avoid interference
                    continue;
                }

                player.sendMessage("Received transmission " + distance + " m away" + message);
            }

            count += 1;
        }
        if (count == 0) {
            player.sendMessage("No signals received within " + receptionRadius + " m");
        }
    }
}

class BlockPlaceListener extends BlockListener {
    Logger log = Logger.getLogger("Minecraft");
    RadioBeacon plugin;

    public BlockPlaceListener(RadioBeacon pl) {
        plugin = pl;
    }

    // Building an antenna
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        if (block.getType() == Material.IRON_BLOCK) {
            // Base material for antenna, if powered
            if (block.isBlockPowered() || block.isBlockIndirectlyPowered()) {
                new Antenna(block.getLocation());
                player.sendMessage("New antenna created");
            }
        } else if (block.getType() == Material.IRON_FENCE) {
            Block against = event.getBlockAgainst();

            Antenna existingAnt = Antenna.getAntennaByTip(against.getLocation());
            if (existingAnt != null) {
                existingAnt.setTipLocation(block.getLocation());
                player.sendMessage("Extended antenna range to " + existingAnt.getBroadcastRadius() + " m");
            }
        } 
    }

    // Destroying an antenna
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        World world = block.getWorld();

        if (block.getType() == Material.IRON_BLOCK) {
            Antenna ant = Antenna.getAntennaByTip(block.getLocation());
            
            if (ant != null) {
                event.getPlayer().sendMessage("Destroyed antenna " + ant);
                ant.destroy(ant);
            }
        } else if (block.getType() == Material.IRON_FENCE) {
            Antenna ant = Antenna.getAntennaByTip(block.getLocation());

            if (ant != null) {
                // Verify whole length of antenna is intact
                int i = ant.getHeight();
                boolean destroy = false;
                while(i > 0) {
                    Location locBelow = ant.getTipLocation().subtract(0, i, 0); 
                    Block blockBelow = world.getBlockAt(locBelow);

                    if (blockBelow.getType() != Material.IRON_BLOCK && blockBelow.getType() != Material.IRON_FENCE) {
                        destroy = true;
                        break;
                    }
                    
                    i -= 1;
                }
                if (destroy) {
                    // Tip became disconnected from base, so destroy
                    // Note: won't detect all cases, only if tip is destroyed (not connecting blocks)
                    event.getPlayer().sendMessage("Destroyed antenna " + ant);
                    Antenna.destroy(ant);
                } else {
                    ant.setTipLocation(ant.getTipLocation().subtract(0, 1, 0));
                    event.getPlayer().sendMessage("Shrunk antenna range to " + ant.getBroadcastRadius() + " m");
                }
            }
        } else if (block.getType() == Material.WALL_SIGN || block.getType() == Material.SIGN_POST) {
            Antenna ant = Antenna.getAntennaByBaseAdjacent(block.getLocation());
            if (ant != null) {
                event.getPlayer().sendMessage("Cleared antenna message");
                ant.message = null;
            }
        }
    }

    // Signs to set transmission message
    public void onSignChange(SignChangeEvent event) {
        Block block = event.getBlock();
        String[] text = event.getLines();

        Antenna ant = Antenna.getAntennaByBaseAdjacent(block.getLocation());
        if (ant != null) {
            ant.message = joinString(text);
        }
        event.getPlayer().sendMessage("Set transmission message: " + ant.message);
    }

    public static String joinString(String[] a) {
        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < a.length; i+= 1) {
            buffer.append(a[i]);
            buffer.append(" ");
        }
        return buffer.toString();
    }

    // Currently antennas retain their magnetized properties even when redstone current is removed
/*
    public void onBlockRedstoneChange(BlockRedstoneEvent event) {
         // TODO: find out how to disable antennas, get when block becomes unpowered
        World world = event.getBlock().getWorld();

        if (event.getOldCurrent() == 0) {
            // TODO: find antenna at location and disable
            log.info("current turned off at "+event.getBlock());

            for (Antenna ant: plugin.ants) {
                // TODO: efficiency
                Block block = world.getBlockAt(ant.location);
                log.info("ant block:"+block);
            }
        }
    }
        */
}

class PlayerInteractListener extends PlayerListener {
    Logger log = Logger.getLogger("Minecraft");
    RadioBeacon plugin;

    public PlayerInteractListener(RadioBeacon pl) {
        plugin = pl;
    }

    public void onPlayerInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        ItemStack item = event.getItem();

        if (block != null && block.getType() == Material.IRON_BLOCK) {
            Antenna ant = Antenna.getAntennaByBase(block.getLocation());
            if (ant == null) {
                return;
            }

            /* TODO: change to portable
            if (item == null || item.getType() != Material.COMPASS) {
                event.getPlayer().sendMessage("You can use this antenna with a radio (compass)");
                return;
            }
            */
            ant.receiveSignals(event.getPlayer());

        }
        // TODO: also activate if click the _sign_ adjacent to the base
        // TODO: and if click anywhere within antenna? maybe not unless holding compass
    }
}


class ReceptionTask implements Runnable {
    Logger log = Logger.getLogger("Minecraft");
    int taskId;

    public void run() {
        for (Player player: Bukkit.getOnlinePlayers()) {
            ItemStack item = player.getItemInHand();

            if (item != null && item.getType() == Material.COMPASS) {
                // Compass = portable radio

                Location receptionLoc = player.getLocation();
                // Bigger stack of compasses = better reception!
                // TODO: configurable
                int receptionRadius = item.getAmount() * 100;   


                Antenna.receiveSignals(player, receptionLoc, receptionRadius);
            }
        }
    }
}

public class RadioBeacon extends JavaPlugin {
    Logger log = Logger.getLogger("Minecraft");
    BlockListener blockListener;
    PlayerListener playerListener;
    ReceptionTask receptionTask;

    public void onEnable() {

        blockListener = new BlockPlaceListener(this);
        playerListener = new PlayerInteractListener(this);
        receptionTask = new ReceptionTask();

        Bukkit.getPluginManager().registerEvent(org.bukkit.event.Event.Type.BLOCK_PLACE, blockListener, org.bukkit.event.Event.Priority.Lowest, this);
        Bukkit.getPluginManager().registerEvent(org.bukkit.event.Event.Type.BLOCK_BREAK, blockListener, org.bukkit.event.Event.Priority.Lowest, this);
        Bukkit.getPluginManager().registerEvent(org.bukkit.event.Event.Type.SIGN_CHANGE, blockListener, org.bukkit.event.Event.Priority.Lowest, this);
        //TODO? getServer().getPluginManager().registerEvent(org.bukkit.event.Event.Type.REDSTONE_CHANGE, blockListener, org.bukkit.event.Event.Priority.Lowest, this);
        
        Bukkit.getPluginManager().registerEvent(org.bukkit.event.Event.Type.PLAYER_INTERACT, playerListener, org.bukkit.event.Event.Priority.Lowest, this);

        // in ticks
        long delayBeforeStarting = 0;
        long period = 5*20;
        int taskId = Bukkit.getScheduler().scheduleAsyncRepeatingTask(this, receptionTask, delayBeforeStarting, period);
        if (taskId == -1) {
            throw new RuntimeException("Failed to schedule radio signal reception task");
        }
        receptionTask.taskId = taskId;


        // TODO: load saved antennas
        log.info("beacon enable");
    }

    public void onDisable() {
        // TODO: load saved antennas
        log.info("beacon disable");
    }

    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("ant")) {
            return false;
        }

        Iterator it = Antenna.tipsAt.entrySet().iterator();
        int count = 0;
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            AntennaLocation at = (AntennaLocation)pair.getKey();
            Antenna ant = (Antenna)pair.getValue();

            sender.sendMessage("Antenna tip at " + ant);
            // TODO: bases
            count += 1;
        }
        sender.sendMessage("Found " + count + " antennas");

        return true;
    }
}
