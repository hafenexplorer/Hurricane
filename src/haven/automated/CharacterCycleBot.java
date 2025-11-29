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

import haven.test.*;
import haven.*;
import java.io.*;
import java.util.*;

public class CharacterCycleBot {
    private final ConfigReader config;
    private AuthenticatedClient client;

    public CharacterCycleBot(String configFile) throws IOException {
        this.config = new ConfigReader(configFile);
        config.validate();
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: CharacterCycleBot <config.txt>");
            System.exit(1);
        }

        try {
            CharacterCycleBot bot = new CharacterCycleBot(args[0]);
            bot.run();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void run() throws InterruptedException, IOException {
        String username = config.getUsername();
        String password = config.getPassword();
        List<String> characters = config.getCharacters();
        int waitMinutes = config.getWaitMinutes();
        String server = config.getServer();
        int mainPort = config.getMainPort();
        int authPort = config.getAuthPort();

        if (characters.isEmpty()) {
            throw new IllegalArgumentException("No characters specified in config file");
        }

        System.out.println("=== Haven & Hearth Character Cycle Bot ===");
        System.out.println("Username: " + username);
        System.out.println("Characters: " + String.join(", ", characters));
        System.out.println("Total characters: " + characters.size());
        System.out.println("Wait time between cycles: " + waitMinutes + " minutes");
        System.out.println("Bot will cycle continuously until interrupted (Ctrl+C)");
        System.out.println();

        int cycleCount = 0;

        // Continuous loop until interrupted
        while (!Thread.currentThread().isInterrupted()) {
            cycleCount++;
            System.out.println("========================================");
            System.out.println("=== Starting Cycle #" + cycleCount + " ===");
            System.out.println("========================================");
            System.out.println();

            // Cycle through all characters
            for (int i = 0; i < characters.size(); i++) {
                String characterName = characters.get(i);

                System.out.println("=== Cycle " + (i + 1) + "/" + characters.size() +
                        ": Logging in and selecting " + characterName + " ===");

                try {
                    loginAndSelectCharacter(characterName);

                    // Wait for character to fully load into the game world
                    System.out.println("Waiting for character to load into game world...");
                    boolean worldLoaded = waitForWorldLoad();
                    
                    if (!worldLoaded) {
                        System.out.println("Warning: World may not be fully loaded, gob scanning may be incomplete");
                    } else {
                        System.out.println("World loaded, waiting 5 seconds for gobs to populate...");
                        Thread.sleep(5000);
                    }

                    // Scan for notable gobs (even if world didn't fully load)
                    if (config.getNotifyBeforeLogout() && config.getDiscordWebhook() != null && !config.getGobWatchlist().isEmpty()) {
                        System.out.println("Scanning for watched gobs...");
                        System.out.println("Watching for: " + String.join(", ", config.getGobWatchlist()));
                        try {
                            if (client != null && client.sess != null) {
                                DiscordWebhook discord = new DiscordWebhook(config.getDiscordWebhook());
                                GobScanner scanner = new GobScanner(client.sess, discord, config.getGobWatchlist());
                                scanner.notifyFoundGobs(characterName);
                                
                                // If world didn't load, provide helpful message
                                if (!worldLoaded) {
                                    System.out.println("Note: World data may not have loaded in headless mode.");
                                    System.out.println("  This is a known limitation - the server may not send world data without a display.");
                                    System.out.println("  Gob scanning will still work if the server sends data in the future.");
                                }
                            } else {
                                System.out.println("Warning: Client session not available for gob scanning");
                            }
                        } catch (Exception e) {
                            System.err.println("Error scanning for gobs: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }

                    // Logout all characters (including the last one)
                    System.out.println("Logging out...");
                    logout();

                    if (i < characters.size() - 1) {
                        System.out.println();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("\nCycle interrupted by user");
                    return;
                } catch (Exception e) {
                    System.err.println("Error during character cycle: " + e.getMessage());
                    e.printStackTrace();
                    // Continue to next character even if one fails
                    if (client != null) {
                        try {
                            logout();
                        } catch (Exception logoutEx) {
                            // Ignore logout errors
                        }
                    }
                }
            }

            // Wait for the specified time after logging out of all characters
            System.out.println();
            System.out.println("=== Cycle #" + cycleCount + " complete. Waiting " + waitMinutes + " minutes before next cycle ===");

            waitMinutes(waitMinutes);

            System.out.println();
        }

        System.out.println();
        System.out.println("=== Automation stopped ===");
        System.out.println("Total cycles completed: " + cycleCount);
    }

    private void loginAndSelectCharacter(String characterName) throws InterruptedException {
        String username = config.getUsername();
        String password = config.getPassword();
        String server = config.getServer();
        int mainPort = config.getMainPort();
        int authPort = config.getAuthPort();

        // Create authenticated client
        client = new AuthenticatedClient(username, password, server, mainPort, authPort, Connection.encrypt.get());

        // Create character selector robot
        CharSelector selector = new CharSelector(client, characterName, () -> {
            System.out.println("Character " + characterName + " selected successfully!");
        });

        // Start the client
        client.start();

        // Wait for character selection to complete
        // The CharSelector will automatically select the character when the charlist appears
        // Wait for the charlist to disappear (indicating character was selected)
        int maxWait = 120; // Maximum 120 seconds to wait
        int waited = 0;
        boolean charSelected = false;

        while (waited < maxWait && client.alive()) {
            Thread.sleep(1000);
            waited++;

            // Check if we're in the game (GameUI would be present, or charlist is gone)
            if (client.ui != null) {
                Widget root = client.ui.root;
                if (root != null) {
                    Widget gameui = findWidget(root, "gameui");
                    Widget charlist = findWidget(root, "charlist");
                    if (gameui != null || charlist == null) {
                        // Either we're in game or charlist is gone (character selected)
                        System.out.println("Character " + characterName + " selected and game loading...");
                        charSelected = true;
                        // Give it a bit more time to fully load
                        Thread.sleep(3000);
                        break;
                    }
                }
            }

            if (waited % 10 == 0) {
                System.out.println("Waiting for character selection... (" + waited + "s)");
            }
        }

        if (!charSelected && !client.alive()) {
            throw new RuntimeException("Client died during character selection");
        }

        if (!charSelected) {
            System.out.println("Warning: Character selection timeout, but continuing...");
        }
    }

    private Widget findWidget(Widget root, String type) {
        if (root == null) return null;
        if (root.getClass().getSimpleName().toLowerCase().contains(type.toLowerCase())) {
            return root;
        }
        for (Widget child = root.child; child != null; child = child.next) {
            Widget found = findWidget(child, type);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * Wait for the game world to fully load (gobs appear in OCache)
     * In headless mode, the server may not send world data until GameUI is created.
     * We wait for either GameUI to be created OR gobs to appear in OCache.
     * @return true if world loaded (gobs found), false if timeout
     */
    private boolean waitForWorldLoad() {
        int maxWait = 90; // Increased to 90 seconds - server might be slow
        int waited = 0;
        boolean gameuiCreated = false;

        while (waited < maxWait && client.alive()) {
            try {
                Thread.sleep(1000);
                waited++;

                // Check if GameUI was created (server sends "newwdg gameui" message)
                if (client.ui != null && client.ui.gui != null) {
                    if (!gameuiCreated) {
                        System.out.println("GameUI created! Server should start sending world data...");
                        gameuiCreated = true;
                    }
                }

                // Check if OCache has any gobs - this works even without GameUI/MapView
                if (client.sess != null && client.sess.glob != null && client.sess.glob.oc != null) {
                    int gobCount = 0;
                    int loadedGobCount = 0;
                    boolean foundPlayerGob = false;
                    
                    synchronized (client.sess.glob.oc) {
                        for (Gob gob : client.sess.glob.oc) {
                            gobCount++;
                            try {
                                Resource res = gob.getres();
                                if (res != null && res.name != null) {
                                    loadedGobCount++;
                                    // Check if this might be the player gob (avatar gobs typically have "gfx/avatar" in path)
                                    if (res.name.contains("avatar") || res.name.contains("char")) {
                                        foundPlayerGob = true;
                                    }
                                }
                            } catch (Loading l) {
                                // Resource still loading
                            } catch (Exception e) {
                                // Ignore errors
                            }
                        }
                    }
                    
                    // If we found gobs, world is loaded
                    if (gobCount > 0) {
                        String playerInfo = foundPlayerGob ? " (player gob detected)" : "";
                        System.out.println("World loaded! Found " + gobCount + " gob(s) in OCache (" + loadedGobCount + " with loaded resources)" + playerInfo);
                        return true;
                    }
                }

                if (waited % 10 == 0) {
                    System.out.println("Waiting for world to load... (" + waited + "s)");
                    // Debug: show what we're waiting for
                    System.out.println("  Session: " + (client.sess != null) + 
                                     ", Glob: " + (client.sess != null && client.sess.glob != null) + 
                                     ", OCache: " + (client.sess != null && client.sess.glob != null && client.sess.glob.oc != null));
                    if (client.ui != null) {
                        System.out.println("  UI exists: true, GameUI: " + (client.ui.gui != null) + 
                                         ", Map: " + (client.ui.gui != null && client.ui.gui.map != null));
                    } else {
                        System.out.println("  UI exists: false");
                    }
                    if (gameuiCreated) {
                        System.out.println("  GameUI created, waiting for server to send gobs...");
                    } else {
                        System.out.println("  Waiting for GameUI to be created (server sends 'newwdg gameui' message)...");
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception e) {
                // Continue waiting on errors
                if (waited % 10 == 0) {
                    System.err.println("Error checking world load: " + e.getMessage());
                }
            }
        }

        System.out.println("Warning: World load timeout after " + maxWait + " seconds");
        System.out.println("  GameUI created: " + gameuiCreated);
        System.out.println("  Note: Gobs are accessible via sess.glob.oc without GameUI,");
        System.out.println("        but the server typically only SENDS gobs after GameUI is created.");
        System.out.println("        Without GameUI, the server may not send world data (gobs).");
        System.out.println("        This is a server-side behavior, not a client limitation.");
        return false;
    }

    private void logout() {
        if (client != null && client.sess != null) {
            System.out.println("Closing session...");
            synchronized (client.sess) {
                client.sess.close();
            }
            // Wait a moment for logout to complete
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            client.stop();
        }
    }

    private void waitMinutes(int minutes) {
        long totalSeconds = minutes * 60L;
        long startTime = System.currentTimeMillis();

        while (true) {
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            long remaining = totalSeconds - elapsed;

            if (remaining <= 0) {
                break;
            }

            long remainingMinutes = remaining / 60;
            long remainingSeconds = remaining % 60;

            if (remainingMinutes > 0) {
                System.out.printf("Waiting: %d minutes %d seconds remaining...\r", remainingMinutes, remainingSeconds);
            } else {
                System.out.printf("Waiting: %d seconds remaining...\r", remainingSeconds);
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("\nWait interrupted!");
                return;
            }
        }
        System.out.println("\nWait complete!");
    }
}
