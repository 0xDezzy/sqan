package org.sofwerx.sqan.manet.common;

/**
 * Links the device index to the corresponding IPV4 address for forwarding traffic
 */
public class VpnForwardValue {
    public final static byte NOT_FORWARDED = (byte)0b11111110;
    private byte index;
    private int ipv4Address;
    public VpnForwardValue(byte index, int ipv4Address) {
        this.index = index;
        this.ipv4Address = ipv4Address;
    }

    public VpnForwardValue(byte index) { this.index = index; }

    public boolean isForwarded() { return index != NOT_FORWARDED; }

    public byte getForwardIndex() { return index; }

    public int getAddress() { return ipv4Address; }

    public void setIndex(byte index) { this.index = index; }
}
