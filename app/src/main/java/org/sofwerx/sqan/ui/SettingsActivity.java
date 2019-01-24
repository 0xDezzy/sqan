package org.sofwerx.sqan.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import org.sofwerx.sqan.Config;
import org.sofwerx.sqan.R;

public class SettingsActivity extends Activity {
    private SharedPreferences.OnSharedPreferenceChangeListener listener = (sharedPreferences, key) -> {
        Log.d(Config.TAG,"Preference changed: "+key);
        if (Config.PREFS_MANET_ENGINE.equalsIgnoreCase(key)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(SettingsActivity.this);
            builder.setTitle(R.string.shutdown_required);
            builder.setMessage(R.string.prefs_manet_changed_description);
            final AlertDialog dialog = builder.create();
            dialog.show();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new CoreSettingsFragment())
                .commit();
    }

    @Override
    protected void onResume() {
        super.onResume();
        PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(listener);
    }

    @Override
    protected void onPause() {
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(listener);
        super.onPause();
    }

    @Override
    protected void onStop() {
        /*if (Config.isRunInForegroundEnabled(this)) {
            Intent intent = new Intent(this, SqAnService.class);
            intent.setAction(SqAnService.ACTION_FOREGROUND_SERVICE);
            startService(intent);
        }*/
        super.onStop();
    }
}
