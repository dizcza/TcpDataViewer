/*
 * Copyright (C) 2014 The Android Open Source Project
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

package ua.dronesapper.magviewer;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.github.mikephil.charting.data.Entry;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

/**
 * This fragment controls Bluetooth to communicate with other devices.
 */
public class MainFragment extends Fragment {
    private static final String TAG = MainFragment.class.getSimpleName();

    // Layout Views
    private SensorLineChart mLineChart;
    private SavedChartsFragment mSavedChartsFragment;
    private EditText mTagSave;
    private final Timer mTimer = new Timer();
    private final ServiceConnection mServiceConnection = new ServiceConnMag();

    TcpClientService mService;
    boolean mBound = false;

    private class BackStackChanged implements FragmentManager.OnBackStackChangedListener {

        private boolean mChartWasActive;

        @Override
        public void onBackStackChanged() {
            if (mLineChart == null) {
                return;
            }
            if (getParentFragmentManager().getBackStackEntryCount() == 0) {
                // Main fragment is back active
                if (mChartWasActive) {
                    // if the chart was active, clear and resume
                    // otherwise, keep paused until the user press the button
                    mLineChart.clear();
                }
            } else {
                // Main fragment is replaced by the SavedChartsFragment
                mChartWasActive = mLineChart.isActive();
                mLineChart.pause();
            }
        }
    }

    private void saveChart() {
        String tag = mTagSave.getText().toString();
        mTagSave.setText("");
        if (!tag.equals("")) {
            tag = " " + tag;
        }

        List<Entry> entries = mLineChart.getChartEntries();
        if (entries == null || entries.size() == 0) {
            // no entries in the chart
            Toast.makeText(getActivity(), "No data", Toast.LENGTH_SHORT).show();
            return;
        }
        File root = android.os.Environment.getExternalStorageDirectory();
        File records = new File(root.getAbsolutePath(), Constants.RECORDS_FOLDER);
        records.mkdirs();

        Locale locale = Locale.getDefault();
        String pattern = String.format(locale, "yyyy.MM.dd HH:mm:ss'%s.txt'", tag);
        String fileName = new SimpleDateFormat(pattern, locale).format(new Date());
        File file = new File(records, fileName);

        try {
            FileOutputStream fos = new FileOutputStream(file);
            PrintWriter pw = new PrintWriter(fos);
            pw.println(mLineChart.getDescription().getText());
            for (Entry entry : entries) {
                pw.println(String.format(locale, "%.6f,%.4f", entry.getX(), entry.getY()));
            }
            pw.close();
            Toast.makeText(getActivity(), "Saved", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class ServiceConnMag implements ServiceConnection {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TcpClientService.TcpBinder binder = (TcpClientService.TcpBinder) service;
            mService = binder.getService();
            mBound = true;
            mTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    Log.d(TAG, "Bitrate " + mService.getBitrate());
                }
            }, 0, 2000);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected");
            mBound = false;
        }

        @Override
        public void onBindingDied(ComponentName name) {
            Log.d(TAG, "onBindingDied");
            requireContext().unbindService(this);
            mBound = false;
        }
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        mSavedChartsFragment = new SavedChartsFragment();

        Intent intent = new Intent(getContext(), TcpClientService.class);
        requireContext().bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);

        getParentFragmentManager().addOnBackStackChangedListener(new BackStackChanged());
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        mLineChart = view.findViewById(R.id.graph);
        mTagSave = view.findViewById(R.id.tag_save);

        final Button saveGraphBtn = view.findViewById(R.id.save_btn);
        final ActivityResultLauncher<String> requestWriteExternal =
                registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                    if (isGranted) {
                        saveChart();
                    } else {
                        Toast.makeText(getActivity(), "Could not save the chart", Toast.LENGTH_SHORT).show();
                    }
                });
        saveGraphBtn.setOnClickListener(buttonView -> {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                saveChart();
            } else {
                // The registered ActivityResultCallback gets the result of this request.
                requestWriteExternal.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        } );
    }

    private void onRecordsReceived() {
        mLineChart.update();
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
    }

    /**
     * Set up the UI and background operations for chat.
     */
    private void setupChat() {
        // Initialize the array adapter for the conversation thread
        FragmentActivity activity = getActivity();
        if (activity == null) {
            return;
        }
    }

    /**
     * Sends a message.
     *
     * @param message A string of text to send.
     */
    private void sendMessage(String message) {
    }

    /**
     * Updates the status on the action bar.
     *
     * @param resId a string resource ID
     */
    private void setStatus(int resId) {
        AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (null == activity) {
            return;
        }
        final ActionBar actionBar = activity.getSupportActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(resId);
    }

    /**
     * Updates the status on the action bar.
     *
     * @param subTitle status
     */
    private void setStatus(CharSequence subTitle) {
        AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (null == activity) {
            return;
        }
        final ActionBar actionBar = activity.getSupportActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(subTitle);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.show_saved: {
                getParentFragmentManager().beginTransaction().replace(R.id.main_fragment, mSavedChartsFragment).addToBackStack(null).commit();
                return true;
            }
        }
        return false;
    }

}
