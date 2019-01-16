package org.sofwerx.sqan.manet.common;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.util.Log;

import org.sofwerx.sqan.Config;
import org.sofwerx.sqan.SqAnService;
import org.sofwerx.sqan.listeners.ManetListener;
import org.sofwerx.sqan.manet.common.issues.AbstractManetIssue;
import org.sofwerx.sqan.manet.common.issues.WiFiIssue;
import org.sofwerx.sqan.manet.nearbycon.NearbyConnectionsManet;
import org.sofwerx.sqan.manet.common.packet.AbstractPacket;
import org.sofwerx.sqan.manet.common.packet.SegmentTool;
import org.sofwerx.sqan.manet.wifiaware.WiFiAwareManet;
import org.sofwerx.sqan.manet.wifidirect.WiFiDirectManet;

import java.util.ArrayList;

/**
 * Abstract class that handles all broad MANET activity. This abstracts away any MANET specific
 * implementation issues and lets SqAN deal with all MANETs in a uniform manner.
 */
public abstract class AbstractManet {
    protected Status status = Status.OFF;
    protected ManetListener listener;
    protected boolean isRunning = false;
    protected final Context context;
    protected final Handler handler;

    //TODO look to add support for the WiFi Round Trip Timing API for spacing
    //https://developer.android.com/guide/topics/connectivity/wifi-rtt

    /**
     * Constructor for MANET
     * @param handler the handler for the thread where this MANET should run be default or null for the main thread (not advised)
     * @param context
     * @param listener
     */
    public AbstractManet(Handler handler, Context context, ManetListener listener) {
        this.handler = handler;
        this.context = context;
        this.listener = listener;
        SegmentTool.setMaxPacketSize(getMaximumPacketSize());
    }

    /**
     * Gets they type of MANET in use (i.e. Nearby Connections, WiFi Aware, WiFi Direct
     * @return
     */
    public abstract ManetType getType();

    /**
     * Checks for any issues blocking or impeding MANET
     * @return true == some issue exists effecting the MANET
     */
    public boolean checkForSystemIssues() {
        boolean passed = true;
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            SqAnService.onIssueDetected(new WiFiIssue(true,"WiFi absent"));
            passed = false;
        }
        if (NetUtil.isWiFiConnected(context)) {
            SqAnService.onIssueDetected(new WiFiIssue(false,"WiFi is connected to another network"));
            passed = false;
        }
        return passed;
    }

    public abstract int getMaximumPacketSize();

    /**
     * Intended to support more efficient radio operations by allowing devices to stop
     * advertising/discovering one the group has been adequately formed.
     * @param newNodesAllowed
     */
    public abstract void setNewNodesAllowed(boolean newNodesAllowed);

    /**
     * Intended to allow the network to reconfigure (i.e. start advertising/discovering again
     * or maybe reallocate topology) in the event that a device is lost (i.e. fails to meet
     * health check requirements)
     * @param node
     */
    public abstract void onNodeLost(SqAnDevice node);

    public final static AbstractManet newFromType(Handler handler, Context context, ManetListener listener, ManetType type) {
        switch (type) {
            case NEARBY_CONNECTION:
                return new NearbyConnectionsManet(handler, context, listener);

            case WIFI_AWARE:
                return new WiFiAwareManet(handler, context, listener);

            case WIFI_DIRECT:
                return new WiFiDirectManet(handler, context, listener);

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
        boolean changed = false;
        switch (status) {
            case ADVERTISING:
                if ((this.status == Status.DISCOVERING) || (this.status == Status.ADVERTISING_AND_DISCOVERING))
                    this.status = Status.ADVERTISING_AND_DISCOVERING;
                else {
                    this.status = Status.ADVERTISING;
                    changed = true;
                }
                break;

            case DISCOVERING:
                if ((this.status == Status.ADVERTISING) || (this.status == Status.ADVERTISING_AND_DISCOVERING))
                    this.status = Status.ADVERTISING_AND_DISCOVERING;
                else {
                    this.status = Status.DISCOVERING;
                    changed = true;
                }
                break;

            default:
                changed = (this.status != status);
                this.status = status;
        }
        if (changed && (listener != null))
            listener.onStatus(this.status);
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