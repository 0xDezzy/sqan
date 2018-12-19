package org.sofwerx.sqan.manet.packet;

import android.util.Log;

import org.sofwerx.sqan.Config;

import java.nio.ByteBuffer;

/**
 * Data bundled for transmission over the MANET
 */
public abstract class AbstractPacket {
    protected PacketHeader packetHeader;

    public AbstractPacket(PacketHeader packetHeader) {
        this.packetHeader = packetHeader;
    }

    //creates a new packet from the byte array
    public static AbstractPacket newFromBytes(byte[] bytes) {
        if ((bytes == null) || (bytes.length < PacketHeader.getSize())) {
            Log.e(Config.TAG,"Unable to generate a packet from the byte array; byte array was not big enough to hold a header");
        }
        AbstractPacket packet = null;
        ByteBuffer reader = ByteBuffer.wrap(bytes);

        byte[] headerBytes = new byte[PacketHeader.getSize()];
        reader.get(headerBytes);
        PacketHeader header = PacketHeader.newFromBytes(headerBytes);

        if (header == null) {
            Log.e(Config.TAG,"Unable to generate a packet header from the byte array");
            return null;
        }

        packet = AbstractPacket.newFromHeader(header);
        if (packet == null) {
            Log.e(Config.TAG,"Unable to generate a packet from the packet header");
            return null;
        }

        int len = reader.remaining();
        if (len > 0) {
            byte[] packetBytes = new byte[len];
            reader.get(packetBytes);
            packet.parse(packetBytes);
        }
        reader.clear();
        return packet;
    }

    public byte[] toByteArray() {
        if (packetHeader == null)
            return null;
        return packetHeader.toByteArray();
    }

    public static AbstractPacket newFromHeader(PacketHeader packetHeader) {
        if (packetHeader == null) {
            Log.e(Config.TAG,"Cannot generate a Packet from an empty packet header");
            return null;
        }

        AbstractPacket packet = null;

        switch (packetHeader.getType()) {
            case PacketHeader.PACKET_TYPE_RAW_BYTES:
                packet = new RawBytesPacket(packetHeader);
                break;
        }

        return packet;
    }

    public abstract void parse(byte[] bytes);
    protected abstract int getType();
}
