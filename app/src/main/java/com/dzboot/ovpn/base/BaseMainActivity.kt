package com.dzboot.ovpn.base

import androidx.viewbinding.ViewBinding
import com.google.android.gms.ads.nativead.NativeAd


abstract class BaseMainActivity<T : ViewBinding> : BaseActivity<T>() {

    abstract fun updateServersList()
    abstract fun showNativeAd(nativeAd: NativeAd)
    abstract fun showPurchase()
    abstract fun hideNativeAd()
    abstract fun hidePurchase(showVIP: Boolean)
}