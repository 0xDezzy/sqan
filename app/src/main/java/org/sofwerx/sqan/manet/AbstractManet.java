package org.sofwerx.sqan.manet;

import android.content.Context;

import org.sofwerx.sqan.listeners.ManetListener;
import org.sofwerx.sqan.manet.nearbycon.NearbyConnectionsManet;
import org.sofwerx.sqan.manet.packet.AbstractPacket;
import org.sofwerx.sqan.manet.packet.SegmentTool;
import org.sofwerx.sqan.manet.wifiaware.WiFiAwareManet;
import org.sofwerx.sqan.manet.wifidirect.WiFiDirectManet;

/**
 * Abstract class that handles all broad MANET activity. This abstracts away any MANET specific
 * implementation issues and lets SqAN deal with all MANETs in a uniform manner.
 */
public abstract class AbstractManet {
    protected Status status = Status.OFF;
    protected ManetListener listener;
    protected boolean isRunning = false;
    protected final Context context;

    public AbstractManet(Context context, ManetListener listener) {
        this.context = context;
        this.listener = listener;
        SegmentTool.setMaxPacketSize(getMaximumPacketSize());
    }

    public abstract ManetType getType();

    public abstract int getMaximumPacketSize();

    public final static AbstractManet newFromType(Context context, ManetListener listener, ManetType type) {
        switch (type) {
            case NEARBY_CONNECTION:
                return new NearbyConnectionsManet(context, listener);

            case WIFI_AWARE:
                return new WiFiAwareManet(context, listener);

            case WIFI_DIRECT:
                return new WiFiDirectManet(context, listener);

            default:
                return null;
        }
    }

    public abstract String getName();
    public boolean isRunning() { return isRunning; }

    public void setListener(ManetListener listener) { this.listener = listener; }

    /**
     * Do any initialization required to move the MANET into a pause state. This may include initial
     * interactions with other nodes in the network.
     */
    public abstract void init() throws ManetException;

    protected void setStatus(Status status) {
        switch (status) {
            case ADVERTISING:
                if ((this.status == Status.DISCOVERING) || (this.status == Status.ADVERTISING_AND_DISCOVERING))
                    this.status = Status.ADVERTISING_AND_DISCOVERING;
                else
                    this.status = Status.ADVERTISING;
                break;

            case DISCOVERING:
                if ((this.status == Status.ADVERTISING) || (this.status == Status.ADVERTISING_AND_DISCOVERING))
                    this.status = Status.ADVERTISING_AND_DISCOVERING;
                else
                    this.status = Status.DISCOVERING;
                break;

            default:
                this.status = status;
        }
    }

    /**
     * Send a pack over the MANET
     * @param packet
     */
    public abstract void burst(AbstractPacket packet) throws ManetException;

    /**
     * Send a pack over the MANET to a specific device
     * @param packet
     * @param device
     * @throws ManetException
     */
    public abstract void burst(AbstractPacket packet, SqAnDevice device) throws ManetException;

    /**
     * Connect to the MANET (i.e. start communicating with other nodes on the network)
     */
    public abstract void connect() throws ManetException;

    /**
     * Pause communication with the MANET (i.e. stop communicating with other nodes on the network but
     * keep the connection warm)
     */
    public abstract void pause() throws ManetException;

    /**
     * Move from a paused state back to a connected state (i.e. resume communicating with the MANET)
     */
    public abstract void resume() throws ManetException;

    /**
     * Disconnect from the MANET (i.e. stop/shutdown - release any resources needed to connect with
     * the MANET)
     */
    public abstract void disconnect() throws ManetException;

    protected void onReceived(AbstractPacket packet) throws ManetException {
        if (packet == null)
            throw new ManetException("Empty packet received over "+getClass());
    }

    public Status getStatus() { return status; }

    /**
     * A periodically executed method to help with any link housekeeping issues
     */
    public abstract void executePeriodicTasks();
}