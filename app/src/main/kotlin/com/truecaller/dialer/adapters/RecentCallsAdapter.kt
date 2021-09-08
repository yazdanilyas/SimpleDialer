package com.truecaller.dialer.adapters

import android.content.Intent
import android.graphics.drawable.Drawable
import android.provider.CallLog.Calls
import android.text.TextUtils
import android.util.TypedValue
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.truecaller.commons.adapters.MyRecyclerViewAdapter
import com.truecaller.commons.dialogs.ConfirmationDialog
import com.truecaller.commons.extensions.*
import com.truecaller.commons.helpers.*
import com.truecaller.commons.views.MyRecyclerView
import truecaller.caller.callerid.name.phone.dialer.app.R
import com.truecaller.dialer.activities.SimpleActivity
import com.truecaller.dialer.extensions.areMultipleSIMsAvailable
import com.truecaller.dialer.helpers.RecentsHelper
import com.truecaller.dialer.interfaces.RefreshItemsListener
import com.truecaller.dialer.models.RecentCall
import kotlinx.android.synthetic.main.item_recent_call.view.*
import java.util.*

class RecentCallsAdapter(
    activity: SimpleActivity,
    var recentCalls: ArrayList<RecentCall>,
    recyclerView: MyRecyclerView,
    val refreshItemsListener: RefreshItemsListener,
    itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, null, itemClick) {

    private lateinit var outgoingCallIcon: Drawable
    private lateinit var incomingCallIcon: Drawable
    private lateinit var incomingMissedCallIcon: Drawable
    private var fontSize = activity.getTextSize()
    private val areMultipleSIMsAvailable = activity.areMultipleSIMsAvailable()
    private val redColor = resources.getColor(R.color.md_red_700)

    init {
        initDrawables()
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_recent_calls

    override fun prepareActionMode(menu: Menu) {
        menu.apply {
            findItem(R.id.cab_block_number).isVisible = isNougatPlus()
            findItem(R.id.cab_add_number).isVisible = isOneItemSelected()
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_block_number -> askConfirmBlock()
            R.id.cab_add_number -> addNumberToContact()
            R.id.cab_remove -> askConfirmRemove()
        }
    }

    override fun getSelectableItemCount() = recentCalls.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = recentCalls.getOrNull(position)?.id

    override fun getItemKeyPosition(key: Int) = recentCalls.indexOfFirst { it.id == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        createViewHolder(R.layout.item_recent_call, parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val recentCall = recentCalls[position]
        holder.bindView(recentCall, true, true) { itemView, layoutPosition ->
            setupView(itemView, recentCall)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = recentCalls.size

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            Glide.with(activity).clear(holder.itemView.item_recents_image)
        }
    }

    fun initDrawables() {
        outgoingCallIcon = resources.getColoredDrawableWithColor(
            R.drawable.ic_outgoing_call_vector,
            baseConfig.textColor
        )
        incomingCallIcon = resources.getColoredDrawableWithColor(
            R.drawable.ic_incoming_call_vector,
            baseConfig.textColor
        )
        incomingMissedCallIcon =
            resources.getColoredDrawableWithColor(R.drawable.ic_incoming_call_vector, redColor)
    }

    private fun askConfirmBlock() {
        val numbers = TextUtils.join(
            ", ",
            getSelectedItems().distinctBy { it.phoneNumber }.map { it.phoneNumber })
        val baseString = R.string.block_confirmation
        val question = String.format(resources.getString(baseString), numbers)

        ConfirmationDialog(activity, question) {
            blockNumbers()
        }
    }

    private fun blockNumbers() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val callsToBlock = getSelectedItems()
        val positions = getSelectedItemPositions()
        recentCalls.removeAll(callsToBlock)

        ensureBackgroundThread {
            callsToBlock.map { it.phoneNumber }.forEach { number ->
                activity.addBlockedNumber(number)
            }

            activity.runOnUiThread {
                removeSelectedItems(positions)
                finishActMode()
            }
        }
    }

    private fun addNumberToContact() {
        val recentCall = getSelectedItems().firstOrNull() ?: return
        Intent().apply {
            action = Intent.ACTION_INSERT_OR_EDIT
            type = "vnd.android.cursor.item/contact"
            putExtra(KEY_PHONE, recentCall.phoneNumber)

            if (resolveActivity(activity.packageManager) != null) {
                activity.startActivity(this)
            } else {
                activity.toast(R.string.no_app_found)
            }
        }
    }

    private fun askConfirmRemove() {
        ConfirmationDialog(activity, activity.getString(R.string.remove_confirmation)) {
            activity.handlePermission(PERMISSION_WRITE_CALL_LOG) {
                removeRecents()
            }
        }
    }

    private fun removeRecents() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val callsToRemove = getSelectedItems()
        val positions = getSelectedItemPositions()
        val idsToRemove = ArrayList<Int>()
        callsToRemove.forEach {
            idsToRemove.add(it.id)
            it.neighbourIDs.mapTo(idsToRemove, { it })
        }

        RecentsHelper(activity).removeRecentCalls(idsToRemove) {
            recentCalls.removeAll(callsToRemove)
            activity.runOnUiThread {
                if (recentCalls.isEmpty()) {
                    refreshItemsListener.refreshItems()
                    finishActMode()
                } else {
                    removeSelectedItems(positions)
                }
            }
        }
    }

    fun updateItems(newItems: ArrayList<RecentCall>) {
        if (newItems.hashCode() != recentCalls.hashCode()) {
            recentCalls = newItems.clone() as ArrayList<RecentCall>
            notifyDataSetChanged()
            finishActMode()
        }
    }

    private fun getSelectedItems() =
        recentCalls.filter { selectedKeys.contains(it.id) } as ArrayList<RecentCall>

    private fun setupView(view: View, call: RecentCall) {
        view.apply {
            item_recents_frame.isSelected = selectedKeys.contains(call.id)
            var nameToShow = call.name
            if (call.neighbourIDs.isNotEmpty()) {
                nameToShow += " (${call.neighbourIDs.size + 1})"
            }

            item_recents_name.apply {
                text = nameToShow
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
            }

            item_recents_date_time.apply {
                text = call.startTS.formatDateOrTime(context, true)
                setTextColor(if (call.type == Calls.MISSED_TYPE) redColor else textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.8f)
            }

            item_recents_duration.apply {
                text = call.duration.getFormattedDuration()
                setTextColor(textColor)
                beVisibleIf(call.type != Calls.MISSED_TYPE && call.type != Calls.REJECTED_TYPE && call.duration > 0)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.8f)
            }

            item_recents_sim_image.beVisibleIf(areMultipleSIMsAvailable)
            item_recents_sim_id.beVisibleIf(areMultipleSIMsAvailable)
            if (areMultipleSIMsAvailable) {
                item_recents_sim_image.applyColorFilter(textColor)
                item_recents_sim_id.setTextColor(textColor.getContrastColor())
                item_recents_sim_id.text = call.simID.toString()
            }

            SimpleContactsHelper(context).loadContactImage(
                call.photoUri,
                item_recents_image,
                call.name
            )

            val drawable = when (call.type) {
                Calls.OUTGOING_TYPE -> outgoingCallIcon
                Calls.MISSED_TYPE -> incomingMissedCallIcon
                else -> incomingCallIcon
            }

            item_recents_type.setImageDrawable(drawable)
        }
    }
}
