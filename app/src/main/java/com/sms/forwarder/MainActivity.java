package com.sms.forwarder;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST = 100;

    private EditText etApiUrl;
    private EditText etDeviceId;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etApiUrl = findViewById(R.id.etApiUrl);
        etDeviceId = findViewById(R.id.etDeviceId);
        tvStatus = findViewById(R.id.tvStatus);
        Button btnSave = findViewById(R.id.btnSave);
        Button btnCheckPerms = findViewById(R.id.btnCheckPerms);

        // Load saved config
        etApiUrl.setText(getSharedPreferences("config", MODE_PRIVATE)
                .getString("api_url", "https://smssend-8ek4.onrender.com/api/messages"));
        etDeviceId.setText(getSharedPreferences("config", MODE_PRIVATE)
                .getString("device_id", Build.MODEL));

        btnSave.setOnClickListener(v -> {
            getSharedPreferences("config", MODE_PRIVATE)
                    .edit()
                    .putString("api_url", etApiUrl.getText().toString().trim())
                    .putString("device_id", etDeviceId.getText().toString().trim())
                    .apply();
            Toast.makeText(this, "Config saved", Toast.LENGTH_SHORT).show();
            tvStatus.setText("✅ Config updated");
        });

        btnCheckPerms.setOnClickListener(v -> checkPermissions());

        // Auto-check on start
        checkPermissions();
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS},
                    PERMISSION_REQUEST);
            tvStatus.setText("⏳ Waiting for permissions...");
        } else {
            tvStatus.setText("✅ Permissions granted | Service ready");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                tvStatus.setText("✅ Permissions granted | Ready");
            } else {
                tvStatus.setText("❌ Permissions denied - SMS won't be captured");
            }
        }
    }
}
