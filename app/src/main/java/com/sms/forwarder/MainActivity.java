package com.sms.forwarder;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
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

    private EditText etPhoneNumber;
    private EditText etApiUrl;
    private EditText etDeviceId;

    private TextView tvSmsPermission;
    private TextView tvNotificationPermission;
    private TextView tvServiceStatus;
    private TextView tvStatus;

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("config", MODE_PRIVATE);

        etPhoneNumber = findViewById(R.id.etPhoneNumber);
        etApiUrl = findViewById(R.id.etApiUrl);
        etDeviceId = findViewById(R.id.etDeviceId);

        tvSmsPermission = findViewById(R.id.tvSmsPermission);
        tvNotificationPermission = findViewById(R.id.tvNotificationPermission);
        tvServiceStatus = findViewById(R.id.tvServiceStatus);
        tvStatus = findViewById(R.id.tvStatus);

        Button btnSave = findViewById(R.id.btnSave);
        Button btnCheckPerms = findViewById(R.id.btnCheckPerms);

        loadConfig();

        btnSave.setOnClickListener(v -> saveConfig());

        btnCheckPerms.setOnClickListener(v -> checkPermissions());

        checkPermissions();
        updateServiceStatus();
    }

    private void loadConfig() {

        String phoneNumber = prefs.getString("phone_number", "");

        String apiUrl = prefs.getString(
                "api_url",
                "https://backend.aerivue.dev/api/messages"
        );

        String deviceId = prefs.getString(
                "device_id",
                Build.MODEL
        );

        etPhoneNumber.setText(phoneNumber);
        etApiUrl.setText(apiUrl);
        etDeviceId.setText(deviceId);
    }

    private void saveConfig() {

        String phoneNumber =
                etPhoneNumber.getText().toString().trim();

        String apiUrl =
                etApiUrl.getText().toString().trim();

        String deviceId =
                etDeviceId.getText().toString().trim();

        if (phoneNumber.isEmpty()) {
            etPhoneNumber.setError("Enter your phone number");
            etPhoneNumber.requestFocus();
            return;
        }

        if (apiUrl.isEmpty()) {
            etApiUrl.setError("Enter API endpoint");
            etApiUrl.requestFocus();
            return;
        }

        if (deviceId.isEmpty()) {
            etDeviceId.setError("Enter device ID");
            etDeviceId.requestFocus();
            return;
        }

        prefs.edit()
                .putString("phone_number", phoneNumber)
                .putString("api_url", apiUrl)
                .putString("device_id", deviceId)
                .apply();

        Toast.makeText(
                this,
                "Configuration saved",
                Toast.LENGTH_SHORT
        ).show();

        tvStatus.setText("✓ Configuration saved");
    }

    private void checkPermissions() {

        boolean receiveSms =
                ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.RECEIVE_SMS
                ) == PackageManager.PERMISSION_GRANTED;

        boolean readSms =
                ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_SMS
                ) == PackageManager.PERMISSION_GRANTED;

        boolean notification = true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            notification =
                    ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED;
        }

        updatePermissionStatus(
                tvSmsPermission,
                receiveSms && readSms,
                "SMS Permission"
        );

        updatePermissionStatus(
                tvNotificationPermission,
                notification,
                "Notification Permission"
        );

        if (!receiveSms || !readSms) {

            tvStatus.setText("Waiting for SMS permission...");

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.RECEIVE_SMS,
                            Manifest.permission.READ_SMS
                    },
                    PERMISSION_REQUEST
            );

        } else {

            tvStatus.setText("✓ SMS permissions granted");
        }

        updateServiceStatus();
    }

    private void updatePermissionStatus(
            TextView view,
            boolean granted,
            String title
    ) {

        if (granted) {

            view.setText(
                    "✓ " + title + "\nGranted"
            );

            view.setTextColor(
                    getColor(android.R.color.holo_green_light)
            );

        } else {

            view.setText(
                    "✕ " + title + "\nNot granted"
            );

            view.setTextColor(
                    getColor(android.R.color.holo_red_light)
            );
        }
    }

    private void updateServiceStatus() {

        boolean smsGranted =
                ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.RECEIVE_SMS
                ) == PackageManager.PERMISSION_GRANTED
                        &&
                        ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.READ_SMS
                        ) == PackageManager.PERMISSION_GRANTED;

        if (smsGranted) {

            tvServiceStatus.setText(
                    "● Service Ready"
            );

            tvServiceStatus.setTextColor(
                    getColor(android.R.color.holo_green_light)
            );

        } else {

            tvServiceStatus.setText(
                    "● Waiting for permission"
            );

            tvServiceStatus.setTextColor(
                    getColor(android.R.color.holo_orange_light)
            );
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (tvSmsPermission != null) {
            checkPermissions();
        }

        if (tvServiceStatus != null) {
            updateServiceStatus();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == PERMISSION_REQUEST) {

            boolean allGranted = true;

            for (int result : grantResults) {

                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {

                tvStatus.setText(
                        "✓ Permissions granted | Service ready"
                );

            } else {

                tvStatus.setText(
                        "✕ SMS permission denied"
                );
            }

            checkPermissions();
        }
    }
}
