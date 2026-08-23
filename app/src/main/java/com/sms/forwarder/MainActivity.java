package com.sms.forwarder;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.TelephonyManager;
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
     * Backend base URL.
     * User ko UI me ye enter karne ki zarurat nahi.
     */
    private static final String API_BASE_URL =
            "https://smssend-8ek4.onrender.com";

    private static final String HEARTBEAT_URL =
            API_BASE_URL + "/api/devices/heartbeat";

    private static final String PREFS = "config";

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

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        updatePermissionStatus();

                        if (hasSmsPermissions()) {
                            Toast.makeText(
                                    this,
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

        sendHeartbeat();
    }

    private void initViews() {

        etPhoneNumber = findViewById(R.id.etPhoneNumber);

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

        btnSavePhone.setOnClickListener(v ->
                savePhoneNumber()
        );

        btnCheckPermissions.setOnClickListener(v ->
                requestPermissionsIfNeeded()
        );
    }

    /*
     * Creates device ID only once.
     * UUID is stored locally and remains the same
     * after app restart.
     */
    private String getOrCreateDeviceId() {

        SharedPreferences prefs =
                getSharedPreferences(PREFS, MODE_PRIVATE);

        String existing =
                prefs.getString(KEY_DEVICE_ID, "");

        if (existing != null && !existing.isEmpty()) {
            return existing;
        }

        String deviceId =
                UUID.randomUUID().toString();

        prefs.edit()
                .putString(KEY_DEVICE_ID, deviceId)
                .apply();

        return deviceId;
    }

    private void initializeConfig() {

        SharedPreferences prefs =
                getSharedPreferences(PREFS, MODE_PRIVATE);

        /*
         * Make sure API URL exists locally so
         * ForwardService can use the same endpoint.
         */
        prefs.edit()
                .putString(KEY_API_URL,
                        API_BASE_URL + "/api/messages")
                .apply();

        /*
         * Create UUID if it doesn't already exist.
         */
        getOrCreateDeviceId();

        /*
         * Load previously saved phone number.
         */
        String phone =
                prefs.getString(
                        KEY_PHONE_NUMBER,
                        ""
                );

        etPhoneNumber.setText(phone);
    }

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
                PREFS,
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

    private void requestPermissionsIfNeeded() {

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

        if (receiveSms && readSms) {

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

    private boolean hasSmsPermissions() {

        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
                &&
                ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_SMS
                ) == PackageManager.PERMISSION_GRANTED;
    }

    private void updatePermissionStatus() {

        if (hasSmsPermissions()) {

            tvPermissionStatus.setText(
                    "✓ SMS permissions granted"
            );

            tvPermissionStatus.setTextColor(
                    getColor(android.R.color.holo_green_light)
            );

        } else {

            tvPermissionStatus.setText(
                    "⚠ SMS permissions required"
            );

            tvPermissionStatus.setTextColor(
                    getColor(android.R.color.holo_orange_light)
            );
        }
    }

    private void updateServiceStatus() {

        /*
         * The SMS receiver is declared in Manifest and
         * ForwardService is available.
         *
         * Actual forwarding begins when SMS is received.
         */
        tvServiceStatus.setText(
                "✓ Forwarding service ready"
        );

        tvServiceStatus.setTextColor(
                getColor(android.R.color.holo_green_light)
        );
    }

    private void sendHeartbeat() {

        SharedPreferences prefs =
                getSharedPreferences(
                        PREFS,
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

            runOnUiThread(() ->
                    setBackendError(
                            "Failed to prepare device data"
                    )
            );

            return;
        }

        RequestBody body =
                RequestBody.create(
                        json.toString(),
                        MediaType.parse(
                                "application/json; charset=utf-8"
                        )
                );

        Request request =
                new Request.Builder()
                        .url(HEARTBEAT_URL)
                        .post(body)
                        .addHeader(
                                "Content-Type",
                                "application/json"
                        )
                        .build();

        tvBackendStatus.setText(
                "⏳ Connecting to backend..."
        );

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

                    runOnUiThread(() ->
                            setBackendError(
                                    "Backend returned HTTP "
                                            + response.code()
                            )
                    );
                }

            } catch (IOException e) {

                runOnUiThread(() ->
                        setBackendError(
                                "Backend unavailable"
                        )
                );
            }

        }).start();
    }

    private void setBackendError(String message) {

        tvBackendStatus.setText(
                "✕ " + message
        );

        tvBackendStatus.setTextColor(
                getColor(
                        android.R.color.holo_red_light
                );

        tvDeviceStatus.setText(
                "⚠ Device not registered"
        );

        tvDeviceStatus.setTextColor(
                getColor(
                        android.R.color.holo_orange_light
                );
    }

    private int getBatteryLevel() {

        BatteryManager batteryManager =
                (BatteryManager) getSystemService(
                        BATTERY_SERVICE
                );

        if (batteryManager == null) {
            return -1;
        }

        return batteryManager.getIntProperty(
                BatteryManager.BATTERY_PROPERTY_CAPACITY
        );
    }

    private boolean isCharging() {

        BatteryManager batteryManager =
                (BatteryManager) getSystemService(
                        BATTERY_SERVICE
                );

        if (batteryManager == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            int status =
                    batteryManager.getIntProperty(
                            BatteryManager.BATTERY_PROPERTY_STATUS
                    );

            return status ==
                    BatteryManager.BATTERY_STATUS_CHARGING
                    ||
                    status ==
                            BatteryManager.BATTERY_STATUS_FULL;
        }

        return false;
    }

    @Override
    protected void onResume() {

        super.onResume();

        updatePermissionStatus();
        updateServiceStatus();
    }
}
