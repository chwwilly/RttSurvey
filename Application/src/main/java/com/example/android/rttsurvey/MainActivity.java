/*
 * Copyright (C) 2018 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.rttsurvey;

//import static com.example.android.rttsurvey.AccessPointRangingResultsActivity.SCAN_RESULT_EXTRA;
import static com.example.android.rttsurvey.RangingSurveyActivity.SURVEY_EXTRA;

import android.Manifest.permission;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.rtt.RangingRequest;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.LayoutManager;

import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.example.android.rttsurvey.MyAdapter.ScanResultClickListener;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.lang.String;
import java.util.TimeZone;


/**
 * Displays list of Access Points enabled with WifiRTT (to check distance). Requests location
 * permissions if they are not approved via secondary splash screen explaining why they are needed.
 */
public class MainActivity extends AppCompatActivity implements ScanResultClickListener {

    private static final String TAG = "MainActivity";

    private boolean mLocationPermissionApproved = false;
    private boolean mExternalPermissionApproved = false;

    List<ScanResult> mAccessPointsSupporting80211mc;

    private WifiManager mWifiManager;
    private WifiScanReceiver mWifiScanReceiver;

    private TextView mOutputTextView;
    private RecyclerView mRecyclerView;

    private MyAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mOutputTextView = findViewById(R.id.access_point_summary_text_view);
        mRecyclerView = findViewById(R.id.recycler_view);

        // Improve performance if you know that changes in content do not change the layout size
        // of the RecyclerView
        mRecyclerView.setHasFixedSize(true);

        // use a linear layout manager
        LayoutManager layoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(layoutManager);

        mAccessPointsSupporting80211mc = new ArrayList<>();

        mAdapter = new MyAdapter(mAccessPointsSupporting80211mc, this);
        mRecyclerView.setAdapter(mAdapter);

        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mWifiScanReceiver = new WifiScanReceiver();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume()");
        super.onResume();

        mLocationPermissionApproved =
                ActivityCompat.checkSelfPermission(this, permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;

        mExternalPermissionApproved =
                ActivityCompat.checkSelfPermission(this, permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED;

        registerReceiver(
                mWifiScanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause()");
        super.onPause();
        unregisterReceiver(mWifiScanReceiver);
    }

    private void logToUi(final String message) {
        if (!message.isEmpty()) {
            Log.d(TAG, message);
            mOutputTextView.setText(message);
        }
    }

    @Override
    public void onScanResultItemClick(ScanResult scanResult) {

    }

    public void onClickFindDistancesToAccessPoints(View view) {
        if (mLocationPermissionApproved && mExternalPermissionApproved) {
            logToUi(getString(R.string.retrieving_access_points));
            mWifiManager.startScan();
            return;
        } else {
            // On 23+ (M+) devices, fine location permission not granted. Request permission.
            Intent startIntent = new Intent(this, LocationPermissionRequestActivity.class);
            startActivity(startIntent);
        }
    }

    public void onClickStartSurvey(View view) {
        if (mAdapter.getSelected().size() > 0) {
            Intent intent = new Intent(this, RangingSurveyActivity.class);
            intent.putParcelableArrayListExtra(SURVEY_EXTRA, (ArrayList<? extends Parcelable>) mAdapter.getSelected());
            startActivity(intent);
        }
    }

    private class WifiScanReceiver extends BroadcastReceiver {

        private List<ScanResult> find80211mcSupportedAccessPoints(
                @NonNull List<ScanResult> originalList) {
            List<ScanResult> newList = new ArrayList<>();

            for (ScanResult scanResult : originalList) {

                if (scanResult.is80211mcResponder()) {
                    //Log.d(TAG, scanResult+"");
                    newList.add(scanResult);
                }

                if (newList.size() >= RangingRequest.getMaxPeers()) {
                    break;
                }
            }
            return newList;
        }

        // This is checked via mLocationPermissionApproved boolean
        @SuppressLint("MissingPermission")
        public void onReceive(Context context, Intent intent) {

            List<ScanResult> scanResults = mWifiManager.getScanResults();

            if (scanResults != null) {

                if (mLocationPermissionApproved) {
                    mAccessPointsSupporting80211mc = find80211mcSupportedAccessPoints(scanResults);

                    mAdapter.swapData(mAccessPointsSupporting80211mc);

                    logToUi(
                            scanResults.size()
                                    + " APs discovered, "
                                    + mAccessPointsSupporting80211mc.size()
                                    + " RTT capable.");

                } else {
                    Log.d(TAG, "Permissions not allowed.");
                }
            }
        }
    }
}

