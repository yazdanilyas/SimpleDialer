package com.truecaller.dialer.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.util.Log
import com.truecaller.commons.extensions.getMyContactsContentProviderCursorLoader
import com.truecaller.commons.helpers.MyContactsContentProvider
import com.truecaller.commons.helpers.SimpleContactsHelper
import com.truecaller.commons.helpers.ensureBackgroundThread
import com.truecaller.dialer.models.CallContact

@SuppressLint("NewApi")
class CallManager {
    companion object {
        var call: Call? = null
        var inCallService: InCallService? = null

        fun accept() {
            call?.answer(VideoProfile.STATE_AUDIO_ONLY)
        }

        fun reject() {
            if (call != null) {
                if (call!!.state == Call.STATE_RINGING) {
                    call!!.reject(false, null)
                } else {
                    call!!.disconnect()
                }
            }
        }

        fun registerCallback(callback: Call.Callback) {
            if (call != null) {
                call!!.registerCallback(callback)
            }
        }

        fun unregisterCallback(callback: Call.Callback) {
            call?.unregisterCallback(callback)
        }

        fun getState() = if (call == null) {
            Call.STATE_DISCONNECTED
        } else {
            call!!.state
        }

        fun keypad(c: Char) {
            call?.playDtmfTone(c)
            call?.stopDtmfTone()
        }

        fun mergeCall() {
            call?.conference(call)
        }

        fun getCallContact(context: Context, callback: (CallContact?) -> Unit) {
            val callContact = CallContact("", "", "")
            if (call == null || call!!.details == null || call!!.details!!.handle == null) {
                callback(callContact)
                return
            }

            val uri = Uri.decode(call!!.details.handle.toString())
            if (uri.startsWith("tel:")) {
                val number = uri.substringAfter("tel:")
                callContact.number = number
                callContact.name = SimpleContactsHelper(context).getNameFromPhoneNumber(number)
                callContact.photoUri =
                    SimpleContactsHelper(context).getPhotoUriFromPhoneNumber(number)

                if (callContact.name != callContact.number) {
                    callback(callContact)
                } else {
                    val privateCursor =
                        context.getMyContactsContentProviderCursorLoader().loadInBackground()
                    ensureBackgroundThread {
                        val privateContacts =
                            MyContactsContentProvider.getSimpleContacts(context, privateCursor)
                        val privateContact =
                            privateContacts.firstOrNull { it.phoneNumber == callContact.number }
                        if (privateContact != null) {
                            callContact.name = privateContact.name
                        }
                        callback(callContact)
                    }
                }
            }
        }

        fun getContacts(): List<Call>? {
            return inCallService?.calls
        }
    }
}
