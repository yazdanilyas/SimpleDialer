package com.truecaller.dialer.dialogs

import androidx.appcompat.app.AlertDialog
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import com.truecaller.commons.extensions.getColorStateList
import com.truecaller.commons.extensions.getContrastColor
import com.truecaller.commons.extensions.setupDialogStuff
import com.truecaller.commons.models.SimpleContact
import truecaller.caller.callerid.name.phone.dialer.app.R
import com.truecaller.dialer.activities.SimpleActivity
import com.truecaller.dialer.adapters.ContactsAdapter
import com.truecaller.dialer.extensions.config
import kotlinx.android.synthetic.main.dialog_select_contact.view.*
import java.util.*

class SelectContactDialog(
    val activity: SimpleActivity,
    contacts: ArrayList<SimpleContact>,
    val callback: (selectedContact: SimpleContact) -> Unit
) {
    private var dialog: AlertDialog? = null
    private var view = activity.layoutInflater.inflate(R.layout.dialog_select_contact, null)

    init {
        view.apply {
            letter_fastscroller.textColor = context.config.textColor.getColorStateList()
            letter_fastscroller_thumb.setupWithFastScroller(letter_fastscroller)
            letter_fastscroller_thumb.textColor = context.config.primaryColor.getContrastColor()

            letter_fastscroller.setupWithRecyclerView(select_contact_list, { position ->
                try {
                    val name = contacts[position].name
                    val character = if (name.isNotEmpty()) name.substring(0, 1) else ""
                    FastScrollItemIndicator.Text(character.toUpperCase(Locale.getDefault()))
                } catch (e: Exception) {
                    FastScrollItemIndicator.Text("")
                }
            })

            select_contact_list.adapter = ContactsAdapter(activity, contacts, select_contact_list) {
                callback(it as SimpleContact)
                dialog?.dismiss()
            }
        }

        dialog = AlertDialog.Builder(activity)
            .setNegativeButton(R.string.cancel, null)
            .create().apply {
                activity.setupDialogStuff(view, this)
            }
    }
}
