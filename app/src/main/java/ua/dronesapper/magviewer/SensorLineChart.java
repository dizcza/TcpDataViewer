package ua.dronesapper.magviewer;

import android.content.Context;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.listener.ChartTouchListener;
import com.github.mikephil.charting.listener.OnChartGestureListener;

import java.util.ArrayList;
import java.util.List;

public class SensorLineChart extends LineChart implements OnChartGestureListener {
    private static final long UPDATE_PERIOD_MS = 2000;
    private static final String CHART_LABEL = "Differential pressure, Pa";

    private final List<Entry> mChartEntries = new ArrayList<>();
    private List<Entry> mChartEntriesSaved;
    private int mPressureScale = 60;  // default pressure scale for SDP31
    private State mState = State.CLEARED;
    private long mLastUpdate = 0;
    private long mTimeShift = 0;

    public enum State {
        CLEARED,   // waiting for sensory data
        ACTIVE,    // actively displaying data
        INACTIVE   // paused
    }

    public SensorLineChart(Context context) {
        super(context);
        prepare(context);
    }

    public SensorLineChart(Context context, AttributeSet attrs) {
        super(context, attrs);
        prepare(context);
    }

    public SensorLineChart(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        prepare(context);
    }

    public synchronized void clear() {
        super.clear();
        mChartEntries.clear();
        mLastUpdate = System.currentTimeMillis();
        mState = State.CLEARED;
    }

    public synchronized boolean isActive() {
        // either CLEARED or ACTIVE
        return mState != State.INACTIVE;
    }

    public void prepare(Context context) {
        setNoDataText("Waiting for sensor data...");
        setDescription(null);
        int appColor = context.getResources().getColor(R.color.ic_launcher_background);
        Paint paint = getPaint(LineChart.PAINT_INFO);
        paint.setColor(appColor);
        setOnChartGestureListener(this);
    }

    /**
     * Adjust estimated time shift with the device clock.
     * @param clockTick absolute time since device boot in us
     */
    private void syncClock(long clockTick) {
        if (clockTick == mTimeShift) {
            // the clocks are synchronized
            return;
        }
        final float delay = (clockTick - mTimeShift) / 1e6f;  // in seconds
        if (mState != State.INACTIVE) {
            // apply delay adjustment if not paused
            for (Entry entry : mChartEntries) {
                entry.setX(entry.getX() + delay);
            }
        }
        mTimeShift = clockTick;
    }

    private void rescaleY(int prScale) {
        if (prScale == mPressureScale) {
            return;
        }
        // apply new scaling factor even if paused
        final float rescale = ((float) mPressureScale) / prScale;
        for (Entry entry : mChartEntries) {
            entry.setY(entry.getY() * rescale);
        }
        mPressureScale = prScale;
    }

    public synchronized void update() {
    }

    public synchronized List<Entry> getChartEntries() {
        return mChartEntriesSaved;
    }

    public synchronized void pause() {
        mState = State.INACTIVE;
    }

    @Override
    public void onChartGestureStart(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {

    }

    @Override
    public void onChartGestureEnd(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {

    }

    @Override
    public void onChartLongPressed(MotionEvent me) {

    }

    @Override
    public void onChartDoubleTapped(MotionEvent me) {

    }

    @Override
    public void onChartSingleTapped(MotionEvent me) {
        synchronized (this) {
            switch (mState) {
                case CLEARED:
                    // ignore touches
                    break;
                case INACTIVE:
                    clear();
                    break;
                case ACTIVE:
                    pause();
                    break;
            }
        }
    }

    @Override
    public void onChartFling(MotionEvent me1, MotionEvent me2, float velocityX, float velocityY) {

    }

    @Override
    public void onChartScale(MotionEvent me, float scaleX, float scaleY) {

    }

    @Override
    public void onChartTranslate(MotionEvent me, float dX, float dY) {

    }
}
