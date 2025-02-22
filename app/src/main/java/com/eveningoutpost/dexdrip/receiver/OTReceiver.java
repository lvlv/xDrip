package com.eveningoutpost.dexdrip.receiver;

import static com.eveningoutpost.dexdrip.xdrip.gs;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;

import com.eveningoutpost.dexdrip.models.BgReading;
import com.eveningoutpost.dexdrip.models.JoH;
import com.eveningoutpost.dexdrip.models.UserError;
import com.eveningoutpost.dexdrip.utilitymodels.Constants;
import com.eveningoutpost.dexdrip.utils.DexCollectionType;
import com.squareup.moshi.Json;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created on 02/20/2025.
 */
public class OTReceiver extends BroadcastReceiver {

    private static final String TAG = "OTReceiver";
    private static final String ACTION_OT_APP = "com.eveningoutpost.dexdrip.OT_APP";
    private static final Object lock = new Object();
    private static SharedPreferences prefs;
    private static final long segmentation_timeslice = (long) (Constants.MINUTE_IN_MS * 4.5);

    @Override
    public void onReceive(Context context, Intent intent) {
        if (DexCollectionType.getDexCollectionType() != DexCollectionType.OTReceiver) {
            UserError.Log.w(TAG, "Received OT Broadcast, but OTReceiver is not selected as collector.");
            return;
        }
        new Thread() {
            @Override
            public void run() {
                PowerManager.WakeLock wl = JoH.getWakeLock("ot-receiver", 60000);
                synchronized (lock) {
                    try {
                        UserError.Log.d(TAG, "OT onReceiver: " + intent.getAction());
                        JoH.benchmark(null);

                        // check source
                        if (prefs == null)
                            prefs = PreferenceManager.getDefaultSharedPreferences(context);

                        final Bundle bundle = intent.getExtras();
                        final String action = intent.getAction();

                        if (bundle == null || action == null) {
                            UserError.Log.d(TAG, "Either bundle or action is null.");
                            return;
                        }

                        UserError.Log.d(TAG, "Action: " + action);
                        JoH.dumpBundle(bundle, TAG);

                        switch (action) {

                            case ACTION_OT_APP:
                                processOTApp(bundle);
                                break;

                            default:
                                UserError.Log.e(TAG, "Unknown action! " + action);
                                break;
                        }
                    } finally {
                        JoH.benchmark("OT process");
                        JoH.releaseWakeLock(wl);
                    }
                } // lock
            }

        }.start();

    }

    private void processOTApp(Bundle bundle) {
        String collection = bundle.getString("collection");
        if (collection == null || !collection.equals("entries")) {
            UserError.Log.e(TAG, "OT Broadcast invalid, collection is " + collection);
            return;
        }
        String data = bundle.getString("data");
        if (data == null || !bundle.containsKey("data")) {
            UserError.Log.e(TAG, "OT Broadcast invalid, missing data.");
            return;
        }
        try {
            JSONArray jsonData = new JSONArray(data);
            for (int i = 0; i < jsonData.length(); ++i) {
                JSONObject jsonEntry = jsonData.getJSONObject(i);
                Long ts = jsonEntry.getLong("date");
                double bgValueMgDl = jsonEntry.getDouble("sgv");
                UserError.Log.d(TAG, "parsed date: " + ts + " sgv: " + bgValueMgDl);
                BgReading.bgReadingInsertOT(bgValueMgDl, ts);
            }
        } catch (JSONException e) {
            UserError.Log.e(TAG, e.toString());
        }
    }

}


