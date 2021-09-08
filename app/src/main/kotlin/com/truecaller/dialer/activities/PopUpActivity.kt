package com.truecaller.dialer.activities

import android.os.Bundle

import truecaller.caller.callerid.name.phone.dialer.app.R

class PopUpActivity : SimpleActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_popup)
//        mAdView = findViewById(R.id.adView)
//        val adRequest = AdRequest.Builder().build()
//        mAdView.loadAd(adRequest)
    }


}
