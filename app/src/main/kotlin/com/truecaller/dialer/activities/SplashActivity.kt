package com.truecaller.dialer.activities

import android.content.Intent
import com.truecaller.commons.activities.BaseSplashActivity

class SplashActivity : BaseSplashActivity() {
    override fun initActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }


}
