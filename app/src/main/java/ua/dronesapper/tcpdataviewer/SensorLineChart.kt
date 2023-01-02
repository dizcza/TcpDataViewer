package ua.dronesapper.tcpdataviewer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
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
import kotlin.properties.Delegates

class SensorLineChart : LineChart, OnChartGestureListener {
    private var mChartEntries = ArrayDeque<Entry>()
    private val mDataProtocolParser = DataProtocolParser()
    private var mState = State.CLEARED
    private var mLastUpdate: Long = 0
    private var mUpdatePeriod: Int = 0
    private var mPlotSizeMax by Delegates.notNull<Int>()

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
        mDataProtocolParser.reset()
        mState = State.CLEARED
    }

    @get:Synchronized
    val chartEntries: List<Entry>
        get() = mChartEntries.toList()

    @get:Synchronized
    val isActive: Boolean
        get() = mState != State.INACTIVE  // either CLEARED or ACTIVE

    private fun prepare(context: Context) {
        setNoDataText("Waiting for sensor data...")
        description = null
        onChartGestureListener = this
        mDataProtocolParser.loadProtocol(context)
        mPlotSizeMax = Utils.getInteger(context, SharedKey.PLOT_KEEP_LAST_COUNT)
        mUpdatePeriod = Utils.getInteger(context, SharedKey.PLOT_UPDATE_PERIOD)
        xAxis.textColor = Color.WHITE
        axisLeft.textColor = Color.WHITE
    }

    @Synchronized
    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
    }

    private fun trimEntries(entries: List<Entry>) : List<Entry> {
        val available = mPlotSizeMax - mChartEntries.size
        val removed = entries.size - available
        if (removed > 0) {
            if (removed > mChartEntries.size) {
                val slicedList = entries.slice(entries.size - mPlotSizeMax until entries.size)
                mChartEntries = ArrayDeque(slicedList)
                return slicedList
            } else {
                for (i in 0 until removed) {
                    mChartEntries.removeFirst()
                }
            }
        }
        mChartEntries.addAll(entries)
        return mChartEntries.toList()
    }

    @Synchronized
    fun update(bytes: ByteArray) {
        if (mState == State.INACTIVE) {
            return
        }
        val entries = trimEntries(mDataProtocolParser.receive(bytes))
        val tick = System.currentTimeMillis()
        if (tick > mLastUpdate + mUpdatePeriod) {
            // either CLEARED or ACTIVE state
            val dataset = LineDataSet(entries, CHART_LABEL)
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

    @Synchronized
    fun onDataProtocolUpdated() {
        mDataProtocolParser.loadProtocol(context)
        clear()
    }

    @Synchronized
    fun onSettingsUpdated() {
        mPlotSizeMax = Utils.getInteger(context, SharedKey.PLOT_KEEP_LAST_COUNT)
        mUpdatePeriod = Utils.getInteger(context, SharedKey.PLOT_UPDATE_PERIOD)
        clear()
    }

    companion object {
        private const val CHART_LABEL = "Data"
    }
}