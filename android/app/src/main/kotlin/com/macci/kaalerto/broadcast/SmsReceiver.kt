package com.macci.kaalerto.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (message in messages) {
                handleSmsMessage(context, message)
            }
        }
    }

    private fun handleSmsMessage(context: Context, message: SmsMessage) {
        // TODO: Parse SMS and insert into event store
        val sender = message.originatingAddress
        val body = message.messageBody

        // Placeholder for SMS parsing
        // This will decode the bit-packed format from BUILD_TASKS.md day 12
    }
}
