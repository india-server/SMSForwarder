package com.sms.forwarder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.telephony.SmsMessage;
import android.util.Log;

import java.util.Objects;

public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Objects.equals(intent.getAction(), "android.provider.Telephony.SMS_RECEIVED")) {
            return;
        }

        try {
            Object[] pdus = (Object[]) intent.getExtras().get("pdus");
            if (pdus == null || pdus.length == 0) return;

            String sender = null;
            StringBuilder body = new StringBuilder();

            for (Object pdu : pdus) {
                SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdu);
                if (sender == null) {
                    sender = sms.getDisplayOriginatingAddress();
                }
                body.append(sms.getMessageBody());
            }

            if (sender != null && body.length() > 0) {
                Log.d(TAG, "📩 SMS from: " + sender + " | Body: " + body);

                // Start foreground service to forward
                Intent serviceIntent = new Intent(context, ForwardService.class);
                serviceIntent.putExtra("sender", sender);
                serviceIntent.putExtra("body", body.toString());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error processing SMS", e);
        }
    }
}
