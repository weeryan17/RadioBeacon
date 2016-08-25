package me.kieranwallbanks.radiobeacon.listener;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;

import me.kieranwallbanks.radiobeacon.RadioBeacon;
import me.kieranwallbanks.radiobeacon.util.Antenna;
import me.kieranwallbanks.radiobeacon.util.AntennaConf;

public class AntennaPlayerListener implements Listener {
    RadioBeacon plugin;

    // Note, using UUID instead of Player for HashMap for reasons on
    // https://github.com/Bukkit/CraftBukkit/commit/2ae0b94ecf4f168a65f585b802b1f886a6cd7e32#-P0

    // Compass targets index selection
    public static ConcurrentHashMap<UUID, Integer> playerTargets = new ConcurrentHashMap<UUID, Integer>();

    // How many scan iterations the player has faithfully held onto their compass for
    public static ConcurrentHashMap<UUID, Integer> playerScanBonus = new ConcurrentHashMap<UUID, Integer>();

    // Whether player portable radio has been disabled using /toggleradio
    public static ConcurrentHashMap<UUID, Boolean> playerDisabled = new ConcurrentHashMap<UUID, Boolean>();

    public static boolean playerRadioEnabled(Player player) {
        Boolean disabledObject = AntennaPlayerListener.playerDisabled.get(player.getUniqueId());
        boolean disabled = false;
        if (disabledObject != null) {
            disabled = disabledObject.booleanValue();
        }

        return !disabled;
    }

 
    public AntennaPlayerListener(RadioBeacon pl) {
        plugin = pl;

        Bukkit.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @SuppressWarnings("deprecation")
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled=true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        ItemStack item = event.getItem();
        Player player = event.getPlayer();
        
        if (block != null && AntennaConf.isFixedBaseMaterial(block.getType())) {
            Antenna ant = Antenna.getAntenna(block.getLocation());
            if (ant == null) {
                return;
            }
            if(!this.isInOverClick(player)){
            	ant.receiveSignals(player);
            	this.preventOverClick(player);
            }
        } else if (block != null && block.getType() == Material.WALL_SIGN) {
            for (int dx = -1; dx <= 1; dx += 1) {
                for (int dz = -1; dz <= 1; dz += 1) {
                    Antenna ant = Antenna.getAntenna(block.getLocation().add(dx, 0, dz));

                    if (ant != null) {
                    	if(!this.isInOverClick(player)){
                        	ant.receiveSignals(player);
                        	this.preventOverClick(player);
                    	}
                    }
                }
            }

            // TODO: and if click anywhere within antenna? maybe not unless holding compass
        } else if (item != null && item.getTypeId() == AntennaConf.mobileRadioItem && AntennaPlayerListener.playerRadioEnabled(player)) {
            if (AntennaConf.mobileShiftTune) {
                // hold Shift + click to tune
                if (!player.isSneaking()) { 
                    return;
                }
            }

            // Increment target index
            Integer targetInteger = playerTargets.get(player.getUniqueId());
            int targetInt;
            if (targetInteger == null) {
                playerTargets.put(player.getUniqueId(), 0);
                targetInt = 0;
            } else {
                // Tune up or down
                int delta;
                if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR) {
                    delta = -1;
                    if (!AntennaConf.mobileRightClickTuneDown) {
                        return;
                    }
                } else {
                    delta = 1;
                    if (!AntennaConf.mobileLeftClickTuneUp) {
                        return;
                    }

                }
                // TODO: show direction in message?

                targetInt = targetInteger.intValue() + delta;
                playerTargets.put(player.getUniqueId(), targetInt);
            }
            int receptionRadius = Antenna.getCompassRadius(item, player);
            player.sendMessage("Tuned radio" + (receptionRadius == 0 ? "" : " (range " + receptionRadius + " m)"));

        }
    }

    @SuppressWarnings("deprecation")
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled=true)
    public void onItemHeldChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getNewSlot());

        if (item != null && item.getTypeId() == AntennaConf.mobileRadioItem && AntennaPlayerListener.playerRadioEnabled(player)) {
            // TODO: this actually doesn't receive signals on change, since this method checks
            // the player's items in hand, and the event is called before they actually change -
            // but, I actually like this design better since the player has to wait to receive.
            Antenna.receiveSignalsAtPlayer(player);
        } else {    
            // if scan increase is enabled, changing items resets scan bonus
            if (AntennaConf.mobileScanBonusRadius != 0) { 
                playerScanBonus.put(player.getUniqueId(), 0);
            }
        }
    }
    final static ArrayList<Player> overclick = new ArrayList<Player>();
    public void preventOverClick(final Player p){
    	overclick.add(p);
    	Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable(){

			@Override
			public void run() {
				overclick.remove(p);
			}
    		
    	}, 10L);
    }
    
    public boolean isInOverClick(Player p){
    	if(overclick.contains(p)){
    		return true;
    	} else {
    		return false;
    	}
    }
}