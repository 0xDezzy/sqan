package org.sofwerx.sqandr.util;

//import android.hardware.usb.UsbConstants;

public class SdrUtils {
    public static int getInt(byte[] b) {
        return b[0] & 0xFF | (b[1] & 0xFF) << 8 |
                (b[2] & 0xFF) << 16 | (b[3] & 0xFF) << 24;
    }

    public static int getInt(byte[] b, int offset) {
        return b[offset+0] & 0xFF | (b[offset+1] & 0xFF) << 8 |
                (b[offset+2] & 0xFF) << 16 | (b[offset+3] & 0xFF) << 24;
    }

    public static long getLong(byte[] b) {
        return b[0] & 0xFF | (b[1] & 0xFF) << 8 | (b[2] & 0xFF) << 16 |
                (b[3] & 0xFF) << 24 | (b[4] & 0xFF) << 32 | (b[5] & 0xFF) << 40 |
                (b[6] & 0xFF) << 48 | (b[7] & 0xFF) << 56;
    }

    public static long getLong(byte[] b, int offset) {
        return b[offset+0] & 0xFF | (b[offset+1] & 0xFF) << 8 | (b[offset+2] & 0xFF) << 16 |
                (b[offset+3] & 0xFF) << 24 | (b[offset+4] & 0xFF) << 32 | (b[offset+5] & 0xFF) << 40 |
                (b[offset+6] & 0xFF) << 48 | (b[offset+7] & 0xFF) << 56;
    }

    public static byte[] intToByteArray(int i) {
        byte[] b = new byte[4];
        b[0] = (byte) (i & 0xff);
        b[1] = (byte) ((i >> 8) & 0xff);
        b[2] = (byte) ((i >> 16) & 0xff);
        b[3] = (byte) ((i >> 24) & 0xff);
        return b;
    }

    public static byte[] longToByteArray(long i) {
        byte[] b = new byte[8];
        b[0] = (byte) (i & 0xff);
        b[1] = (byte) ((i >> 8) & 0xff);
        b[2] = (byte) ((i >> 16) & 0xff);
        b[3] = (byte) ((i >> 24) & 0xff);
        b[4] = (byte) ((i >> 32) & 0xff);
        b[5] = (byte) ((i >> 40) & 0xff);
        b[6] = (byte) ((i >> 48) & 0xff);
        b[7] = (byte) ((i >> 56) & 0xff);
        return b;
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

    public static String getUsbClass(int interfaceClass) {
//        switch (interfaceClass) {
//            case UsbConstants.USB_CLASS_APP_SPEC:
//                return "APP_SPEC";
//            case UsbConstants.USB_CLASS_AUDIO:
//                return "AUDIO";
//            case UsbConstants.USB_CLASS_CDC_DATA:
//                return "CDC_DATA";
//            case UsbConstants.USB_CLASS_COMM:
//                return "COMM";
//            case UsbConstants.USB_CLASS_CONTENT_SEC:
//                return "CONTENT_SEC";
//            case UsbConstants.USB_CLASS_CSCID:
//                return "CSCID";
//            case UsbConstants.USB_CLASS_HID:
//                return "HID";
//            case UsbConstants.USB_CLASS_HUB:
//                return "HUB";
//            case UsbConstants.USB_CLASS_MASS_STORAGE:
//                return "MASS_STORAGE";
//            case UsbConstants.USB_CLASS_MISC:
//                return "MISC";
//            case UsbConstants.USB_CLASS_PER_INTERFACE:
//                return "PER_INTERFACE";
//            case UsbConstants.USB_CLASS_PHYSICA:
//                return "PHYSICA";
//            case UsbConstants.USB_CLASS_PRINTER:
//                return "PRINTER";
//            case UsbConstants.USB_CLASS_STILL_IMAGE:
//                return "STILL_IMAGE";
//            case UsbConstants.USB_CLASS_VENDOR_SPEC:
//                return "VENDOR_SPEC";
//            case UsbConstants.USB_CLASS_VIDEO:
//                return "VIDEO";
//            case UsbConstants.USB_CLASS_WIRELESS_CONTROLLER:
//                return "WIRELESS_CONTROLLER";
//        }
        return "Unknown ("+interfaceClass+")";
    }

    public static String getEndpointType(int type) {
//        switch (type) {
//            case UsbConstants.USB_ENDPOINT_XFER_BULK:
//                return "XFER_BULK";
//            case UsbConstants.USB_ENDPOINT_XFER_CONTROL:
//                return "XFER_CONTROL";
//            case UsbConstants.USB_ENDPOINT_XFER_INT:
//                return "XFER_INT";
//            case UsbConstants.USB_ENDPOINT_XFER_ISOC:
//                return "XFER_ISOC";
//        }
        return "unknown";
    }

    public static String getUsbDirection(int dir) {
//        switch (dir) {
//            case UsbConstants.USB_DIR_IN:
//                return "IN";
//            case UsbConstants.USB_DIR_OUT:
//                return "OUT";
//        }
        return "error";
    }
}
