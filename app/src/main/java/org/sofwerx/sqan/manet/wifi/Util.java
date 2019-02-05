package org.sofwerx.sqan.manet.wifi;

import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;

public class Util {
    public static String getFailureStatusString(int reason) {
        switch (reason) {
            case WifiP2pManager.BUSY:
                return "busy";

            case WifiP2pManager.P2P_UNSUPPORTED:
                return "P2P unsupported";

            case WifiP2pManager.ERROR:
                return "unspecified error";

            case WifiP2pManager.NO_SERVICE_REQUESTS:
                return "no service requests";
        }
        return "unknown";
    }

    public static String getDeviceStatusString(int status) {
        switch (status) {
            case WifiP2pDevice.AVAILABLE:
                return "Available";

            case WifiP2pDevice.FAILED:
                return "Failed";

            case WifiP2pDevice.INVITED:
                return "Invited";

            case WifiP2pDevice.CONNECTED:
                return "Connected";
        }
        return "unknown";
    }
}
