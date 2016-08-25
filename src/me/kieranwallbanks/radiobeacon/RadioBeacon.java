/*
http://dev.bukkit.org/server-mods/radiobeacon/

Copyright (c) 2012, Mushroom Hostage & kezz101
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of the <organization> nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package me.kieranwallbanks.radiobeacon;

import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import me.kieranwallbanks.radiobeacon.listener.AntennaBlockListener;
import me.kieranwallbanks.radiobeacon.listener.AntennaPlayerListener;
import me.kieranwallbanks.radiobeacon.listener.AntennaWeatherListener;
import me.kieranwallbanks.radiobeacon.tasks.ReceptionTask;
import me.kieranwallbanks.radiobeacon.util.Antenna;
import me.kieranwallbanks.radiobeacon.util.AntennaConf;
import me.kieranwallbanks.radiobeacon.util.AntennaXZ;

/* example of listening to custom event
class AntennaNetworkListener implements Listener {
    RadioBeacon plugin;

    public AntennaNetworkListener(RadioBeacon plugin) {
        this.plugin = plugin;

        Bukkit.getServer().getPluginManager().registerEvents(this, plugin);
    }


    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled=true)
    public void onAntennaChange(AntennaChangeEvent event) {
        RadioBeacon.log("Cool it worked! "+event);
    }
}*/

public class RadioBeacon extends JavaPlugin {
    public static Logger logger = Logger.getLogger("Minecraft");
    Listener blockListener;
    Listener playerListener;
    Listener weatherListener;
    ReceptionTask receptionTask;

    public void onEnable() {
        if (!AntennaConf.load(this)) {
            return;
        }


        blockListener = new AntennaBlockListener(this);
        playerListener = new AntennaPlayerListener(this);
        //Listener networkListener = new AntennaNetworkListener(this);

        if (AntennaConf.fixedWeatherListener) {
            weatherListener = new AntennaWeatherListener(this);
        } else {
            weatherListener = null;
        }

        receptionTask = new ReceptionTask(this);

        // Compass notification task
        int taskId;
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, receptionTask, 
            AntennaConf.mobileTaskStartDelaySeconds,
            AntennaConf.mobileTaskPeriodSeconds);

        if (taskId == -1) {
            logger.severe("Failed to schedule radio signal reception task");
            Bukkit.getServer().getPluginManager().disablePlugin(this);
            return;
        }
        receptionTask.taskId = taskId;
    }

    public void onDisable() {
        AntennaConf.saveAntennas(this);
    }

    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        if (cmd.getName().equalsIgnoreCase("antennas")) {
            if (args.length > 0) {
                if (args[0].equals("list")) {
                    listAntennas(sender);
                } else if (args[0].equals("save")) {
                    if (!(sender instanceof Player) || ((Player)sender).hasPermission("radiobeacon.admin")) {
                        AntennaConf.saveAntennas(this);
                    } else {
                        sender.sendMessage("You do not have permission to save antennas");
                    }
                } else if (args[0].equals("load")) {
                    if (!(sender instanceof Player) || ((Player)sender).hasPermission("radiobeacon.admin")) {
                        AntennaConf.loadAntennas(this);
                    } else {
                        sender.sendMessage("You do not have permission to load antennas");
                    }
                } else if (args[0].equals("check")) {
                    if (!(sender instanceof Player) || ((Player)sender).hasPermission("radiobeacon.admin")) {
                        Antenna.checkIntactAll(sender);
                    } else {
                        sender.sendMessage("You do not have permission to check antennas");
                    }
                }
            } else {
                listAntennas(sender);
            }

            return true;
        } else if (cmd.getName().equalsIgnoreCase("toggleradio")) {
            Player player = null;

            if (args.length > 0) {
                String playerName = args[0];
                player = Bukkit.getPlayer(playerName);
            } else {
                if (sender instanceof Player) {
                    player = (Player)sender;
                } else {
                    sender.sendMessage("Usage: /toggleradio <player>");
                    return true;
                }
            }
            if (player == null) {
                sender.sendMessage("No such player");
                return true;
            }

            Boolean obj = AntennaPlayerListener.playerDisabled.get(player.getUniqueId());
            boolean newState;
            if (obj == null) {
                newState = true;
            } else {
                newState = !obj.booleanValue();
            }

            AntennaPlayerListener.playerDisabled.put(player.getUniqueId(), newState);
            
            sender.sendMessage(ChatColor.GREEN + "Toggled radio "+(newState ? "off" : "on"));
            
            return true;
        }
        return false;
    }

    public static void log(String message) {
        if (AntennaConf.verbose) {
            logger.info(message);
        }
    }

    // Show either all antennas information, if have permission, or count only if not
    private void listAntennas(CommandSender sender) {
        int count = 0;
        boolean reveal = !(sender instanceof Player) || ((Player)sender).hasPermission("radiobeacon.reveal");

        for (Map.Entry<AntennaXZ,Antenna> pair : Antenna.xz2Ant.entrySet()) {
            //AntennaXZ xz = pair.getKey();
            Antenna ant = pair.getValue();

            if (reveal) {
                sender.sendMessage("Antenna: " + ant);
            }
            count += 1;
        }

        sender.sendMessage("There are " + count + " antennas" + (!reveal ? " out there somewhere" : ""));
    }
}
