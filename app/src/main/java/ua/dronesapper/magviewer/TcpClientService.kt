package ua.dronesapper.magviewer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.io.DataInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.Socket

class TcpClientService : Service() {
    private lateinit var thread: Thread

    // Binder given to clients
    private val binder = TcpBinder()

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    inner class TcpBinder : Binder() {
        // Return this instance of LocalService so clients can call public methods
        fun getService(): TcpClientService = this@TcpClientService
    }

    private inner class TcpRunnable(lineChart: SensorLineChart) : Runnable {
        val mLineChart: SensorLineChart = lineChart

        override fun run() {
            Looper.prepare()

            try {
                val ip = InetAddress.getByName(IP)
                val socket = Socket(ip, PORT)
                val dataInputStream = DataInputStream(socket.getInputStream())

                Toast.makeText(applicationContext, "Socket opened", Toast.LENGTH_SHORT).show()

                val buffer = ByteArray(Constants.BUFFER_SIZE)

                while (!Thread.currentThread().isInterrupted) {
                    try {
                        val readBytes = dataInputStream.read(buffer);
                        if (readBytes == -1) {
                            throw IOException("Not enough buffer")
                        }
                        val bufferChunk = buffer.copyOfRange(0, readBytes)
                        mLineChart.update(bufferChunk)
                    } catch (e: IOException) {
                        e.printStackTrace()
                        try {
                            dataInputStream.close()
                        } catch (ex: IOException) {
                            ex.printStackTrace()
                        }
                        break
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                        try {
                            dataInputStream.close()
                        } catch (ex: IOException) {
                            ex.printStackTrace()
                        }
                        break
                    }
                }

                socket.close()

            } catch (e: IOException) {
                e.printStackTrace()
            }
            Toast.makeText(applicationContext, "Socket closed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    fun startDaemonThread(lineChart: SensorLineChart) {
        startMeForeground()
        thread = Thread(TcpRunnable(lineChart))
        thread.isDaemon = true
        thread.start()
    }

    override fun onDestroy() {
        Log.d(TAG, "Socket onDestroy")
        thread.interrupt()
    }

    private fun startMeForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val NOTIFICATION_CHANNEL_ID = packageName
            val channelName = "Tcp Client Background Service"
            val chan = NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE)
            chan.lightColor = Color.BLUE
            chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            val manager = (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            manager.createNotificationChannel(chan)
            val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            val notification = notificationBuilder.setOngoing(true)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("Tcp Client is running in background")
                    .setPriority(NotificationManager.IMPORTANCE_MIN)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .build()
            startForeground(2, notification)
        } else {
            startForeground(1, Notification())
        }
    }

    companion object {
        val TAG = TcpClientService::class.java.simpleName
        private const val IP = "192.168.3.62"
        private const val PORT = 3333
    }
}