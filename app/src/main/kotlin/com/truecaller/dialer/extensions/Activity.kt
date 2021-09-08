package com.truecaller.dialer.extensions

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import com.truecaller.commons.extensions.isDefaultDialer
import com.truecaller.commons.extensions.launchCallIntent
import com.truecaller.commons.extensions.telecomManager
import com.truecaller.commons.helpers.PERMISSION_READ_PHONE_STATE
import com.truecaller.dialer.activities.SimpleActivity
import com.truecaller.dialer.dialogs.SelectSIMDialog

fun SimpleActivity.startCallIntent(recipient: String) {
    if (isDefaultDialer()) {
        getHandleToUse(null, recipient) { handle ->
            launchCallIntent(recipient, handle)
        }
    } else {
        launchCallIntent(recipient, null)
    }
}

// used at devices with multiple SIM cards
@SuppressLint("MissingPermission")
fun SimpleActivity.getHandleToUse(
    intent: Intent?,
    phoneNumber: String,
    callback: (handle: PhoneAccountHandle) -> Unit
) {
    handlePermission(PERMISSION_READ_PHONE_STATE) {
        if (it) {
            val defaultHandle =
                telecomManager.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL)
            when {
                intent?.hasExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE) == true -> callback(
                    intent.getParcelableExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE)!!
                )
                config.getCustomSIM(phoneNumber)?.isNotEmpty() == true -> {
                    val storedLabel = Uri.decode(config.getCustomSIM(phoneNumber))
                    val availableSIMs = getAvailableSIMCardLabels()
                    val firstornull = availableSIMs.firstOrNull { it.label == storedLabel }?.handle
                        ?: availableSIMs.first().handle
                    callback(firstornull)
                }
                defaultHandle != null -> callback(defaultHandle)
                else -> {
                    SelectSIMDialog(this, phoneNumber) { handle ->
                        callback(handle)
                    }
                }
            }
        }
    }
}
