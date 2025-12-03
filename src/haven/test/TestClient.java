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
import java.util.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public class TestClient implements Runnable, UI.Context {
    public Session sess;
    public InetSocketAddress addr;
    public String user;
    public byte[] cookie;
    public ThreadGroup tg;
    public Thread me;
    public UI ui;
    public boolean loop = false;
    public Collection<Robot> robots = new HashSet<Robot>();
    private static Object errsync = new Object();
    
    public TestClient(String user) {
	try {
	    addr = new InetSocketAddress(InetAddress.getByName("localhost"), 1870);
	} catch(java.net.UnknownHostException e) {
	    throw(new RuntimeException("localhost not known"));
	}
	this.user = user;
	this.cookie = new byte[64];
	tg = new ThreadGroup(HackThread.tg(), "Test client") {
		public void uncaughtException(Thread t, Throwable e) {
		    synchronized(errsync) {
			// Suppress non-critical UI command errors that don't affect automation
			if (e instanceof haven.UI.CommandException) {
			    // These are typically widget creation errors that don't prevent core functionality
			    // Only log if it's not a ClassCastException or ArgumentFormatException (common in TestClient)
			    Throwable cause = e.getCause();
			    if (cause != null && 
				(cause instanceof ClassCastException || 
				 cause instanceof haven.Utils.ArgumentFormatException)) {
				// Silently ignore these common non-critical errors
				return;
			    }
			}
			System.err.println("Exception in test client: " + TestClient.this.user);
			e.printStackTrace(System.err);
		    }
		    TestClient.this.stop();
		}
	    };
    }
    
    public void connect() throws InterruptedException {
	sess = new Session(addr, new Session.User(user), false, cookie);
    }
    
    public void addbot(Robot bot) {
	synchronized(robots) {
	    robots.add(bot);
	}
    }
    
    public void rembot(Robot bot) {
	synchronized(robots) {
	    robots.remove(bot);
	}
    }
    
    public void setmousepos(Coord c) {
    }

    public class TestUI extends UI {
	public TestUI(Coord sz, Session sess) {
	    super(TestClient.this, sz, null);
	    this.sess = sess;
	}
	
	public void newwidgetp(int id, String type, int parent, Object[] pargs, Object... cargs) throws InterruptedException {
	    // Debug: Log when gameui widget is being created
	    if ("gameui".equals(type)) {
		System.out.println("[TestClient] Server requested GameUI creation (id=" + id + ", parent=" + parent + ")");
		System.out.println("[TestClient] Environment available: " + (env != null));
		if (env != null) {
		    System.out.println("[TestClient] Environment type: " + env.getClass().getName());
		}
		if (cargs.length > 0) {
		    System.out.println("[TestClient] GameUI args: chrid=" + cargs[0] + 
				       (cargs.length > 1 ? ", plid=" + cargs[1] : "") +
				       (cargs.length > 2 ? ", genus=" + cargs[2] : ""));
		}
	    }
	    
	    // Special handling for gameui - create synchronously to ensure ui.gui is set immediately
	    if ("gameui".equals(type)) {
		if (cargs.length >= 2) {
		    String chrid = (String)cargs[0];
		    long plid = Utils.uiv(cargs[1]);
		    String genus = cargs.length > 2 ? (String)cargs[2] : "";
		    
		    System.out.println("[TestClient] Creating GameUI synchronously: chrid=" + chrid + ", plid=" + plid + ", genus=" + genus);
		    
		    try {
			// Create GameUI synchronously (bypassing async CommandQueue)
			GameUI gui = new GameUI(chrid, plid, genus);
			
			// Set up widget attachment manually (since attach() is protected)
			// We replicate what GameUI.attach() does: set ui reference and call setGUI()
			synchronized(this) {
			    gui.ui = this;        // Set widget's UI reference (public field)
			    // Recursively set ui for all children (replicating Widget.attach() behavior)
			    for (Widget ch = gui.child; ch != null; ch = ch.next) {
				ch.ui = this;
				// Recursively attach grandchildren
				for (Widget gch = ch.child; gch != null; gch = gch.next) {
				    gch.ui = this;
				}
			    }
			    setGUI(gui);          // Set UI's gui reference (this is what GameUI.attach() does)
			    bind(gui, id);        // Register widget in widgets map
			}
			
			// Add to parent widget - this will call added() and attached() hooks
			if (parent != -1) {
			    Widget pwdg = getwidget(parent);
			    if (pwdg != null) {
				pwdg.addchild(gui, pargs);
			    } else {
				// Parent not ready yet, use async addwidget
				addwidget(id, parent, pargs);
			    }
			} else if (root != null) {
			    // No parent specified, add to root
			    root.addchild(gui, pargs);
			}
			
			// Verify ui.gui is set
			if (this.gui != null && this.gui == gui) {
			    System.out.println("[TestClient] GameUI created and attached successfully! ui.gui is set - server should now send gobs!");
			} else {
			    System.err.println("[TestClient] WARNING: GameUI created but ui.gui not properly set!");
			    // Force set it as fallback
			    setGUI(gui);
			    System.out.println("[TestClient] Forced ui.gui set as fallback");
			}
			
			// Notify robots
			synchronized(robots) {
			    for(Robot r : robots)
				r.newwdg(id, gui, cargs);
			}
			
		    } catch (Exception e) {
			System.err.println("[TestClient] ERROR creating GameUI synchronously: " + e.getMessage());
			System.err.println("[TestClient] Falling back to async creation...");
			e.printStackTrace();
			
			// Fallback to async creation
			try {
			    super.newwidget(id, type, parent, pargs, cargs);
			    // Wait a bit for async creation
			    int waited = 0;
			    while (waited < 50 && (getwidget(id) == null || this.gui == null)) {
				Thread.sleep(100);
				waited++;
			    }
			    Widget w = getwidget(id);
			    if (w != null && w instanceof GameUI && this.gui != null) {
				System.out.println("[TestClient] GameUI created via async fallback! ui.gui is set.");
			    } else {
				System.err.println("[TestClient] Async fallback also failed - ui.gui still null");
			    }
			} catch (Exception e2) {
			    System.err.println("[TestClient] Async fallback also failed: " + e2.getMessage());
			    e2.printStackTrace();
			}
			
			// Notify robots even if creation had issues
			Widget w = getwidget(id);
			if (w != null) {
			    synchronized(robots) {
				for(Robot r : robots)
				    r.newwdg(id, w, cargs);
			    }
			}
		    }
		} else {
		    System.err.println("[TestClient] ERROR: GameUI creation requires at least 2 cargs (chrid, plid)");
		    // Fall through to normal widget creation
		    super.newwidget(id, type, parent, pargs, cargs);
		}
	    } else {
		// Normal widget creation for non-gameui widgets
		try {
		    super.newwidget(id, type, parent, pargs, cargs);
		    Widget w = getwidget(id);
		    synchronized(robots) {
			for(Robot r : robots)
			    r.newwdg(id, w, cargs);
		    }
		} catch (Exception e) {
		    // Suppress non-critical UI command errors that don't affect automation
		    if (e instanceof haven.UI.CommandException) {
			Throwable cause = e.getCause();
			if (cause != null && 
			    (cause instanceof ClassCastException || 
			     cause instanceof haven.Utils.ArgumentFormatException)) {
			    // Silently ignore these common non-critical errors
			    return;
			}
		    }
		    throw e;
		}
	    }
	}
	
	@Override
	public void addwidget(int id, int parent, Object... pargs) {
	    // Handle MapView creation - server sends mapview widget to be added to GameUI
	    if (pargs.length > 0 && pargs[0] instanceof String) {
		String place = ((String)pargs[0]).intern();
		if (place == "mapview") {
		    Widget child = getwidget(id);
		    Widget pwdg = getwidget(parent);
		    
		    if (child instanceof MapView && pwdg instanceof GameUI) {
			System.out.println("[TestClient] MapView widget (id=" + id + ") being added to GameUI (id=" + parent + ")");
			// Call GameUI.addchild() directly with mapview place
			((GameUI)pwdg).addchild(child, pargs);
			System.out.println("[TestClient] MapView added to GameUI successfully! Server should now send gobs.");
			return;
		    } else {
			System.out.println("[TestClient] WARNING: MapView addchild - child=" + (child != null ? child.getClass().getSimpleName() : "null") + 
					   ", parent=" + (pwdg != null ? pwdg.getClass().getSimpleName() : "null"));
		    }
		}
	    }
	    
	    // For all other widgets, use normal async addwidget
	    super.addwidget(id, parent, pargs);
	}
	
	public void destroy(Widget w) {
	    int id = widgetid(w);
	    synchronized(robots) {
		for(Robot r : robots)
		    r.dstwdg(id, w);
	    }
	    super.destroy(w);
	}
	
	public void uimsg(int id, String msg, Object... args) {
	    Widget w = getwidget(id);
	    synchronized(robots) {
		for(Robot r : robots)
		    r.uimsg(id, w, msg, args);
	    }
	    super.uimsg(id, msg, args);
	}
    }

    public void run() {
	try {
	    try {
		do {
		    connect();
		    RemoteUI rui = new RemoteUI(sess);
		    // Use offscreen rendering context to allow GameUI/MapView to be created
		    // This is minimal (1x1 buffer) and doesn't require a display
		    // It uses minimal system resources and remains headless
		    haven.rs.GLOffscreen offscreen = haven.rs.GLOffscreen.get();
		    ui = new TestUI(new Coord(800, 600), sess);
		    // Set environment BEFORE RemoteUI.run() so it's available when GameUI is created
		    // RemoteUI.sendua() will send environment info to server, which may trigger gameui
		    ui.env = offscreen.env();
		    System.out.println("[TestClient] Environment set: " + (ui.env != null) + 
				       (ui.env != null ? " (" + ui.env.getClass().getSimpleName() + ")" : ""));
		    rui.run(ui);
		} while(loop);
	    } catch(InterruptedException e) {
	    }
	} finally {
	    stop();
	}
    }
    
    public void start() {
	me = new HackThread(tg, this, "Main thread");
	me.start();
    }
    
    public void stop() {
	tg.interrupt();
    }
    
    public boolean alive() {
	return((me != null) && me.isAlive());
    }
    
    public void join() {
	while(alive()) {
	    try {
		me.join();
	    } catch(InterruptedException e) {
		tg.interrupt();
	    }
	}
    }
    
    public String toString() {
	return("Client " + user);
    }
}
