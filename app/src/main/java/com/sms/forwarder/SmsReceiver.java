package com.sms.forwarder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

public class SmsReceiver extends BroadcastReceiver {

    private static final String TAG = "SmsReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {

        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            return;
        }

        Log.d(TAG, "📩 SMS_RECEIVED broadcast received");

        try {
            Bundle extras = intent.getExtras();

            if (extras == null) {
                Log.e(TAG, "❌ Intent extras are null");
                return;
            }

            Object[] pdus = (Object[]) extras.get("pdus");

            if (pdus == null || pdus.length == 0) {
                Log.e(TAG, "❌ No SMS PDUs found");
                return;
            }

            String format = extras.getString("format");

            String sender = null;
            StringBuilder body = new StringBuilder();

            for (Object pdu : pdus) {

                SmsMessage sms;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    sms = SmsMessage.createFromPdu(
                            (byte[]) pdu,
                            format
                    );
                } else {
                    sms = SmsMessage.createFromPdu((byte[]) pdu);
                }

                if (sms == null) {
                    continue;
                }

                if (sender == null) {
                    sender = sms.getDisplayOriginatingAddress();
                }

                body.append(sms.getMessageBody());
            }

            if (sender == null || body.length() == 0) {
                Log.e(TAG, "❌ Sender or SMS body empty");
                return;
            }

            String message = body.toString();

            Log.d(
                    TAG,
                    "📩 SMS from: " + sender +
                    " | Body: " + message
            );

            Intent serviceIntent =
                    new Intent(context, ForwardService.class);

            serviceIntent.putExtra("sender", sender);
            serviceIntent.putExtra("body", message);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                Log.d(
                        TAG,
                        "🚀 Starting ForwardService"
                );

                context.startForegroundService(serviceIntent);

            } else {

                context.startService(serviceIntent);
            }

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "❌ Error processing SMS",
                    e
            );
        }
    }
}
