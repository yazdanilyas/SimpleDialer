package com.truecaller.dialer.helpers

// shared prefs
const val SPEED_DIAL = "speed_dial"
const val REMEMBER_SIM_PREFIX = "remember_sim_"

const val CONTACTS_TAB_MASK = 1
const val FAVORITES_TAB_MASK = 2
const val RECENTS_TAB_MASK = 4

val tabsList = arrayListOf(CONTACTS_TAB_MASK, FAVORITES_TAB_MASK, RECENTS_TAB_MASK)

private const val PATH = "com.simplemobiletools.dialer.action."
const val ACCEPT_CALL = PATH + "accept_call"
const val DECLINE_CALL = PATH + "decline_call"

const val PARAM_TITLE = "param_title"
const val TITLE_PRIVACY_POLCIY = "Privacy Policy"

const val PARAM_WEB_URL = "web_url"
const val PRIVACY_POLICY_URL =
    "https://truecalleridpolicy.blogspot.com/2020/05/privacy-policy.html?m=1"
