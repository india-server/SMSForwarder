package com.sms.forwarder;

import android.Manifest;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    /*
     * Backend configuration
     * User does not need to enter this in the UI.
     */
    private static final String API_BASE_URL =
            "https://smssend-8ek4.onrender.com";

    private static final String HEARTBEAT_URL =
            API_BASE_URL + "/api/devices/heartbeat";

    private static final String MESSAGE_URL =
            API_BASE_URL + "/api/messages";

    private static final String PREFS_NAME = "config";

    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_PHONE_NUMBER = "phone_number";
    private static final String KEY_API_URL = "api_url";

    private EditText etPhoneNumber;

    private TextView tvPermissionStatus;
    private TextView tvServiceStatus;
    private TextView tvBackendStatus;
    private TextView tvDeviceStatus;
    private TextView tvLastSync;

    private Button btnSavePhone;
    private Button btnCheckPermissions;

    private final OkHttpClient client =
            new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .build();

    /*
     * Android runtime permission launcher.
     */
    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {

                        updatePermissionStatus();

                        if (hasSmsPermissions()) {

                            Toast.makeText(
                                    MainActivity.this,
                                    "SMS permissions granted",
                                    Toast.LENGTH_SHORT
                            ).show();

                            sendHeartbeat();
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        initViews();

        initializeConfig();

        updatePermissionStatus();

        updateServiceStatus();

        /*
         * Register/update the device when the app opens.
         */
        sendHeartbeat();
    }

    /*
     * Connect XML views.
     */
    private void initViews() {

        etPhoneNumber =
                findViewById(R.id.etPhoneNumber);

        tvPermissionStatus =
                findViewById(R.id.tvPermissionStatus);

        tvServiceStatus =
                findViewById(R.id.tvServiceStatus);

        tvBackendStatus =
                findViewById(R.id.tvBackendStatus);

        tvDeviceStatus =
                findViewById(R.id.tvDeviceStatus);

        tvLastSync =
                findViewById(R.id.tvLastSync);

        btnSavePhone =
                findViewById(R.id.btnSavePhone);

        btnCheckPermissions =
                findViewById(R.id.btnCheckPermissions);

        btnSavePhone.setOnClickListener(
                v -> savePhoneNumber()
        );

        btnCheckPermissions.setOnClickListener(
                v -> requestPermissionsIfNeeded()
        );
    }

    /*
     * Create a persistent random device ID.
     *
     * It is generated only once and then stored locally.
     * It is NOT displayed in the UI.
     */
    private String getOrCreateDeviceId() {

        SharedPreferences prefs =
                getSharedPreferences(
                        PREFS_NAME,
                        MODE_PRIVATE
                );

        String existingId =
                prefs.getString(
                        KEY_DEVICE_ID,
                        ""
                );

        if (existingId != null
                && !existingId.isEmpty()) {

            return existingId;
        }

        String newDeviceId =
                UUID.randomUUID().toString();

        prefs.edit()
                .putString(
                        KEY_DEVICE_ID,
                        newDeviceId
                )
                .apply();

        return newDeviceId;
    }

    /*
     * Initialize automatically managed configuration.
     */
    private void initializeConfig() {

        SharedPreferences prefs =
                getSharedPreferences(
                        PREFS_NAME,
                        MODE_PRIVATE
                );

        /*
         * ForwardService uses this value for SMS forwarding.
         */
        prefs.edit()
                .putString(
                        KEY_API_URL,
                        MESSAGE_URL
                )
                .apply();

        /*
         * Make sure device ID exists.
         */
        getOrCreateDeviceId();

        /*
         * Restore saved phone number.
         */
        String savedPhone =
                prefs.getString(
                        KEY_PHONE_NUMBER,
                        ""
                );

        etPhoneNumber.setText(savedPhone);
    }

    /*
     * Save phone number locally and immediately
     * update the backend device record.
     */
    private void savePhoneNumber() {

        String phone =
                etPhoneNumber
                        .getText()
                        .toString()
                        .trim();

        if (phone.isEmpty()) {

            etPhoneNumber.setError(
                    "Enter your phone number"
            );

            etPhoneNumber.requestFocus();

            return;
        }

        if (phone.length() < 7) {

            etPhoneNumber.setError(
                    "Enter a valid phone number"
            );

            etPhoneNumber.requestFocus();

            return;
        }

        getSharedPreferences(
                PREFS_NAME,
                MODE_PRIVATE
        )
                .edit()
                .putString(
                        KEY_PHONE_NUMBER,
                        phone
                )
                .apply();

        Toast.makeText(
                this,
                "Phone number saved",
                Toast.LENGTH_SHORT
        ).show();

        sendHeartbeat();
    }

    /*
     * Request SMS permissions if they are not already granted.
     */
    private void requestPermissionsIfNeeded() {

        if (hasSmsPermissions()) {

            updatePermissionStatus();

            Toast.makeText(
                    this,
                    "SMS permissions already granted",
                    Toast.LENGTH_SHORT
            ).show();

            sendHeartbeat();

            return;
        }

        permissionLauncher.launch(
                new String[]{
                        Manifest.permission.RECEIVE_SMS,
                        Manifest.permission.READ_SMS
                }
        );
    }

    /*
     * Check required SMS permissions.
     */
    private boolean hasSmsPermissions() {

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

        return receiveSms && readSms;
    }

    /*
     * Update permission status in UI.
     */
    private void updatePermissionStatus() {

        if (hasSmsPermissions()) {

            tvPermissionStatus.setText(
                    "✓ SMS permissions granted"
            );

            tvPermissionStatus.setTextColor(
                    getColor(
                            android.R.color.holo_green_light
                    )
            );

        } else {

            tvPermissionStatus.setText(
                    "⚠ SMS permissions required"
            );

            tvPermissionStatus.setTextColor(
                    getColor(
                            android.R.color.holo_orange_light
                    )
            );
        }
    }

    /*
     * Update service status.
     *
     * ForwardService is declared in the application
     * and is started by SmsReceiver when an SMS arrives.
     */
    private void updateServiceStatus() {

        tvServiceStatus.setText(
                "✓ Forwarding service ready"
        );

        tvServiceStatus.setTextColor(
                getColor(
                        android.R.color.holo_green_light
                )
        );
    }

    /*
     * Send device registration / heartbeat to backend.
     */
    private void sendHeartbeat() {

        SharedPreferences prefs =
                getSharedPreferences(
                        PREFS_NAME,
                        MODE_PRIVATE
                );

        String phoneNumber =
                prefs.getString(
                        KEY_PHONE_NUMBER,
                        ""
                );

        String deviceId =
                getOrCreateDeviceId();

        int batteryLevel =
                getBatteryLevel();

        boolean charging =
                isCharging();

        JSONObject json =
                new JSONObject();

        try {

            json.put(
                    "deviceId",
                    deviceId
            );

            json.put(
                    "phoneNumber",
                    phoneNumber
            );

            json.put(
                    "model",
                    Build.MODEL
            );

            json.put(
                    "manufacturer",
                    Build.MANUFACTURER
            );

            json.put(
                    "androidVersion",
                    Build.VERSION.RELEASE
            );

            json.put(
                    "appVersion",
                    "1.0"
            );

            json.put(
                    "batteryLevel",
                    batteryLevel
            );

            json.put(
                    "isCharging",
                    charging
            );

        } catch (Exception e) {

            setBackendError(
                    "Could not prepare device data"
            );

            return;
        }

        RequestBody requestBody =
                RequestBody.create(
                        json.toString(),
                        MediaType.parse(
                                "application/json; charset=utf-8"
                        )
                );

        Request request =
                new Request.Builder()
                        .url(HEARTBEAT_URL)
                        .post(requestBody)
                        .addHeader(
                                "Content-Type",
                                "application/json"
                        )
                        .build();

        runOnUiThread(() -> {

            tvBackendStatus.setText(
                    "⏳ Connecting to backend..."
            );

            tvBackendStatus.setTextColor(
                    getColor(
                            android.R.color.holo_orange_light
                    )
            );
        });

        new Thread(() -> {

            try (Response response =
                         client.newCall(request).execute()) {

                if (response.isSuccessful()) {

                    runOnUiThread(() -> {

                        tvBackendStatus.setText(
                                "✓ Backend connected"
                        );

                        tvBackendStatus.setTextColor(
                                getColor(
                                        android.R.color.holo_green_light
                                )
                        );

                        tvDeviceStatus.setText(
                                "✓ Device registered"
                        );

                        tvDeviceStatus.setTextColor(
                                getColor(
                                        android.R.color.holo_green_light
                                )
                        );

                        tvLastSync.setText(
                                "Last sync: Just now"
                        );
                    });

                } else {

                    setBackendError(
                            "Backend returned HTTP "
                                    + response.code()
                    );
                }

            } catch (IOException e) {

                setBackendError(
                        "Backend unavailable"
                );
            }

        }).start();
    }

    /*
     * Display backend/device error.
     */
    private void setBackendError(String message) {

        runOnUiThread(() -> {

            tvBackendStatus.setText(
                    "✕ " + message
            );

            tvBackendStatus.setTextColor(
                    getColor(
                            android.R.color.holo_red_light
                    )
            );

            tvDeviceStatus.setText(
                    "⚠ Device not registered"
            );

            tvDeviceStatus.setTextColor(
                    getColor(
                            android.R.color.holo_orange_light
                    )
            );
        });
    }

    /*
     * Get battery percentage.
     */
    private int getBatteryLevel() {

        IntentFilter filter =
                new IntentFilter(
                        Intent.ACTION_BATTERY_CHANGED
                );

        Intent batteryStatus =
                registerReceiver(
                        null,
                        filter
                );

        if (batteryStatus == null) {
            return -1;
        }

        int level =
                batteryStatus.getIntExtra(
                        BatteryManager.EXTRA_LEVEL,
                        -1
                );

        int scale =
                batteryStatus.getIntExtra(
                        BatteryManager.EXTRA_SCALE,
                        -1
                );

        if (level < 0 || scale <= 0) {
            return -1;
        }

        return (level * 100) / scale;
    }

    /*
     * Check whether the device is charging.
     */
    private boolean isCharging() {

        IntentFilter filter =
                new IntentFilter(
                        Intent.ACTION_BATTERY_CHANGED
                );

        Intent batteryStatus =
                registerReceiver(
                        null,
                        filter
                );

        if (batteryStatus == null) {
            return false;
        }

        int status =
                batteryStatus.getIntExtra(
                        BatteryManager.EXTRA_STATUS,
                        -1
                );

        return status ==
                BatteryManager.BATTERY_STATUS_CHARGING
                ||
                status ==
                        BatteryManager.BATTERY_STATUS_FULL;
    }

    @Override
    protected void onResume() {

        super.onResume();

        updatePermissionStatus();

        updateServiceStatus();
    }
}
