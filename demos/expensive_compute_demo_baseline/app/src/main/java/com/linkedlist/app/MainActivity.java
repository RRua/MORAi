package com.linkedlist.app;

import android.os.Bundle;
import android.util.Log;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Drives the expensive-compute workload and renders results.
 *
 * <p>This BASELINE build has no memoization at all, so every call in
 * {@link Workload} is computed from scratch. The sibling project
 * {@code expensive_compute_demo_memoized} runs the identical workload with
 * {@code @Memoize} annotations enabled, which is how we compare real-device
 * performance.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "ExpensiveDemo";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView tv = new TextView(this);
        tv.setTextSize(12f);
        tv.setPadding(32, 32, 32, 32);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(tv);
        setContentView(scroll);

        StringBuilder sb = new StringBuilder();
        sb.append("BASELINE (no memoization)\n\n");

        Workload workload = new Workload(new ExpensiveLib());

        // Warmup so JIT has a fair chance on each function.
        workload.runAll();

        long total = 0;
        Workload.Result[] results = workload.runAll();
        for (Workload.Result r : results) {
            sb.append(r).append('\n');
            Log.i(TAG, r.toString());
            total += r.totalNanos;
        }
        sb.append(String.format("%-22s %8.2f ms\n", "TOTAL", total / 1_000_000.0));
        Log.i(TAG, "TOTAL " + (total / 1_000_000.0) + " ms");

        tv.setText(sb.toString());
    }
}
