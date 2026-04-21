package com.linkedlist.app;

import android.os.Bundle;
import android.util.Log;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import dev.memoize.runtime.LogLevel;
import dev.memoize.runtime.MemoLogger;

/**
 * MEMOIZED twin of the baseline demo. Runs the exact same {@link Workload}, but
 * the expensive methods live on an annotated {@code ExpensiveLib} whose bytecode
 * has been rewritten by the memoize Gradle plugin to install per-method caches.
 *
 * <p>Before driving the workload we flip the embedded {@link MemoLogger} to
 * {@code INFO} so dispatcher creation and the end-of-run report are emitted to
 * Logcat under the {@code Memoize} tag. Switch to {@code DEBUG} or {@code TRACE}
 * if you want per-call events — note these make the log volume significant.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "ExpensiveDemo";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Enable embedded logging at INFO. Toggle up to DEBUG / TRACE to debug
        // individual calls; at OFF (the default) all guard checks short-circuit
        // for zero runtime overhead.
        MemoLogger.setLevel(LogLevel.INFO);

        TextView tv = new TextView(this);
        tv.setTextSize(12f);
        tv.setPadding(32, 32, 32, 32);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(tv);
        setContentView(scroll);

        StringBuilder sb = new StringBuilder();
        sb.append("MEMOIZED (@Memoize annotations)\n\n");

        Workload workload = new Workload(new ExpensiveLib());

        // Warmup populates the caches so the timed pass measures the hit path.
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
    }
}
