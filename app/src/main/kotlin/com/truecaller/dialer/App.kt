package com.truecaller.dialer

import android.app.Application
import com.onesignal.OneSignal
import com.truecaller.commons.extensions.checkAppIconColor
import com.truecaller.commons.extensions.checkUseEnglish
import com.truecaller.dialer.receivers.NotificationReceiver

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        checkUseEnglish()
        checkAppIconColor()
//        MobileAds.initialize(this)
        initOneSignal()
    }

    private fun initOneSignal() {
        // Logging set to help debug issues, remove before releasing your app.
        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE)
        // OneSignal Initialization
        OneSignal.startInit(this)
            .inFocusDisplaying(OneSignal.OSInFocusDisplayOption.Notification)
            .unsubscribeWhenNotificationsAreDisabled(true)
            .setNotificationReceivedHandler(NotificationReceiver())
            .init()
    }

    private fun onewpp() {


    }

}
