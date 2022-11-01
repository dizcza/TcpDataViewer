package ua.dronesapper.magneticviewer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class MainActivity : AppCompatActivity() {
    private val TAG = MainActivity::class.java.simpleName

    private lateinit var mService: TcpClientService
    private var mBound: Boolean = false
    private val timer = Timer()
    private val connection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            val binder = service as TcpClientService.LocalBinder
            mService = binder.getService()
            mBound = true

            timer.scheduleAtFixedRate( object : TimerTask() {
                override fun run() {
                    Log.d(TAG, "Bitrate ${mService.getBitrate()}");
                }
            }, 0, 2000)
        }

        override fun onBindingDied(name: ComponentName) {
            Log.d(TAG, "onBindingDied")
            unbindService(this)
            mBound = false
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            Log.d(TAG, "onServiceDisconnected")
            mBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Intent(this, TcpClientService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        unbindService(connection)
        timer.cancel()
        mBound = false
    }

}