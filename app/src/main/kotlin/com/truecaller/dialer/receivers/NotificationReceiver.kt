package com.truecaller.dialer.receivers

import android.util.Log
import com.onesignal.OSNotification
import com.onesignal.OneSignal


class NotificationReceiver : OneSignal.NotificationReceivedHandler {


    override fun notificationReceived(notification: OSNotification?) {
        Log.e("ANKUR", "NOO NO")
        val data = notification!!.payload.additionalData
        val shutDownAppKey: String?
        if (data != null) {
            shutDownAppKey = data.optString("shutDownApp", null)
            if (shutDownAppKey != null) {
                if (shutDownAppKey.equals("yes")) {
                    //saveShutDownAppPerference()
                }
            }
        }
    }


}
