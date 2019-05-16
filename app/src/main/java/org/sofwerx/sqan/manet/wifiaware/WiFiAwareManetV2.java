package org.sofwerx.sqan.manet.wifiaware;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.NetworkSpecifier;
import android.net.wifi.aware.AttachCallback;
import android.net.wifi.aware.DiscoverySession;
import android.net.wifi.aware.DiscoverySessionCallback;
import android.net.wifi.aware.IdentityChangedListener;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.PublishDiscoverySession;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.SubscribeDiscoverySession;
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.aware.WifiAwareSession;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.RequiresApi;

import org.sofwerx.sqan.Config;
import org.sofwerx.sqan.ManetOps;
import org.sofwerx.sqan.SqAnService;
import org.sofwerx.sqan.listeners.ManetListener;
import org.sofwerx.sqan.manet.common.AbstractManet;
import org.sofwerx.sqan.manet.common.MacAddress;
import org.sofwerx.sqan.manet.common.ManetException;
import org.sofwerx.sqan.manet.common.ManetType;
import org.sofwerx.sqan.manet.common.SqAnDevice;
import org.sofwerx.sqan.manet.common.Status;
import org.sofwerx.sqan.manet.common.TeammateConnectionPlanner;
import org.sofwerx.sqan.manet.common.issues.WiFiInUseIssue;
import org.sofwerx.sqan.manet.common.issues.WiFiIssue;
import org.sofwerx.sqan.manet.common.packet.AbstractPacket;
import org.sofwerx.sqan.manet.common.packet.HeartbeatPacket;
import org.sofwerx.sqan.manet.common.packet.PacketHeader;
import org.sofwerx.sqan.manet.common.sockets.SocketChannelConfig;
import org.sofwerx.sqan.manet.common.sockets.TransportPreference;
import org.sofwerx.sqan.util.AddressUtil;
import org.sofwerx.sqan.util.CommsLog;
import org.sofwerx.sqan.util.NetUtil;

import java.io.StringWriter;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static android.os.Build.VERSION_CODES.O;

/**
 * MANET built over the Wi-Fi Aware™ (Neighbor Awareness Networking) capabilities
 * found on Android 8.0 (API level 26) and higher
 *  (https://developer.android.com/guide/topics/connectivity/wifi-aware)
 */
public class WiFiAwareManetV2 extends AbstractManet {
    private final static String TAG = Config.TAG+".Aware";
    private static final String SERVICE_ID = "sqan";
    private WifiAwareManager wifiAwareManager;
    private BroadcastReceiver hardwareStatusReceiver;
    private final AttachCallback attachCallback;
    private final IdentityChangedListener identityChangedListener;
    private WifiAwareSession awareSession;
    private final PublishConfig configPub;
    private final SubscribeConfig configSub;
    //private static final long INTERVAL_LISTEN_BEFORE_PUBLISH = 1000l * 30l; //amount of time to listen for an existing hub before assuming the hub role
    //private static final long INTERVAL_BEFORE_FALLBACK_DISCOVERY = 1000l * 15l; //amount of time to try connecting with devices identified OOB before failing over to WiFi Aware Discovery
    private PublishDiscoverySession pubDiscoverySession;
    private SubscribeDiscoverySession subDiscoverySession;
    private AtomicInteger messageIds = new AtomicInteger(0);
    private ConnectivityManager connectivityManager;

    public WiFiAwareManetV2(Handler handler, Context context, ManetListener listener) {
        super(handler, context,listener);
        if (Build.VERSION.SDK_INT >= O) {
            wifiAwareManager = null;
            identityChangedListener = new IdentityChangedListener() {
                @Override
                public void onIdentityChanged(byte[] mac) { onMacChanged(mac); }
            };
            connectivityManager = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
            pubDiscoverySession = null;
            subDiscoverySession = null;
            configPub = new PublishConfig.Builder().setServiceName(SERVICE_ID).build();
            configSub = new SubscribeConfig.Builder().setServiceName(SERVICE_ID).build();
            attachCallback = new AttachCallback() {
                @Override
                public void onAttached(final WifiAwareSession session) {
                    if (handler != null) {
                        handler.post(() -> {
                            Log.d(TAG, "onAttached(session)");
                            awareSession = session;
                            startAdvertising();
                            startDiscovery();
                        });
                    }
                    isRunning.set(true);
                }

                @Override
                public void onAttachFailed() {
                    if (handler != null) {
                        handler.post(() -> {
                            Log.e(TAG, "unable to attach to WiFiAware manager");
                            setStatus(Status.ERROR);
                        });
                    }
                    wifiAwareManager = null;
                    isRunning.set(false);
                }
            };
        } else {
            attachCallback = null;
            identityChangedListener = null;
            configPub = null;
            configSub = null;
        }
    }

    @Override
    public ManetType getType() { return ManetType.WIFI_AWARE; }

    @Override
    public boolean checkForSystemIssues() {
        boolean passed = super.checkForSystemIssues();
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)) {
            SqAnService.onIssueDetected(new WiFiIssue(true,"This device does not have WiFi Aware"));
            passed = false;
        } else {
            if (Build.VERSION.SDK_INT >= O) {
                WifiAwareManager mngr = (WifiAwareManager) context.getSystemService(Context.WIFI_AWARE_SERVICE);
                if (!mngr.isAvailable()) {
                    SqAnService.onIssueDetected(new WiFiIssue(true, "WiFi Aware is supported but the system is not making it available"));
                    passed = false;
                }
            }
        }
        if (NetUtil.isWiFiConnected(context))
            SqAnService.onIssueDetected(new WiFiInUseIssue(false,"WiFi is connected to another network"));
        return passed;
    }

    @Override
    public int getMaximumPacketSize() { return 255; /*TODO temp maximum that reflects Aware message limitations */ }

    @Override
    public void setNewNodesAllowed(boolean newNodesAllowed) {/*TODO*/ }

    @Override
    public String getName() { return "WiFi Aware™"; }

    @Override
    public void init() throws ManetException {
        if (Build.VERSION.SDK_INT < O)
            return;
        if (!isRunning.get()) {
            Log.d(TAG,"WiFiAwareManet init()");
            isRunning.set(true);
            if (hardwareStatusReceiver == null) {
                hardwareStatusReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) { onWiFiAwareStatusChanged(); }
                };
                IntentFilter filter = new IntentFilter(WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED);
                context.registerReceiver(hardwareStatusReceiver, filter, null, handler);
            }
            if (wifiAwareManager == null) {
                NetUtil.turnOnWiFiIfNeeded(context);
                NetUtil.forceLeaveWiFiNetworks(context); //TODO include a check to protect an active connection if its used for data backhaul
                wifiAwareManager = (WifiAwareManager) context.getSystemService(Context.WIFI_AWARE_SERVICE);
                if ((wifiAwareManager != null) && wifiAwareManager.isAvailable())
                    wifiAwareManager.attach(attachCallback, identityChangedListener, handler);
                else {
                    Log.e(TAG, "WiFi Aware Manager is not available");
                    setStatus(Status.ERROR);
                }
            }
        }
    }

    @Override
    protected boolean isBluetoothBased() { return false; }

    @Override
    protected boolean isWiFiBased() { return true; }

    private void startAdvertising() {
        if (Build.VERSION.SDK_INT >= O) {
            CommsLog.log(CommsLog.Entry.Category.STATUS, "Aware start advertising");
            if (awareSession != null) {
                setStatus(Status.ADVERTISING);
                awareSession.publish(configPub, new CustomDSCallback(), handler);
            }
        }
    }

    private void startDiscovery() {
        if (Build.VERSION.SDK_INT >= O) {
            stopDiscovery();
            if (awareSession != null) {
                CommsLog.log(CommsLog.Entry.Category.STATUS, "Aware discovery started");
                setStatus(Status.DISCOVERING);
                awareSession.subscribe(configSub, new CustomDSCallback(), handler);
            }
        }
    }

    private void stopDiscovery() {
        if (Build.VERSION.SDK_INT >= O) {
            Log.d(TAG, "Closing discovery session");
            if (pubDiscoverySession != null) {
                pubDiscoverySession.close();
                pubDiscoverySession = null;
            }
            if (subDiscoverySession != null) {
                subDiscoverySession.close();
                subDiscoverySession = null;
            }
        }
    }

    /*private void relayPacketIfNeeded(Connection originConnection, final byte[] data, final int destination, final int origin, final int hopCount) {
        if ((originConnection != null) && (data != null) && (connections != null) && !connections.isEmpty()) {
            synchronized (connections) {
                SqAnDevice device;
                AbstractPacket reconstructed = null;
                for (Connection connection : connections) {
                    if ((connection == null) || (originConnection == connection) || (connection.getDevice() == null))
                        continue;
                    device = connection.getDevice();
                    if ((device.getUUID() != origin) //dont send to ourselves
                        && AddressUtil.isApplicableAddress(device.getUUID(),destination)
                        && hopCount < device.getHopsToDevice(origin)) {
                        CommsLog.log(CommsLog.Entry.Category.COMMS,"WiFi Aware relaying packet from "+origin+" ("+hopCount+" hops) to "+device.getLabel());

                        if (reconstructed == null) {
                            reconstructed = AbstractPacket.newFromBytes(data);
                            reconstructed.incrementHopCount();
                        }
                        boolean sendViaBt = false;
                        if (device.isWiFiPreferred()) {
                            try {
                                burst(reconstructed,device);
                            } catch (ManetException e) {
                                Log.e(TAG,"Exception when trying to send packet to "+device.getLabel()+" over Aware: "+e.getMessage());
                                sendViaBt = true;
                            }
                        }
                        if (device.isBtPreferred() || sendViaBt) {
                            CommsLog.log(CommsLog.Entry.Category.COMMS,"WiFi Aware referring packet from "+origin+" ("+hopCount+" hops) to "+device.getLabel()+" for relay via bluetooth");
                            SqAnService.burstVia(reconstructed, TransportPreference.BLUETOOTH);
                        }
                    }
                }
            }
        } else
            CommsLog.log(CommsLog.Entry.Category.PROBLEM,"Aware cannot relay a null packet or handle a null connection origin");
    }*/

    @Override
    public void burst(AbstractPacket packet) throws ManetException {
        if (packet != null) {
            //TODO
        } else
            Log.d(TAG,"Cannot send null packet");
    }

    private void burst(AbstractPacket packet, SqAnDevice device) throws ManetException {
        if (packet != null) {
            boolean sent = false;

            //TODO

            if (!sent) {
                /*if ((connections != null) && !connections.isEmpty()) {
                    synchronized (connections) {
                        for (Connection connection:connections) {
                            if ((connection.getDevice() != null) && (connection.getPeerHandle() != null)) {
                                if (AddressUtil.isApplicableAddress(connection.getDevice().getUUID(), packet.getSqAnDestination())) {
                                    Log.d(TAG,"Failing over to send packet to "+connection.getDevice().getLabel()+" via Aware message (socket connection not available)");
                                    burst(packet, connection.getPeerHandle());
                                    sent = true;
                                }
                            }
                        }
                    }
                } else
                    Log.d(TAG, "Aware tried to burst but no nodes available to receive");*/
            }
            if (sent) {
                if (listener != null)
                    listener.onTx(packet);
            }
        } else
            Log.d(TAG,"Trying to burst over manet but packet was null");
    }

    /*******************/

    private void burst(AbstractPacket packet, PeerHandle peerHandle) {
        if (packet == null) {
            Log.d(TAG,"Aware Cannot send empty packet");
            return;
        }
        burst(packet.toByteArray(),peerHandle);
    }

    private void burst(final byte[] bytes, final PeerHandle peerHandle) {
        if (bytes == null) {
            Log.d(TAG,"Aware cannot send empty byte array");
            return;
        }
        if (peerHandle == null) {
            Log.d(TAG,"Cannot burst to an empty PeerHandle");
            return;
        }
        if ((pubDiscoverySession == null) && (subDiscoverySession == null)) {
            Log.d(TAG,"Cannot burst as no discoverySession exists");
            return;
        }
        if (Build.VERSION.SDK_INT >= O) {
            byte[] payload = encrypt(bytes);
            if (payload.length > getMaximumPacketSize())
                Log.d(TAG, "Packet larger than WiFi Aware max; ignoring... TODO");
            else if (payload == null)
                Log.e(TAG, "Error encrypting payload");
            else {
                handler.post(() -> {
                    boolean pub = true;
                    boolean sub = true;
                    Pairing pairing = Pairing.find(peerHandle);
                    if (pairing != null) {
                        pub = pairing.isPeerHandlePub();
                        sub = pairing.isPeerHandleSub();
                    }
                    if (pub && (pubDiscoverySession != null)) {
                        Log.d(TAG, "Sending Message (via Pub) " + (messageIds.get() + 1) + " (" + payload.length + "b) to " + peerHandle.hashCode());
                        pubDiscoverySession.sendMessage(peerHandle, messageIds.incrementAndGet(), payload);
                        ManetOps.addBytesToTransmittedTally(payload.length);
                    }
                    if (pub && (subDiscoverySession != null)) {
                        Log.d(TAG, "Sending Message (via Sub) " + (messageIds.get() + 1) + " (" + payload.length + "b) to " + peerHandle.hashCode());
                        subDiscoverySession.sendMessage(peerHandle, messageIds.incrementAndGet(), payload);
                        ManetOps.addBytesToTransmittedTally(payload.length);
                    }
                });
            }
        } else
            Log.d(TAG,"Cannot burst, WiFi Aware is not supported");
    }

    @Override
    public void connect() throws ManetException {
        //TODO
    }

    @Override
    public void pause() throws ManetException {
        //TODO
    }

    @Override
    public void resume() throws ManetException {
        //TODO
    }

    @Override
    public void disconnect() throws ManetException {
        super.disconnect();
        Pairing.clear();
        //TODO

        /*if ((connections != null) && !connections.isEmpty() && (connectivityManager != null)) {
            synchronized (connections) {
                for (Connection connection:connections) {
                    if (connection.getCallback() != null) {
                        try {
                            connectivityManager.unregisterNetworkCallback(connection.getCallback());
                        } catch (Exception e) {
                            Log.e(TAG,"Cannot unregister NertworkCallback for "+connection.toString());
                        }
                    }
                }
            }
        }*/

        if (hardwareStatusReceiver != null) {
            try {
                context.unregisterReceiver(hardwareStatusReceiver);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
            hardwareStatusReceiver = null;
        }
        if (pubDiscoverySession != null) {
            pubDiscoverySession.close();
            pubDiscoverySession = null;
        }
        if (subDiscoverySession != null) {
            subDiscoverySession.close();
            subDiscoverySession = null;
        }
        if (awareSession != null) {
            awareSession.close();
            awareSession = null;
        }
        setStatus(Status.OFF);
        CommsLog.log(CommsLog.Entry.Category.STATUS, "MANET disconnected");
    }

    @Override
    protected void onDeviceLost(SqAnDevice device, boolean directConnection) {
        //TODO
    }

    @Override
    public void executePeriodicTasks() {
        if (!isRunning()) {
            try {
                Log.d(TAG,"Attempting to restart WiFi Aware manager");
                init();
            } catch (ManetException e) {
                Log.e(TAG, "Unable to initialize WiFi Aware: " + e.getMessage());
            }
        }

        removeUnresponsiveConnections();
    }

    private void removeUnresponsiveConnections() {
        /*if ((connections != null) && !connections.isEmpty()) {
            synchronized (connections) {
                int i=0;
                final long timeToConsiderStale = System.currentTimeMillis() - TIME_TO_CONSIDER_STALE_DEVICE;
                while (i<connections.size()) {
                    if (connections.get(i) == null) {
                        connections.remove(i);
                        continue;
                    }
                    if (timeToConsiderStale > connections.get(i).getLastConnection()) {
                        CommsLog.log(CommsLog.Entry.Category.CONNECTION,"Removing stale WiFi Aware connection: "+((connections.get(i).getDevice()==null)?"device unknown":connections.get(i).getDevice().getLabel()));
                        connections.remove(i);
                    } else
                        i++;
                }
            }
        }*/
    }

    /**
     * Entry point when a change in the availability of WiFiAware is detected
     */
    private void onWiFiAwareStatusChanged() {
        if (Build.VERSION.SDK_INT >= O) {
            WifiAwareManager mgr = (WifiAwareManager) context.getSystemService(Context.WIFI_AWARE_SERVICE);
            if (mgr != null) {
                Log.d(TAG, "WiFi Aware state changed: " + (mgr.isAvailable() ? "available" : "not available"));
                //TODO
            }
        }
    }

    /**
     * Called based on the frequently (30min or less) randomization of MACs assigned for WiFiAware
     * @param mac the new MAC assigned to this device for WiFiAware use
     */
    public void onMacChanged (byte[] mac) {
        Config.getThisDevice().setAwareMac(new MacAddress(mac));
        //TODO
    }

    private class AwareManetConnectionCallback extends ConnectivityManager.NetworkCallback {
        private final static long CALLBACK_TIMEOUT = 1000l * 10l;
        private final long timeToStale;

        public AwareManetConnectionCallback() {
            super();
            timeToStale = System.currentTimeMillis() + CALLBACK_TIMEOUT;
        }

        /*public boolean isStale() {
            return !success && (System.currentTimeMillis() > timeToStale);
        }*/

        @Override
        public void onAvailable(Network network) {
            /*success = true;
            Log.d(TAG,"NetworkCallback onAvailable() for "+((connection.getDevice()==null)?"null device":connection.getDevice().getLabel()));
            if (ipv6 == null) {
                ipv6 = NetUtil.getAwareAddress(context, network);
                if (ipv6 != null) {
                    Log.d(TAG, "Aware IP address assigned as " + ipv6.getHostAddress());
                    handleNetworkChange(network,connection,ipv6);
                }
            }*/
        }

        @Override
        public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
            /*Log.d(TAG,"NetworkCallback onLinkPropertiesChanged() for "+((connection.getDevice()==null)?"null device":connection.getDevice().getLabel()));
            ipv6 = NetUtil.getAwareAddress(linkProperties);
            SqAnDevice thisDevice = Config.getThisDevice();
            if (thisDevice == null)
                return;
            if (thisDevice.getAwareServerIp() == null)
                Log.d(TAG, "Aware IP address assigned as " + ipv6.getHostAddress());
            else if (!ipv6.equals(thisDevice.getAwareServerIp())) {
                Log.d(TAG, "Aware IP address changed to " + ipv6.getHostAddress());
                stopSocketConnections(false);
            }

            if (ipv6 != null)
                handleNetworkChange(network,connection,ipv6);
            else
                Log.d(TAG,"Could not do anything with the link property change as the ipv6 address was null");*/
        }

        @Override
        public void onLost(Network network) {
            /*success = false;
            Log.d(TAG,"Aware onLost() for "+((connection.getDevice()==null)?"null device":connection.getDevice().getLabel()));
            if (connection != null)  {
                try {
                    connectivityManager.unregisterNetworkCallback(this);
                    connection.setCallback(null);
                    Log.d(TAG,"unregistered NetworkCallback for "+connection.toString());
                } catch (Exception e) {
                    Log.w(TAG,"Unable to unregister this NetworkCallback: "+e.getMessage());
                }
            }
            if (Config.getThisDevice() != null)
                handleNetworkChange(null,connection,Config.getThisDevice().getAwareServerIp());*/
        }

        @Override
        public void onUnavailable() {
            //TODO
            Log.d(TAG, "NetworkCallback onUnavailable()");
        }

        @Override
        public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
            //TODO
            Log.d(TAG, "NetworkCallback onCapabilitiesChanged()");
        }

        @Override
        public void onLosing(Network network, int maxMsToLive) {
            //TODO
            Log.d(TAG, "NetworkCallback onLosing()");
        }
    }

    @RequiresApi(O)
    private class CustomDSCallback extends DiscoverySessionCallback {
        @Override
        public void onPublishStarted(PublishDiscoverySession session) {
            Log.d(TAG, "onPublishStarted()");
            pubDiscoverySession = session;
            setStatus(Status.ADVERTISING);
            if (listener != null)
                listener.onStatus(status);
        }

        @Override
        public void onMessageReceived(final PeerHandle peerHandle, final byte[] message) {
            if (handler != null)
                handler.post(() -> {
                    boolean burst = false;
                    if (Pairing.find(peerHandle) == null)
                        burst = true; //send a heartbeat the first time we discover another device
                    handleMessage(Pairing.update(peerHandle), decrypt(message));
                    if (burst)
                        burst(new HeartbeatPacket(Config.getThisDevice(), HeartbeatPacket.DetailLevel.MEDIUM),peerHandle);
                });
        }

        @Override
        public void onSubscribeStarted(final SubscribeDiscoverySession session) {
            CommsLog.log(CommsLog.Entry.Category.STATUS, "Aware subscription started");
            subDiscoverySession = session;
            setStatus(Status.CONNECTED);
        }

        @Override
        public void onServiceDiscovered(PeerHandle peerHandle, byte[] serviceSpecificInfo, List<byte[]> matchFilter) {
            CommsLog.log(CommsLog.Entry.Category.STATUS, "Aware service discovered");
            setStatus(Status.CONNECTED);
            if (handler != null)
                handler.post(() -> {
                    Pairing pairing = Pairing.update(peerHandle);
                    if (pairing != null)
                        pairing.setPeerHandleOrigin(Pairing.PeerHandleOrigin.SUB);
                    burst(new HeartbeatPacket(Config.getThisDevice(), HeartbeatPacket.DetailLevel.MEDIUM),peerHandle);
                });
        }

        @Override
        public void onMessageSendSucceeded(int messageId) {
            super.onMessageSendSucceeded(messageId);
            Log.d(TAG, "Aware Message " + messageId + " was successfully sent");
        }

        @Override
        public void onSessionConfigFailed() {
            CommsLog.log(CommsLog.Entry.Category.CONNECTION, "Aware session configuration failed");
            //TODO
        }

        @Override
        public void onSessionTerminated() {
            CommsLog.log(CommsLog.Entry.Category.CONNECTION, "awareSession onSessionTerminated()");
            //TODO
        }

        @Override
        public void onSessionConfigUpdated() {
            CommsLog.log(CommsLog.Entry.Category.CONNECTION, "awareSession onSessionConfigUpdated()");
        }

        @Override
        public void onMessageSendFailed(int messageId) {
            Log.w(TAG, "Aware Message " + messageId + " failed");
        }
    };

    private void handleMessage(Pairing tx, byte[] message) {
        AbstractPacket packet = AbstractPacket.newFromBytes(message);
        if (packet == null) {
            CommsLog.log(CommsLog.Entry.Category.PROBLEM, "WiFi Aware message from "+((tx==null)?"unknown device":tx.getLabel())+" could not be parsed");
            return;
        }
        SqAnDevice device;
        if (packet.isDirectFromOrigin())
            device = SqAnDevice.findByUUID(packet.getOrigin());
        else
            device = tx.getDevice();
        if (device == null) {
            if (packet instanceof HeartbeatPacket) {
                if (packet.isDirectFromOrigin()) {
                    device = ((HeartbeatPacket)packet).getDevice();
                    if (device != null) {
                        SqAnDevice.add(device);
                        tx.setDevice(device);
                    }
                }
            }
            if (device == null)
                CommsLog.log(CommsLog.Entry.Category.COMMS, "WiFi Aware received a message from an unknown device");
            else
                CommsLog.log(CommsLog.Entry.Category.COMMS, "WiFi Aware received a message from previously unknown device, but HeartbeatPacket has device info for "+device.getLabel());
        }
        if (device != null) {
            Log.d(TAG,"Aware Message received from "+device.getLabel()+" ("+((tx.getPeerHandle()==null)?"unk peer handle":tx.getPeerHandle().hashCode())+")");
            device.setHopsAway(packet.getCurrentHopCount(), false,true, device.isDirectWiFiHighPerformance()); //don't consider WiFi Aware messages as the same thing as direct WiFI connection
            device.setLastConnect();
            device.addToDataTally(message.length);
        }
        //TODO relayPacketIfNeeded(connection,message,packet.getSqAnDestination(),packet.getOrigin(),packet.getCurrentHopCount());
        super.onReceived(packet);
    }

    /**
     * TODO Stubbed-out method to encrypt data
     * @param payload
     * @return
     */
    private byte[] encrypt(byte[] payload) {
        return payload;
    }

    /**
     * TODO Stubbed-out method to decrypt data
     * @param payload
     * @return
     */
    private byte[] decrypt(byte[] payload) {
        return payload;
    }
}
