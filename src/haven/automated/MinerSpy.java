/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Björn Johannessen <johannessen.bjorn@gmail.com>
 *
 *  Redistribution and/or modification of this file is subject to the
 *  terms of the GNU Lesser General Public License, version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Other parts of this source tree adhere to other copying
 *  rights. Please see the file `COPYING' in the root directory of the
 *  source tree for details.
 *
 *  A copy the GNU Lesser General Public License is distributed along
 *  with the source tree of which this file is a part in the file
 *  `doc/LPGL-3'. If it is missing for any reason, please see the Free
 *  Software Foundation's website at <http://www.fsf.org/>, or write
 *  to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 *  Boston, MA 02111-1307 USA
 */

package haven.automated;

import haven.*;

import java.io.IOException;
import java.util.*;

public class MinerSpy implements Runnable {
    private final GameUI gui;
    private boolean stop;
    private boolean active;
    private ConfigReader config;
    private DiscordWebhook webhook;

    // Track last notification time for each ore type to avoid spam
    private final Map<String, Long> lastNotificationTime = new HashMap<>();
    private static final long NOTIFICATION_COOLDOWN = 600000; // 10 minute cooldown per ore type

    public MinerSpy(GameUI gui) {
        this.gui = gui;
        this.stop = false;
        this.active = false;

        // Try to load config from multiple possible locations
        String[] possiblePaths = {
                "config.txt",                    // Current working directory
                "../config.txt",                 // Parent directory
                "./config.txt",                  // Explicit current directory
                System.getProperty("user.dir") + "/config.txt"  // User directory
        };

        boolean configLoaded = false;
        System.out.println("Miner Spy: Attempting to load config file...");
        System.out.println("Miner Spy: Current working directory: " + System.getProperty("user.dir"));

        for (String path : possiblePaths) {
            try {
                System.out.println("Miner Spy: Trying path: " + path);
                config = new ConfigReader(path);
                configLoaded = true;
                System.out.println("Miner Spy: Config loaded successfully from: " + path);
                break;
            } catch (IOException e) {
                System.err.println("Miner Spy: Failed to load from " + path + ": " + e.getMessage());
            }
        }

        if (!configLoaded) {
            System.err.println("Miner Spy: Could not load config.txt from any location!");
            System.err.println("Miner Spy: Please ensure config.txt exists in: " + System.getProperty("user.dir"));
            gui.error("Miner Spy: Config file not found! Check console for details.");
            return;
        }

        // Try to get webhook URL
        String webhookUrl = config.getDiscordWebhook();
        System.out.println("Miner Spy: Webhook URL from config: " + (webhookUrl != null ? "[CONFIGURED]" : "[NOT SET]"));

        if (webhookUrl != null && !webhookUrl.isEmpty()) {
            webhook = new DiscordWebhook(webhookUrl);
            System.out.println("Miner Spy: Discord webhook loaded successfully");
            System.out.println("Miner Spy: Ready to send notifications!");
        } else {
            System.err.println("Miner Spy: No Discord webhook URL configured in config.txt");
            System.err.println("Miner Spy: Please add 'discord_webhook=YOUR_WEBHOOK_URL' to config.txt");
            gui.error("Miner Spy: Discord webhook not configured!");
        }
    }

    public void setActive(boolean active) {
        this.active = active;
        if (active) {
            System.out.println("Miner Spy: Monitoring activated");
            gui.msg("Miner Spy: Monitoring activated", java.awt.Color.GREEN);
        } else {
            System.out.println("Miner Spy: Monitoring deactivated");
            gui.msg("Miner Spy: Monitoring deactivated", java.awt.Color.ORANGE);
        }
    }

    public boolean isActive() {
        return active;
    }

    public void stop() {
        stop = true;
    }

    @Override
    public void run() {
        while (!stop) {
            try {
                if (active && webhook != null) {
                    checkForOres();
                }
                Thread.sleep(60000); // Check every 60 seconds
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                System.err.println("Miner Spy: Error during monitoring: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void checkForOres() {
        if (gui.map == null || gui.map.player() == null) {
            return;
        }

        Map<String, Integer> detectedOres = new TreeMap<>();

        try {
            // Scan within 50 tile radius
            for (int x = -50; x <= 50; x++) {
                for (int y = -50; y <= 50; y++) {
                    try {
                        int t = gui.ui.sess.glob.map.gettile(gui.map.player().rc.floor().div(11).add(x, y));
                        Resource res = gui.ui.sess.glob.map.tilesetr(t);

                        // Use same detection method as OreAndStoneCounter
                        if (res.name.contains("gfx/tiles/rocks/")) {
                            String basename = res.basename();
                            String fullName = Config.ORE_FULL_NAMES.get(basename);

                            // Only monitor ores (not stones) - if it's in ORE_FULL_NAMES, it's an ore
                            if (fullName != null) {
                                detectedOres.put(fullName, detectedOres.getOrDefault(fullName, 0) + 1);
                            }
                        }
                    } catch (Loading ignored) {
                        // Resource not loaded yet, skip
                    }
                }
            }

            // Debug output
            if (detectedOres.isEmpty()) {
                System.out.println("Miner Spy: No ores detected in scan area");
            } else {
                System.out.println("Miner Spy: Detected ores: " + detectedOres);
            }

            // Send notifications for detected ores
            for (Map.Entry<String, Integer> entry : detectedOres.entrySet()) {
                String oreName = entry.getKey();
                int count = entry.getValue();

                // Check cooldown
                Long lastNotif = lastNotificationTime.get(oreName);
                long currentTime = System.currentTimeMillis();

                if (lastNotif == null || (currentTime - lastNotif) > NOTIFICATION_COOLDOWN) {
                    System.out.println("Miner Spy: Attempting to send notification for " + oreName + " (count: " + count + ")");
                    sendOreNotification(oreName, count);
                    lastNotificationTime.put(oreName, currentTime);
                } else {
                    long timeLeft = (NOTIFICATION_COOLDOWN - (currentTime - lastNotif)) / 1000;
                    System.out.println("Miner Spy: " + oreName + " on cooldown (" + timeLeft + " seconds remaining)");
                }
            }
        } catch (Exception e) {
            System.err.println("Miner Spy: Error checking for ores: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void sendOreNotification(String oreName, int count) {
        try {
            // Get character name (chrid is the character ID, might not be the display name)
            String characterName = gui.chrid;

            // Build notification message
            String title = "⛏️ Ore Detected!";
            StringBuilder description = new StringBuilder();
            description.append("**Ore Type:** ").append(oreName).append("\n");
            description.append("**Visible Tiles:** ").append(count).append("\n");
            description.append("**Character:** ").append(characterName).append("\n");
            description.append("**Time:** ").append(new Date().toString());

            // Send embed with gold color (0xFFD700)
            boolean success = webhook.sendEmbed(title, description.toString(), 0xFFD700);

            if (success) {
                System.out.println("Miner Spy: Notification sent - " + oreName + " x" + count);
                gui.msg("Miner Spy: Notification sent - " + oreName, java.awt.Color.YELLOW);
            } else {
                System.err.println("Miner Spy: Failed to send notification");
            }
        } catch (Exception e) {
            System.err.println("Miner Spy: Error sending notification: " + e.getMessage());
            e.printStackTrace();
        }
    }
}