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

    public GobScanner(Session sess, DiscordWebhook discord, List<String> watchList) {
        this.sess = sess;
        this.discord = discord;
        this.watchList = watchList;
    }

    /**
     * Scan for gobs matching the watch list
     * @return List of found gob names (basenames)
     */
    public List<String> scanForGobs() {
        List<String> foundGobs = new ArrayList<>();

        if (sess == null || sess.glob == null || sess.glob.oc == null) {
            System.out.println("Warning: Session or OCache not available for gob scanning");
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

        // Scan all visible gobs
        synchronized (sess.glob.oc) {
            for (Gob gob : sess.glob.oc) {
                try {
                    Resource res = gob.getres();
                    if (res != null && res.name != null) {
                        String basename = res.basename().toLowerCase();
                        // Check if this gob matches any in our watch list
                        for (String watched : watchSet) {
                            if (basename.contains(watched) || watched.contains(basename)) {
                                // Use the original basename (not lowercase) for display
                                String displayName = res.basename();
                                if (!foundGobs.contains(displayName)) {
                                    foundGobs.add(displayName);
                                }
                                break;
                            }
                        }
                    }
                } catch (Loading l) {
                    // Resource still loading, skip
                } catch (Exception e) {
                    // Ignore other errors
                }
            }
        }

        return foundGobs;
    }

    /**
     * Notify Discord about found gobs
     * @param characterName The name of the character that found the gobs
     */
    public void notifyFoundGobs(String characterName) {
        List<String> foundGobs = scanForGobs();

        if (foundGobs.isEmpty()) {
            System.out.println("No watched gobs found for character: " + characterName);
            return;
        }

        System.out.println("Found " + foundGobs.size() + " watched gob(s) for character: " + characterName);
        System.out.println("Gobs: " + String.join(", ", foundGobs));

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