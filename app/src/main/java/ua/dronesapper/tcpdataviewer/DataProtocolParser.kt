package ua.dronesapper.tcpdataviewer

import android.content.Context
import com.github.mikephil.charting.data.Entry
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.ShortBuffer


class DataProtocolParser {
    private var mRecordId: Long = 0

    private var endian: ByteOrder = ByteOrder.BIG_ENDIAN
    private var dtype: Int = 0

    var bytes: ByteArray = ByteArray(0)

    fun receive(data : ByteArray) : List<Entry> {
        if (dtype == 0) {
            // raw bytes
            return parseBytes(data)
        }
        bytes += data
        return parseInts()
    }

    private fun parseBytes(data: ByteArray) : List<Entry> {
        val entries = ArrayList<Entry>(data.size)
        for (b in data) {
            val entry = Entry(
                mRecordId++.toFloat(),
                b.toFloat()
            )
            entries.add(entry)
        }
        return entries.toList()
    }

    fun reset() {
        mRecordId = 0
        bytes = ByteArray(0)
    }

    private fun getEntriesFromBuffer(buffer: Buffer): List<Entry> {
        val entries = ArrayList<Entry>()
        while (buffer.hasRemaining()) {
            val yVal: Float
            if (buffer is ShortBuffer) {
                yVal = buffer.get().toFloat()
            } else if (buffer is IntBuffer) {
                yVal = buffer.get().toFloat()
            } else if (buffer is FloatBuffer) {
                yVal = buffer.get()
            } else {
                throw java.lang.RuntimeException("Unsupported buffer: ${buffer.javaClass.simpleName}")
            }
            val entry = Entry(
                mRecordId++.toFloat(),
                yVal
            )
            entries.add(entry)
        }
        return entries.toList()
    }

    private fun parseInts() : List<Entry> {
        val bufferBytes = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val entries: List<Entry>
        val dtypeSize: Int
        when (dtype) {
            1 -> {
                // short
                dtypeSize = 2
                entries = getEntriesFromBuffer(bufferBytes.asShortBuffer())
            }
            2 -> {
                // int
                dtypeSize = 4
                entries = getEntriesFromBuffer(bufferBytes.asIntBuffer())
            }
            3 -> {
                // float
                dtypeSize = 4
                entries = getEntriesFromBuffer(bufferBytes.asFloatBuffer())
            }
            else -> {
                throw java.lang.RuntimeException("Unsupported data type: $dtype")
            }
        }
        val start = entries.size * dtypeSize
        bytes = bytes.sliceArray(start until bytes.size)
        return entries
    }

    fun loadProtocol(context: Context) {
        val sharedPref = Utils.getSharedPref(context)
        dtype = sharedPref.getInt(SharedKey.DATA_TYPE, 0)
        val endianInt = sharedPref.getInt(SharedKey.ENDIAN, 0)
        endian = if (endianInt == 0) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
    }
}