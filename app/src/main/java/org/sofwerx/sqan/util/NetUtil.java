package org.sofwerx.sqan.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.aware.WifiAwareManager;
import android.os.Build;
import android.util.Log;

import org.sofwerx.sqan.Config;
import org.sofwerx.sqan.SqAnService;
import org.sofwerx.sqan.manet.common.SqAnDevice;
import org.sofwerx.sqan.manet.common.issues.WiFiInUseIssue;

import java.io.StringWriter;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class NetUtil {
    public enum ConnectionType {WIFI,MOBILE,NOT_CONNECTED};
    public static String INTENT_CONNECTIVITY_CHANGED = "android.net.conn.CONNECTIVITY_CHANGE";
    public static String INTENT_WIFI_CHANGED = "android.net.wifi.WIFI_STATE_CHANGED";

    public static ConnectionType getConnectivityStatus(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        if (activeNetwork != null) {
            if(activeNetwork.getType() == ConnectivityManager.TYPE_WIFI)
                return ConnectionType.WIFI;

            if(activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE)
                return ConnectionType.MOBILE;
        }
        return ConnectionType.NOT_CONNECTED;
    }

    /**
     * Turns-on WiFi is it is not already
     */
    public static void turnOnWiFiIfNeeded(Context context) {
        WifiManager wiFiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (!wiFiManager.isWifiEnabled())
            wiFiManager.setWifiEnabled(true);
    }

    /**
     * Leave any current WiFi networks
     */
    public static void forceLeaveWiFiNetworks(Context context) {
        if (isWiFiConnected(context)) {
            Log.d(Config.TAG,"WiFi connection detected; disconnecting");
            WifiManager wiFiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            wiFiManager.disconnect(); //TODO maybe find a more elegant solution than this
            SqAnService.clearIssue(new WiFiInUseIssue(false,null));
        }
    }

    /**
     * Is this device currently actively connected to a WiFi network
     * @param context
     * @return true == active WiFi connection
     */
    public static boolean isWiFiConnected(Context context) {
        ConnectivityManager connectionManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectionManager.getActiveNetworkInfo();
        return ((activeNetworkInfo != null) && activeNetworkInfo.isConnected() && (activeNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI));
    }

    /**
     * Provides a checksum byte for a given byte array
     * @param bytes
     * @return
     */
    public static byte getChecksum(byte[] bytes) {
        byte checksum = 0b111000;
        if (bytes != null) {
            for (byte b:bytes) {
                checksum ^= b; //just a quick XOR for checksum
            }
        }
        return checksum;

    }

    public static final byte[] longToByteArray(long value) {
        return new byte[] {
                (byte)(value >>> 56),
                (byte)(value >>> 48),
                (byte)(value >>> 40),
                (byte)(value >>> 32),
                (byte)(value >>> 24),
                (byte)(value >>> 16),
                (byte)(value >>> 8),
                (byte)value};
    }

    public static final byte[] intToByteArray(int value) {
        return new byte[] {
                (byte)(value >>> 24),
                (byte)(value >>> 16),
                (byte)(value >>> 8),
                (byte)value};
    }

    public static final int byteArrayToInt(byte[] bytes) {
        if ((bytes == null) || (bytes.length != 4))
            return Integer.MIN_VALUE;
        return bytes[0] << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF);
    }

    /**
     * Provides a string listing of the byte values in this array for debug purposes
     * @param bytes
     * @return
     */
    public static String getStringOfBytesForDebug(byte[] bytes) {
        if (bytes == null)
            return null;
        StringWriter out = new StringWriter();

        boolean first = true;
        for (byte b:bytes) {
            if (first)
                first = false;
            else
                out.append(' ');
            out.append(Integer.toString(b));
        }

        return out.toString();
    }

    /**
     * Gets the Int value at a particular offset in an IP Packet
     * @param packet
     * @param offset
     * @return Int value or Int.MIN_VALUE if something went wrong
     */
    public static int getIntInIpPacket(byte[] packet, int offset) {
        if ((packet == null) || (offset < 0) || (offset + 4 > packet.length))
            return Integer.MIN_VALUE;
        byte[] intBytes = new byte[4];
        for (int i=0;i<4;i++) {
            intBytes[i] = packet[offset+i];
        }
        return byteArrayToInt(intBytes);
    }

    /**
     * Parse an IP packet to get the source IP address
     * @param packet
     * @return
     */
    public static int getSourceIpFromIpPacket(byte[] packet) {
        int ip = getIntInIpPacket(packet,12);
        if (ip == Integer.MIN_VALUE)
            return SqAnDevice.BROADCAST_IP;
        return ip;
    }

    /**
     * Parse an IP packet to get the destination IP address
     * @param packet
     * @return
     */
    public static int getDestinationIpFromIpPacket(byte[] packet) {
        int ip = getIntInIpPacket(packet,16);
        if (ip == Integer.MIN_VALUE)
            return SqAnDevice.BROADCAST_IP;
        return ip;
    }


    public static enum DscpType {
        Network_Control,
        Telephony,
        Signaling,
        Multimedia_Conferencing,
        Real_Time_Interactive,
        Multimedia_Streaming,
        Broadcast_Video,
        Low_Latency_Data,
        OAM,
        High_Throughput_Data,
        Standard,
        Low_Priority_Data,
        UNKNOWN
    }

    /**
     * Gets the DCP type based on the BSCP byte
     * @param dscp
     * @return type or UNKNOWN
     */
    public static DscpType getDscpType(byte dscp) {
        switch (dscp & MASK_DSCP) {
            case ((byte)0b110000):
                return DscpType.Network_Control;

            case ((byte)0b101110):
                return DscpType.Telephony;

            case ((byte)0b101000):
                return DscpType.Signaling;

            case ((byte)0b100010):
            case ((byte)0b100100):
            case ((byte)0b100110):
                return DscpType.Multimedia_Conferencing;

            case ((byte)0b100000):
                return DscpType.Real_Time_Interactive;

            case ((byte)0b011010):
            case ((byte)0b0111000):
            case ((byte)0b011110):
                return DscpType.Multimedia_Streaming;

            case ((byte)0b011000):
                return DscpType.Broadcast_Video;

            case ((byte)0b010010):
            case ((byte)0b010100):
            case ((byte)0b010110):
                return DscpType.Low_Latency_Data;

            case ((byte)0b010000):
                return DscpType.OAM;

            case ((byte)0b001010):
            case ((byte)0b001100):
            case ((byte)0b001110):
                return DscpType.High_Throughput_Data;

            case ((byte)0b000000):
                return DscpType.Standard;

            case ((byte)0b001000):
                return DscpType.Low_Priority_Data;

            default:
                return DscpType.UNKNOWN;
        }
    }

    private final static byte MASK_DSCP = (byte)0b11111100;
    /**
     * Parse an IP packet to get the DSCP
     * @param packet
     * @return
     */
    public static byte getDscpFromIpPacket(byte[] packet) {
        if ((packet == null) || (packet.length < 4))
            return Byte.MIN_VALUE;
        byte dscpByte = packet[1];
        return (byte)(dscpByte & MASK_DSCP);
    }

    public static Inet6Address getAwareAddress(Context context, Network network) {
        if (context == null)
            return null;
        ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        /*if (network == null) { //try to find the Aware network
            Network[] networks = connMgr.getAllNetworks();
            if (networks != null) {
                Log.d(Config.TAG,networks.length+" available networks");
                for (Network nw:networks) {
                    //TODO trying to find the aware network here
                }
            }
        }*/
        if (network == null)
            return null;
        LinkProperties linkProperties = connMgr.getLinkProperties(network);
        return getAwareAddress(linkProperties);
    }

    public static Inet6Address getAwareAddress(LinkProperties linkProperties) {
        Inet6Address ipv6 = null;

        try {
            NetworkInterface awareNi = NetworkInterface.getByName(linkProperties.getInterfaceName());
            Enumeration inetAddresses = awareNi.getInetAddresses();
            while (inetAddresses.hasMoreElements()) {
                InetAddress addr = (InetAddress) inetAddresses.nextElement();
                if (addr instanceof Inet6Address) {
                    if (addr.isLinkLocalAddress()) {
                        ipv6 = (Inet6Address) addr;
                        break;
                    }
                }
            }
        } catch (Exception ignore) {
        }

        return ipv6;
    }

    private final static int MIN_PASSPHRASE_LENGTH = 8; //TODO unable to find these definitively, so they are guesses
    private final static int MAX_PASSPHRASE_LENGTH = 63; //TODO unable to find these definitively, so they are guesses

    /**
     * Adjusts the passcode to meet WiFi Aware requirements
     * @param passcode
     * @return
     */
    public static String conformPasscodeToWiFiAwareRequirements(String passcode) {
        if ((passcode != null) && (passcode.length() >= MIN_PASSPHRASE_LENGTH) && (passcode.length() <= MAX_PASSPHRASE_LENGTH))
            return passcode;
        StringWriter out = new StringWriter();

        int i=0;
        while (i < MIN_PASSPHRASE_LENGTH) {
            if (i < passcode.length())
                out.append(passcode.charAt(i));
            else
                out.append('x');
            i++;
        }
        while ((i < MAX_PASSPHRASE_LENGTH) && (i < passcode.length())) {
            out.append(passcode.charAt(i));
            i++;
        }
        String output = out.toString();
        Log.d(Config.TAG,"Passphrase \""+passcode+"\" adjusted to \""+output+"\" to conform with WiFi Aware requirements");
        return out.toString();
    }
}