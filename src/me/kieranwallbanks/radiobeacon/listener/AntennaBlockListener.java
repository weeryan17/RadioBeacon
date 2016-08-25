package me.kieranwallbanks.radiobeacon.listener;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import me.kieranwallbanks.radiobeacon.RadioBeacon;
import me.kieranwallbanks.radiobeacon.tasks.AntennaExplosionReactionTask;
import me.kieranwallbanks.radiobeacon.util.Antenna;
import me.kieranwallbanks.radiobeacon.util.AntennaConf;
import me.kieranwallbanks.radiobeacon.util.AntennaXZ;

public class AntennaBlockListener implements Listener {
    RadioBeacon plugin;

    public AntennaBlockListener(RadioBeacon plugin) {
        this.plugin = plugin;

        Bukkit.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // Building an antenna
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled=true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        if (AntennaConf.isFixedBaseMaterial(block.getType())) {
            // Base material for antenna, if powered
            if (block.isBlockPowered() || block.isBlockIndirectlyPowered()) {
                if (!player.hasPermission("radiobeacon.create")) {
                    String message = AntennaConf.fixedDenyCreateMessage;
                    if (message != null && !message.equals("")) {
                        player.sendMessage(message);
                    }
                    return;
                }

                if (block.getY() < AntennaConf.fixedBaseMinY) {
                    player.sendMessage("Not creating antenna below depth of " + AntennaConf.fixedBaseMinY + " m");
                } else {
                    Antenna ant = new Antenna(block.getLocation());

                    // Usually, will be placing a new antenna from scratch.. but if they are repairing
                    // look for the highest iron bars above it
                    Location above = block.getLocation().add(0, 1, 0);
                    if (above.getBlock().getType() == AntennaConf.fixedAntennaMaterial) {
                        ant.setTipYAtHighest(above.getBlockY());
                    }

                    if (ant.isRelay) {
                        player.sendMessage("New relay antenna created");
                    } else {
                        player.sendMessage("New antenna created"); //, with range "+ant.getBroadcastRadius()+" m");
                    }
                }
            } else {
                if (AntennaConf.fixedUnpoweredNagMessage != null && !AntennaConf.fixedUnpoweredNagMessage.equals("")) {
                    player.sendMessage(AntennaConf.fixedUnpoweredNagMessage);
                }
            }
        } else if (block.getType() == AntennaConf.fixedAntennaMaterial) {
            Antenna ant = Antenna.getAntenna(block.getLocation());
            if (ant == null) {
                // No antenna at this xz column to extend
                return;
            }

            int placedY = block.getLocation().getBlockY();
            if (placedY < ant.baseY) {
                // Coincidental placement below antenna
                return;
            }

            if (placedY > ant.tipY + 1) {
                // Might be trying to extend, but it is too far above the tip
                // so is not (yet) contiguous
                return;
            }

            int oldRadius = ant.getBroadcastRadius();
            ant.setTipYAtHighest(placedY);
            int newRadius = ant.getBroadcastRadius();

            if (oldRadius == newRadius) { 
                player.sendMessage("Reached maximum " + newRadius + " m");
            } else {
                player.sendMessage("Extended antenna range to " + newRadius + " m");
            }
        } 
    }

    // Destroying an antenna
    @SuppressWarnings("deprecation")
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled=true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        World world = block.getWorld();

        if (AntennaConf.isFixedBaseMaterial(block.getType())) {
            Antenna ant = Antenna.getAntenna(block.getLocation());
            
            if (ant == null) {
                // No antenna at this xz column
                return;
            }

            if (ant.baseY != block.getLocation().getBlockY()) {
                // A coincidental iron block above or below the actual antenna base
                return;
            }

            Antenna.destroy(ant);
            event.getPlayer().sendMessage("Destroyed antenna");
        } else if (block.getType() == AntennaConf.fixedAntennaMaterial) {
            Antenna ant = Antenna.getAntenna(block.getLocation());

            if (ant == null) {
                return;
            }

            int destroyedY = block.getLocation().getBlockY();

            if (destroyedY < ant.baseY || destroyedY > ant.tipY) {
                // A coincidental antenna block below or above the antenna, ignore
                return;
            } 

            // Look down from the broken tip, to the first intact antenna/base piece
            int newTipY = destroyedY;
            int pieceType;
            int x = block.getLocation().getBlockX();
            int z = block.getLocation().getBlockZ();
            // Nearly always, this will only execute once, but if the antenna changed 
            // without us knowing, just be sure, and check the block(s) below until
            // we find valid antenna material. Note, this will only find the first
            // gap--if somehow other blocks below get destroyed, we won't know.
            do {
                newTipY -= 1;

                pieceType = world.getBlockTypeIdAt(x, newTipY, z);
            } while(!AntennaConf.isFixedBaseMaterial(pieceType) &&
                    pieceType != AntennaConf.fixedAntennaMaterial.getId() &&
                    newTipY > 0);

            ant.setTipY(newTipY);
            event.getPlayer().sendMessage("Shrunk antenna range to " + ant.getBroadcastRadius() + " m");

        } else if (block.getType() == Material.WALL_SIGN || block.getType() == Material.SIGN_POST) {
            Antenna ant = Antenna.getAntennaByAdjacent(block.getLocation());
            if (ant == null) {
                return;
            }
            event.getPlayer().sendMessage("Cleared antenna message");
            ant.setMessage(null);
            // do not update relay
        }
    }

    // Signs to set transmission message
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled=true)
    public void onSignChange(SignChangeEvent event) {
        Block block = event.getBlock();
        String[] text = event.getLines();

        Antenna ant = Antenna.getAntennaByAdjacent(block.getLocation());
        if (ant != null) {
            Player player = event.getPlayer();

            if (ant.isRelay) {
                player.sendMessage("To set a relay message, build a normal antenna within range of this relay");
                event.setCancelled(true);
                block.breakNaturally();
                return;
            }

            if (!player.hasPermission("radiobeacon.addmessage")) {
                String message = AntennaConf.fixedDenyAddMessageMessage;
                if (message != null && !message.equals("")) {
                    player.sendMessage(message);
                }
                event.setCancelled(true);
                if (AntennaConf.fixedDenyAddMessageBreak) {
                    block.breakNaturally();
                }
                return;
            }

            ant.setMessage(joinString(text));
            player.sendMessage("Set transmission message: " + ant.message);
            // setting message is a signal to update relays
            ant.notifyRelays(player);
        }
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
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled=true)
    public void onBlockRedstoneChange(BlockRedstoneEvent event) {
         // TODO: find out how to disable antennas, get when block becomes unpowered
        World world = event.getBlock().getWorld();

        if (event.getOldCurrent() == 0) {
            // TODO: find antenna at location and disable
            RadioBeacon.log("current turned off at "+event.getBlock());

            for (Antenna ant: plugin.ants) {
                // TODO: efficiency
                Block block = world.getBlockAt(ant.location);
                RadioBeacon.log("ant block:"+block);
            }
        }
    }
    */

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled=true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.isCancelled()) {
            return;
        }
    
        Set<AntennaXZ> affected = new HashSet<AntennaXZ>();

        for (Block block: event.blockList()) {
            Antenna ant = Antenna.getAntenna(block.getLocation());
            if (ant != null) {
                affected.add(ant.xz);

                RadioBeacon.log("Explosion affected "+ant);
                ant.checkIntact();
            }
        }

        if (affected.size() > 0) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new AntennaExplosionReactionTask(plugin, affected), AntennaConf.fixedExplosionReactionDelay);
        }
    }
}