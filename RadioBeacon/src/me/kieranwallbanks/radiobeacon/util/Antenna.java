package me.kieranwallbanks.radiobeacon.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import me.kieranwallbanks.radiobeacon.AntennaChangeEvent;
import me.kieranwallbanks.radiobeacon.RadioBeacon;
import me.kieranwallbanks.radiobeacon.listener.AntennaPlayerListener;

public class Antenna implements Comparable<Antenna> {
    // TODO: map by world first? see discussion http://forums.bukkit.org/threads/performance-question-merge-world-with-chunk-coordinates-or-not.60160/#post-969934
    static public ConcurrentHashMap<AntennaXZ, Antenna> xz2Ant = new ConcurrentHashMap<AntennaXZ, Antenna>();

    public final AntennaXZ xz;
    public final int baseY;
    public int tipY;

    public String message;
    public final boolean isRelay;

    // Normal antenna creation method
    public Antenna(Location loc) {
        xz = new AntennaXZ(loc);
        baseY = (int)loc.getY();
        tipY = baseY;

        xz2Ant.put(xz, this);

        isRelay = loc.getBlock().getType() == AntennaConf.fixedBaseRelayMaterial;

        RadioBeacon.log("New antenna " + this);

        Bukkit.getServer().getPluginManager().callEvent(new AntennaChangeEvent(this, AntennaChangeEvent.Action.CREATE));
    }

    // Load from serialized format (from disk)
    public Antenna(Map<?,?> d) {
        World world;

        if (d.get("world") != null) {
            // Legacy world
            world = Bukkit.getWorld(UUID.fromString((String)d.get("world")));
            if (world == null) {
                // TODO: gracefully handle, and skip this antenna (maybe its world was deleted, no big deal)
                throw new RuntimeException("Antenna loading failed, no world with UUID: " + d.get("world"));
            }
        } else {
            world = Bukkit.getWorld((String)d.get("W"));
            if (world == null) {
                throw new RuntimeException("Antenna loading failed, no world with name: "+ d.get("W"));
            }
        }

        if (d.get("baseX") != null) {
            // Legacy format, tipX and tipZ are redundant
            xz = new AntennaXZ(world, (Integer)d.get("baseX"), (Integer)d.get("baseZ"));
        } else {
            xz = new AntennaXZ(world, (Integer)d.get("X"), (Integer)d.get("Z"));
        }
        baseY = (Integer)d.get("baseY");
        tipY = (Integer)d.get("tipY");
        if (d.get("relay") != null) {
            isRelay = (Boolean)d.get("relay");
        } else {
            isRelay = false;
        }

        setMessage((String)d.get("message"));

        xz2Ant.put(xz, this);

        RadioBeacon.log("Loaded antenna " + this);
    }

    // Dump to serialized format (to disk)
    public HashMap<String,Object> dump() {
        HashMap<String,Object> d = new HashMap<String,Object>();

        // For simplicity, dump as a flat data structure

        //d.put("world", xz.world.getUID().toString());
        d.put("W", xz.world.getName());

        d.put("X", xz.x);
        d.put("Z", xz.z);
        d.put("baseY", baseY);
        d.put("tipY", tipY);

        d.put("message", message);
        d.put("relay", isRelay);
        // TODO: other user data?

        return d;
    }

    public String toString() {
        return "<Antenna r="+getBroadcastRadius()+" height="+getHeight()+" xz="+xz+" baseY="+baseY+" tipY="+tipY+" w="+xz.world.getName()+
            " l="+getLightningAttractRadius()+" p="+getBlastPower()+
            " r="+isRelay+" "+
            " m="+message+">";
    }

    public static Antenna getAntenna(Location loc) {
        return getAntenna(new AntennaXZ(loc));
    }
    public static Antenna getAntenna(AntennaXZ loc) {
        Antenna a = xz2Ant.get(loc);
        return a;
    }

    // Get an antenna by base directly adjacent to given location
    public static Antenna getAntennaByAdjacent(Location loc) {
        for (int x = -1; x <= 1; x += 1) {
            for (int z = -1; z <= 1; z += 1) {
                Antenna ant = getAntenna(loc.clone().add(x+0.5, 0, z+0.5));
                if (ant != null) {
                    return ant;
                }
            }
        }
        return null;
    }

    public static void destroy(Antenna ant) {
        if (xz2Ant.remove(ant.xz) == null) {
            throw new RuntimeException("No antenna at "+ant.xz+" to destroy!");
        }

        RadioBeacon.log("Destroyed antenna " + ant);
        Bukkit.getServer().getPluginManager().callEvent(new AntennaChangeEvent(ant, AntennaChangeEvent.Action.DESTROY));
    }

    // Set or get textual message being broadcasted (may be null for none)
    public void setMessage(String m) {
        message = m;
        
        Bukkit.getServer().getPluginManager().callEvent(new AntennaChangeEvent(this, AntennaChangeEvent.Action.MESSAGE));
    }

    public String getMessage() {
        return message;
    }

    // Extend or shrink size of the antenna, updating the new center location
    public void setTipY(int newTipY) {
        RadioBeacon.log("Move tip from "+tipY+" to + " +newTipY);
        tipY = newTipY;

        Bukkit.getServer().getPluginManager().callEvent(new AntennaChangeEvent(this, AntennaChangeEvent.Action.TIP_MOVE));
    }

    // Set new tip at highest Y with iron fences, starting at given Y
    // Will set at or above placedY
    @SuppressWarnings("deprecation")
	public void setTipYAtHighest(int placedY) {
        World world = xz.world;
        int x = xz.x;
        int z = xz.z;

        // Starting at location, get the highest Y coordinate in the world that is part of the antenna material

        // Look up until hit first non-antenna material
        // If pillaring up, won't enter loop at all
        // But we have to check, so antennas with gaps can be 'repaired' to extend their
        // range to their full tip
        int newTipY = placedY;
        while(world.getBlockTypeIdAt(x, newTipY, z) == AntennaConf.fixedAntennaMaterial.getId()) {
            newTipY += 1;
        }

        setTipY(newTipY);
    }

    public Location getTipLocation() {
        return xz.getLocation(tipY);
    }

    public Location getSourceLocation() {
        return AntennaConf.fixedRadiateFromTip ? getTipLocation() : getBaseLocation();
    }
    
    public Location getBaseLocation() {
        return xz.getLocation(baseY);
    }

    public int getHeight() {
        return tipY - baseY;
    }

    // Get radius of broadcasts for fixed antenna
    public int getBroadcastRadius() {
        int height = getHeight();
        if (AntennaConf.fixedMaxHeight != 0 && height > AntennaConf.fixedMaxHeight) {
            // Above max will not extend range
            height = AntennaConf.fixedMaxHeight;
        } 

        // TODO: exponential not multiplicative?
        int radius = AntennaConf.fixedInitialRadius + height * AntennaConf.fixedRadiusIncreasePerBlock;

        if (xz.world.hasStorm()) {
            radius = (int)((double)radius * AntennaConf.fixedRadiusStormFactor);
        }
        if (xz.world.isThundering()) {
            radius = (int)((double)radius * AntennaConf.fixedRadiusThunderFactor);
        }

        if (isRelay) {
            radius = (int)((double)radius * AntennaConf.fixedRadiusRelayFactor);
        }

        return radius;
    }

    // Get radius of reception for fixed antenna
    // This is normally same as broadcast, but can be changed
    public int getReceptionRadius() {
        if (AntennaConf.fixedReceptionRadiusDivisor == 0) {
            // special meaning no reception radius (must directly overlap)
            return 0;
        }

        int receptionRadius = getBroadcastRadius() / AntennaConf.fixedReceptionRadiusDivisor;

        return receptionRadius;
    }

    // 2D radius within lightning strike will strike base
    public int getLightningAttractRadius() {
        int attractRadius = (int)(AntennaConf.fixedLightningAttractRadiusInitial + getHeight() * AntennaConf.fixedLightningAttractRadiusIncreasePerBlock);

        return Math.min(attractRadius, AntennaConf.fixedLightningAttractRadiusMax);
    }

    // Explosive power on direct lightning strike
    public float getBlastPower() {
        float power = (float)(AntennaConf.fixedBlastPowerInitial + getHeight() * AntennaConf.fixedBlastPowerIncreasePerBlock);

        return Math.min(power, (float)AntennaConf.fixedBlastPowerMax);
    }

    public boolean withinReceiveRange(Location receptionLoc, int receptionRadius) {
        if (!xz.world.equals(receptionLoc.getWorld())) {
            // No cross-world communication... yet! TODO: how?
            return false;
        }
       
        // Sphere intersection of broadcast range from source
        return getSourceLocation().distanceSquared(receptionLoc) < square(getBroadcastRadius() + receptionRadius);
    }

    // Square a number, returning a double as to not overflow if x>sqrt(2**31)
    private static double square(int x) {
        return (double)x * (double)x;
    }

    // Get 3D distance from tip
    public int getDistance(Location receptionLoc) {
        return (int)Math.sqrt(getSourceLocation().distanceSquared(receptionLoc));
    }

    // Get 2D distance from antenna xz
    public double get2dDistance(Location otherLoc) {
        Location otherLoc2d = otherLoc.clone();
        Location baseLoc = getBaseLocation();

        otherLoc2d.setY(baseLoc.getY());

        return baseLoc.distance(otherLoc2d);
    }

    // Return whether antenna is in same world as other location
    public boolean inSameWorld(Location otherLoc) {
        return xz.world.equals(otherLoc.getWorld());
    }

    // Receive antenna signals (to this antenna) and show to player
    public void receiveSignals(Player player) {
        player.sendMessage("Antenna range: " + getBroadcastRadius() + " m"); //, lightning attraction: " + getLightningAttractRadius() + " m" + ", blast power: " + getBlastPower());

        receiveSignals(player, getSourceLocation(), getReceptionRadius(), false);
    }

    // Update any nearby relay antennas with message from this antenna, informing the player
    public void notifyRelays(Player player) {
        List<Antenna> nearbyAnts = receiveSignals(player, getSourceLocation(), getReceptionRadius(), false);
      
        // Update any relay antennas within range
        for (Antenna ant: nearbyAnts) {
            if (ant.isRelay) {
                int distance = ant.getDistance(getSourceLocation());

                ant.setMessage("[Relayed " + distance + " m] " + this.getMessage());
                RadioBeacon.log("Notified relay: " + ant);
                player.sendMessage("Notified relay " + distance + " m away");
            }
        }
    }

    // Receive signals from mobile radio held by player
    @SuppressWarnings("deprecation")
	static public void receiveSignalsAtPlayer(Player player) {
		ItemStack item = player.getItemInHand();

        if (item == null || item.getTypeId() != AntennaConf.mobileRadioItem && AntennaPlayerListener.playerRadioEnabled(player)) {
            // Compass = mobile radio
            return;
        }

        Location receptionLoc = player.getLocation();
        int receptionRadius = getCompassRadius(item, player);

        Antenna.receiveSignals(player, receptionLoc, receptionRadius, true);
    }

    // Get reception radius for a stack of compasses
    // The default of one compass has a radius of 0, meaning you must be directly within range,
    // but more compasses can increase the range further
    static public int getCompassRadius(ItemStack item, Player player) {
        World world = player.getWorld();

        // Bigger stack of compasses = better reception!
        int n = item.getAmount() - 1;
        int receptionRadius = AntennaConf.mobileInitialRadius + n * AntennaConf.mobileIncreaseRadius;

        // If scan bonus enabled, add 
        if (AntennaConf.mobileScanBonusRadius != 0) {
            Integer bonusObject = AntennaPlayerListener.playerScanBonus.get(player.getUniqueId());
            if (bonusObject != null) {
                receptionRadius += bonusObject.intValue();
            }
        }

        if (world.hasStorm()) {
            receptionRadius = (int)((double)receptionRadius * AntennaConf.mobileRadiusStormFactor);
        }
        if (world.isThundering()) {
            receptionRadius = (int)((double)receptionRadius * AntennaConf.mobileRadiusThunderFactor);
        }


        receptionRadius = Math.min(receptionRadius, AntennaConf.mobileMaxRadius);

        return receptionRadius;
    }


    // Receive signals from standing at any location
    static public List<Antenna> receiveSignals(Player player, Location receptionLoc, int receptionRadius, boolean signalLock) {
        int count = 0;
        List<Antenna> nearbyAnts = new ArrayList<Antenna>();

        for (Map.Entry<AntennaXZ,Antenna> pair : Antenna.xz2Ant.entrySet()) {
            Antenna otherAnt = pair.getValue();

            if (otherAnt.withinReceiveRange(receptionLoc, receptionRadius)) {
                //RadioBeacon.log("Received transmission from " + otherAnt);

                int distance = otherAnt.getDistance(receptionLoc);
                if (distance == 0) {
                    // Squelch self-transmissions to avoid interference
                    continue;
                }

                nearbyAnts.add(otherAnt);
            }
        }

        // Sort so reception list is deterministic, for target index
        Collections.sort(nearbyAnts, new AntennaDistanceComparator(receptionLoc));
        for (Antenna otherAnt: nearbyAnts) {
            notifySignal(player, receptionLoc, otherAnt, otherAnt.getDistance(receptionLoc));
        }

        count = nearbyAnts.size();
        if (count == 0) {
            if (AntennaConf.mobileNoSignalsMessage != null && !AntennaConf.mobileNoSignalsMessage.equals("")) {
                player.sendMessage(AntennaConf.mobileNoSignalsMessage.replace("%d", ""+receptionRadius));
            }
        } else if (signalLock) {
            if (AntennaConf.mobileSignalLock) {
                // Player radio compass targetting
                Integer targetInteger = AntennaPlayerListener.playerTargets.get(player.getUniqueId());
                Location targetLoc;
                int targetInt;
                if (targetInteger == null) {
                    targetInt = 0;
                } else {
                    targetInt = Math.abs(targetInteger.intValue()) % count;
                }

                Antenna antLoc = nearbyAnts.get(targetInt);
                targetLoc = antLoc.getSourceLocation();
                if (AntennaConf.mobileSetCompassTarget) {
                    player.setCompassTarget(targetLoc);
                }

                String message = antLoc.getMessage();
                player.sendMessage("Locked onto signal at " + antLoc.getDistance(player.getLocation()) + " m" + (message == null ? "" : ": " + message));
                //RadioBeacon.log("Targetting " + targetLoc);
            }
        }

        return nearbyAnts;
    }

    // Tell player about an incoming signal from an antenna
    static private void notifySignal(Player player, Location receptionLoc, Antenna ant, int distance) {
        String message = "";
        if (ant.message != null) {
            message = ": " + ant.message;
        }

        player.sendMessage("Received transmission (" + distance + " m)" + message);
    }

    // Check if antenna is intact, what we know about it matching reality
    // Returns whether had to fix it
    public boolean checkIntact() {
        World world = xz.world;
        int x = xz.x;
        int z = xz.z;

        // Base
        Location base = new Location(world, x, baseY, z);
        if (base.getBlock() == null || !AntennaConf.isFixedBaseMaterial(base.getBlock().getType())) {
            RadioBeacon.log("checkIntact: antenna is missing base!");
            destroy(this);
            return false;
        }

        // Antenna
        for (int y = baseY + 1; y < tipY; y += 1) {
            Location piece = new Location(world, x, y, z);

            if (piece.getBlock() == null || piece.getBlock().getType() != AntennaConf.fixedAntennaMaterial) {
                RadioBeacon.log("checkIntact: antenna is shorter than expected!");
                setTipY(y);
                return false;
            }
        }

        return true;
    }

    public static void checkIntactAll(CommandSender sender) {
        int count = 0, fixed = 0;

        for (Map.Entry<AntennaXZ,Antenna> pair : Antenna.xz2Ant.entrySet()) {
            Antenna ant = pair.getValue();

            if (!ant.checkIntact()) {
                fixed += 1;
            }
    
            count += 1;
        }
        sender.sendMessage("Updated "+fixed+" of "+count+" antennas");
    }

   
    // Delegate comparison to location
    public int compareTo(Antenna otherAnt) {
        return xz.compareTo(otherAnt.xz);
    }
}