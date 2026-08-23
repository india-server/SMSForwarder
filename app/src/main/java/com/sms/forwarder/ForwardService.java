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

    @Override
    public void onCreate() {

        super.onCreate();

        createNotificationChannel();

        startForeground(
                1,
                buildNotification(
                        "SMS Forwarder Active"
                )
        );
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId
    ) {

        if (intent != null &&
                intent.hasExtra("sender") &&
                intent.hasExtra("body")) {

            String sender =
                    intent.getStringExtra(
                            "sender"
                    );

            String body =
                    intent.getStringExtra(
                            "body"
                    );

            SharedPreferences prefs =
                    getSharedPreferences(
                            "config",
                            MODE_PRIVATE
                    );

            String apiUrl =
                    prefs.getString(
                            "api_url",
                            "https://smssend-8ek4.onrender.com/api/messages"
                    );

            String deviceId =
                    prefs.getString(
                            "device_id",
                            "unknown"
                    );

            if (sender != null &&
                    body != null &&
                    !sender.isEmpty() &&
                    !body.isEmpty()) {

                forwardMessage(
                        apiUrl,
                        sender,
                        body,
                        deviceId
                );
            }
        }

        return START_STICKY;
    }

    private void forwardMessage(
            String apiUrl,
            String sender,
            String body,
            String deviceId
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
                        + "\""
                        + "}";

        RequestBody requestBody =
                RequestBody.create(
                        json,
                        JSON
                );

        Request request =
                new Request.Builder()
                        .url(apiUrl)
                        .post(requestBody)
                        .addHeader(
                                "Content-Type",
                                "application/json"
                        )
                        .build();

        new Thread(() -> {

            try (Response response =
                         client.newCall(request)
                                 .execute()) {

                if (response.isSuccessful()) {

                    Log.d(
                            TAG,
                            "SMS forwarded successfully"
                    );

                } else {

                    String errorBody = "";

                    if (response.body() != null) {
                        errorBody =
                                response.body()
                                        .string();
                    }

                    Log.e(
                            TAG,
                            "Forward failed: "
                                    + response.code()
                                    + " | "
                                    + errorBody
                    );
                }

            } catch (IOException e) {

                Log.e(
                        TAG,
                        "Network error",
                        e
                );
            }

        }).start();
    }

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
                .build();
    }

    @Override
    public IBinder onBind(
            Intent intent
    ) {
        return null;
    }
}
