package com.sms.forwarder;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

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

    private static final int PERMISSION_REQUEST = 100;

    private static final String DEFAULT_API_URL =
            "https://smssend-8ek4.onrender.com/api/messages";

    private EditText etPhoneNumber;
    private EditText etApiUrl;
    private EditText etDeviceId;

    private TextView tvSmsPermission;
    private TextView tvNotificationPermission;
    private TextView tvServiceStatus;
    private TextView tvStatus;

    private SharedPreferences prefs;

    private final OkHttpClient client =
            new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("config", MODE_PRIVATE);

        etPhoneNumber = findViewById(R.id.etPhoneNumber);
        etApiUrl = findViewById(R.id.etApiUrl);
        etDeviceId = findViewById(R.id.etDeviceId);

        tvSmsPermission = findViewById(R.id.tvSmsPermission);
        tvNotificationPermission =
                findViewById(R.id.tvNotificationPermission);

        tvServiceStatus =
                findViewById(R.id.tvServiceStatus);

        tvStatus =
                findViewById(R.id.tvStatus);

        Button btnSave =
                findViewById(R.id.btnSave);

        Button btnCheckPerms =
                findViewById(R.id.btnCheckPerms);

        createDeviceIdIfNeeded();

        loadConfig();

        btnSave.setOnClickListener(v -> {

            if (saveConfig()) {
                registerDevice();
                startForwardService();
            }

        });

        btnCheckPerms.setOnClickListener(v -> {
            checkPermissions();
            registerDevice();
            startForwardService();
        });

        /*
         * Initial startup.
         */
        checkPermissions();

        updateServiceStatus();

        /*
         * IMPORTANT:
         * Register device immediately.
         */
        registerDevice();

        /*
         * Start foreground service so that
         * heartbeat continues even when no SMS
         * has arrived yet.
         */
        startForwardService();
    }

    /**
     * Create a stable unique ID for this app installation.
     */
    private void createDeviceIdIfNeeded() {

        String deviceId =
                prefs.getString("device_id", "");

        if (deviceId == null || deviceId.trim().isEmpty()) {

            String newDeviceId =
                    UUID.randomUUID().toString();

            prefs.edit()
                    .putString(
                            "device_id",
                            newDeviceId
                    )
                    .apply();

            Log.d(
                    TAG,
                    "Generated device ID: "
                            + newDeviceId
            );
        }
    }

    /**
     * Load saved configuration into UI.
     */
    private void loadConfig() {

        String phoneNumber =
                prefs.getString(
                        "phone_number",
                        ""
                );

        String apiUrl =
                prefs.getString(
                        "api_url",
                        DEFAULT_API_URL
                );

        String deviceId =
                prefs.getString(
                        "device_id",
                        ""
                );

        etPhoneNumber.setText(phoneNumber);
        etApiUrl.setText(apiUrl);
        etDeviceId.setText(deviceId);
    }

    /**
     * Save configuration.
     */
    private boolean saveConfig() {

        String phoneNumber =
                etPhoneNumber
                        .getText()
                        .toString()
                        .trim();

        String apiUrl =
                etApiUrl
                        .getText()
                        .toString()
                        .trim();

        String deviceId =
                etDeviceId
                        .getText()
                        .toString()
                        .trim();

        if (phoneNumber.isEmpty()) {

            etPhoneNumber.setError(
                    "Enter your phone number"
            );

            etPhoneNumber.requestFocus();

            return false;
        }

        if (apiUrl.isEmpty()) {

            etApiUrl.setError(
                    "Enter API endpoint"
            );

            etApiUrl.requestFocus();

            return false;
        }

        if (deviceId.isEmpty()) {

            etDeviceId.setError(
                    "Device ID is required"
            );

            etDeviceId.requestFocus();

            return false;
        }

        prefs.edit()
                .putString(
                        "phone_number",
                        phoneNumber
                )
                .putString(
                        "api_url",
                        apiUrl
                )
                .putString(
                        "device_id",
                        deviceId
                )
                .apply();

        Toast.makeText(
                this,
                "Configuration saved",
                Toast.LENGTH_SHORT
        ).show();

        tvStatus.setText(
                "✓ Configuration saved"
        );

        return true;
    }

    /**
     * Register / update device on backend.
     */
    private void registerDevice() {

        String deviceId =
                prefs.getString(
                        "device_id",
                        ""
                );

        if (deviceId.isEmpty()) {
            createDeviceIdIfNeeded();

            deviceId =
                    prefs.getString(
                            "device_id",
                            ""
                    );
        }

        String apiUrl =
                prefs.getString(
                        "api_url",
                        DEFAULT_API_URL
                );

        String baseUrl =
                getBaseUrl(apiUrl);

        String heartbeatUrl =
                baseUrl +
                        "/api/devices/heartbeat";

        String phoneNumber =
                prefs.getString(
                        "phone_number",
                        ""
                );

        String model =
                Build.MODEL != null
                        ? Build.MODEL
                        : "";

        String manufacturer =
                Build.MANUFACTURER != null
                        ? Build.MANUFACTURER
                        : "";

        String androidVersion =
                Build.VERSION.RELEASE != null
                        ? Build.VERSION.RELEASE
                        : "";

        String appVersion =
                getAppVersion();

        String json =
                "{"
                        + "\"deviceId\":\""
                        + escapeJson(deviceId)
                        + "\","

                        + "\"phoneNumber\":\""
                        + escapeJson(phoneNumber)
                        + "\","

                        + "\"model\":\""
                        + escapeJson(model)
                        + "\","

                        + "\"manufacturer\":\""
                        + escapeJson(manufacturer)
                        + "\","

                        + "\"androidVersion\":\""
                        + escapeJson(androidVersion)
                        + "\","

                        + "\"appVersion\":\""
                        + escapeJson(appVersion)
                        + "\""
                        + "}";

        Log.d(
                TAG,
                "Registering device: "
                        + deviceId
        );

        new Thread(() -> {

            try {

                RequestBody body =
                        RequestBody.create(
                                json,
                                MediaType.parse(
                                        "application/json; charset=utf-8"
                                )
                        );

                Request request =
                        new Request.Builder()
                                .url(heartbeatUrl)
                                .post(body)
                                .addHeader(
                                        "Content-Type",
                                        "application/json"
                                )
                                .build();

                try (Response response =
                             client.newCall(request).execute()) {

                    int code =
                            response.code();

                    Log.d(
                            TAG,
                            "Device heartbeat HTTP "
                                    + code
                    );

                    if (response.isSuccessful()) {

                        runOnUiThread(() ->
                                tvStatus.setText(
                                        "✓ Device registered"
                                )
                        );

                    } else {

                        String responseBody = "";

                        if (response.body() != null) {
                            responseBody =
                                    response.body().string();
                        }

                        Log.e(
                                TAG,
                                "Device registration failed: "
                                        + code
                                        + " | "
                                        + responseBody
                        );

                        runOnUiThread(() ->
                                tvStatus.setText(
                                        "⚠ Device registration failed"
                                )
                        );
                    }
                }

            } catch (Exception e) {

                Log.e(
                        TAG,
                        "Heartbeat failed",
                        e
                );

                runOnUiThread(() ->
                        tvStatus.setText(
                                "⚠ Server connection failed"
                        )
                );
            }

        }).start();
    }

    /**
     * Start ForwardService.
     */
    private void startForwardService() {

        try {

            Intent intent =
                    new Intent(
                            this,
                            ForwardService.class
                    );

            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.O) {

                ContextCompat.startForegroundService(
                        this,
                        intent
                );

            } else {

                startService(intent);
            }

            updateServiceStatus();

            Log.d(
                    TAG,
                    "ForwardService start requested"
            );

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Unable to start service",
                    e
            );

            tvServiceStatus.setText(
                    "● Service start failed"
            );
        }
    }

    /**
     * Get base URL from configured messages endpoint.
     *
     * Example:
     * https://example.com/api/messages
     *
     * becomes:
     * https://example.com
     */
    private String getBaseUrl(String apiUrl) {

        String baseUrl = apiUrl.trim();

        String marker =
                "/api/messages";

        int index =
                baseUrl.indexOf(marker);

        if (index >= 0) {

            baseUrl =
                    baseUrl.substring(
                            0,
                            index
                    );
        }

        while (baseUrl.endsWith("/")) {

            baseUrl =
                    baseUrl.substring(
                            0,
                            baseUrl.length() - 1
                    );
        }

        return baseUrl;
    }

    /**
     * App version.
     */
    private String getAppVersion() {

        try {

            return getPackageManager()
                    .getPackageInfo(
                            getPackageName(),
                            0
                    )
                    .versionName;

        } catch (Exception e) {

            return "1.0";
        }
    }

    /**
     * Check permissions.
     */
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

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU) {

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

            tvStatus.setText(
                    "Waiting for SMS permission..."
            );

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.RECEIVE_SMS,
                            Manifest.permission.READ_SMS
                    },
                    PERMISSION_REQUEST
            );

        } else {

            tvStatus.setText(
                    "✓ SMS permissions granted"
            );
        }

        updateServiceStatus();
    }

    /**
     * Permission UI helper.
     */
    private void updatePermissionStatus(
            TextView view,
            boolean granted,
            String title
    ) {

        if (granted) {

            view.setText(
                    "✓ " +
                            title +
                            "\nGranted"
            );

            view.setTextColor(
                    getColor(
                            android.R.color.holo_green_light
                    )
            );

        } else {

            view.setText(
                    "✕ " +
                            title +
                            "\nNot granted"
            );

            view.setTextColor(
                    getColor(
                            android.R.color.holo_red_light
                    )
            );
        }
    }

    /**
     * Service status UI.
     */
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
                    getColor(
                            android.R.color.holo_green_light
                    )
            );

        } else {

            tvServiceStatus.setText(
                    "● Waiting for permission"
            );

            tvServiceStatus.setTextColor(
                    getColor(
                            android.R.color.holo_orange_light
                    )
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

        if (requestCode ==
                PERMISSION_REQUEST) {

            boolean allGranted = true;

            for (int result : grantResults) {

                if (result !=
                        PackageManager.PERMISSION_GRANTED) {

                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {

                tvStatus.setText(
                        "✓ Permissions granted | Service ready"
                );

                registerDevice();
                startForwardService();

            } else {

                tvStatus.setText(
                        "✕ SMS permission denied"
                );
            }

            checkPermissions();
        }
    }

    /**
     * Escape JSON values safely.
     */
    private String escapeJson(String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
