package ua.dronesapper.magviewer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class ChartDataAdapter extends ArrayAdapter<LineDataLabeled> {

    ChartDataAdapter(Context context, List<LineDataLabeled> objects) {
        super(context, 0, objects);
    }

    @SuppressLint("InflateParams")
    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        LineDataLabeled data = getItem(position);
        LineChart chart;

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(
                    R.layout.saved_chart_item, null);
            chart = convertView.findViewById(R.id.chart);
            convertView.setTag(chart);
        } else {
            chart = (LineChart) convertView.getTag();
        }

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);

        chart.setData(data);

        Description description = new Description();
        description.setText(data.label);
        chart.setDescription(description);

        chart.animateY(700);

        return convertView;
    }
}


class LineDataLabeled extends LineData {
    public final String label;

    public LineDataLabeled(ILineDataSet dataSet, String label) {
        super(dataSet);
        this.label = label;
    }
}

public class SavedChartsFragment extends Fragment {

    private ChartDataAdapter mAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final ActivityResultLauncher<String> requestReadExternal =
                registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                    if (isGranted) {
                        mAdapter.addAll(loadCharts());
                    } else {
                        getParentFragmentManager().popBackStack();
                    }
                });
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestReadExternal.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.saved_chart_list, container, false);
    }

    private List<LineDataLabeled> loadCharts() {
        List<LineDataLabeled> charts = new ArrayList<>();
        File root = Environment.getExternalStorageDirectory();
        final File folder = new File(root.getAbsolutePath(), Constants.RECORDS_FOLDER);
        File[] files = folder.listFiles();
        if (!folder.exists() || files == null) {
            return charts;
        }
        for (File file : files) {
            List<Entry> chartEntries = new ArrayList<>();
            String descriptionText = "";
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                descriptionText = br.readLine();
                String line;
                while ((line = br.readLine()) != null) {
                    String[] xy = line.split(",");
                    float time = Float.parseFloat(xy[0]);
                    float dp = Float.parseFloat(xy[1]);
                    chartEntries.add(new Entry(time, dp));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            LineDataSet dataset = new LineDataSet(chartEntries, file.getName().replace(".txt", ""));
            charts.add(new LineDataLabeled(dataset, descriptionText));
        }
        return charts;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        ListView listView = view.findViewById(R.id.charts_list);
        List<LineDataLabeled> charts = loadCharts();
        mAdapter = new ChartDataAdapter(getContext(), charts);
        listView.setAdapter(mAdapter);
    }

}
