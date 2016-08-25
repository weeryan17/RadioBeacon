package me.kieranwallbanks.radiobeacon.tasks;

import java.util.Set;

import me.kieranwallbanks.radiobeacon.RadioBeacon;
import me.kieranwallbanks.radiobeacon.util.Antenna;
import me.kieranwallbanks.radiobeacon.util.AntennaXZ;

//Task to check affected antennas after nearby explosion
public class AntennaExplosionReactionTask implements Runnable {
 Set<AntennaXZ> affected;
 RadioBeacon plugin;

 	public AntennaExplosionReactionTask(RadioBeacon pl, Set<AntennaXZ> a) {
 		plugin = pl;
 		affected = a;
 	}

 public void run() {
     for (AntennaXZ xz: affected) {
         Antenna ant = Antenna.getAntenna(xz);

         if (ant != null) {
             RadioBeacon.log("Explosion affected "+ant);

             ant.checkIntact();
         }
     }
 }
}