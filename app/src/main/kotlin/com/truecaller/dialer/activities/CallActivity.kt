package com.truecaller.dialer.activities

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.PowerManager
import android.provider.MediaStore
import android.telecom.Call
import android.telecom.CallAudioState
import android.util.Log
import android.util.Size
import android.view.WindowManager
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat

import com.truecaller.commons.extensions.*
import com.truecaller.commons.helpers.MINUTE_SECONDS
import com.truecaller.commons.helpers.isOreoMr1Plus
import com.truecaller.commons.helpers.isOreoPlus
import com.truecaller.commons.helpers.isQPlus
import com.truecaller.dialer.extensions.addCharacter
import com.truecaller.dialer.extensions.audioManager
import com.truecaller.dialer.extensions.config
import com.truecaller.dialer.extensions.getHandleToUse
import com.truecaller.dialer.helpers.ACCEPT_CALL
import com.truecaller.dialer.helpers.CallManager
import com.truecaller.dialer.helpers.DECLINE_CALL
import com.truecaller.dialer.models.CallContact
import com.truecaller.dialer.receivers.CallActionReceiver
import kotlinx.android.synthetic.main.activity_call.*
import kotlinx.android.synthetic.main.dialpad.*
import truecaller.caller.callerid.name.phone.dialer.app.R
import java.util.*

class CallActivity : SimpleActivity() {
    private val CALL_NOTIFICATION_ID = 1

    private var isSpeakerOn = false
    private var isMicrophoneOn = true
    private var isCallEnded = false
    private var callDuration = 0
    private var callContact: CallContact? = null
    private var callContactAvatar: Bitmap? = null
    private var proximityWakeLock: PowerManager.WakeLock? = null
    private var callTimer = Timer()

    override fun onCreate(savedInstanceState: Bundle?) {
        supportActionBar?.hide()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)

        updateTextColors(call_holder)
        initButtons()
//        initInterstitialAd()
        audioManager.mode = AudioManager.MODE_IN_CALL
        CallManager.getCallContact(applicationContext) { contact ->
            callContact = contact
            callContactAvatar = getCallContactAvatar()
            runOnUiThread {
                setupNotification()
                updateOtherPersonsInfo()
                checkCalledSIMCard()
            }
        }

        addLockScreenFlags()
        initProximitySensor()

        CallManager.registerCallback(callCallback)
        updateCallState(CallManager.getState())
    }


    override fun onDestroy() {
        super.onDestroy()
        notificationManager.cancel(CALL_NOTIFICATION_ID)
        CallManager.unregisterCallback(callCallback)
        callTimer.cancel()
        if (proximityWakeLock?.isHeld == true) {
            proximityWakeLock!!.release()
        }

        if (CallManager.getState() == Call.STATE_DIALING) {
            endCall()
        }
    }

    override fun onBackPressed() {
        if (dialpad_wrapper.isVisible()) {
            dialpad_wrapper.beGone()
            return
        } else {
            super.onBackPressed()
        }

        if (CallManager.getState() == Call.STATE_DIALING) {
            endCall()
        }
    }

    private fun initButtons() {

        addCallBtn.setOnClickListener {
            launchDialer()
        }
        call_merge.setOnClickListener {
            mergeCall()
        }
        call_decline.setOnClickListener {
            endCall()
        }

        call_accept.setOnClickListener {
            acceptCall()
        }

        call_toggle_microphone.setOnClickListener {
            toggleMicrophone()
        }

        call_toggle_speaker.setOnClickListener {
            toggleSpeaker()
        }

        call_dialpad.setOnClickListener {
            toggleDialpadVisibility()
        }

        dialpad_close.setOnClickListener {
            dialpad_wrapper.beGone()
        }

        call_end.setOnClickListener {
            endCall()
        }

        dialpad_0_holder.setOnClickListener { dialpadPressed('0') }
        dialpad_1.setOnClickListener { dialpadPressed('1') }
        dialpad_2.setOnClickListener { dialpadPressed('2') }
        dialpad_3.setOnClickListener { dialpadPressed('3') }
        dialpad_4.setOnClickListener { dialpadPressed('4') }
        dialpad_5.setOnClickListener { dialpadPressed('5') }
        dialpad_6.setOnClickListener { dialpadPressed('6') }
        dialpad_7.setOnClickListener { dialpadPressed('7') }
        dialpad_8.setOnClickListener { dialpadPressed('8') }
        dialpad_9.setOnClickListener { dialpadPressed('9') }

        dialpad_0_holder.setOnLongClickListener { dialpadPressed('+'); true }
        dialpad_asterisk.setOnClickListener { dialpadPressed('*') }
        dialpad_hashtag.setOnClickListener { dialpadPressed('#') }

        dialpad_wrapper.setBackgroundColor(config.backgroundColor)
        arrayOf(
            call_toggle_microphone,
            call_toggle_speaker,
            call_dialpad,
            dialpad_close,
            call_sim_image
        ).forEach {
            it.applyColorFilter(config.textColor)
        }

        call_sim_id.setTextColor(config.textColor.getContrastColor())
    }

//    private lateinit var mInterstitialAd: InterstitialAd

//    private fun initInterstitialAd() {
//        mInterstitialAd = InterstitialAd(this)
//        mInterstitialAd.adUnitId = getString(R.string.interstitial_ads)
//        mInterstitialAd.loadAd(AdRequest.Builder().build())
//        setInterstitialAdListener()
//    }
//
//    private fun setInterstitialAdListener() {
//        mInterstitialAd.adListener = object : AdListener() {
//            override fun onAdLoaded() {
//                // Code to be executed when an ad finishes loading.
//            }
//
//            override fun onAdFailedToLoad(errorCode: Int) {
//                // Code to be executed when an ad request fails.
//            }
//
//            override fun onAdOpened() {
//                // Code to be executed when the ad is displayed.
//            }
//
//            override fun onAdClicked() {
//                // Code to be executed when the user clicks on an ad.
//                finish()
//            }
//
//            override fun onAdLeftApplication() {
//                // Code to be executed when the user has left the app.
//                finish()
//            }
//
//            override fun onAdClosed() {
//                // Code to be executed when the interstitial ad is closed.
//                finish()
//            }
//        }
//    }


    private fun dialpadPressed(char: Char) {
        CallManager.keypad(char)
        dialpad_input.addCharacter(char)
    }

    private fun toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn
        val drawable =
            if (isSpeakerOn) R.drawable.ic_speaker_on_vector else R.drawable.ic_speaker_off_vector
        call_toggle_speaker.setImageDrawable(getDrawable(drawable))
        audioManager.isSpeakerphoneOn = isSpeakerOn

        val newRoute =
            if (isSpeakerOn) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
        CallManager.inCallService?.setAudioRoute(newRoute)
    }

    private fun toggleMicrophone() {
        isMicrophoneOn = !isMicrophoneOn
        val drawable =
            if (isMicrophoneOn) R.drawable.ic_microphone_vector else R.drawable.ic_microphone_off_vector
        call_toggle_microphone.setImageDrawable(getDrawable(drawable))
        audioManager.isMicrophoneMute = !isMicrophoneOn
        CallManager.inCallService?.setMuted(!isMicrophoneOn)
    }

    private fun toggleDialpadVisibility() {
        if (dialpad_wrapper.isVisible()) {
            dialpad_wrapper.beGone()
        } else {
            dialpad_wrapper.beVisible()
        }
    }

    private fun updateOtherPersonsInfo() {
        if (callContact == null) {
            return
        }

        caller_name_label.text =
            if (callContact!!.name.isNotEmpty()) callContact!!.name else getString(R.string.unknown_caller)

        if (callContactAvatar != null) {
            caller_avatar.setImageBitmap(callContactAvatar)
        }
    }

    @SuppressLint("MissingPermission")
    private fun checkCalledSIMCard() {
        try {
            val accounts = telecomManager.callCapablePhoneAccounts
            if (accounts.size > 1) {
                accounts.forEachIndexed { index, account ->
                    if (account == CallManager.call?.details?.accountHandle) {
                        call_sim_id.text = "${index + 1}"
                        call_sim_id.beVisible()
                        call_sim_image.beVisible()
                    }
                }
            }
        } catch (ignored: Exception) {
        }
    }

    private fun updateCallState(state: Int) {
        when (state) {
            Call.STATE_RINGING -> callRinging()
            Call.STATE_ACTIVE -> callStarted()
            Call.STATE_DISCONNECTED -> endCall()
            Call.STATE_CONNECTING, Call.STATE_DIALING -> initOutgoingCallUI()
            Call.STATE_SELECT_PHONE_ACCOUNT -> showPhoneAccountPicker()
        }

        if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) {
            callTimer.cancel()
        }

        val statusTextId = when (state) {
            Call.STATE_RINGING -> R.string.is_calling
            Call.STATE_DIALING -> R.string.dialing
            else -> 0
        }

        if (statusTextId != 0) {
            call_status_label.text = getString(statusTextId)
        }

        setupNotification()
    }

    private fun acceptCall() {
        CallManager.accept()
    }

    private fun initOutgoingCallUI() {
        incoming_call_holder.beGone()
        ongoing_call_holder.beVisible()
    }

    private fun callRinging() {
        incoming_call_holder.beVisible()
    }

    private fun callStarted() {
        incoming_call_holder.beGone()
        ongoing_call_holder.beVisible()
        try {
            callTimer.scheduleAtFixedRate(getCallTimerUpdateTask(), 1000, 1000)
        } catch (ignored: Exception) {
        }
    }

    private fun showPhoneAccountPicker() {
        getHandleToUse(intent, callContact!!.number) { handle ->
            CallManager.call?.phoneAccountSelected(handle, false)
        }
    }

    private fun endCall() {
        CallManager.reject()
        if (proximityWakeLock?.isHeld == true) {
            proximityWakeLock!!.release()
        }

        if (isCallEnded) {
            finish()
            return
        }

        try {
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (ignored: Exception) {
        }

        isCallEnded = true
        if (callDuration > 0) {
            runOnUiThread {
                call_status_label.text =
                    "${callDuration.getFormattedDuration()} (${getString(R.string.call_ended)})"
                Handler().postDelayed({
                    //AJ CHANGE
                    goToNextScreen()
                    //finish()
                }, 2000)
            }
        } else {
            call_status_label.text = getString(R.string.call_ended)
            finish()
        }
    }

    private fun getCallTimerUpdateTask() = object : TimerTask() {
        override fun run() {
            callDuration++
            runOnUiThread {
                if (!isCallEnded) {
                    call_status_label.text = callDuration.getFormattedDuration()
                }
            }
        }
    }

    @SuppressLint("NewApi")
    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            updateCallState(state)
        }
    }

    @SuppressLint("NewApi")
    private fun addLockScreenFlags() {
        if (isOreoMr1Plus()) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }

        if (isOreoPlus()) {
            (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).requestDismissKeyguard(
                this,
                null
            )
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        }
    }

    private fun initProximitySensor() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        proximityWakeLock = powerManager.newWakeLock(
            PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
            "com.simplemobiletools.dialer.pro:wake_lock"
        )
        proximityWakeLock!!.acquire(10 * MINUTE_SECONDS * 1000L)
    }

    @SuppressLint("NewApi")
    private fun setupNotification() {
        val callState = CallManager.getState()
        val channelId = "simple_dialer_call"
        if (isOreoPlus()) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val name = "call_notification_channel"

            NotificationChannel(channelId, name, importance).apply {
                setSound(null, null)
                notificationManager.createNotificationChannel(this)
            }
        }

        val openAppIntent = Intent(this, CallActivity::class.java)
        openAppIntent.flags = Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
        val openAppPendingIntent = PendingIntent.getActivity(this, 0, openAppIntent, 0)

        val acceptCallIntent = Intent(this, CallActionReceiver::class.java)
        acceptCallIntent.action = ACCEPT_CALL
        val acceptPendingIntent =
            PendingIntent.getBroadcast(this, 0, acceptCallIntent, PendingIntent.FLAG_CANCEL_CURRENT)

        val declineCallIntent = Intent(this, CallActionReceiver::class.java)
        declineCallIntent.action = DECLINE_CALL
        val declinePendingIntent =
            PendingIntent.getBroadcast(
                this,
                1,
                declineCallIntent,
                PendingIntent.FLAG_CANCEL_CURRENT
            )

        val callerName =
            if (callContact != null && callContact!!.name.isNotEmpty()) callContact!!.name else getString(
                R.string.unknown_caller
            )
        val contentTextId = when (callState) {
            Call.STATE_RINGING -> R.string.is_calling
            Call.STATE_DIALING -> R.string.dialing
            Call.STATE_DISCONNECTED -> R.string.call_ended
            Call.STATE_DISCONNECTING -> R.string.call_ending
            else -> R.string.ongoing_call
        }

        val collapsedView = RemoteViews(packageName, R.layout.call_notification).apply {
            setText(R.id.notification_caller_name, callerName)
            setText(R.id.notification_call_status, getString(contentTextId))
            setVisibleIf(R.id.notification_accept_call, callState == Call.STATE_RINGING)

            setOnClickPendingIntent(R.id.notification_decline_call, declinePendingIntent)
            setOnClickPendingIntent(R.id.notification_accept_call, acceptPendingIntent)

            if (callContactAvatar != null) {
                setImageViewBitmap(
                    R.id.notification_thumbnail,
                    getCircularBitmap(callContactAvatar!!)
                )
            }
        }

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_phone_vector)
            .setContentIntent(openAppPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(Notification.CATEGORY_CALL)
            .setCustomContentView(collapsedView)
            .setOngoing(true)
            .setSound(null)
            .setUsesChronometer(callState == Call.STATE_ACTIVE)
            .setChannelId(channelId)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())

        val notification = builder.build()
        notificationManager.notify(CALL_NOTIFICATION_ID, notification)
    }

    @SuppressLint("NewApi")
    private fun getCallContactAvatar(): Bitmap? {
        var bitmap: Bitmap? = null
        if (callContact?.photoUri?.isNotEmpty() == true) {
            val photoUri = Uri.parse(callContact!!.photoUri)
            try {
                bitmap = if (isQPlus()) {
                    val tmbSize = resources.getDimension(R.dimen.list_avatar_size).toInt()
                    contentResolver.loadThumbnail(photoUri, Size(tmbSize, tmbSize), null)
                } else {
                    MediaStore.Images.Media.getBitmap(contentResolver, photoUri)
                }

                bitmap = getCircularBitmap(bitmap!!)
            } catch (ignored: Exception) {
                return null
            }
        }

        return bitmap
    }

    private fun getCircularBitmap(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.width, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()
        val rect = Rect(0, 0, bitmap.width, bitmap.height)
        val radius = bitmap.width / 2.toFloat()

        paint.isAntiAlias = true
        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawCircle(radius, radius, radius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)
        return output
    }

    private fun goToNextScreen() {
//        if (mInterstitialAd.isLoaded) {
//            mInterstitialAd.show()
//        } else {
        finish()
        Log.d("TAG", "The interstitial wasn't loaded yet.")
//        }
    }

    private fun mergeCall() {
        CallManager.mergeCall()
    }
//    public void mergeCall() {
//
//        final CallList calls = CallList.getInstance();
//        CallHelper activeCall = calls.getActiveCall();
//
//        if (activeCall != null) {
//
//            final boolean canMerge = activeCall.can(
//                    android.telecom.Call.Details.CAPABILITY_MERGE_CONFERENCE);
//            final boolean canSwap = activeCall.can(
//                    android.telecom.Call.Details.CAPABILITY_SWAP_CONFERENCE);
//            // (2) Attempt actions on conference calls
//            if (canMerge) {
//                TelecomAdapter.getInstance().merge(activeCall.getId());
//
//            } else if (canSwap) {
//                TelecomAdapter.getInstance().swap(activeCall.getId());
//            }
//        }
//    }

    private fun launchDialer() {
        startActivity(Intent(this, DialpadActivity::class.java))
    }

}
