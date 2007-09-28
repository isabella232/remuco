package remuco.controller;

import remuco.UserException;
import remuco.comm.Comm;
import remuco.comm.IMessageReceiver;
import remuco.comm.IMessageSender;
import remuco.comm.Message;
import remuco.player.Player;
import remuco.player.StringParam;
import remuco.util.Log;

/**
 * Intermediates between the communication layer ({@link Comm}) and the player ({@link Player}).<br>
 * Also delivers relevant events from the communication layer to the UI ({@link IConnectionListener}).
 * 
 * @author Christian Buennig
 * 
 */
public final class CommController implements IMessageReceiver, IMessageSender {

	private final ClientInfo cinfo;

	/**
	 * The communication layer in use. Gets created by {@link #connect(String)}
	 * and destroyed by {@link #disconnect()}.
	 */
	private Comm comm;

	/**
	 * True if the comm layer is in connected state <b>and</b> if client info
	 * has been send to the server <b>and</b> player info has been received
	 * from the server. False otherwise.
	 */
	private boolean completelyConnected = false;

	/** To be used for messages generated by the controller */
	private final Message msg = new Message();

	private final Player player;

	private final ICCEventListener ccel;

	/**
	 * New comm controller.
	 * 
	 * @param ccel
	 *            the comm controller event listener to keep up to date about
	 *            the conncection process (which starts when
	 *            {@link #connect(String)} has been called).
	 */
	public CommController(ICCEventListener ccel) {

		this.ccel = ccel;
		
		cinfo = new ClientInfo();
		
		player = new Player(this);

	}

	/**
	 * Trys to connect to the given device. This method returns immediately and
	 * informs the {@link ICCEventListener} specified when creating this
	 * controller about relevant events about the connection, e.g.
	 * {@link ICCEventListener#EVENT_CONNECTING} (this event will occur
	 * immediately while calling this method - if no error occures, in this case
	 * {@link ICCEventListener#EVENT_ERROR} will occur).
	 * 
	 * @param device
	 * 
	 * @see ICCEventListener
	 */
	public void connect(String device) {

		if (comm != null) {
			Log.ln("[CC] already connect{ing,ed}, will disconnect first.. !");
			disconnect();
		}

		ccel.event(ICCEventListener.EVENT_CONNECTING, "Try to connect..");

		try {
			comm = new Comm(device, this);
		} catch (UserException e) {
			ccel.event(ICCEventListener.EVENT_ERROR, e.getError() + ": "
					+ e.getDetails());
			comm = null;
		}

	}

	/**
	 * Disconnects from the current device or stops trying to connect to a
	 * device.
	 * 
	 */
	public void disconnect() {

		if (comm == null)
			return;

		comm.down();
		comm = null;

		completelyConnected = false;

	}

	/**
	 * Get the player. This player mirrors the state of the remote player and
	 * provides methods to control the remote player.
	 * <p>
	 * <i>Note:</i> The returned reference is valid the whole lifetime of the
	 * <code>CommController</code>.
	 * 
	 * @return the player
	 */
	public Player getPlayer() {
		return player;
	}

	public void receiveMessage(Message m) {

		try {
			switch (m.id) {
			case Message.ID_LOCAL_CONNECTED:

				Log.ln("[CC] send client info");

				msg.id = Message.ID_IFC_CINFO;
				msg.sd = cinfo.sdGet();

				comm.sendMessage(msg);

				break;

			case Message.ID_IFS_PINFO:

				Log.ln("[CC] handle player info");

				player.info.sdSet(m.sd);

				completelyConnected = true;

				ccel.event(ICCEventListener.EVENT_CONNECTED, "Connected :)");

				break;

			case Message.ID_LOCAL_DISCONNECTED:

				completelyConnected = false;
				ccel.event(ICCEventListener.EVENT_CONNECTING, StringParam
						.getParam(m.sd));

				player.reset();

				break;

			case Message.ID_IFS_SRVDOWN:

				Log.ln("[CC] handle srv down");

				completelyConnected = false;
				ccel.event(ICCEventListener.EVENT_CONNECTING,
						"The server has shutdown. Try to reconnect...");

				player.reset();

				break;

			case Message.ID_LOCAL_ERROR:

				completelyConnected = false;
				ccel.event(ICCEventListener.EVENT_ERROR, StringParam
						.getParam(m.sd));

				break;

			default:

				Log.ln("[CC] handle message for player");

				player.receiveMessage(m);

				break;
			}
		} catch (RuntimeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(1);
		}

	}

	public void sendMessage(Message m) {

		if (completelyConnected)
			comm.sendMessage(m);

	}

}
