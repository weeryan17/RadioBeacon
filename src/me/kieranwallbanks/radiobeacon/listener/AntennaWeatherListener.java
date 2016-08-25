package me.kieranwallbanks.radiobeacon.listener;

import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.weather.LightningStrikeEvent;

import me.kieranwallbanks.radiobeacon.RadioBeacon;
import me.kieranwallbanks.radiobeacon.util.Antenna;
import me.kieranwallbanks.radiobeacon.util.AntennaConf;
import me.kieranwallbanks.radiobeacon.util.AntennaXZ;

public class AntennaWeatherListener implements Listener {
    RadioBeacon plugin;

    public AntennaWeatherListener(RadioBeacon plugin) {
        this.plugin = plugin;
            
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled=true)
    public void onLightningStrike(LightningStrikeEvent event) { 
        World world = event.getWorld();
        Location strikeLocation = event.getLightning().getLocation();

        Antenna directAnt = Antenna.getAntenna(strikeLocation);
        if (directAnt != null) {

            float power = directAnt.getBlastPower();
            Location baseLoc = directAnt.getBaseLocation();

            // Direct hit!
            RadioBeacon.log("directly hit "+directAnt+", exploding with power "+power);


            if (power > 0) {
                world.createExplosion(baseLoc, power, AntennaConf.fixedBlastSetFire);
            }

            // Ensure antenna is destroyed
            Block baseBlock = world.getBlockAt(baseLoc);
            if (AntennaConf.isFixedBaseMaterial(baseBlock.getType())) {
                baseBlock.setType(Material.AIR);

                // TODO: log that it was destroyed by lightning
                Antenna.destroy(directAnt);
            }
            // TODO: move destruction check to explosion event? There's ENTITY_EXPODE,
            // but is it fired for any createExplosion? Should make explosions destroy
            // antennas, regardless. If possible.

            return;
        }

        // Find nearby antennas
        Antenna victimAnt = null;
        int victimHeight = 0;

        for (Map.Entry<AntennaXZ,Antenna> pair : Antenna.xz2Ant.entrySet()) {
            Antenna ant = pair.getValue();

            if (!ant.inSameWorld(strikeLocation)) {
                // No cross-world lightning strikes!
                continue;
            }
     
            // Within strike range?
            double distance = ant.get2dDistance(strikeLocation);
            if (distance < ant.getLightningAttractRadius()) {
                RadioBeacon.log("strike near antenna "+ant+", within "+distance+" of "+strikeLocation);

                if (AntennaConf.fixedLightningStrikeOne) {
                    // Only strike the tallest antenna
                    // This allows larger antennas to be built as "lightning rods", attracting
                    // lightning away from other, smaller antennas nearby
                    if (ant.getHeight() > victimHeight) {
                        victimHeight = ant.getHeight();
                        victimAnt = ant;
                    }
                } else {
                    // Strike all antennas within range! Triple strike!
                    strikeAntenna(ant);
                }
            }
        }

        if (victimAnt != null) {
            strikeAntenna(victimAnt);
        }
    }

    // Strike an antenna with lightning
    private void strikeAntenna(Antenna victimAnt) {
        RadioBeacon.log("striking "+victimAnt);

        World world = victimAnt.xz.world;

        if (AntennaConf.fixedLightningDamage) {
            world.strikeLightning(victimAnt.getBaseLocation());
        } else {
            world.strikeLightningEffect(victimAnt.getBaseLocation());
        }
    }
}