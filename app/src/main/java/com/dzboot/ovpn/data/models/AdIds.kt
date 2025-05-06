package com.dzboot.ovpn.data.models

import com.google.gson.annotations.SerializedName


data class AdIds(
    @SerializedName("abi") var abi: String,     //Admob banner
    @SerializedName("aii") var aii: String,     //Admob Interstitial
    @SerializedName("arii") var arii: String,   //Admob Rewarded Interstitial
    @SerializedName("ari") var ari: String,     //Admob Rewarded
    @SerializedName("ani") var ani: String,     //Admob Native
    @SerializedName("aai") var aai: String,     //Admob AppOpen
)
