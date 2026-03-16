package com.example.ironsignature;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RecordingActivity extends Activity {

    // ================= CONSTANTS =================
    private static final String TSP_CMD = "/sys/class/sec/tsp/cmd";
    private static final String TSP_RESULT = "/sys/class/sec/tsp/cmd_result";

    private final BlockingQueue<RawFrame> rawQueue =
            new LinkedBlockingQueue<>(256);

    private static final long RECORD_DURATION_MS = 61_000;
    private static final int MATRIX_SIZE = 1156;

    private static final int COLOR_GREEN = 0xFF4CAF50;
    private static final int COLOR_YELLOW = 0xFFFFEB3B;
    private static final int COLOR_RED = 0xFFF44336;
    private static final int COLOR_GRAY = 0xFF9E9E9E;

    // ================= UI =================
    private TextView tvParticipant, tvStatus, tvTimer, tvPressure;
    private Button btnCalibrate, btnStart, btnBack;
    private FingerCircleView fingerCircle;

    // ================= STATE =================
    private String participantId;
    private volatile boolean isRecording = false;

    private long startTime;
    private int sampleCount = 0;
    private double targetPressure = 0;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<DataSample> samples = new ArrayList<>();

    // =====================================================
    // LIFECYCLE
    // =====================================================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recording);

        participantId = getIntent().getStringExtra("PARTICIPANT_ID");
        if (participantId == null || participantId.trim().isEmpty()) {
            Toast.makeText(this, "Participant ID missing", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        participantId = participantId.trim().toUpperCase(Locale.US);

        initViews();
        setMaxBrightness();
        checkRoot();
    }

    // =====================================================
    // UI SETUP
    // =====================================================
    private void initViews() {
        tvParticipant = findViewById(R.id.tvParticipant);
        tvStatus = findViewById(R.id.tvStatus);
        tvTimer = findViewById(R.id.tvTimer);
        tvPressure = findViewById(R.id.tvPressure);

        btnCalibrate = findViewById(R.id.btnCalibrate);
        btnStart = findViewById(R.id.btnStart);
        btnBack = findViewById(R.id.btnBack);
        fingerCircle = findViewById(R.id.fingerCircle);

        tvParticipant.setText(participantId);
        tvTimer.setText("00:00");

        applyPressureUI(
                COLOR_YELLOW,
                Color.BLACK,
                "Touch=0.00",
                "PLACE FINGER IN CIRCLE"
        );

        // -------- BUTTONS --------
        btnCalibrate.setOnClickListener(v -> calibratePanel());

        btnStart.setOnClickListener(v -> {
            if (isRecording) stopRecording();
            else startRecording();
        });

        btnBack.setOnClickListener(v -> {
            if (isRecording) stopRecording();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

        // -------- PRESSURE CALLBACK FROM CIRCLE --------
        fingerCircle.setOnPressureListener(eff -> {

            int color;
            String status;

            if (eff < 0.45f) {
                color = COLOR_YELLOW;
                status = "LIGHT TOUCH";
            } else if (eff > 0.50f && eff < 0.95f) {
                color = COLOR_GREEN;
                status = "GOOD CONTACT";
            } else {
                color = COLOR_RED;
                status = "HEAVY PRESS";
            }

            String text = String.format(Locale.US, "Touch=%.2f", eff);

            applyPressureUI(
                    color,
                    Color.BLACK,
                    text,
                    isRecording ? tvStatus.getText().toString() : status
            );
        });
    }

    // =====================================================
    // UI HELPER
    // =====================================================
    private void applyPressureUI(int bgColor, int textColor,
                                 String pressureText, String statusText) {
        ui.post(() -> {
            tvPressure.setText(pressureText);
            tvPressure.setTextColor(textColor);
            tvPressure.setBackground(new ColorDrawable(bgColor));
            tvStatus.setText(statusText);
        });
    }

    // =====================================================
    // SCREEN
    // =====================================================
    private void setMaxBrightness() {
        WindowManager.LayoutParams p = getWindow().getAttributes();
        p.screenBrightness = 1.0f;
        getWindow().setAttributes(p);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    // =====================================================
    // ROOT CHECK
    // =====================================================
    private void checkRoot() {
        new Thread(() -> {
            boolean ok = hasRoot();
            ui.post(() -> {
                if (!ok) {
                    applyPressureUI(
                            COLOR_GRAY,
                            Color.BLACK,
                            "NO ROOT",
                            "❌ ROOT REQUIRED"
                    );
                    btnStart.setEnabled(false);
                    btnCalibrate.setEnabled(false);
                }
            });
        }).start();
    }

    private boolean hasRoot() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = r.readLine();
            p.waitFor();
            return line != null && line.contains("uid=0");
        } catch (Exception e) {
            return false;
        }
    }

    private List<String> runRootCmd(String tspCommand) {
        List<String> out = new ArrayList<>();
        try {
            String shell =
                    "echo " + tspCommand + " > " + TSP_CMD +
                            " && sleep 0.05 && cat " + TSP_RESULT;

            Process p = Runtime.getRuntime().exec(
                    new String[]{"su", "-c", shell});

            BufferedReader stdout =
                    new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader stderr =
                    new BufferedReader(new InputStreamReader(p.getErrorStream()));

            String line;
            while ((line = stdout.readLine()) != null)
                out.add(line.trim());

            while ((line = stderr.readLine()) != null)
                out.add("ERR:" + line);

            p.waitFor();
        } catch (Exception e) {
            out.add("ERR:" + e.getMessage());
        }
        return out;
    }


    // =====================================================
    // CALIBRATION
    // =====================================================
    private void calibratePanel() {
        applyPressureUI(
                COLOR_YELLOW,
                Color.BLACK,
                tvPressure.getText().toString(),
                "CALIBRATING..."
        );
        btnCalibrate.setEnabled(false);

        new Thread(() -> {
            runRootCmd("run_force_calibration");
            runRootCmd("run_reference_read_all");
            ui.post(() -> {
                btnCalibrate.setEnabled(true);
                tvStatus.setText("CALIBRATION DONE");
            });
        }).start();
    }

    // =====================================================
    // RECORDING
    // =====================================================
    private void startRecording() {
        isRecording = true;
        sampleCount = 0;
        targetPressure = 0;
        samples.clear();
        rawQueue.clear();
        startTime = System.currentTimeMillis();

        ui.post(() -> {
            btnStart.setText("⏹ STOP");
            btnCalibrate.setEnabled(false);
            tvTimer.setText("00:00");
            tvStatus.setText("RECORDING...");
        });

        ui.post(timerRunnable);
        ui.postDelayed(() -> {
            if (isRecording) stopRecording();
        }, RECORD_DURATION_MS);

        new Thread(this::samplingLoop, "TSP-Sampler").start();
        new Thread(this::processingLoop, "TSP-Processor").start();
    }

    private void stopRecording() {
        if (!isRecording) return;
        isRecording = false;

        ui.removeCallbacksAndMessages(null);

        ui.post(() -> {
            btnStart.setText("▶ START");
            btnCalibrate.setEnabled(true);
            tvStatus.setText("SAVING...");
        });

        new Thread(this::saveCSV).start();
    }

    // =====================================================
    // TIMER
    // =====================================================
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRecording) return;
            long sec = (System.currentTimeMillis() - startTime) / 1000;
            tvTimer.setText(String.format(Locale.US, "00:%02d", sec));
            ui.postDelayed(this, 200);
        }
    };

    // =====================================================
    // SAMPLING (DELTA)
    // =====================================================
    private void samplingLoop() {
        while (isRecording) {

            List<String> out = runRootCmd("run_rawcap_read_all");  // run_delta_read_all

            if (!out.isEmpty() && !out.get(0).startsWith("ERROR")) {

                String raw = String.join(" ", out)
                        .replaceFirst("^run_rawcap_read_all:\\s*", "")
                        .replaceFirst(",\\s*$", "");   // run_delta_read_all

                // ⬇️ enqueue raw frame ONLY
                rawQueue.offer(new RawFrame(raw, System.currentTimeMillis()));
            }

        }
    }

    private void processingLoop() {
        while (isRecording || !rawQueue.isEmpty()) {
            try {
                RawFrame f = rawQueue.take();   // blocks if empty

                double pressure = computePressure(f.raw);

                samples.add(new DataSample(
                        f.raw,
                        pressure,
                        f.ts
                ));

            } catch (InterruptedException ignored) {}
        }
    }

    // =====================================================
    // SIGNAL PROCESSING
    // =====================================================
    private double computePressure(String raw) {
        int[] v = parseRawcap(raw);
        int peak = 0, area = 0;

        for (int x : v) {
            int a = Math.abs(x);
            if (a > 5) {
                area++;
                if (a > peak) peak = a;
            }
        }
        return peak * Math.sqrt(area / 100.0);
    }

    private int[] parseRawcap(String raw) {
        int[] arr = new int[MATRIX_SIZE];
        Matcher m = Pattern.compile("-?\\d+").matcher(raw);
        int i = 0;
        while (m.find() && i < MATRIX_SIZE) {
            arr[i++] = Integer.parseInt(m.group());
        }
        return arr;
    }

    // =====================================================
    // SAVE CSV
    // =====================================================
    private void saveCSV() {
        try {
            File dir = new File(getExternalFilesDir(null), "IronSignature");
            if (!dir.exists()) dir.mkdirs();

            String ts = new SimpleDateFormat(
                    "yyyy_MM_dd_HH_mm_ss", Locale.US
            ).format(new Date());

            File file = new File(dir,
                    participantId + "_" + ts + ".csv");

            FileWriter w = new FileWriter(file);

            // Header
            w.write("participant_id,frame_index,timestamp_ms,pressure_metric,delta_values\n");

            int idx = 0;
            for (DataSample s : samples) {
                w.write(
                        participantId + "," +
                                idx++ + "," +
                                s.timestamp + "," +
                                String.format(Locale.US, "%.4f", s.pressure) + "," +
                                "\"" + s.rawData + "\"\n"
                );
            }

            w.flush();
            w.close();

            ui.post(() -> Toast.makeText(
                    this,
                    "Saved " + samples.size() + " frames",
                    Toast.LENGTH_LONG
            ).show());

        } catch (Exception e) {
            ui.post(() -> Toast.makeText(
                    this,
                    "Save error: " + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show());
        }
    }



    private static class RawFrame {
        final String raw;
        final long ts;
        RawFrame(String r, long t) {
            raw = r;
            ts = t;
        }
    }

}



