package org.sofwerx.sqandr.sdr;

//import android.util.Log;
import org.sofwerx.pisqan.Log;

import org.sofwerx.pisqan.Config;
//import org.sofwerx.sqan.listeners.PeripheralStatusListener;
import org.sofwerx.sqandr.sdr.sar.Segment;
import org.sofwerx.sqandr.sdr.sar.Segmenter;
import org.sofwerx.sqandr.util.WriteableInputStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractDataConnection {
    private final static String TAG = Config.TAG+".DataCon";
    private final static long TIME_BETWEEN_STALE_SEGMENTATION_CHECKS = 500l;
    protected DataConnectionListener listener;
    public abstract boolean isActive();
    public abstract void write(byte[] data);
    public abstract void burstPacket(byte[] data);
    public void setListener(DataConnectionListener listener) { this.listener = listener; }
    private AtomicBoolean keepGoing = new AtomicBoolean(true);
//    protected PeripheralStatusListener peripheralStatusListener = null;
    private WriteableInputStream dataBuffer;
    private Thread readThread;
    private long nextStaleCheck = Long.MIN_VALUE;
    private ArrayList<Segmenter> segmenters;
    protected long sdrConnectionCongestedUntil = Long.MIN_VALUE;
    private final static long TIME_CONGESTION_IS_RECENT = 1000l * 5l; //time in ms to consider any congestion marker as recent

    //TODO implement the below
    protected long rfCongestedUntil = Long.MIN_VALUE;
    private final static long TIME_FOR_RF_ACTIVITY_TO_ADD_TO_CONGESTION = 100l; //ms to wait if data is flowing in via RF on the bulk data channel

    protected void handleRawDatalinkInput(final byte[] raw) {
        if (raw == null) {
            Log.w(TAG,"handleRawDatalinkInput received null input, ignoring");
            return;
        }
        else {
            Log.v(TAG, "handleRawDatalinkInput received " + raw.length + "b raw input");
        }
        Log.v(TAG,"handleRawDatalinkInput is processing "+raw.length+"b");
        if (dataBuffer == null)
            dataBuffer = new WriteableInputStream();
        dataBuffer.write(raw);
        Log.v(TAG,raw.length+"b added to dataBuffer");
        if (readThread == null) {
            readThread = new Thread() {
                @Override
                public void run() {
                    Log.d(TAG,"starting read thread");
                    while(keepGoing.get()) {
                        byte[] out = readPacketData();
                        Log.d(TAG,((out==null)?"null":out.length+"b")+" packet data read by dataBuffer");
                        if ((out != null) && (listener != null))
                            listener.onReceiveDataLinkData(out);
                        else if (listener == null)
                            Log.d(TAG,"...but ignored as DataConnectionListener in AbstractDataConnection is null");
                        if (System.currentTimeMillis() > nextStaleCheck) {
                            if ((segmenters != null) && !segmenters.isEmpty()) {
                                int i=0;
                                while (i<segmenters.size()) {
                                    if ((segmenters.get(i) == null) || segmenters.get(i).isStale()) {
                                        Log.d(TAG,"Segment #"+i+" (id "+((int)segmenters.get(i).getPacketId())+") stale, dropping (Segments: "+segmenters.get(i).getParts()+")");
                                        segmenters.remove(i);
                                        if (listener != null)
                                            listener.onPacketDropped();
                                    } else
                                        i++;
                                }
                                nextStaleCheck = System.currentTimeMillis() + TIME_BETWEEN_STALE_SEGMENTATION_CHECKS;
                            } else
                                nextStaleCheck = System.currentTimeMillis() + TIME_BETWEEN_STALE_SEGMENTATION_CHECKS * 2;
                        }
                    }
                }
            };
            readThread.start();
        }
    }

    /**
     * Is the comms path to the SDR unable to keep up with the current outflow of data
     * @return true == congested
     */
    public boolean isSdrConnectionCongested() {
        return System.currentTimeMillis() < sdrConnectionCongestedUntil;
    }

    /**
     * Is the connection to the SDR congested or recently congested
     * @return true == the connection to the SDR was recently or is congested
     */
    public boolean isSdrConnectionRecentlyCongested() {
        return System.currentTimeMillis() < sdrConnectionCongestedUntil + TIME_CONGESTION_IS_RECENT;
    }

    public void close() {
        keepGoing.set(false);
        if (dataBuffer != null) {
            Log.d(TAG,"Closing data buffer");
            try {
                dataBuffer.close();
            } catch (IOException e) {
                Log.w(TAG,"Unable to close dataBuffer: "+e.getMessage());
            }
            dataBuffer = null;
        }
        if (readThread != null) {
            Log.d(TAG,"Stopping readThread");
            readThread.interrupt();
            readThread = null;
        }
    }

    private int readPartialHeader() throws IOException {
        byte[] header = new byte[3];
        dataBuffer.read(header);
        int size;
        if (Segment.isQuickValidCheck(header)) {
            size = header[2] & 0xFF; //needed to convert signed byte into unsigned int
            return size;
        } else {
            if (this.listener == null)
                return 0;

            if (listener != null)
                listener.onPacketDropped();
            int lost = 0;
            while (keepGoing.get()) {
                byte dig = (byte)dataBuffer.read();
                lost++;
                if ((lost % 100) == 0) {
                    if (listener != null) {
                        Log.d(TAG, "100b lost as no packet header found");
                        listener.onPacketDropped();
                    }
                }
                if (dig == Segment.HEADER_MARKER[0]) {
                    dig = (byte)dataBuffer.read();
                    lost++;
                    if (dig == Segment.HEADER_MARKER[1]) {
                        size = dataBuffer.read() & 0xFF;
                        lost++;
                        if ((size < Segment.MAX_LENGTH_BEFORE_SEGMENTING) && (size > 0)) {
                            Log.d(TAG,lost+"b lost, but new header found");
                            return size;
                        }
                    }
                }
            }
            return -1;
        }
    }

    private Segmenter findSegmenter(byte packetId) {
        if ((segmenters != null) && !segmenters.isEmpty()) {
            for (Segmenter segmenter:segmenters) {
                if (segmenter.getPacketId() == packetId)
                    return segmenter;
            }
        }
        return null;
    }

    private void handleSegment(Segment segment) {
        if (segment == null)
            return;
        Segmenter segmenter = findSegmenter(segment.getPacketId());
        if (segmenter == null) {
            Log.d(TAG,"First segment for new packet received");
            segmenter = new Segmenter(segment.getPacketId());
            segmenter.add(segment);
            if (segmenters == null)
                segmenters = new ArrayList<>();
            segmenters.add(segmenter);
        } else {
            segmenter.add(segment);
            if (segmenter.isComplete()) {
                Log.d(TAG,"Packet with "+segmenter.getSegmentCount()+" segments successfully reassembled");
                if (listener != null)
                    listener.onReceiveDataLinkData(segmenter.reassemble());
                segmenters.remove(segmenter);
            }
        }
    }

    private byte[] readPacketData() {
        Log.d(TAG,"readPacketData()");
        if (dataBuffer == null)
            return null;
        try {
            int size = readPartialHeader();
            if ((size < 0) || (size > Segment.MAX_LENGTH_BEFORE_SEGMENTING))
                throw new IOException("Unable to read packet - invalid size "+size+"b - this condition should never happen unless the link is shutting down");
            Log.d(TAG,"packet size "+size+"b");
            byte[] rest = new byte[size+2]; //2 added to get the rest of the header
            dataBuffer.read(rest);
            Log.d(TAG,"Rest of packet read");
            Segment segment = new Segment();
            segment.parseRemainder(rest);
            if (segment.isValid()) {
                if (segment.isStandAlone()) {
                    Log.d(TAG,"Standalone packet recovered");
                    return segment.getData();
                } else
                    handleSegment(segment);
            } else {
                Log.d(TAG,"readPacketData produced invalid Segment; dropping");
                if (listener != null)
                    listener.onPacketDropped();
            }
        } catch (IOException e) {
            Log.e(TAG,"Unable to read packet: "+e.getMessage());
        }
        return null;
    }

//    public void setPeripheralStatusListener(PeripheralStatusListener listener) {
//        this.peripheralStatusListener = listener;
//    }
}
