package com.sms.forwarder;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST = 100;

    private static final String DEFAULT_API_URL =
            "https://smssend-8ek4.onrender.com/api/messages";

    private static final String DEVICE_API_URL =
            "https://smssend-8ek4.onrender.com/api/devices";

    private static final MediaType JSON =
            MediaType.parse("application/json; charset=utf-8");

    private EditText etApiUrl;
    private EditText etDeviceId;
    private TextView tvStatus;

    private final OkHttpClient client =
            new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        etApiUrl = findViewById(R.id.etApiUrl);
        etDeviceId = findViewById(R.id.etDeviceId);
        tvStatus = findViewById(R.id.tvStatus);

        Button btnSave =
                findViewById(R.id.btnSave);

        Button btnCheckPerms =
                findViewById(R.id.btnCheckPerms);

        SharedPreferences prefs =
                getSharedPreferences(
                        "config",
                        MODE_PRIVATE
                );

        String deviceId =
                prefs.getString(
                        "device_id",
                        null
                );

        if (deviceId == null || deviceId.trim().isEmpty()) {
            deviceId =
                    Settings.Secure.getString(
                            getContentResolver(),
                            Settings.Secure.ANDROID_ID
                    );

            if (deviceId == null ||
                    deviceId.trim().isEmpty()) {

                deviceId =
                        "install-" +
                        System.currentTimeMillis();
            }

            prefs.edit()
                    .putString(
                            "device_id",
                            deviceId
                    )
                    .apply();
        }

        etApiUrl.setText(
                prefs.getString(
                        "api_url",
                        DEFAULT_API_URL
                )
        );

        etDeviceId.setText(deviceId);

        btnSave.setOnClickListener(v -> {

            String apiUrl =
                    etApiUrl.getText()
                            .toString()
                            .trim();

            String savedDeviceId =
                    etDeviceId.getText()
                            .toString()
                            .trim();

            if (apiUrl.isEmpty()) {
                apiUrl = DEFAULT_API_URL;
            }

            if (savedDeviceId.isEmpty()) {
                Toast.makeText(
                        this,
                        "Device ID cannot be empty",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            prefs.edit()
                    .putString(
                            "api_url",
                            apiUrl
                    )
                    .putString(
                            "device_id",
                            savedDeviceId
                    )
                    .apply();

            Toast.makeText(
                    this,
                    "Config saved",
                    Toast.LENGTH_SHORT
            ).show();

            tvStatus.setText(
                    "Config saved"
            );

            registerDevice();
        });

        btnCheckPerms.setOnClickListener(
                v -> checkPermissions()
        );

        checkPermissions();

        // Register/update basic device information.
        registerDevice();
    }

    /* =====================================================
     * PERMISSIONS
     * =================================================== */

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

        if (!receiveSms || !readSms) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.RECEIVE_SMS,
                            Manifest.permission.READ_SMS
                    },
                    PERMISSION_REQUEST
            );

            tvStatus.setText(
                    "Waiting for SMS permission..."
            );

        } else {

            tvStatus.setText(
                    "SMS permission granted | Service ready"
            );

            registerDevice();
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

        if (requestCode != PERMISSION_REQUEST) {
            return;
        }

        boolean receiveGranted =
                ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.RECEIVE_SMS
                ) == PackageManager.PERMISSION_GRANTED;

        boolean readGranted =
                ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_SMS
                ) == PackageManager.PERMISSION_GRANTED;

        if (receiveGranted && readGranted) {

            tvStatus.setText(
                    "SMS permission granted | Ready"
            );

        } else {

            tvStatus.setText(
                    "SMS permission denied"
            );
        }

        registerDevice();
    }

    /* =====================================================
     * DEVICE REGISTRATION
     * =================================================== */

    private void registerDevice() {

        new Thread(() -> {

            try {

                SharedPreferences prefs =
                        getSharedPreferences(
                                "config",
                                MODE_PRIVATE
                        );

                String deviceId =
                        prefs.getString(
                                "device_id",
                                ""
                        );

                if (deviceId == null ||
                        deviceId.trim().isEmpty()) {
                    return;
                }

                JSONObject root =
                        new JSONObject();

                root.put(
                        "deviceId",
                        deviceId
                );

                root.put(
                        "manufacturer",
                        Build.MANUFACTURER
                );

                root.put(
                        "model",
                        Build.MODEL
                );

                root.put(
                        "androidVersion",
                        Build.VERSION.RELEASE
                );

                root.put(
                        "sdkVersion",
                        Build.VERSION.SDK_INT
                );

                String appVersion =
                        getAppVersion();

                root.put(
                        "appVersion",
                        appVersion
                );

                JSONObject battery =
                        getBatteryInfo();

                root.put(
                        "battery",
                        battery
                );

                JSONObject network =
                        getNetworkInfo();

                root.put(
                        "network",
                        network
                );

                JSONObject permissions =
                        new JSONObject();

                permissions.put(
                        "sms",
                        hasSmsPermission()
                );

                permissions.put(
                        "notifications",
                        hasNotificationPermission()
                );

                root.put(
                        "permissions",
                        permissions
                );

                RequestBody body =
                        RequestBody.create(
                                root.toString(),
                                JSON
                        );

                Request request =
                        new Request.Builder()
                                .url(DEVICE_API_URL)
                                .post(body)
                                .addHeader(
                                        "Content-Type",
                                        "application/json"
                                )
                                .build();

                try (Response response =
                             client.newCall(request).execute()) {

                    if (response.isSuccessful()) {

                        runOnUiThread(() ->
                                tvStatus.setText(
                                        hasSmsPermission()
                                                ? "Device registered | SMS ready"
                                                : "Device registered | SMS permission required"
                                )
                        );

                    } else {

                        runOnUiThread(() ->
                                tvStatus.setText(
                                        "Device registration failed: " +
                                        response.code()
                                )
                        );
                    }
                }

            } catch (Exception e) {

                runOnUiThread(() ->
                        tvStatus.setText(
                                "Device registration error"
                        )
                );
            }

        }).start();
    }

    /* =====================================================
     * DEVICE INFO
     * =================================================== */

    private JSONObject getBatteryInfo() {

        JSONObject result =
                new JSONObject();

        try {

            BatteryManager batteryManager =
                    (BatteryManager)
                            getSystemService(
                                    BATTERY_SERVICE
                            );

            int level =
                    batteryManager.getIntProperty(
                            BatteryManager.BATTERY_PROPERTY_CAPACITY
                    );

            result.put(
                    "level",
                    level
            );

            result.put(
                    "charging",
                    isCharging()
            );

        } catch (Exception ignored) {
        }

        return result;
    }

    private boolean isCharging() {

        android.content.IntentFilter filter =
                new android.content.IntentFilter(
                        android.content.Intent.ACTION_BATTERY_CHANGED
                );

        android.content.Intent battery =
                registerReceiver(
                        null,
                        filter
                );

        if (battery == null) {
            return false;
        }

        int status =
                battery.getIntExtra(
                        BatteryManager.EXTRA_STATUS,
                        -1
                );

        return status ==
                BatteryManager.BATTERY_STATUS_CHARGING ||
                status ==
                BatteryManager.BATTERY_STATUS_FULL;
    }

    private JSONObject getNetworkInfo() {

        JSONObject result =
                new JSONObject();

        try {

            ConnectivityManager cm =
                    (ConnectivityManager)
                            getSystemService(
                                    Context.CONNECTIVITY_SERVICE
                            );

            Network network =
                    cm.getActiveNetwork();

            if (network == null) {

                result.put(
                        "connected",
                        false
                );

                result.put(
                        "type",
                        ""
                );

                return result;
            }

            NetworkCapabilities capabilities =
                    cm.getNetworkCapabilities(
                            network
                    );

            if (capabilities == null) {

                result.put(
                        "connected",
                        false
                );

                return result;
            }

            result.put(
                    "connected",
                    true
            );

            if (capabilities.hasTransport(
                    NetworkCapabilities.TRANSPORT_WIFI
            )) {

                result.put(
                        "type",
                        "wifi"
                );

            } else if (
                    capabilities.hasTransport(
                            NetworkCapabilities.TRANSPORT_CELLULAR
                    )
            ) {

                result.put(
                        "type",
                        "cellular"
                );

            } else {

                result.put(
                        "type",
                        "other"
                );
            }

        } catch (Exception e) {

            try {
                result.put(
                        "connected",
                        false
                );
            } catch (Exception ignored) {
            }
        }

        return result;
    }

    private String getAppVersion() {

        try {

            return getPackageManager()
                    .getPackageInfo(
                            getPackageName(),
                            0
                    )
                    .versionName;

        } catch (Exception e) {

            return "unknown";
        }
    }

    /* =====================================================
     * PERMISSION STATUS
     * =================================================== */

    private boolean hasSmsPermission() {

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

    private boolean hasNotificationPermission() {

        if (Build.VERSION.SDK_INT <
                Build.VERSION_CODES.TIRAMISU) {

            return true;
        }

        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED;
    }
}
