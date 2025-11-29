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
import java.util.*;

public class GobScanner {
    private final Session sess;
    private final DiscordWebhook discord;
    private final List<String> watchList;
    private UI ui; // Optional UI reference for accessing GameUI

    public GobScanner(Session sess, DiscordWebhook discord, List<String> watchList) {
        this.sess = sess;
        this.discord = discord;
        this.watchList = watchList;
        this.ui = (sess != null && sess.ui != null) ? sess.ui : null;
    }

    /**
     * Extract short name from resource path.
     * For "gfx/kritter/boar/boar" returns "boar"
     * For "gfx/terrainobjs/stone" returns "stone"
     * @param resourceName The full resource path
     * @return The short name (last segment of the path)
     */
    private String getShortName(String resourceName) {
        if (resourceName == null || resourceName.isEmpty()) {
            return "";
        }
        int lastSlash = resourceName.lastIndexOf('/');
        if (lastSlash < 0) {
            return resourceName.toLowerCase();
        }
        return resourceName.substring(lastSlash + 1).toLowerCase();
    }

    /**
     * Check if any segment of the resource path matches the watched name.
     * This allows matching "boar" against "gfx/kritter/boar/boar" or "gfx/kritter/boar"
     * @param resourceName The full resource path
     * @param watchedName The short name to match (already lowercase)
     * @return true if any path segment matches
     */
    private boolean matchesShortName(String resourceName, String watchedName) {
        if (resourceName == null || watchedName == null || watchedName.isEmpty()) {
            return false;
        }
        
        // Split path into segments
        String[] segments = resourceName.split("/");
        for (String segment : segments) {
            if (segment.toLowerCase().equals(watchedName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the OCache to scan from - try GameUI.map first, then fall back to sess.glob
     * 
     * IMPORTANT: Gobs are accessible via sess.glob.oc WITHOUT GameUI.
     * However, the server typically only SENDS gobs after GameUI is created.
     * In headless mode, if the server doesn't create GameUI, it won't send gobs.
     * 
     * @return OCache instance or null
     */
    private OCache getOCache() {
        // Try to get OCache from GameUI.map first (more reliable when world is loaded)
        if (ui != null && ui.gui != null && ui.gui.map != null && ui.gui.map.glob != null) {
            return ui.gui.map.glob.oc;
        }
        // Fall back to session's glob - this is the same OCache, just accessed directly
        // Note: The server must send gobs for this to contain data
        if (sess != null && sess.glob != null) {
            return sess.glob.oc;
        }
        return null;
    }

    /**
     * Scan for gobs matching the watch list
     * @return List of found gob names (basenames)
     */
    public List<String> scanForGobs() {
        List<String> foundGobs = new ArrayList<>();
        Map<String, Integer> allGobs = new HashMap<>(); // For debugging: resource name -> count

        OCache oc = getOCache();
        if (oc == null) {
            System.out.println("Warning: OCache not available for gob scanning");
            System.out.println("  UI: " + (ui != null) + ", GameUI: " + (ui != null && ui.gui != null) + 
                             ", Map: " + (ui != null && ui.gui != null && ui.gui.map != null));
            System.out.println("  Session: " + (sess != null) + ", Glob: " + (sess != null && sess.glob != null));
            return foundGobs;
        }

        // Convert watch list to lowercase for case-insensitive matching
        Set<String> watchSet = new HashSet<>();
        for (String gobName : watchList) {
            watchSet.add(gobName.toLowerCase().trim());
        }

        if (watchSet.isEmpty()) {
            return foundGobs;
        }

        int totalGobs = 0;
        int loadedGobs = 0;
        int loadingGobs = 0;
        int nullResourceGobs = 0;

        // Scan all visible gobs
        synchronized (oc) {
            for (Gob gob : oc) {
                totalGobs++;
                try {
                    Resource res = gob.getres();
                    if (res != null && res.name != null) {
                        loadedGobs++;
                        String resourceName = res.name;
                        
                        // Track all gobs for debugging
                        String basename = res.basename();
                        allGobs.put(resourceName, allGobs.getOrDefault(resourceName, 0) + 1);
                        
                        // Check if any segment of the resource path matches the watched short name
                        for (String watched : watchSet) {
                            if (matchesShortName(resourceName, watched)) {
                                // Use the original basename for display
                                String displayName = res.basename();
                                if (!foundGobs.contains(displayName)) {
                                    foundGobs.add(displayName);
                                    System.out.println("  MATCH: Found '" + watched + "' in resource: " + resourceName);
                                }
                                break;
                            }
                        }
                    } else {
                        nullResourceGobs++;
                    }
                } catch (Loading l) {
                    // Resource still loading, skip
                    loadingGobs++;
                } catch (Exception e) {
                    // Log unexpected errors for debugging
                    System.err.println("Error getting resource for gob: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                }
            }
        }

        // Debug output
        System.out.println("Gob scan statistics:");
        System.out.println("  Total gobs in OCache: " + totalGobs);
        System.out.println("  Gobs with loaded resources: " + loadedGobs);
        System.out.println("  Gobs with loading resources: " + loadingGobs);
        System.out.println("  Gobs with null resources: " + nullResourceGobs);
        System.out.println("  Watching for: " + String.join(", ", watchList));
        
        // Show sample of available gobs (first 20 unique resource names)
        if (!allGobs.isEmpty()) {
            System.out.println("Sample of available gobs (first 20 unique):");
            int count = 0;
            for (Map.Entry<String, Integer> entry : allGobs.entrySet()) {
                if (count >= 20) break;
                System.out.println("  " + entry.getKey() + " (count: " + entry.getValue() + ")");
                count++;
            }
            if (allGobs.size() > 20) {
                System.out.println("  ... and " + (allGobs.size() - 20) + " more unique gob types");
            }
        }

        return foundGobs;
    }

    /**
     * Get the list of gobs being watched
     * @return List of watched gob names
     */
    public List<String> getWatchList() {
        return new ArrayList<>(watchList);
    }

    /**
     * Notify Discord about found gobs
     * @param characterName The name of the character that found the gobs
     */
    public void notifyFoundGobs(String characterName) {
        // Display what gobs we're looking for
        if (!watchList.isEmpty()) {
            System.out.println("Scanning for watched gobs: " + String.join(", ", watchList));
        }
        
        List<String> foundGobs = scanForGobs();

        // Always print terminal message
        if (foundGobs.isEmpty()) {
            System.out.println("No watched gobs found for character: " + characterName);
            return;
        }

        System.out.println("Found " + foundGobs.size() + " watched gob(s) for character: " + characterName);
        System.out.println("Gobs: " + String.join(", ", foundGobs));

        // Only send Discord notification if gobs were found
        if (discord == null) {
            System.out.println("Discord webhook not configured, skipping notification");
            return;
        }

        // Format the notification message
        StringBuilder message = new StringBuilder();
        message.append("**Haven & Hearth Gob Alert**\n");
        message.append("Character: ").append(characterName).append("\n");
        message.append("Found: ").append(String.join(", ", foundGobs)).append("\n");

        // Add timestamp
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        message.append("Time: ").append(sdf.format(new java.util.Date()));

        // Send to Discord
        boolean success = discord.send(message.toString());
        if (success) {
            System.out.println("Discord notification sent successfully");
        } else {
            System.err.println("Failed to send Discord notification");
        }
    }
}