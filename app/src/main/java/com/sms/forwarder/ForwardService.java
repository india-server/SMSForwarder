package com.sms.forwarder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ForwardService extends Service {

    private static final String TAG = "ForwardService";
    private static final String CHANNEL_ID = "sms_forwarder";

    private static final String DEFAULT_API =
            "https://smssend-8ek4.onrender.com";

    private static final MediaType JSON =
            MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client =
            new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build();

    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();

        startForeground(
                1,
                buildNotification("SMS Forwarder Active")
        );

        Log.d(TAG, "Service created");

        // Register/update device when service starts.
        sendHeartbeat();
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId
    ) {

        if (intent != null
                && intent.hasExtra("sender")
                && intent.hasExtra("body")) {

            String sender =
                    intent.getStringExtra("sender");

            String body =
                    intent.getStringExtra("body");

            SharedPreferences prefs =
                    getSharedPreferences(
                            "config",
                            MODE_PRIVATE
                    );

            String apiBase =
                    prefs.getString(
                            "api_url",
                            DEFAULT_API + "/api/messages"
                    );

            String deviceId =
                    getDeviceId(prefs);

            String phoneNumber =
                    prefs.getString(
                            "phone_number",
                            ""
                    );

            Log.d(
                    TAG,
                    "Processing SMS | sender="
                            + sender
                            + " | device="
                            + deviceId
            );

            forwardMessage(
                    apiBase,
                    sender,
                    body,
                    deviceId,
                    phoneNumber
            );

            // Also update device status.
            sendHeartbeat();
        }

        return START_STICKY;
    }

    /**
     * Gets an app-scoped device ID.
     *
     * Do not use Build.MODEL as the unique ID because
     * multiple phones can have the same model.
     */
    private String getDeviceId(
            SharedPreferences prefs
    ) {

        String existing =
                prefs.getString(
                        "device_id",
                        ""
                );

        if (existing != null
                && !existing.trim().isEmpty()) {

            return existing.trim();
        }

        String generated =
                UUID.randomUUID().toString();

        prefs.edit()
                .putString(
                        "device_id",
                        generated
                )
                .apply();

        return generated;
    }

    /**
     * Sends device information to:
     * POST /api/devices/heartbeat
     */
    private void sendHeartbeat() {

        SharedPreferences prefs =
                getSharedPreferences(
                        "config",
                        MODE_PRIVATE
                );

        String apiUrl =
                prefs.getString(
                        "api_url",
                        DEFAULT_API + "/api/messages"
                );

        String baseUrl = extractBaseUrl(apiUrl);

        String heartbeatUrl =
                baseUrl + "/api/devices/heartbeat";

        String deviceId =
                getDeviceId(prefs);

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

        Request request =
                new Request.Builder()
                        .url(heartbeatUrl)
                        .post(
                                RequestBody.create(
                                        json,
                                        JSON
                                )
                        )
                        .addHeader(
                                "Content-Type",
                                "application/json"
                        )
                        .build();

        new Thread(() -> {

            try (Response response =
                         client.newCall(request).execute()) {

                if (response.isSuccessful()) {

                    Log.d(
                            TAG,
                            "Heartbeat successful | device="
                                    + deviceId
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

            } catch (Exception e) {

                Log.e(
                        TAG,
                        "Heartbeat network error",
                        e
                );
            }

        }).start();
    }

    private void forwardMessage(
            String apiUrl,
            String sender,
            String body,
            String deviceId,
            String phoneNumber
    ) {

        String json =
                "{"
                        + "\"sender\":\""
                        + escapeJson(sender)
                        + "\","

                        + "\"message\":\""
                        + escapeJson(body)
                        + "\","

                        + "\"deviceId\":\""
                        + escapeJson(deviceId)
                        + "\","

                        + "\"phoneNumber\":\""
                        + escapeJson(phoneNumber)
                        + "\""
                        + "}";

        Request request =
                new Request.Builder()
                        .url(apiUrl)
                        .post(
                                RequestBody.create(
                                        json,
                                        JSON
                                )
                        )
                        .addHeader(
                                "Content-Type",
                                "application/json"
                        )
                        .build();

        new Thread(() -> {

            try (Response response =
                         client.newCall(request).execute()) {

                if (response.isSuccessful()) {

                    Log.d(
                            TAG,
                            "SMS forwarded successfully | "
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
                            "Forward failed | HTTP "
                                    + response.code()
                                    + " | "
                                    + responseBody
                    );
                }

            } catch (IOException e) {

                Log.e(
                        TAG,
                        "Network error while forwarding SMS",
                        e
                );
            }

        }).start();
    }

    private String extractBaseUrl(
            String apiUrl
    ) {

        if (apiUrl == null
                || apiUrl.trim().isEmpty()) {

            return DEFAULT_API;
        }

        String url =
                apiUrl.trim();

        String marker =
                "/api/messages";

        int index =
                url.indexOf(marker);

        if (index >= 0) {
            return url.substring(
                    0,
                    index
            );
        }

        if (url.endsWith("/")) {
            return url.substring(
                    0,
                    url.length() - 1
            );
        }

        return url;
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

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "SMS Forwarder",
                            NotificationManager.IMPORTANCE_LOW
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
    public IBinder onBind(
            Intent intent
    ) {
        return null;
    }
}
