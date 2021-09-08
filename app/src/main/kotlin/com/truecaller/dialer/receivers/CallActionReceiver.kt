package com.truecaller.dialer.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.truecaller.dialer.helpers.ACCEPT_CALL
import com.truecaller.dialer.helpers.CallManager
import com.truecaller.dialer.helpers.DECLINE_CALL

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACCEPT_CALL -> CallManager.accept()
            DECLINE_CALL -> CallManager.reject()
        }
    }
}
