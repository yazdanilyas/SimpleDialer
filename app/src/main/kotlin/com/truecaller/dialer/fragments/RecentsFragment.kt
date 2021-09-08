package com.truecaller.dialer.fragments

import android.content.Context
import android.util.AttributeSet
import com.truecaller.commons.extensions.*
import com.truecaller.commons.helpers.MyContactsContentProvider
import com.truecaller.commons.helpers.PERMISSION_READ_CALL_LOG
import com.truecaller.commons.helpers.SimpleContactsHelper
import truecaller.caller.callerid.name.phone.dialer.app.R
import com.truecaller.dialer.activities.SimpleActivity
import com.truecaller.dialer.adapters.RecentCallsAdapter
import com.truecaller.dialer.extensions.config
import com.truecaller.dialer.helpers.RecentsHelper
import com.truecaller.dialer.interfaces.RefreshItemsListener
import com.truecaller.dialer.models.RecentCall
import kotlinx.android.synthetic.main.fragment_recents.view.*

class RecentsFragment(context: Context, attributeSet: AttributeSet) :
    MyViewPagerFragment(context, attributeSet), RefreshItemsListener {
    override fun setupFragment() {
        val placeholderResId = if (context.hasPermission(PERMISSION_READ_CALL_LOG)) {
            R.string.no_previous_calls
        } else {
            R.string.could_not_access_the_call_history
        }

        recents_placeholder.text = context.getString(placeholderResId)
        recents_placeholder_2.apply {
            setTextColor(context.config.primaryColor)
            underlineText()
            setOnClickListener {
                requestCallLogPermission()
            }
        }
    }

    override fun textColorChanged(color: Int) {
        (recents_list?.adapter as? RecentCallsAdapter)?.apply {
            initDrawables()
            updateTextColor(color)
        }
    }

    override fun primaryColorChanged(color: Int) {}

    override fun refreshItems() {
        val privateCursor = context?.getMyContactsContentProviderCursorLoader()?.loadInBackground()
        RecentsHelper(context).getRecentCalls { recents ->
            SimpleContactsHelper(context).getAvailableContacts(false) { contacts ->
                val privateContacts =
                    MyContactsContentProvider.getSimpleContacts(context, privateCursor)
                recents.filter { it.phoneNumber == it.name }.forEach { recent ->
                    var wasNameFilled = false
                    if (privateContacts.isNotEmpty()) {
                        val privateContact =
                            privateContacts.firstOrNull { it.phoneNumber == recent.phoneNumber }
                        if (privateContact != null) {
                            recent.name = privateContact.name
                            wasNameFilled = true
                        }
                    }

                    if (!wasNameFilled) {
                        val contact = contacts.firstOrNull { it.phoneNumber == recent.phoneNumber }
                        if (contact != null) {
                            recent.name = contact.name
                        }
                    }
                }

                activity?.runOnUiThread {
                    gotRecents(recents)
                }
            }
        }
    }

    private fun gotRecents(recents: ArrayList<RecentCall>) {
        if (recents.isEmpty()) {
            recents_placeholder.beVisible()
            recents_placeholder_2.beVisibleIf(!context.hasPermission(PERMISSION_READ_CALL_LOG))
            recents_list.beGone()
        } else {
            recents_placeholder.beGone()
            recents_placeholder_2.beGone()
            recents_list.beVisible()

            val currAdapter = recents_list.adapter
            if (currAdapter == null) {
                RecentCallsAdapter(activity as SimpleActivity, recents, recents_list, this) {
                    activity?.launchCallIntent((it as RecentCall).phoneNumber)
                }.apply {
                    recents_list.adapter = this
                }
            } else {
                (currAdapter as RecentCallsAdapter).updateItems(recents)
            }
        }
    }

    private fun requestCallLogPermission() {
        activity?.handlePermission(PERMISSION_READ_CALL_LOG) {
            if (it) {
                recents_placeholder.text = context.getString(R.string.no_previous_calls)
                recents_placeholder_2.beGone()

                RecentsHelper(context).getRecentCalls { recents ->
                    activity?.runOnUiThread {
                        gotRecents(recents)
                    }
                }
            }
        }
    }
}
