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

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class ConfigReader {
    private final Map<String, String> config = new HashMap<>();
    private final Path configPath;

    public ConfigReader(String configFile) throws IOException {
        this.configPath = Paths.get(configFile);
        load();
    }

    public ConfigReader(Path configPath) throws IOException {
        this.configPath = configPath;
        load();
    }

    private void load() throws IOException {
        if (!Files.exists(configPath)) {
            throw new FileNotFoundException("Config file not found: " + configPath);
        }

        try (BufferedReader reader = Files.newBufferedReader(configPath)) {
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.trim();

                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                // Parse key=value pairs
                int eqIndex = line.indexOf('=');
                if (eqIndex < 0) {
                    System.err.println("Warning: Invalid line " + lineNum + " (no '=' found): " + line);
                    continue;
                }

                String key = line.substring(0, eqIndex).trim();
                String value = line.substring(eqIndex + 1).trim();

                if (key.isEmpty()) {
                    System.err.println("Warning: Empty key on line " + lineNum);
                    continue;
                }

                config.put(key.toLowerCase(), value);
            }
        }
    }

    public String get(String key) {
        return config.get(key.toLowerCase());
    }

    public String get(String key, String defaultValue) {
        String value = get(key);
        return (value != null) ? value : defaultValue;
    }

    public int getInt(String key, int defaultValue) {
        String value = get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            System.err.println("Warning: Invalid integer value for '" + key + "': " + value);
            return defaultValue;
        }
    }

    // Convenience methods for common config values
    public String getUsername() {
        String username = get("username");
        if (username == null) {
            username = get("user");
        }
        if (username == null) {
            username = get("login");
        }
        return username;
    }

    public String getPassword() {
        String password = get("password");
        if (password == null) {
            password = get("pass");
        }
        if (password == null) {
            password = get("pwd");
        }
        return password;
    }

    public String getCharacter1() {
        return get("character1", "Bunny");
    }

    public String getCharacter2() {
        return get("character2", "Payne");
    }

    /**
     * Get all characters from config (character1 through character10)
     * @return List of character names, in order
     */
    public List<String> getCharacters() {
        List<String> characters = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            String charName = get("character" + i);
            if (charName != null && !charName.trim().isEmpty()) {
                characters.add(charName.trim());
            }
        }

        // If no characters found, return default characters for backward compatibility
        if (characters.isEmpty()) {
            String char1 = getCharacter1();
            String char2 = getCharacter2();
            if (char1 != null && !char1.isEmpty()) {
                characters.add(char1);
            }
            if (char2 != null && !char2.isEmpty()) {
                characters.add(char2);
            }
        }

        return characters;
    }

    public int getWaitMinutes() {
        return getInt("wait_minutes", 20);
    }

    public String getServer() {
        return get("server", "game.havenandhearth.com");
    }

    public int getMainPort() {
        return getInt("mainport", 1870);
    }

    public int getAuthPort() {
        return getInt("authport", 1871);
    }

    public String getDiscordWebhook() {
        return get("discord_webhook");
    }

    public List<String> getGobWatchlist() {
        String watchlist = get("gob_watchlist");
        if (watchlist == null || watchlist.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<String> list = new ArrayList<>();
        String[] items = watchlist.split(",");
        for (String item : items) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                list.add(trimmed);
            }
        }
        return list;
    }

    public boolean getNotifyBeforeLogout() {
        String value = get("notify_before_logout");
        if (value == null) {
            return true; // Default to enabled
        }
        return value.equalsIgnoreCase("true") || value.equals("1");
    }

    public void validate() throws IllegalArgumentException {
        if (getUsername() == null || getUsername().isEmpty()) {
            throw new IllegalArgumentException("Username not specified in config");
        }
        if (getPassword() == null || getPassword().isEmpty()) {
            throw new IllegalArgumentException("Password not specified in config");
        }
    }
}
