package me.kieranwallbanks.radiobeacon.util;

import java.util.Comparator;

import org.bukkit.Location;

//Compare antennas based on their distance from some fixed location
public class AntennaDistanceComparator implements Comparator<Antenna> {
	Location otherLoc;

	public AntennaDistanceComparator(Location otherLoc) {
		this.otherLoc = otherLoc;
	}

	public int compare(Antenna a, Antenna b) {
		return a.getDistance(otherLoc) - b.getDistance(otherLoc);
	}
}