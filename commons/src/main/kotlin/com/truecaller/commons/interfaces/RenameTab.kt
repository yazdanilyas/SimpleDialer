package com.truecaller.commons.interfaces

import com.truecaller.commons.activities.BaseSimpleActivity

interface RenameTab {
    fun initTab(activity: BaseSimpleActivity, paths: ArrayList<String>)

    fun dialogConfirmed(callback: (success: Boolean) -> Unit)
}
