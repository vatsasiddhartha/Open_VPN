package com.dzboot.ovpn.data.remote

import com.dzboot.ovpn.BuildConfig
import com.dzboot.ovpn.data.models.AdIds
import com.dzboot.ovpn.data.models.Server
import com.dzboot.ovpn.data.models.Setting
import com.dzboot.ovpn.helpers.SubscriptionManager
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.*


interface Api {

    @GET("get-setting")
    suspend fun getSetting(@Query("key") key: String): Response<Setting>

    @GET("get-ad-ids")
    fun getAdIds(): Call<AdIds>

    @GET("list")
    @Headers("Cache-Control: no-cache")
    suspend fun getServers(
        @Query("p") appId: String = BuildConfig.APPLICATION_ID,
        @Query("v") version: Int = BuildConfig.VERSION_CODE
    ): Response<List<Server>>

    @GET("best")
    @Headers("Cache-Control: no-cache")
    suspend fun getBestServerId(
        @Query("p") appId: String = BuildConfig.APPLICATION_ID,
        @Query("v") version: Int = BuildConfig.VERSION_CODE,
        @Query("s") subscribed: Int = if (SubscriptionManager.getInstance().isSubscribed) 1 else 0
    ): Response<Int>

    @GET("get")
    suspend fun getServer(@Query("id") serverId: Int): Response<String>

    @GET("request-ip")
    @Headers("Cache-Control: no-cache")
    suspend fun getIp(): Response<Setting>

    @FormUrlEncoded
    @POST("log-connect")
    @Headers("Cache-Control: no-cache")
    fun logConnect(
        @Field("server_id") serverId: Int,
        @Field("device_id") deviceId: String,
        @Field("ip") ip: String,
        @Field("is_auto") isAuto: Boolean
    ): Call<Void>

    @FormUrlEncoded
    @POST("log-disconnect")
    @Headers("Cache-Control: no-cache")
    fun logDisconnect(
        @Field("server_id") serverId: Int,
        @Field("device_id") deviceId: String
    ): Call<Void>
}