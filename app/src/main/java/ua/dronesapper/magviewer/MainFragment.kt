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
package ua.dronesapper.magviewer

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.*
import android.widget.*
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import ua.dronesapper.magviewer.TcpClientService.TcpBinder
import java.util.*


abstract class FragmentWritePermissionManager : Fragment() {
    companion object {
        @JvmStatic
        protected val TAG = FragmentWritePermissionManager::class.java.simpleName
    }

    // request write permission Android <11
    private val mRequestWriteExternalOldAPI: ActivityResultLauncher<String> = registerForActivityResult(
        ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            onWritePermissionGranted()
        } else {
            Toast.makeText(context, "Write permission not granted", Toast.LENGTH_SHORT).show()
        }
    }

    // request write permission Android >=11
    private val mStorageActivityResultLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { activityResult: ActivityResult ->
            // Launched only for Android >=11
            Log.d(TAG, "onActivityResult: $activityResult")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    //Manage External Storage Permission is granted
                    onWritePermissionGranted()
                } else {
                    //Manage External Storage Permission is denied
                    Log.d(TAG, "onActivityResult: Manage External Storage Permission is denied")
                    Toast.makeText(
                        context,
                        "Manage External Storage Permission is denied",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

    protected fun requestWritePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            //Android is 11(R) or above
            try {
                val intent = Intent()
                intent.action = Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                val uri: Uri = Uri.fromParts("package", requireContext().packageName, null)
                intent.data = uri
                mStorageActivityResultLauncher.launch(intent)
            } catch (e: Exception) {
                e.printStackTrace()
                val intent = Intent()
                intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                mStorageActivityResultLauncher.launch(intent)
            }
        } else {
            //Android is below 11(R)
            // The registered ActivityResultCallback gets the result of this request.
            mRequestWriteExternalOldAPI.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    protected fun permissionWriteGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            //Android is 11(R) or above
            Environment.isExternalStorageManager()
        } else {
            //Android is below 11(R)
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    protected abstract fun onWritePermissionGranted()
}

/**
 * This fragment controls Bluetooth to communicate with other devices.
 */
class MainFragment : FragmentWritePermissionManager() {

    private val mSavedChartsFragment = SavedChartsFragment()
    private val mServiceConnection = ServiceConnectionTcp()
    private lateinit var mLineChart: SensorLineChart
    private lateinit var mTagSave: EditText
    private var mBound = false

    private inner class BackStackChanged : FragmentManager.OnBackStackChangedListener {
        private var mChartWasActive = false
        override fun onBackStackChanged() {
            if (parentFragmentManager.backStackEntryCount == 0) {
                // Main fragment is back active
                if (mChartWasActive) {
                    // if the chart was active, clear and resume
                    // otherwise, keep paused until the user press the button
                    mLineChart.clear()
                }
            } else {
                // Main fragment is replaced by the SavedChartsFragment
                mChartWasActive = mLineChart.isActive
                mLineChart.pause()
            }
        }
    }

    private inner class ServiceConnectionTcp : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as TcpBinder
            val myService = binder.getService()
            myService.startDaemonThread(mLineChart)
            mBound = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.d(TAG, "onServiceDisconnected")
            mBound = false
        }

        override fun onBindingDied(name: ComponentName) {
            Log.d(TAG, "onBindingDied")
            requireContext().unbindService(this)
            mBound = false
        }
    }

    @SuppressLint("ApplySharedPref")
    private fun createDataProtocolDialog(): AlertDialog {
        mLineChart.pause()
        val sharedPref: SharedPreferences = Utils.getSharedPref(requireContext())

        val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext())
        val dialogView: View = layoutInflater.inflate(R.layout.dialog_data_type, null)

        Utils.radioGroupLoadChecked(dialogView, SharedKey.DATA_TYPE)
        Utils.radioGroupLoadChecked(dialogView, SharedKey.ENDIAN)

        builder.setView(dialogView)
            .setPositiveButton(
                R.string.dialog_data_protocol_save
            ) { _, _ ->
                val editor = sharedPref.edit()
                Utils.radioGroupSaveChecked(dialogView, SharedKey.DATA_TYPE, editor)
                Utils.radioGroupSaveChecked(dialogView, SharedKey.ENDIAN, editor)
                editor.commit()
                mLineChart.onDataProtocolUpdated()
            }
            .setNegativeButton(
                R.string.dialog_cancel
            ) { dialog, _ -> dialog.cancel()
                mLineChart.clear()
            }
        return builder.create()
    }

    @SuppressLint("ApplySharedPref")
    private fun createPlotSettingsDialog(): AlertDialog {
        mLineChart.pause()
        val sharedPref: SharedPreferences = Utils.getSharedPref(requireContext())

        val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext())
        val dialogView: View = layoutInflater.inflate(R.layout.dialog_plot_settings, null)

        val textPlotSize: TextView = dialogView.findViewById(R.id.plot_size)
        textPlotSize.text = Utils.getInteger(requireContext(), SharedKey.PLOT_KEEP_LAST_COUNT).toString()

        val textPlotUpdatePeriod: TextView = dialogView.findViewById(R.id.plot_update_period)
        textPlotUpdatePeriod.text = Utils.getInteger(requireContext(), SharedKey.PLOT_UPDATE_PERIOD).toString()

        builder.setView(dialogView)
            .setPositiveButton(
                R.string.dialog_data_protocol_save
            ) { _, _ ->
                val editor = sharedPref.edit()
                editor.putInt(SharedKey.PLOT_KEEP_LAST_COUNT, textPlotSize.text.toString().toInt())
                editor.putInt(SharedKey.PLOT_UPDATE_PERIOD, textPlotUpdatePeriod.text.toString().toInt())
                editor.commit()
                mLineChart.onSettingsUpdated()
            }
            .setNegativeButton(
                R.string.dialog_cancel
            ) { dialog, _ -> dialog.cancel()
                mLineChart.clear()
            }
        return builder.create()
    }

    @SuppressLint("ApplySharedPref")
    private fun createConnectDialog(): AlertDialog {
        if (mBound) {
            requireContext().unbindService(mServiceConnection)
            mBound = false
        }
        val sharedPref: SharedPreferences = Utils.getSharedPref(requireContext())

        val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext())
        val dialogView: View = layoutInflater.inflate(R.layout.dialog_connect, null)

        val textIPAddrView: TextView = dialogView.findViewById(R.id.server_ipaddr)
        textIPAddrView.text =
            sharedPref.getString(
                SharedKey.SERVER_IPADDR,
                requireContext().getString(R.string.ipaddr_default)
            )

        val textPortView: TextView = dialogView.findViewById(R.id.server_port)
        textPortView.text = Utils.getInteger(requireContext(), SharedKey.SERVER_PORT).toString()

        builder.setView(dialogView)
            .setPositiveButton(R.string.dialog_connect, DialogInterface.OnClickListener { dialog, id ->
                val editor = sharedPref.edit()
                val serverIP = textIPAddrView.text.toString()
                editor.putString(SharedKey.SERVER_IPADDR, serverIP)
                val port = textPortView.text.toString().toInt()
                editor.putInt(SharedKey.SERVER_PORT, port)
                editor.commit()

                startService()
            })
            .setNegativeButton(R.string.dialog_cancel,
                DialogInterface.OnClickListener { dialog, id -> dialog.cancel() })
        return builder.create()
    }

    private fun saveChart() {
        val tagName = mTagSave.text.toString()
        mLineChart.saveCharts(tagName)
        mTagSave.setText("")
    }

    override fun onWritePermissionGranted() {
        saveChart()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    private fun startService() {
        mLineChart.clear()
        val intent = Intent(context, TcpClientService::class.java)
        requireContext().bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStart() {
        super.onStart()
        if (!mBound) {
            startService()
        }
    }

    override fun onStop() {
        super.onStop()
        if (mBound) {
            requireContext().unbindService(mServiceConnection)
            mBound = false
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mLineChart = view.findViewById(R.id.graph)
        mTagSave = view.findViewById(R.id.tag_save)
        parentFragmentManager.addOnBackStackChangedListener(BackStackChanged())

        val saveGraphBtn = view.findViewById<Button>(R.id.save_btn)
        saveGraphBtn.setOnClickListener {
            if (permissionWriteGranted()) {
                saveChart()
            } else {
                requestWritePermission()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.show_saved -> {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_main, mSavedChartsFragment).addToBackStack(null)
                    .commit()
                return true
            }
            R.id.connect_dialog -> {
                createConnectDialog().show()
                return true
            }
            R.id.data_protocol_dialog -> {
                createDataProtocolDialog().show()
                return true
            }
            R.id.plot_settings -> {
                createPlotSettingsDialog().show()
                return true
            }
        }
        return false
    }

}