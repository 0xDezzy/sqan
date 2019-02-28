package org.sofwerx.sqan.manet.common.sockets.client;

import android.util.Log;

import org.sofwerx.sqan.Config;
import org.sofwerx.sqan.ManetOps;
import org.sofwerx.sqan.listeners.ManetListener;
import org.sofwerx.sqan.manet.common.packet.AbstractPacket;
import org.sofwerx.sqan.manet.common.sockets.Challenge;
import org.sofwerx.sqan.manet.common.sockets.PacketParser;
import org.sofwerx.sqan.manet.common.sockets.SocketChannelConfig;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;

public class SocketTransceiver {
    private final static int MAX_PACKET_SIZE = 256000;
    private ByteBuffer inputBuffer;
    private final SocketChannelConfig config;
    private ClientState state;
    private static final int MAX_QUEUE_SLOTS = 10;
    //private SocketChannel socket;
    private final PacketParser parser;
    private enum ClientState {
        READING_BODY, READING_CHALLENGE
    }

    protected SocketTransceiver(SocketChannelConfig config, PacketParser parser) {
        this.state = ClientState.READING_CHALLENGE;
        this.config = config;
        this.parser = parser;
        this.inputBuffer = ByteBuffer.allocate(Challenge.CHALLENGE_LENGTH);
    }

    private void immediateOutput(ByteBuffer data, final WritableByteChannel channel) throws IOException {
        while (true) {
            while (data.hasRemaining() && channel.write(data) > 0) {}
            if (!data.hasRemaining()) {
                Log.d(Config.TAG,"Socket output complete");
                return;
            }
            try {
                Thread.sleep(100l);
            } catch (Throwable ignore) {
            }
        }
    }

    public void closeAll() {
        /*try {
            if (socket != null)
                socket.close();
        } catch (IOException ignore) {
        }*/
    }

    public boolean isReadyToWrite() {
        return state != ClientState.READING_CHALLENGE;
    }

    public int queue(AbstractPacket packet, WritableByteChannel channel, ManetListener listener) throws IOException {
        if (isReadyToWrite()) {
            byte[] data = parser.toBytes(packet);
            if (data != null) {
                Log.d(Config.TAG,"queuing "+data.length+"b message");
                ByteBuffer out = ByteBuffer.allocate(4 + data.length);
                out.putInt(data.length);
                out.put(data);
                out.flip();
                ManetOps.addBytesToTransmittedTally(data.length);
                immediateOutput(out, channel);
                if (listener != null)
                    listener.onTx(packet);
            }
        } else
            Log.d(Config.TAG,"Packet sent for queuing but socket connection is not ready to write");

        return MAX_QUEUE_SLOTS;
    }

    public void read(ReadableByteChannel channel, WritableByteChannel output) throws IOException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, ShortBufferException {
        boolean keepGoing = true;
        boolean firstTime = true;
        while (keepGoing) {
            if (state == ClientState.READING_CHALLENGE) {
                keepGoing = readChallenge(firstTime, channel, output);
                Log.d(Config.TAG,"SocketTransceiver.readChallenge complete, keepGoing "+keepGoing);
            } else
                keepGoing = parseMessage(channel);
            firstTime = false;
        }
    }

    private boolean parseMessage(ReadableByteChannel channel) throws IOException {
        if ((channel == null) || !channel.isOpen()) //channel is now closed
            return false;
        ByteBuffer preambleBuffer = ByteBuffer.allocate(4);
        while (preambleBuffer.hasRemaining() && (channel.read(preambleBuffer) > 0)) {}

        if (preambleBuffer.position() == 0)
            return false; //nothing to read
        preambleBuffer.rewind();
        int size = preambleBuffer.getInt();
        if (size < 0)
            throw new IOException("SocketTransceiver unable to processPacketAndNotifyManet a message with a negative size");
        else if (size > MAX_PACKET_SIZE)
            throw new IOException("SocketTransceiver unable to processPacketAndNotifyManet a "+size+"b message, this must be an error");
        else
            Log.d(Config.TAG,"SocketTransceiver received "+size+"b message");
        ByteBuffer data = ByteBuffer.allocate(size);
        while (data.hasRemaining() && (channel.read(data) > 0)) {}
        byte[] payload = data.array();
        parser.processPacketAndNotifyManet(payload);
        return false;
    }

    private boolean readChallenge(boolean firstTime, ReadableByteChannel channel, WritableByteChannel output) throws IOException, NoSuchAlgorithmException {
        Log.d(Config.TAG,"SocketTransceiver reading challenge");
        boolean success = false;
        try {
            while ((inputBuffer != null ) && inputBuffer.hasRemaining() && (channel.read(inputBuffer) > 0)) {
                firstTime = false;
            }
        } catch (NullPointerException e) {
        }
        if (inputBuffer.hasRemaining())
            return false;

        inputBuffer.flip();

        if (config != null) {
            try {
                byte[] responseArray = Challenge.getResponse(null,null);
                ByteBuffer response = ByteBuffer.allocate(responseArray.length);
                response.put(responseArray);
                response.flip();
                Log.d(Config.TAG,"Writing challenge response");
                immediateOutput(response, output);
                state = ClientState.READING_BODY;
                if (parser != null)
                    parser.getManet().onAuthenticatedOnNet();
            } catch (UnsupportedEncodingException ignore) {
            }
        }
        inputBuffer.position(0);
        return success;
    }
}
