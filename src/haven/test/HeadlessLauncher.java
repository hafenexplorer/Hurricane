/*
 * Headless Launcher for Haven & Hearth
 * Launches the game in headless mode using TestClient with authentication from config.txt
 */

package haven.test;

import haven.automated.ConfigReader;
import haven.automated.GobScanner;
import haven.automated.DiscordWebhook;
import haven.Connection;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

public class HeadlessLauncher {
    public static void main(String[] args) {
        String configFile = "config.txt";
        
        // Allow config file to be specified as first argument
        if (args.length > 0) {
            configFile = args[0];
        }
        
        try {
            System.out.println("Loading configuration from: " + configFile);
            ConfigReader config = new ConfigReader(Paths.get(configFile));
            
            // Read configuration
            String username = config.getUsername();
            String password = config.getPassword();
            String server = config.get("server", "game.havenandhearth.com");
            int mainPort = config.getInt("mainport", 1870);
            int authPort = config.getInt("authport", 1871);
            boolean encrypt = Connection.encrypt.get();
            
            // Validate required fields
            if (username == null || username.isEmpty()) {
                System.err.println("ERROR: username not found in config.txt");
                System.err.println("Please add: username=your_username");
                System.exit(1);
            }
            
            if (password == null || password.isEmpty()) {
                System.err.println("ERROR: password not found in config.txt");
                System.err.println("Please add: password=your_password");
                System.exit(1);
            }
            
            System.out.println("Configuration loaded:");
            System.out.println("  Username: " + username);
            System.out.println("  Server: " + server);
            System.out.println("  Main Port: " + mainPort);
            System.out.println("  Auth Port: " + authPort);
            System.out.println("  Encrypt: " + encrypt);
            System.out.println();
            
            // Create authenticated client
            System.out.println("Creating headless client...");
            AuthenticatedClient client = new AuthenticatedClient(
                username, password, server, mainPort, authPort, encrypt
            );
            
            // Start the client
            System.out.println("Starting client...");
            client.start();
            
            // Wait for client to connect and GameUI to be created
            System.out.println("Waiting for client to connect and world to load...");
            waitForWorldLoad(client);
            
            // Run GobScanner if watchlist is configured
            List<String> watchList = config.getGobWatchlist();
            if (!watchList.isEmpty()) {
                System.out.println();
                System.out.println("Gob watchlist configured: " + String.join(", ", watchList));
                
                // Get Discord webhook (optional)
                String webhookUrl = config.getDiscordWebhook();
                DiscordWebhook discord = (webhookUrl != null && !webhookUrl.isEmpty()) 
                    ? new DiscordWebhook(webhookUrl) 
                    : null;
                
                if (discord == null) {
                    System.out.println("Discord webhook not configured - notifications will only appear in terminal");
                }
                
                // Create and run GobScanner
                if (client.sess != null) {
                    GobScanner scanner = new GobScanner(client.sess, discord, watchList);
                    // Use username as character name for notification
                    String characterName = username;
                    scanner.notifyFoundGobs(characterName);
                } else {
                    System.err.println("WARNING: Client session not available for gob scanning");
                }
            } else {
                System.out.println("No gob watchlist configured - skipping gob scanning");
                System.out.println("  Add 'gob_watchlist=boar,stone,ore' to config.txt to enable scanning");
            }
            
            // Wait for client to finish
            System.out.println();
            System.out.println("Client running. Press Ctrl+C to stop.");
            client.join();
            
            System.out.println("Client stopped.");
            
        } catch (IOException e) {
            System.err.println("ERROR: Failed to read config file: " + configFile);
            System.err.println("Error: " + e.getMessage());
            System.err.println();
            System.err.println("Please ensure config.txt exists in the project root directory.");
            System.err.println("Example config.txt:");
            System.err.println("  username=your_username");
            System.err.println("  password=your_password");
            System.err.println("  server=game.havenandhearth.com");
            System.err.println("  mainport=1870");
            System.err.println("  authport=1871");
            System.exit(1);
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Wait for the game world to load (GameUI created and gobs available)
     * @param client The TestClient instance
     */
    private static void waitForWorldLoad(TestClient client) {
        int maxWait = 90; // 90 seconds max wait
        int waited = 0;
        boolean gameuiCreated = false;
        boolean gobsReceived = false;
        
        while (waited < maxWait && client.alive()) {
            try {
                Thread.sleep(1000);
                waited++;
                
                // Check if GameUI was created
                if (client.ui != null && client.ui.gui != null) {
                    if (!gameuiCreated) {
                        System.out.println("GameUI created! Server should start sending world data...");
                        gameuiCreated = true;
                    }
                }
                
                // Check if OCache has any gobs
                if (client.sess != null && client.sess.glob != null && client.sess.glob.oc != null) {
                    int gobCount = 0;
                    synchronized (client.sess.glob.oc) {
                        for (@SuppressWarnings("unused") haven.Gob g : client.sess.glob.oc) {
                            gobCount++;
                            if (gobCount > 0) break; // Just check if any exist
                        }
                    }
                    
                    if (gobCount > 0) {
                        if (!gobsReceived) {
                            System.out.println("Gobs received! World data is loading. (Found " + gobCount + " gob(s))");
                            gobsReceived = true;
                            // Give it a few more seconds for more gobs to load
                            if (waited < 10) {
                                Thread.sleep(3000);
                            }
                            break; // World is loaded
                        }
                    }
                }
                
                if (waited % 10 == 0 && waited > 0) {
                    System.out.println("Waiting for world to load... (" + waited + "s)");
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
                        System.out.println("  Waiting for GameUI to be created...");
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // Continue waiting on errors
                if (waited % 10 == 0) {
                    System.err.println("Error checking world load: " + e.getMessage());
                }
            }
        }
        
        if (gameuiCreated && gobsReceived) {
            System.out.println("World loaded successfully!");
        } else if (gameuiCreated) {
            System.out.println("Warning: GameUI created but no gobs received yet. Server may be slow.");
            System.out.println("  Gob scanning will still work if gobs arrive later.");
        } else {
            System.out.println("Warning: GameUI not created after " + maxWait + " seconds.");
            System.out.println("  This may prevent gob scanning from working.");
        }
    }
}

