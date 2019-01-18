package org.sofwerx.sqan.manet.common.pnt;

import android.util.Log;

import org.sofwerx.sqan.Config;

import java.nio.ByteBuffer;

/**
 * Used to represent a location in spacetime
 * Lat/Lng/Alt are WGS-84 and vertically meters height above ellipsoid
 * Time is device local
 */
public class SpaceTime {
    public final static double NO_ALTITUDE = Double.NaN;
    private double latitude, longitude, altitude;
    private long time;

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public SpaceTime() {
        this(Double.NaN,Double.NaN,Double.NaN,Long.MIN_VALUE);
    }

    /**
     * SpaceTime
     * @param latitude WGS-84
     * @param longitude WGS-84
     * @param altitude m HAE
     * @param time network time
     */
    public SpaceTime(double latitude, double longitude, double altitude, long time) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.time = time;
    }

    /**
     * SpaceTime
     * @param latitude WGS-84
     * @param longitude WGS-84
     * @param altitude m HAE
     */
    public SpaceTime(double latitude, double longitude, double altitude) {
        this(latitude,longitude,altitude,NetworkTime.toNetworkTime(System.currentTimeMillis()));
    }

    /**
     * SpaceTime
     * @param latitude WGS-84
     * @param longitude WGS-84
     */
    public SpaceTime(double latitude, double longitude) {
        this(latitude,longitude,NO_ALTITUDE,NetworkTime.toNetworkTime(System.currentTimeMillis()));
    }

    /**
     * SpaceTime
     * @param latitude WGS-84
     * @param longitude WGS-84
     * @param time network time
     */
    public SpaceTime(double latitude, double longitude, long time) {
        this(latitude,longitude,NO_ALTITUDE,time);
    }

    /**
     * Gets the altitude
     * @return m HAE or Double.NaN if no altitude
     */
    public double getAltitude() { return altitude; }

    /**
     * Set the altitude
     * @param altitude m HAE or Double.NaN if no altitude
     */
    public void setAltitude(double altitude) { this.altitude = altitude; }

    /**
     * Does this point contain altitude data
     * @return true == has altitude data
     */
    public boolean hasAltitude() { return !Double.isNaN(altitude); }

    /**
     * Gets the time
     * @return time as calculated based on network time
     */
    public long getTime() {
        return time;
    }

    /**
     * Sets the time
     * @param networkTime
     */
    public void setTime(long networkTime) {
        this.time = time;
    }

    /**
     * Does this point have the minimum data needed to be valuable
     * @return true == point has value
     */
    public boolean isValid() {
        return (time > 0l) && !Double.isNaN(latitude) && !Double.isNaN(longitude);
    }

    public final static int SIZE_IN_BYTES = 8 + 8 + 8 + 8;

    public byte[] toByteArray() {
        ByteBuffer out = ByteBuffer.allocate(SIZE_IN_BYTES);
        out.putDouble(latitude);
        out.putDouble(longitude);
        out.putDouble(altitude);
        out.putLong(time);
        return out.array();
    }

    /**
     * Gets a byte array representing an empty SpaceTime
     * @return
     */
    public static byte[] toByteArrayEmptySpaceTime() {
        SpaceTime spaceTime = new SpaceTime();
        return spaceTime.toByteArray();
    }

    public void parse(byte[] bytes) {
        if ((bytes == null) || (bytes.length != SIZE_IN_BYTES)) {
            Log.e(Config.TAG,"Unable to parse SpaceTime as "+SIZE_IN_BYTES+"b expected");
            return;
        }
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        latitude = buf.getDouble();
        longitude = buf.getDouble();
        altitude = buf.getDouble();
        time = buf.getLong();
    }
}
