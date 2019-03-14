package org.sofwerx.sqan.vpn;

import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import org.sofwerx.sqan.Config;
import org.sofwerx.sqan.R;
import org.sofwerx.sqan.SqAnService;
import org.sofwerx.sqan.ui.SettingsActivity;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import androidx.annotation.NonNull;

public class SqAnVpnService extends VpnService implements Handler.Callback {
    private static final int SQAN_VPN_NOTIFICATION = 837;
    private static final String TAG = SqAnVpnService.class.getSimpleName();
    public static final String ACTION_CONNECT = "org.sofwerx.sqan.vpn.START";
    public static final String ACTION_DISCONNECT = "org.sofwerx.sqan.vpn.STOP";
    private FileOutputStream out;
    private Handler mHandler;

    public static void start(@NonNull Context context) {
        Intent intent = new Intent(context, SqAnVpnService.class);
        intent.setAction(SqAnVpnService.ACTION_CONNECT);
        context.startService(intent);
    }

    public void onReceived(final byte[] data) {
        if (data == null)
            return;
        if (out != null) {
            Log.d(TAG,data.length+"b VpnPacket data received from SqAN");
            try {
                out.write(data);
            } catch (IOException e) {
                Log.e(TAG,"Unable to forward VpnPacket from SqAN to the VPN:"+e.getMessage());
            }
        } else
            Log.d(TAG,data.length+"b VpnPacket data received from SqAN, but VPN is not yet ready");
    }

    private final AtomicReference<Thread> mConnectingThread = new AtomicReference<>();
    private final AtomicReference<Connection> mConnection = new AtomicReference<>();
    private AtomicInteger mNextConnectionId = new AtomicInteger(1);
    private PendingIntent mConfigureIntent;
    private LiteWebServer webServer;

    private static class Connection extends Pair<Thread, ParcelFileDescriptor> {
        public Connection(Thread thread, ParcelFileDescriptor pfd) {
            super(thread, pfd);
        }
    }

    @Override
    public void onCreate() {
        if (mHandler == null)
            mHandler = new Handler(this);
        mConfigureIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, SettingsActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_DISCONNECT.equals(intent.getAction())) {
            disconnect();
            return START_NOT_STICKY;
        } else {
            connect();
            return START_STICKY;
        }
    }

    @Override
    public void onDestroy() {
        disconnect();
    }

    @Override
    public boolean handleMessage(Message message) {
        Toast.makeText(this, message.what, Toast.LENGTH_SHORT).show();
        if (message.what != R.string.disconnected)
            updateForegroundNotification(message.what);
        return true;
    }

    private void connect() {
        updateForegroundNotification(R.string.connecting);
        mHandler.sendEmptyMessage(R.string.connecting);
        startConnection(new SqAnVpnConnection(this, mNextConnectionId.getAndIncrement()));
        if (Config.isVpnHostLandingPage())
            webServer = new LiteWebServer();
        SqAnService.getInstance().setVpnService(this);
    }
    private void startConnection(final SqAnVpnConnection connection) {
        final Thread thread = new Thread(connection, "SqAnVpnThread");
        setConnectingThread(thread);
        connection.setConfigureIntent(mConfigureIntent);
        connection.setOnEstablishListener(tunInterface -> {
            mHandler.sendEmptyMessage(R.string.connected);
            mConnectingThread.compareAndSet(thread, null);
            setConnection(new Connection(thread, tunInterface));
        });
        thread.start();
    }
    private void setConnectingThread(final Thread thread) {
        final Thread oldThread = mConnectingThread.getAndSet(thread);
        if (oldThread != null)
            oldThread.interrupt();
    }
    private void setConnection(final Connection connection) {
        final Connection oldConnection = mConnection.getAndSet(connection);
        if ((connection != null) && (connection.second != null))
            out = new FileOutputStream(connection.second.getFileDescriptor());
        if (oldConnection != null) {
            try {
                oldConnection.first.interrupt();
                oldConnection.second.close();
            } catch (IOException e) {
                Log.e(TAG, "Closing VPN interface: "+e.getMessage());
            }
        }
    }

    private void disconnect() {
        if (out != null) {
            try {
                out.close();
            } catch (Exception ignore) {
            }
            out = null;
        }
        SqAnService.getInstance().setVpnService(null);
        if (webServer != null) {
            webServer.stop();
            webServer = null;
        }
        mHandler.sendEmptyMessage(R.string.disconnected);
        setConnectingThread(null);
        setConnection(null);
        stopForeground(true);
    }

    private void updateForegroundNotification(final int message) {
        final String NOTIFICATION_CHANNEL_ID = "SqAnVpn";
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mNotificationManager.createNotificationChannel(new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_ID,
                    NotificationManager.IMPORTANCE_DEFAULT));
            builder = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID);
        } else
            builder = new Notification.Builder(this);
        startForeground(SQAN_VPN_NOTIFICATION, builder
                .setSmallIcon(R.drawable.ic_vpn)
                .setContentText(getString(message))
                .setContentIntent(mConfigureIntent)
                .build());
    }
}