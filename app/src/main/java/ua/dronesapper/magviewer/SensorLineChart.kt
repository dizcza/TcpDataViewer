package ua.dronesapper.magviewer

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.Toast
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.listener.ChartTouchListener.ChartGesture
import com.github.mikephil.charting.listener.OnChartGestureListener
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayDeque

class SensorLineChart : LineChart, OnChartGestureListener {
    private val mChartEntries = ArrayDeque<Entry>(Constants.DEQUEUE_SIZE)

    @get:Synchronized
    val chartEntries: List<Entry>
        get() = mChartEntries.toList()

    private var mState = State.CLEARED
    private var mLastUpdate: Long = 0
    private var mRecordId: Long = 0

    enum class State {
        CLEARED,  // waiting for sensory data
        ACTIVE,  // actively displaying data
        INACTIVE // paused
    }

    constructor(context: Context) : super(context) {
        prepare(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        prepare(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        prepare(context)
    }

    @Synchronized
    override fun clear() {
        super.clear()
        postInvalidate()
        mChartEntries.clear()
        mState = State.CLEARED
    }

    @get:Synchronized
    val isActive: Boolean
        get() = mState != State.INACTIVE  // either CLEARED or ACTIVE

    private fun prepare(context: Context) {
        setNoDataText("Waiting for sensor data...")
        description = null
        onChartGestureListener = this
    }

    @Synchronized
    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
    }

    @Synchronized
    fun update(bytes: ByteArray) {
        if (mState == State.INACTIVE) {
            return
        }
        var available = Constants.DEQUEUE_SIZE - mChartEntries.size
        val remove = bytes.size - available
        if (remove > 0) {
            if (remove > mChartEntries.size) {
                mChartEntries.clear()
            } else {
                for (i in 0 until remove) {
                    mChartEntries.removeFirst()
                }
            }
        }

        available = Constants.DEQUEUE_SIZE - mChartEntries.size
        val start = if (bytes.size < available) 0 else bytes.size - available
        for (i in start until bytes.size) {
            val entry = Entry(
                mRecordId++.toFloat(),
                bytes[i].toFloat()
            )
            mChartEntries.addLast(entry)
        }

        val tick = System.currentTimeMillis()
        if (tick > mLastUpdate + UPDATE_PERIOD_MS) {
            // either CLEARED or ACTIVE state
            val dataset = LineDataSet(mChartEntries.toList(), CHART_LABEL)
            val data = LineData(dataset)
            setData(data)
            postInvalidate()
            mLastUpdate = tick
            mState = State.ACTIVE
        }
    }

    @Synchronized
    fun pause() {
        mState = State.INACTIVE
    }

    override fun onChartGestureStart(me: MotionEvent, lastPerformedGesture: ChartGesture) {}
    override fun onChartGestureEnd(me: MotionEvent, lastPerformedGesture: ChartGesture) {}
    override fun onChartLongPressed(me: MotionEvent) {}
    override fun onChartDoubleTapped(me: MotionEvent) {}

    @Synchronized
    override fun onChartSingleTapped(me: MotionEvent) {
        when (mState) {
            State.CLEARED -> {}
            State.INACTIVE -> clear()
            State.ACTIVE -> pause()
        }
    }

    override fun onChartFling(
        me1: MotionEvent,
        me2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ) {
    }

    override fun onChartScale(me: MotionEvent, scaleX: Float, scaleY: Float) {}
    override fun onChartTranslate(me: MotionEvent, dX: Float, dY: Float) {}

    /**
     * Save current chart.
     * Not thread safe.
     */
    fun saveCharts(name: String) {
        var tagName = name
        if (tagName != "") {
            tagName = " $tagName"
        }

        val entries = chartEntries  // synchronized
        if (entries.isEmpty()) {
            // no entries in the chart
            Toast.makeText(context, "No data", Toast.LENGTH_SHORT).show()
            return
        }
        val recordsDir = Utils.getRecordsFolder(context)
        if (!recordsDir.exists()) {
            if (!recordsDir.mkdirs()) {
                Toast.makeText(context, "mkdir failed", Toast.LENGTH_SHORT).show()
            }
        }
        val locale = Locale.getDefault()
        val pattern = String.format(locale, "yyyy.MM.dd HH.mm.ss'%s.txt'", tagName)
        val fileName = SimpleDateFormat(pattern, locale).format(Date())
        val file = File(recordsDir, fileName)
        try {
            val fos = FileOutputStream(file)
            val pw = PrintWriter(fos)
            for (entry in entries) {
                pw.println(String.format(locale, "%.6f,%.4f", entry.x, entry.y))
            }
            pw.close()
            Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val UPDATE_PERIOD_MS: Long = 10
        private const val CHART_LABEL = "Magnetic field diff, uT"
    }
}