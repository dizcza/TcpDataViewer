package ua.dronesapper.magviewer

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
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

    private fun openSocket(): Socket {
        val sharedPref = Utils.getSharedPref(applicationContext)
        val ipaddrStr: String = sharedPref.getString(
            Constants.SERVER_IPADDR_SHARED_KEY,
            applicationContext.getString(R.string.ipaddr)
        )!!

        val port: Int = sharedPref.getInt(
            Constants.SERVER_PORT_SHARED_KEY,
            applicationContext.resources.getInteger(R.integer.port)
        )

        val ip: InetAddress = InetAddress.getByName(ipaddrStr)
        return Socket(ip, port)
    }

    private inner class TcpRunnable(lineChart: SensorLineChart) : Runnable {
        val mLineChart: SensorLineChart = lineChart

        override fun run() {
            Looper.prepare()

            try {
                val socket = openSocket()
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
                Toast.makeText(applicationContext, "Socket closed", Toast.LENGTH_SHORT).show()

            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(applicationContext, e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    fun startDaemonThread(lineChart: SensorLineChart) {
        thread = Thread(TcpRunnable(lineChart))
        thread.isDaemon = true
        thread.start()
    }

    override fun onDestroy() {
        Log.d(TAG, "Socket onDestroy")
        thread.interrupt()
    }

    companion object {
        val TAG = TcpClientService::class.java.simpleName
    }
}