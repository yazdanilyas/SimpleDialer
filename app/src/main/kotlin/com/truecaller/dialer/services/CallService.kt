package com.truecaller.dialer.services

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import com.truecaller.dialer.activities.CallActivity
import com.truecaller.dialer.helpers.CallList
import com.truecaller.dialer.helpers.CallManager

class CallService : InCallService() {
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        val intent = Intent(this, CallActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        CallManager.call = call
        CallManager.inCallService = this
        //
//        CallList.getInstance().onCallAdded(call)

    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallManager.call = null
        CallManager.inCallService = null
        //
//        CallList.getInstance().onCallRemoved(call)
    }


}
