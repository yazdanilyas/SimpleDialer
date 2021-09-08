package com.truecaller.commons.interfaces

interface CopyMoveListener {
    fun copySucceeded(copyOnly: Boolean, copiedAll: Boolean, destinationPath: String)

    fun copyFailed()
}
