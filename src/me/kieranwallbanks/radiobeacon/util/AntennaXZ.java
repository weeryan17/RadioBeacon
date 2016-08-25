package me.kieranwallbanks.radiobeacon.util;

import org.bukkit.Location;
import org.bukkit.World;

//2D integral location (unlike Bukkit's location)
public class AntennaXZ implements Comparable<AntennaXZ> {
	public World world;
	int x, z;

	public AntennaXZ(World w, int x0,  int z0) {
		world = w;
		x = x0;
		z = z0;
	}

	public AntennaXZ(Location loc) {
		world = loc.getWorld();
		x = loc.getBlockX();
		z = loc.getBlockZ();
	}

	public Location getLocation(double y) {
		return new Location(world, x + 0.5, y, z + 0.5);
	}

	public String toString() {
		return x + "," + z;
	}

	@Override
	public int compareTo(AntennaXZ rhs) {
		if (!world.equals(rhs.world)) {
			return world.getName().compareTo(rhs.world.getName());
		}
		
		if (x - rhs.x != 0) {
			return x - rhs.x;
		} else if (z - rhs.z != 0) {
			return z - rhs.z;
		}

		return 0;
	}

	public boolean equals(Object obj) {
		return compareTo((AntennaXZ) obj) == 0;      // why do I have to do this myself?
	}

	public int hashCode() {
		// lame hashing TODO: improve?
		return x * z;
	}
}
