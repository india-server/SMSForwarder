package com.sms.forwarder;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final int SMS_PERMISSION = 100;

    private static final String API =
            "https://smssend-8ek4.onrender.com";

    private static final MediaType JSON =
            MediaType.parse(
                    "application/json; charset=utf-8"
            );

    private EditText phoneInput;
    private TextView statusText;

    private final OkHttpClient client =
            new OkHttpClient.Builder()
                    .connectTimeout(
                            15,
                            TimeUnit.SECONDS
                    )
                    .readTimeout(
                            30,
                            TimeUnit.SECONDS
                    )
                    .build();


    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_main
        );

        phoneInput =
                findViewById(
                        R.id.etPhone
                );

        statusText =
                findViewById(
                        R.id.tvStatus
                );

        Button save =
                findViewById(
                        R.id.btnSave
                );

        Button permissions =
                findViewById(
                        R.id.btnPermissions
                );


        SharedPreferences prefs =
                getSharedPreferences(
                        "config",
                        MODE_PRIVATE
                );


        phoneInput.setText(
                prefs.getString(
                        "phone_number",
                        ""
                )
        );


        save.setOnClickListener(v -> {

            String phone =
                    phoneInput
                            .getText()
                            .toString()
                            .trim();

            if (phone.isEmpty()) {

                phoneInput.setError(
                        "Enter your phone number"
                );

                return;
            }


            prefs.edit()
                    .putString(
                            "phone_number",
                            phone
                    )
                    .apply();


            statusText.setText(
                    "Saving device information..."
            );


            sendHeartbeat();

        });


        permissions.setOnClickListener(v ->
                requestSmsPermission()
        );


        /*
         * Send initial device information.
         * No SMS data is involved here.
         */

        sendHeartbeat();
    }


    private String getDeviceId() {

        SharedPreferences prefs =
                getSharedPreferences(
                        "config",
                        MODE_PRIVATE
                );


        String id =
                prefs.getString(
                        "device_id",
                        null
                );


        if (id == null) {

            id =
                    UUID.randomUUID()
                            .toString();


            prefs.edit()
                    .putString(
                            "device_id",
                            id
                    )
                    .apply();

        }


        return id;
    }


    private void sendHeartbeat() {

        SharedPreferences prefs =
                getSharedPreferences(
                        "config",
                        MODE_PRIVATE
                );


        String phone =
                prefs.getString(
                        "phone_number",
                        ""
                );


        if (phone.isEmpty()) {
            return;
        }


        int battery =
                getBatteryLevel();


        boolean charging =
                isCharging();


        JSONObject json =
                new JSONObject();


        try {

            json.put(
                    "deviceId",
                    getDeviceId()
            );

            json.put(
                    "phoneNumber",
                    phone
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
                    battery
            );

            json.put(
                    "isCharging",
                    charging
            );

        } catch (Exception e) {
            return;
        }


        RequestBody body =
                RequestBody.create(
                        json.toString(),
                        JSON
                );


        Request request =
                new Request.Builder()
                        .url(
                                API +
                                "/api/devices/heartbeat"
                        )
                        .post(body)
                        .addHeader(
                                "Content-Type",
                                "application/json"
                        )
                        .build();


        new Thread(() -> {

            try (
                    Response response =
                            client
                                    .newCall(request)
                                    .execute()
            ) {

                if (response.isSuccessful()) {

                    runOnUiThread(() ->
                            statusText.setText(
                                    "✓ Device connected"
                            )
                    );

                } else {

                    runOnUiThread(() ->
                            statusText.setText(
                                    "Server error: " +
                                    response.code()
                            )
                    );

                }

            } catch (IOException e) {

                runOnUiThread(() ->
                        statusText.setText(
                                "Connection failed"
                        )
                );

            }

        }).start();
    }


    private int getBatteryLevel() {

        BatteryManager manager =
                (BatteryManager)
                        getSystemService(
                                BATTERY_SERVICE
                        );


        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.LOLLIPOP) {

            int level =
                    manager.getIntProperty(
                            BatteryManager
                                    .BATTERY_PROPERTY_CAPACITY
                    );

            if (level >= 0) {
                return level;
            }
        }


        Intent intent =
                registerReceiver(
                        null,
                        new android.content.IntentFilter(
                                Intent.ACTION_BATTERY_CHANGED
                        )
                );


        if (intent == null) {
            return -1;
        }


        int level =
                intent.getIntExtra(
                        BatteryManager.EXTRA_LEVEL,
                        -1
                );

        int scale =
                intent.getIntExtra(
                        BatteryManager.EXTRA_SCALE,
                        -1
                );


        if (
                level < 0 ||
                scale <= 0
        ) {
            return -1;
        }


        return
                Math.round(
                        (level * 100f) / scale
                );
    }


    private boolean isCharging() {

        Intent intent =
                registerReceiver(
                        null,
                        new android.content.IntentFilter(
                                Intent.ACTION_BATTERY_CHANGED
                        )
                );


        if (intent == null) {
            return false;
        }


        int status =
                intent.getIntExtra(
                        BatteryManager.EXTRA_STATUS,
                        -1
                );


        return
                status ==
                        BatteryManager
                                .BATTERY_STATUS_CHARGING
                ||
                status ==
                        BatteryManager
                                .BATTERY_STATUS_FULL;
    }


    private void requestSmsPermission() {

        if (
                ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.RECEIVE_SMS
                )
                != PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.RECEIVE_SMS,
                            Manifest.permission.READ_SMS
                    },
                    SMS_PERMISSION
            );

        } else {

            statusText.setText(
                    "✓ SMS permission already granted"
            );

        }
    }


    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] results
    ) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                results
        );


        if (
                requestCode ==
                        SMS_PERMISSION
        ) {

            if (
                    results.length > 0 &&
                    results[0] ==
                            PackageManager.PERMISSION_GRANTED
            ) {

                statusText.setText(
                        "✓ SMS permission granted"
                );

            } else {

                statusText.setText(
                        "SMS permission denied"
                );

            }

        }
    }
}
