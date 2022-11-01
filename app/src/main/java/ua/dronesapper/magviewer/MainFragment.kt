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
import android.os.*
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import ua.dronesapper.magviewer.TcpClientService.TcpBinder
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * This fragment controls Bluetooth to communicate with other devices.
 */
class MainFragment : Fragment() {
    // Layout Views
    private var mLineChart: SensorLineChart? = null
    private val mSavedChartsFragment = SavedChartsFragment()
    private var mTagSave: EditText? = null
    private var mBound = false

    private val mServiceConnection = ServiceConnectionTcp()

    private inner class BackStackChanged : FragmentManager.OnBackStackChangedListener {
        private var mChartWasActive = false
        override fun onBackStackChanged() {
            if (mLineChart == null) {
                return
            }
            if (parentFragmentManager.backStackEntryCount == 0) {
                // Main fragment is back active
                if (mChartWasActive) {
                    // if the chart was active, clear and resume
                    // otherwise, keep paused until the user press the button
                    mLineChart!!.clear()
                }
            } else {
                // Main fragment is replaced by the SavedChartsFragment
                mChartWasActive = mLineChart!!.isActive
                mLineChart!!.pause()
            }
        }
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
                Constants.SERVER_IPADDR_SHARED_KEY,
                requireContext().getString(R.string.ipaddr)
            )

        val textPortView: TextView = dialogView.findViewById(R.id.server_port)
        textPortView.text =
            sharedPref.getInt(
                Constants.SERVER_PORT_SHARED_KEY,
                requireContext().resources.getInteger(R.integer.port)
            ).toString()

        builder.setView(dialogView)
            .setPositiveButton(R.string.dialog_connect, DialogInterface.OnClickListener { dialog, id ->
                val editor = sharedPref.edit()
                val serverIP = textIPAddrView.text.toString()
                editor.putString(Constants.SERVER_IPADDR_SHARED_KEY, serverIP)
                val port = textPortView.text.toString().toInt()
                editor.putInt(Constants.SERVER_PORT_SHARED_KEY, port)
                editor.commit()

                startService()
            })
            .setNegativeButton(R.string.dialog_cancel,
                DialogInterface.OnClickListener { dialog, id -> dialog.cancel() })
        return builder.create()
    }

    private fun saveChart() {
        var tag = mTagSave!!.text.toString()
        mTagSave!!.setText("")
        if (tag != "") {
            tag = " $tag"
        }
        val entries = mLineChart!!.chartEntries
        if (entries == null || entries.isEmpty()) {
            // no entries in the chart
            Toast.makeText(context, "No data", Toast.LENGTH_SHORT).show()
            return
        }
        val root = Environment.getExternalStorageDirectory()
        val records = File(root.absolutePath, Constants.RECORDS_FOLDER)
        records.mkdirs()
        val locale = Locale.getDefault()
        val pattern = String.format(locale, "yyyy.MM.dd HH:mm:ss'%s.txt'", tag)
        val fileName = SimpleDateFormat(pattern, locale).format(Date())
        val file = File(records, fileName)
        try {
            val fos = FileOutputStream(file)
            val pw = PrintWriter(fos)
            pw.println(mLineChart!!.description.text)
            for (entry in entries) {
                pw.println(String.format(locale, "%.6f,%.4f", entry.x, entry.y))
            }
            pw.close()
            Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private inner class ServiceConnectionTcp : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as TcpBinder
            val myService = binder.getService()
            myService.startDaemonThread(mLineChart!!)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        parentFragmentManager.addOnBackStackChangedListener(BackStackChanged())
    }

    private fun startService() {
        mLineChart!!.clear()
        val intent = Intent(context, TcpClientService::class.java)
        requireContext().bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        requireContext().unbindService(mServiceConnection)
        mBound = false
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
        val saveGraphBtn = view.findViewById<Button>(R.id.save_btn)
        val requestWriteExternal =
            registerForActivityResult<String, Boolean>(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
                if (isGranted) {
                    saveChart()
                } else {
                    Toast.makeText(context, "Could not save the chart", Toast.LENGTH_SHORT).show()
                }
            }
        saveGraphBtn.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                saveChart()
            } else {
                // The registered ActivityResultCallback gets the result of this request.
                requestWriteExternal.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        startService()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.show_saved -> {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.main_fragment, mSavedChartsFragment).addToBackStack(null)
                    .commit()
                return true
            }
            R.id.connect_dialog -> {
                createConnectDialog().show()
                return true
            }
        }
        return false
    }

    companion object {
        private val TAG = MainFragment::class.java.simpleName
    }
}