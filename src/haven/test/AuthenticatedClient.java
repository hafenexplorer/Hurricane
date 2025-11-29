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

package haven.test;

import haven.*;
import java.net.*;
import java.io.*;

public class AuthenticatedClient extends TestClient {
    private final String username;
    private final String password;
    private final String server;
    private final int mainPort;
    private final int authPort;
    private final boolean encrypt;

    public AuthenticatedClient(String username, String password, String server, int mainPort, int authPort, boolean encrypt) {
        super(username); // Initialize with username for display
        this.username = username;
        this.password = password;
        this.server = server;
        this.mainPort = mainPort;
        this.authPort = authPort;
        this.encrypt = encrypt;
    }

    public AuthenticatedClient(String username, String password) {
        this(username, password, "game.havenandhearth.com", 1870, 1871, Connection.encrypt.get());
    }

    @Override
    public void connect() throws InterruptedException {
        try {
            // Step 1: Authenticate with auth server
            System.out.println("Authenticating with " + server + ":" + authPort + "...");
            AuthClient.Credentials creds = new AuthClient.NativeCred(username, password);

            String authedAccount;
            byte[] cookie;
            SocketAddress authaddr = null;

            try (AuthClient auth = new AuthClient(server, authPort)) {
                authaddr = auth.address();
                try {
                    authedAccount = creds.tryauth(auth);
                } catch (AuthClient.Credentials.AuthException e) {
                    throw new RuntimeException("Authentication failed: " + e.getMessage(), e);
                }
                cookie = auth.getcookie();
                if (encrypt) {
                    // Get alias for encrypted connection if needed
                    try {
                        String alias = auth.getalias();
                        // Alias is stored in Session.User if needed
                    } catch (IOException e) {
                        // Alias not critical, continue
                    }
                }
            } catch (UnknownHostException e) {
                throw new RuntimeException("Could not locate auth server: " + server, e);
            } catch (IOException e) {
                throw new RuntimeException("Failed to connect to auth server: " + e.getMessage(), e);
            }

            // Step 2: Connect to game server
            System.out.println("Connecting to game server " + server + ":" + mainPort + "...");
            InetAddress[] addrs = InetAddress.getAllByName(server);
            if (addrs.length == 0) {
                throw new UnknownHostException(server);
            }

            // Prefer the auth server address if available
            if (authaddr != null && authaddr instanceof InetSocketAddress) {
                InetAddress authHost = ((InetSocketAddress)authaddr).getAddress();
                java.util.Arrays.sort(addrs, (a, b) -> {
                    boolean pa = Utils.eq(a, authHost), pb = Utils.eq(b, authHost);
                    if (pa && pb) return 0;
                    else if (pa) return -1;
                    else if (pb) return 1;
                    else return 0;
                });
            }

            Session.User acct = new Session.User(authedAccount);
            if (encrypt && authaddr != null) {
                try (AuthClient auth = new AuthClient(server, authPort)) {
                    try {
                        new AuthClient.NativeCred(username, password).tryauth(auth);
                        String alias = auth.getalias();
                        if (alias != null) {
                            acct.alias(alias);
                        }
                    } catch (Exception e) {
                        // Continue without alias
                    }
                } catch (IOException e) {
                    // Continue without alias
                }
            }

            // Try each address until one works
            Session connectedSession = null;
            for (int i = 0; i < addrs.length; i++) {
                if (i > 0) {
                    System.out.println("Trying address " + (i + 1) + "/" + addrs.length + "...");
                }
                try {
                    connectedSession = new Session(new InetSocketAddress(addrs[i], mainPort), acct, encrypt, cookie);
                    break;
                } catch (Connection.SessionConnError err) {
                    // Try next address
                } catch (Connection.SessionError err) {
                    throw new RuntimeException("Session error: " + err.getMessage(), err);
                }
            }

            if (connectedSession == null) {
                throw new RuntimeException("Could not connect to any server address");
            }

            this.sess = connectedSession;
            this.user = authedAccount;
            this.cookie = cookie;
            this.addr = new InetSocketAddress(server, mainPort);

            System.out.println("Successfully connected as " + authedAccount);

        } catch (UnknownHostException e) {
            throw new RuntimeException("Could not locate server: " + server, e);
        }
    }
}