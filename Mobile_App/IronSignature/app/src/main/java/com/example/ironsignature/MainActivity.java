package com.example.ironsignature;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends Activity {
    private EditText etParticipantId;
    private Button btnNext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_id_entry);

        etParticipantId = findViewById(R.id.etParticipantId);
        btnNext = findViewById(R.id.btnNext);

        btnNext.setOnClickListener(v -> {
            String id = etParticipantId.getText().toString().trim().toUpperCase();
            if (id.isEmpty() || id.length() < 3) {
                Toast.makeText(this, "⚠️ Enter valid Participant ID (min 3 chars)", Toast.LENGTH_LONG).show();
                return;
            }

            Intent intent = new Intent(this, RecordingActivity.class);
            intent.putExtra("PARTICIPANT_ID", id);
            startActivity(intent);
            finish(); // Close ID screen
        });
    }
}
