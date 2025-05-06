package com.dzboot.ovpn.base

import androidx.annotation.StringRes

interface BaseFragmentInterface {

    val TAG : String

    @StringRes
    fun getPageTitle(): Int
}