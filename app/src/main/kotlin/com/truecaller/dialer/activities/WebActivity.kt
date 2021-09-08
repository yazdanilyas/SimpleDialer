package com.truecaller.dialer.activities

import android.os.Bundle
import android.webkit.WebView
import truecaller.caller.callerid.name.phone.dialer.app.R
import com.truecaller.dialer.helpers.PARAM_TITLE
import com.truecaller.dialer.helpers.PARAM_WEB_URL

class WebActivity : SimpleActivity() {

    lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_view)
        webView = findViewById(R.id.webView)
        title = intent.getStringExtra(PARAM_TITLE) ?: getString(R.string.app_name)
        webView.loadUrl(intent.getStringExtra(PARAM_WEB_URL) ?: "")
    }


}
