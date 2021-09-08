package com.truecaller.dialer.fragments

import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout
import com.truecaller.commons.adapters.MyRecyclerViewAdapter
import com.truecaller.dialer.activities.SimpleActivity
import com.truecaller.dialer.extensions.config
import com.truecaller.dialer.helpers.Config
import kotlinx.android.synthetic.main.fragment_letters_layout.view.*
import kotlinx.android.synthetic.main.fragment_recents.view.*

abstract class MyViewPagerFragment(context: Context, attributeSet: AttributeSet) :
    RelativeLayout(context, attributeSet) {
    protected var activity: SimpleActivity? = null

    private lateinit var config: Config

    fun setupFragment(activity: SimpleActivity) {
        config = activity.config
        if (this.activity == null) {
            this.activity = activity

            setupFragment()
        }
    }

    fun finishActMode() {
        (fragment_list?.adapter as? MyRecyclerViewAdapter)?.finishActMode()
        (recents_list?.adapter as? MyRecyclerViewAdapter)?.finishActMode()
    }

    abstract fun setupFragment()

    abstract fun textColorChanged(color: Int)

    abstract fun primaryColorChanged(color: Int)
}
