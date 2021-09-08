package com.truecaller.dialer.activities

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat
import androidx.viewpager.widget.ViewPager
import com.android.volley.toolbox.Volley
import com.facebook.ads.*
import com.truecaller.commons.extensions.*
import com.truecaller.commons.helpers.*
import com.truecaller.commons.models.FAQItem
import com.truecaller.dialer.adapters.ViewPagerAdapter
import com.truecaller.dialer.extensions.config
import com.truecaller.dialer.fragments.MyViewPagerFragment
import com.truecaller.dialer.helpers.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_contacts.*
import kotlinx.android.synthetic.main.fragment_favorites.*
import kotlinx.android.synthetic.main.fragment_recents.*
import truecaller.caller.callerid.name.phone.dialer.app.R
import java.util.*

class MainActivity : SimpleActivity() {
    private var storedTextColor = 0
    private var storedPrimaryColor = 0
    private var isFirstResume = true
    private var isSearchOpen = false
    private var searchMenuItem: MenuItem? = null
    private val TAG = "facebookAds"
    private lateinit var interstitialAd: InterstitialAd
    private lateinit var adView: AdView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        appLaunched(BuildConfig.APPLICATION_ID)
        setupTabColors()
        loadFbBanner()
        storeStateVariables()

        if (isDefaultDialer()) {
            checkContactPermissions()
        } else {
            launchSetDefaultDialerIntent()
        }

    }

    override fun onResume() {
        super.onResume()
        val dialpadIcon =
            resources.getColoredDrawableWithColor(R.drawable.ic_dialpad_vector, getFABIconColor())
        main_dialpad_button.apply {
            setImageDrawable(dialpadIcon)
            background.applyColorFilter(getAdjustedPrimaryColor())
        }

        main_tabs_holder.setBackgroundColor(config.backgroundColor)

        val configTextColor = config.textColor
        if (storedTextColor != configTextColor) {
            getInactiveTabIndexes(viewpager.currentItem).forEach {
                main_tabs_holder.getTabAt(it)?.icon?.applyColorFilter(configTextColor)
            }

            getAllFragments().forEach {
                it?.textColorChanged(configTextColor)
            }
        }

        val configPrimaryColor = config.primaryColor
        if (storedPrimaryColor != configPrimaryColor) {
            main_tabs_holder.setSelectedTabIndicatorColor(getAdjustedPrimaryColor())
            main_tabs_holder.getTabAt(viewpager.currentItem)?.icon?.applyColorFilter(
                getAdjustedPrimaryColor()
            )
            getAllFragments().forEach {
                it?.primaryColorChanged(configPrimaryColor)
            }
        }

        if (!isFirstResume) {
            refreshItems()
        }

        checkShortcuts()
        isFirstResume = false
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
    }

    override fun onStop() {
        super.onStop()
        searchMenuItem?.collapseActionView()
    }

    override fun onDestroy() {
        super.onDestroy()
        config.lastUsedViewPagerPage = viewpager.currentItem
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        menu.findItem(R.id.search).isVisible = viewpager.currentItem == 0

        setupSearch(menu)
        updateMenuItemColors(menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.settings -> startActivity(Intent(applicationContext, SettingsActivity::class.java))
            R.id.about -> launchAbout()
            R.id.privacyPolicy -> startActivity(
                Intent(
                    applicationContext,
                    WebActivity::class.java
                ).apply {
                    putExtra(PARAM_TITLE, TITLE_PRIVACY_POLCIY)
                    putExtra(PARAM_WEB_URL, PRIVACY_POLICY_URL)
                })
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        // we don't really care about the result, the app can work without being the default Dialer too
        if (requestCode == REQUEST_CODE_SET_DEFAULT_DIALER) {
            checkContactPermissions()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshItems()
    }

    private fun storeStateVariables() {
        config.apply {
            storedTextColor = textColor
            storedPrimaryColor = primaryColor
        }
    }

    private fun checkContactPermissions() {
        handlePermission(PERMISSION_READ_CONTACTS) {
            if (it) {
                handlePermission(PERMISSION_GET_ACCOUNTS) {
                    initFragments()
                }
            } else {
                initFragments()
            }
        }
    }

    private fun setupSearch(menu: Menu) {
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        searchMenuItem = menu.findItem(R.id.search)
        (searchMenuItem!!.actionView as SearchView).apply {
            setSearchableInfo(searchManager.getSearchableInfo(componentName))
            isSubmitButtonEnabled = false
            queryHint = getString(R.string.search)
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String) = false

                override fun onQueryTextChange(newText: String): Boolean {
                    if (isSearchOpen) {
                        contacts_fragment?.onSearchQueryChanged(newText)
                    }
                    return true
                }
            })
        }

        MenuItemCompat.setOnActionExpandListener(
            searchMenuItem,
            object : MenuItemCompat.OnActionExpandListener {
                override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                    isSearchOpen = true
                    main_dialpad_button.beGone()
                    return true
                }

                override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                    contacts_fragment?.onSearchClosed()
                    isSearchOpen = false
                    main_dialpad_button.beVisible()
                    return true
                }
            })
    }

    @SuppressLint("NewApi")
    private fun checkShortcuts() {
        val appIconColor = config.appIconColor
        if (isNougatMR1Plus() && config.lastHandledShortcutColor != appIconColor) {
            val launchDialpad = getLaunchDialpadShortcut(appIconColor)

            try {
                shortcutManager.dynamicShortcuts = listOf(launchDialpad)
                config.lastHandledShortcutColor = appIconColor
            } catch (ignored: Exception) {
            }
        }
    }

    @SuppressLint("NewApi")
    private fun getLaunchDialpadShortcut(appIconColor: Int): ShortcutInfo {
        val newEvent = getString(R.string.dialpad)
        val drawable = resources.getDrawable(R.drawable.shortcut_dialpad)
        (drawable as LayerDrawable).findDrawableByLayerId(R.id.shortcut_dialpad_background)
            .applyColorFilter(appIconColor)
        val bmp = drawable.convertToBitmap()

        val intent = Intent(this, DialpadActivity::class.java)
        intent.action = Intent.ACTION_VIEW
        return ShortcutInfo.Builder(this, "launch_dialpad")
            .setShortLabel(newEvent)
            .setLongLabel(newEvent)
            .setIcon(Icon.createWithBitmap(bmp))
            .setIntent(intent)
            .build()
    }

    private fun setupTabColors() {
        val lastUsedPage = getDefaultTab()
        main_tabs_holder.apply {
            background = ColorDrawable(config.backgroundColor)
            setSelectedTabIndicatorColor(getAdjustedPrimaryColor())
            getTabAt(lastUsedPage)?.select()
            getTabAt(lastUsedPage)?.icon?.applyColorFilter(getAdjustedPrimaryColor())

            getInactiveTabIndexes(lastUsedPage).forEach {
                getTabAt(it)?.icon?.applyColorFilter(config.textColor)
            }
        }
    }

    private fun getInactiveTabIndexes(activeIndex: Int) =
        (0 until tabsList.size).filter { it != activeIndex }

    private fun initFragments() {
        viewpager.offscreenPageLimit = tabsList.size - 1
        viewpager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {
                searchMenuItem?.collapseActionView()
            }

            override fun onPageScrolled(
                position: Int,
                positionOffset: Float,
                positionOffsetPixels: Int
            ) {
            }

            override fun onPageSelected(position: Int) {
                main_tabs_holder.getTabAt(position)?.select()
                getAllFragments().forEach {
                    it?.finishActMode()
                }
                invalidateOptionsMenu()
                if (position == 0 || position == 2)
                    loadFbInterstitial()
            }
        })

        main_tabs_holder.onTabSelectionChanged(
            tabUnselectedAction = {
                it.icon?.applyColorFilter(config.textColor)
            },
            tabSelectedAction = {
                viewpager.currentItem = it.position
                it.icon?.applyColorFilter(getAdjustedPrimaryColor())
            }
        )

        main_tabs_holder.removeAllTabs()
        tabsList.forEachIndexed { index, value ->
            val tab = main_tabs_holder.newTab().setIcon(getTabIcon(index))
            main_tabs_holder.addTab(tab, index, getDefaultTab() == index)
        }

        // selecting the proper tab sometimes glitches, add an extra selector to make sure we have it right
        main_tabs_holder.onGlobalLayout {
            Handler().postDelayed({
                main_tabs_holder.getTabAt(getDefaultTab())?.select()
                invalidateOptionsMenu()
            }, 100L)
        }

        main_dialpad_button.setOnClickListener {
            Intent(applicationContext, DialpadActivity::class.java).apply {
                startActivity(this)
            }
        }
    }

    private fun getTabIcon(position: Int): Drawable {
        val drawableId = when (position) {
            0 -> R.drawable.ic_person_vector
            1 -> R.drawable.ic_star_on_vector
            else -> R.drawable.ic_clock_vector
        }

        return resources.getColoredDrawableWithColor(drawableId, config.textColor)
    }

    private fun refreshItems() {
        if (isDestroyed || isFinishing) {
            return
        }

        if (viewpager.adapter == null) {
            viewpager.adapter = ViewPagerAdapter(this)
            viewpager.currentItem = getDefaultTab()
        }

        contacts_fragment?.refreshItems()
        favorites_fragment?.refreshItems()
        recents_fragment?.refreshItems()
    }

    private fun getAllFragments() = arrayListOf(
        contacts_fragment,
        favorites_fragment,
        recents_fragment
    ).toMutableList() as ArrayList<MyViewPagerFragment?>

    private fun getDefaultTab(): Int {
        return when (config.defaultTab) {
            TAB_LAST_USED -> config.lastUsedViewPagerPage
            TAB_CONTACTS -> 0
            TAB_FAVORITES -> 1
            else -> 2
        }
    }

    private fun launchAbout() {
        val licenses = LICENSE_GLIDE or LICENSE_INDICATOR_FAST_SCROLL

        val faqItems = arrayListOf(
            FAQItem(R.string.faq_2_title_commons, R.string.faq_2_text_commons),
            FAQItem(R.string.faq_6_title_commons, R.string.faq_6_text_commons),
            FAQItem(R.string.faq_7_title_commons, R.string.faq_7_text_commons)
        )

        startAboutActivity(R.string.app_name, licenses, BuildConfig.VERSION_NAME, faqItems, true)
    }

//    private lateinit var mInterstitialAd: InterstitialAd

    //    private fun initInterstitialAd() {
//        mInterstitialAd = InterstitialAd(this)
//        mInterstitialAd.adUnitId = getString(R.string.interstitial_ads)
//        mInterstitialAd.loadAd(AdRequest.Builder().build())
//        setInterstitialAdListener()
//    }
//
//    private fun setInterstitialAdListener() {
//        mInterstitialAd.adListener = object : AdListener() {
//            override fun onAdLoaded() {
//                // Code to be executed when an ad finishes loading.
//                mInterstitialAd.show()
//            }
//
//            override fun onAdFailedToLoad(errorCode: Int) {
//                // Code to be executed when an ad request fails.
//            }
//
//            override fun onAdOpened() {
//                // Code to be executed when the ad is displayed.
//            }
//
//            override fun onAdClicked() {
//                // Code to be executed when the user clicks on an ad.
//                // finish()
//            }
//
//            override fun onAdLeftApplication() {
//                // Code to be executed when the user has left the app.
//                //   finish()
//            }
//
//            override fun onAdClosed() {
//                // Code to be executed when the interstitial ad is closed.
//                //  finish()
//            }
//        }
//    }
    private fun loadFbBanner() {

        adView = AdView(this, getString(R.string.fb_banner_placement_id), AdSize.BANNER_HEIGHT_50)
        // Find the Ad Container
        val adContainer = banner_container as LinearLayout
        // Add the ad view to your activity layout
        adContainer.addView(adView)
        // Request an ad
        val adListener: AdListener = object : AdListener {
            override fun onError(ad: Ad, adError: AdError) {
                // Ad error callback
                Log.d(TAG, "onError: " + adError.errorMessage)
//            Toast.makeText(
//                this@MainActivity,
//                "Error: " + adError.errorMessage,
//                Toast.LENGTH_LONG
//            )
//                .show()
            }

            override fun onAdLoaded(ad: Ad) {
                Log.d("TAG", "onAdLoaded: Ad loaded success")
            }

            override fun onAdClicked(ad: Ad) {
                // Ad clicked callback
            }

            override fun onLoggingImpression(ad: Ad) {
                // Ad impression logged callback
            }
        }
        AdSettings.addTestDevice("d7bc1606-2891-437d-9aa5-ae6de67007de")
        adView.loadAd(adView.buildLoadAdConfig().withAdListener(adListener).build())
    }

    private fun loadFbInterstitial() {
        interstitialAd = InterstitialAd(this, getString(R.string.fb_interstitial_placement_id))
        val interstitialAdListener: InterstitialAdListener = object : InterstitialAdListener {
            override fun onInterstitialDisplayed(ad: Ad) {
                // Interstitial ad displayed callback
                Log.e(TAG, "Interstitial ad displayed.")
            }

            override fun onInterstitialDismissed(ad: Ad) {

                // Interstitial dismissed callback
                Log.e(TAG, "Interstitial ad dismissed.")
            }

            override fun onError(ad: Ad, adError: AdError) {
                // Ad error callback
                Log.e(TAG, "Interstitial ad failed to load: " + adError.errorMessage)
            }

            override fun onAdLoaded(ad: Ad) {
                // Interstitial ad is loaded and ready to be displayed
                Log.d(TAG, "Interstitial ad is loaded and ready to be displayed!")
                // Show the ad
                interstitialAd.show()
            }

            override fun onAdClicked(ad: Ad) {
                // Ad clicked callback
                Log.d(TAG, "Interstitial ad clicked!")
            }

            override fun onLoggingImpression(ad: Ad) {
                // Ad impression logged callback
                Log.d(TAG, "Interstitial ad impression logged!")
            }
        }
        AdSettings.addTestDevice("d7bc1606-2891-437d-9aa5-ae6de67007de")
        interstitialAd.loadAd(
            interstitialAd.buildLoadAdConfig().withAdListener(interstitialAdListener).build()
        )
    }

}
