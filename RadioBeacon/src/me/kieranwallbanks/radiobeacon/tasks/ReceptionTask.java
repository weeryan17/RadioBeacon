package me.kieranwallbanks.radiobeacon.tasks;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import me.kieranwallbanks.radiobeacon.RadioBeacon;
import me.kieranwallbanks.radiobeacon.listener.AntennaPlayerListener;
import me.kieranwallbanks.radiobeacon.util.Antenna;
import me.kieranwallbanks.radiobeacon.util.AntennaConf;

//Periodically check for nearby signals to receive at mobile compass radios
public class ReceptionTask implements Runnable {
	RadioBeacon plugin;
	public int taskId;

	public ReceptionTask(RadioBeacon plugin) {
		this.plugin = plugin;
	}

	@SuppressWarnings("deprecation")
	public void run() {
		for (Player player: Bukkit.getOnlinePlayers()) {
			ItemStack item = player.getItemInHand();
			
			if (item != null && item.getTypeId() == AntennaConf.mobileRadioItem && AntennaPlayerListener.playerRadioEnabled(player)) {
				// if scan increase is enabled, increment scan # each scan 
				if (AntennaConf.mobileScanBonusRadius != 0) {   
					Integer scanBonusObject = AntennaPlayerListener.playerScanBonus.get(player.getUniqueId());
					int scanBonus = scanBonusObject == null ? 0 : scanBonusObject.intValue();

					int newScanBonus = scanBonus + AntennaConf.mobileScanBonusRadius;
					newScanBonus = Math.min(newScanBonus, AntennaConf.mobileScanBonusMaxRadius);

					AntennaPlayerListener.playerScanBonus.put(player.getUniqueId(), newScanBonus);
				}


				// Compass = mobile radio
				Antenna.receiveSignalsAtPlayer(player);
			}
		}

		AntennaConf.saveAntennas(plugin); 
	}
}