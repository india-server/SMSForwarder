package com.sms.forwarder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ForwardService extends Service {

    private static final String TAG =
            "ForwardService";

    private static final String CHANNEL_ID =
            "sms_forwarder";

    private static final int NOTIFICATION_ID =
            1;

    private static final long HEARTBEAT_INTERVAL_SECONDS =
            60;

    private static final String DEFAULT_API_URL =
            "https://smssend-8ek4.onrender.com/api/messages";

    private static final MediaType JSON =
            MediaType.parse(
                    "application/json; charset=utf-8"
            );

    private final OkHttpClient client =
            new OkHttpClient.Builder()
                    .connectTimeout(
                            15,
                            TimeUnit.SECONDS
                    )
                    .writeTimeout(
                            15,
                            TimeUnit.SECONDS
                    )
                    .readTimeout(
                            30,
                            TimeUnit.SECONDS
                    )
                    .build();

    private ScheduledExecutorService scheduler;

    @Override
    public void onCreate() {

        super.onCreate();

        createNotificationChannel();

        startForeground(
                NOTIFICATION_ID,
                buildNotification(
                        "SMS Forwarder Active"
                )
        );

        /*
         * Create scheduler.
         */
        scheduler =
                Executors.newSingleThreadScheduledExecutor();

        /*
         * Send first heartbeat immediately.
         */
        scheduler.execute(
                this::sendHeartbeat
        );

        /*
         * Continue heartbeat every 60 seconds.
         */
        scheduler.scheduleAtFixedRate(
                this::sendHeartbeat,
                HEARTBEAT_INTERVAL_SECONDS,
                HEARTBEAT_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );

        Log.d(
                TAG,
                "Service created | heartbeat started"
        );
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId
    ) {

        /*
         * SMS receiver sends sender + body.
         */
        if (intent != null
                && intent.hasExtra("sender")
                && intent.hasExtra("body")) {

            String sender =
                    intent.getStringExtra("sender");

            String body =
                    intent.getStringExtra("body");

            if (sender != null
                    && body != null
                    && !body.isEmpty()) {

                Log.d(
                        TAG,
                        "SMS received | sender="
                                + sender
                );

                forwardMessage(
                        sender,
                        body
                );

                /*
                 * Refresh device status
                 * immediately after SMS.
                 */
                sendHeartbeat();
            }
        }

        return START_STICKY;
    }

    /**
     * Send device heartbeat.
     */
    private void sendHeartbeat() {

        try {

            SharedPreferences prefs =
                    getSharedPreferences(
                            "config",
                            MODE_PRIVATE
                    );

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

            String deviceId =
                    prefs.getString(
                            "device_id",
                            ""
                    );

            if (deviceId.isEmpty()) {

                Log.w(
                        TAG,
                        "Heartbeat skipped: device ID missing"
                );

                return;
            }

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

            int batteryLevel =
                    getBatteryLevel();

            boolean charging =
                    isCharging();

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
                            + "\","

                            + "\"batteryLevel\":"
                            + batteryLevel
                            + ","

                            + "\"isCharging\":"
                            + charging

                            + "}";

            RequestBody body =
                    RequestBody.create(
                            json,
                            JSON
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
                         client
                                 .newCall(request)
                                 .execute()) {

                if (response.isSuccessful()) {

                    Log.d(
                            TAG,
                            "Heartbeat OK | device="
                                    + deviceId
                                    + " | battery="
                                    + batteryLevel
                                    + "% | charging="
                                    + charging
                    );

                } else {

                    String responseBody = "";

                    if (response.body() != null) {

                        responseBody =
                                response.body().string();
                    }

                    Log.e(
                            TAG,
                            "Heartbeat failed | HTTP "
                                    + response.code()
                                    + " | "
                                    + responseBody
                    );
                }
            }

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Heartbeat error",
                    e
            );
        }
    }

    /**
     * Forward SMS message.
     */
    private void forwardMessage(
            String sender,
            String message
    ) {

        try {

            SharedPreferences prefs =
                    getSharedPreferences(
                            "config",
                            MODE_PRIVATE
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

            String phoneNumber =
                    prefs.getString(
                            "phone_number",
                            ""
                    );

            if (deviceId.isEmpty()) {

                Log.e(
                        TAG,
                        "SMS forwarding skipped: "
                                + "device ID missing"
                );

                return;
            }

            String json =
                    "{"
                            + "\"sender\":\""
                            + escapeJson(sender)
                            + "\","

                            + "\"message\":\""
                            + escapeJson(message)
                            + "\","

                            + "\"deviceId\":\""
                            + escapeJson(deviceId)
                            + "\","

                            + "\"phoneNumber\":\""
                            + escapeJson(phoneNumber)
                            + "\""
                            + "}";

            RequestBody body =
                    RequestBody.create(
                            json,
                            JSON
                    );

            Request request =
                    new Request.Builder()
                            .url(apiUrl)
                            .post(body)
                            .addHeader(
                                    "Content-Type",
                                    "application/json"
                            )
                            .build();

            try (Response response =
                         client
                                 .newCall(request)
                                 .execute()) {

                if (response.isSuccessful()) {

                    Log.d(
                            TAG,
                            "SMS forwarded successfully | "
                                    + "sender="
                                    + sender
                    );

                } else {

                    String responseBody = "";

                    if (response.body() != null) {

                        responseBody =
                                response.body().string();
                    }

                    Log.e(
                            TAG,
                            "SMS forward failed | HTTP "
                                    + response.code()
                                    + " | "
                                    + responseBody
                    );
                }
            }

        } catch (IOException e) {

            Log.e(
                    TAG,
                    "Network error while forwarding SMS",
                    e
            );
        }
    }

    /**
     * Get battery percentage.
     */
    private int getBatteryLevel() {

        try {

            BatteryManager batteryManager =
                    (BatteryManager)
                            getSystemService(
                                    BATTERY_SERVICE
                            );

            if (batteryManager != null) {

                int level =
                        batteryManager.getIntProperty(
                                BatteryManager.BATTERY_PROPERTY_CAPACITY
                        );

                if (level >= 0
                        && level <= 100) {

                    return level;
                }
            }

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Unable to read battery",
                    e
            );
        }

        return 0;
    }

    /**
     * Check charging state.
     */
    private boolean isCharging() {

        try {

            BatteryManager batteryManager =
                    (BatteryManager)
                            getSystemService(
                                    BATTERY_SERVICE
                            );

            if (batteryManager != null
                    && Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.M) {

                return batteryManager.isCharging();
            }

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Unable to read charging state",
                    e
            );
        }

        return false;
    }

    /**
     * Extract base URL.
     *
     * https://example.com/api/messages
     *
     * ->
     *
     * https://example.com
     */
    private String getBaseUrl(
            String apiUrl
    ) {

        String baseUrl =
                apiUrl.trim();

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
     * JSON escaping.
     */
    private String escapeJson(
            String value
    ) {

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

    /**
     * Notification channel.
     */
    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "SMS Forwarder",
                            NotificationManager
                                    .IMPORTANCE_LOW
                    );

            channel.setDescription(
                    "SMS Forwarder background service"
            );

            NotificationManager manager =
                    getSystemService(
                            NotificationManager.class
                    );

            if (manager != null) {

                manager.createNotificationChannel(
                        channel
                );
            }
        }
    }

    /**
     * Foreground notification.
     */
    private Notification buildNotification(
            String text
    ) {

        Intent intent =
                new Intent(
                        this,
                        MainActivity.class
                );

        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        this,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE
                );

        return new NotificationCompat.Builder(
                this,
                CHANNEL_ID
        )
                .setContentTitle(
                        "📨 SMS Forwarder"
                )
                .setContentText(text)
                .setSmallIcon(
                        android.R.drawable
                                .ic_menu_report_image
                )
                .setContentIntent(
                        pendingIntent
                )
                .setOngoing(true)
                .setAutoCancel(false)
                .build();
    }

    @Override
    public void onDestroy() {

        Log.d(
                TAG,
                "Service destroyed"
        );

        if (scheduler != null) {

            scheduler.shutdownNow();
            scheduler = null;
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(
            Intent intent
    ) {

        return null;
    }
}
