package remuco;

import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;

import remuco.connection.BluetoothConnector;
import remuco.connection.GenericStreamConnection;
import remuco.connection.IConnector;
import remuco.connection.RemotePlayer;
import remuco.data.PlayerControl;
import remuco.ui.IScreen;
import remuco.util.FormLogPrinter;
import remuco.util.Log;
import remuco.util.Tools;

public class Main extends MIDlet implements CommandListener {

	private static final String APP_PROP_CONNTYPE = "remuco-connection";

	public static final String APP_PROP_UI_THEME_LIST = "remuco-ui-canvas-theme-list";

	private static final String APP_PROP_UI = "remuco-ui";

	private static final Command CMD_BACK = new Command("Ok", Command.BACK, 10);

	private static final Command CMD_SHOW_LOG = new Command("Logs",
			Command.SCREEN, 99);

	private static MIDlet midlet;

	protected Main() {
		super();
		midlet = this;
	}
	
	/**
	 * This method is to offer access to application properties outside this
	 * class.
	 * 
	 * @param name
	 *            name of the application proerty
	 * @param def
	 *            default value
	 * @return the property's value as an int or the default if the property is
	 *         not set or if the property value is not a number
	 */
	public static int getAPropInt(String name, int def) {

		String s = getAPropString(name, null);

		if (s == null)
			return def;

		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			Log.ln("Property " + name + " is no int!");
			return def;
		}

	}

	/**
	 * This method is to offer access to application properties outside this
	 * class.
	 * 
	 * @param name
	 *            name of the application proerty
	 * @param def
	 *            default value
	 * @return the property's value or the default if not set
	 */
	public static String getAPropString(String name, String def) {

		String s;

		if (midlet == null) {
			return def;
		}

		s = midlet.getAppProperty(name);
		
		if (s == null) {
			Log.ln("Property " + name + " is not set!");
			return def;
		}
		
		return s;
	}

	protected RemotePlayer player;

	private Display display;

	private boolean initialized = false;

	private Form logForm;

	private IScreen screenMain;

	public void commandAction(Command c, Displayable d) {
		if (c == IScreen.CMD_DISPOSE) {
			cleanUp();
			notifyDestroyed();
		} else if (c == CMD_BACK) { // back from log form
			screenMain.setActive(true);
		} else if (c == Alert.DISMISS_COMMAND) { // back from error alert
			logForm.removeCommand(CMD_BACK);
			logForm.addCommand(IScreen.CMD_DISPOSE);
			logForm.setCommandListener(this);
			display.setCurrent(logForm);
		} else if (c == CMD_SHOW_LOG) {
			screenMain.setActive(false);
			display.setCurrent(logForm);
		}
	}

	protected void destroyApp(boolean arg0) throws MIDletStateChangeException {
		cleanUp();
	}

	protected void pauseApp() {
		screenMain.setActive(false);
	}

	protected void startApp() throws MIDletStateChangeException {

		if (init()) {
			screenMain.setActive(true);
		}
	}

	private void cleanUp() {
		if (player != null) {
			player.control(PlayerControl.PC_LOGOFF);
			Tools.sleep(200); // prevent connection shutdown before logoff has
			// been send
		}
		Log.ln(this, "bye bye!");
	}

	/**
	 * Reads the application property {@link #APP_PROP_CONNTYPE}, creates an
	 * according connector, uses the connector to create a connection and
	 * returns the created connection.
	 * 
	 * @return connection to host system
	 */
	private GenericStreamConnection getConnection() {
		String s;
		IConnector connector;
		GenericStreamConnection connection;

		// detect which connector to use
		s = getAppProperty(APP_PROP_CONNTYPE);
		if (s == null) {
			Log.ln(this, "No Connector specified in application descriptor !");
			return null;
		} else if (s.equals(IConnector.CONNTYPE_BLUETOOTH)) {
			connector = new BluetoothConnector();
		} else {
			Log.ln(this, "Connection type " + s + " unknown !");
			return null;
		}

		// create a connection
		if (!connector.init(display)) {
			Log.ln(this, "initializing connector failed");
			return null;
		} else {
			connection = connector.getConnection();
			synchronized (connection) {
				connector.createConnection();
				try {
					connection.wait();
					return connection;
				} catch (InterruptedException e) {
					Log.ln(this, "I have been interrupted");
					return null;
				}
			}
		}
	}

	/**
	 * Reads the application property {@link #APP_PROP_UI} and returns the main
	 * screen of the according UI.
	 * 
	 * @return main screen of the according UI
	 */
	private IScreen getMainScreen() {
		String s;
		s = getAppProperty(APP_PROP_UI);
		if (s == null) {
			Log.ln(this, "No UI specified in apllication descriptor !");
			return new remuco.ui.simple.MainScreen();
		} else if (s.equals(IScreen.UI_SIMPLE)) {
			return new remuco.ui.simple.MainScreen();
		} else if (s.equals(IScreen.UI_CANVAS)) {
			return new remuco.ui.canvas.MainScreen();
		} else {
			Log.ln(this, "UI type " + s + " unknown !");
			return new remuco.ui.simple.MainScreen();
		}
	}

	private boolean init() {
		if (initialized) {
			return true;
		}

		// logging
		logForm = new Form("Logging");
		logForm.addCommand(CMD_BACK);
		logForm.setCommandListener(this);
		Log.setOut(new FormLogPrinter(logForm));

		display = Display.getDisplay(this);

		// create connection
		GenericStreamConnection connection = getConnection();
		if (connection == null || !connection.isOpen()) {
			// error handling
			Alert alert = new Alert("Error");
			alert.setType(AlertType.ERROR);
			alert.setString("Connecting failed. You will now see some log "
					+ "messages, which may help you.");
			alert.setTimeout(Alert.FOREVER);
			alert.setCommandListener(this);
			display.setCurrent(alert);
			return false;
		}

		Log.ln(this, "connection to host established.");

		player = new RemotePlayer(connection);

		screenMain = getMainScreen();
		if (screenMain == null)
			return false;
		screenMain.setUp(this, display, player);
		screenMain.getDisplayable().addCommand(CMD_SHOW_LOG); // logging

		initialized = true;
		return true;
	}
}
