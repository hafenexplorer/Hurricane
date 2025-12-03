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

import haven.OptWnd;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class DiscordWebhook {
    private final String webhookUrl;

    public DiscordWebhook(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    /**
     * Send a simple text message to Discord webhook
     * @param content The message content to send
     * @return true if successful, false otherwise
     */
    public boolean send(String content) {
        //if (webhookUrl == null || webhookUrl.isEmpty()) {
        String urlToUse = webhookUrl;

        // If webhookUrl is not configured, try to use OptWnd.discordEndpointTextEntry as fallback
        if (urlToUse == null || urlToUse.isEmpty()) {
            try {
                if (OptWnd.discordEndpointTextEntry != null) {
                    String fallbackUrl = OptWnd.discordEndpointTextEntry.buf.line();
                    if (fallbackUrl != null && !fallbackUrl.isEmpty()) {
                        urlToUse = fallbackUrl;
                    }
                }
            } catch (Exception e) {
                // If accessing OptWnd fails, continue with null check below
            }
        }

        if (urlToUse == null || urlToUse.isEmpty()) {
            System.err.println("Discord webhook URL not configured");
            return false;
        }

        try {
            //URL url = new URL(webhookUrl);
            URL url = new URL(urlToUse);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            // Build JSON payload
            String json = "{\"content\":\"" + escapeJson(content) + "\"}";

            // Send request
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = json.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Check response
            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                return true;
            } else {
                System.err.println("Discord webhook returned error code: " + responseCode);
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        System.err.println(responseLine);
                    }
                }
                return false;
            }
        } catch (MalformedURLException e) {
            System.err.println("Invalid Discord webhook URL: " + e.getMessage());
            return false;
        } catch (IOException e) {
            System.err.println("Failed to send Discord webhook: " + e.getMessage());
            return false;
        }
    }

    /**
     * Send a rich embed message to Discord webhook
     * @param title The embed title
     * @param description The embed description
     * @param color The embed color (as integer, e.g., 0xFF0000 for red)
     * @return true if successful, false otherwise
     */
    public boolean sendEmbed(String title, String description, int color) {
        //if (webhookUrl == null || webhookUrl.isEmpty()) {
        String urlToUse = webhookUrl;

        // If webhookUrl is not configured, try to use OptWnd.discordEndpointTextEntry as fallback
        if (urlToUse == null || urlToUse.isEmpty()) {
            try {
                if (OptWnd.discordEndpointTextEntry != null) {
                    String fallbackUrl = OptWnd.discordEndpointTextEntry.buf.line();
                    if (fallbackUrl != null && !fallbackUrl.isEmpty()) {
                        urlToUse = fallbackUrl;
                    }
                }
            } catch (Exception e) {
                // If accessing OptWnd fails, continue with null check below
            }
        }

        if (urlToUse == null || urlToUse.isEmpty()) {
            System.err.println("Discord webhook URL not configured");
            return false;
        }

        try {
            //URL url = new URL(webhookUrl);
            URL url = new URL(urlToUse);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            // Build JSON payload with embed
            String json = String.format(
                    "{\"embeds\":[{\"title\":\"%s\",\"description\":\"%s\",\"color\":%d}]}",
                    escapeJson(title),
                    escapeJson(description),
                    color
            );

            // Send request
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = json.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Check response
            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                return true;
            } else {
                System.err.println("Discord webhook returned error code: " + responseCode);
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        System.err.println(responseLine);
                    }
                }
                return false;
            }
        } catch (MalformedURLException e) {
            System.err.println("Invalid Discord webhook URL: " + e.getMessage());
            return false;
        } catch (IOException e) {
            System.err.println("Failed to send Discord webhook: " + e.getMessage());
            return false;
        }
    }

    /**
     * Escape special characters in JSON strings
     */
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}