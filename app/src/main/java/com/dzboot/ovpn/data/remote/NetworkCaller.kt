package com.dzboot.ovpn.data.remote

import android.content.Context
import com.dzboot.ovpn.BuildConfig
import com.dzboot.ovpn.constants.Constants
import com.dzboot.ovpn.custom.BooleanTypeAdapter
import com.dzboot.ovpn.helpers.CipherHelper
import com.google.gson.GsonBuilder

import org.lsposed.lsparanoid.Obfuscate
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.io.File


@Obfuscate
class NetworkCaller {

    companion object {
        private val LOCAL_BASE_URL = "http://192.168.1.100/"
        private val API = "api/v1/"
        val BASE_URL = if (isUsingLocalServer()) LOCAL_BASE_URL else BuildConfig.BASE_URL

        fun isUsingLocalServer() = BuildConfig.BASE_URL.isBlank() || BuildConfig.BASE_URL == "http://.........../"

        // For Singleton instantiation
        @Volatile
        private var api: Api? = null


        fun getApi(context: Context): Api {
            return api ?: synchronized(this) {
                val logging = HttpLoggingInterceptor()
                logging.level =
                    if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE

                val client = OkHttpClient.Builder()
                    .addInterceptor(logging)
                    .addInterceptor {
                        val request = it.request().newBuilder()
                            .addHeader("Authorization", Constants.API_KEY)
                            .addHeader("User-Agent", "OVPN")
                            .url(it.request().url.newBuilder().addQueryParameter("t", CipherHelper.getToken()).build())
                        it.proceed(request.build())
                    }
                    .addInterceptor(CacheInterceptor())
                    .cache(Cache(File(context.cacheDir, "http-cache"), 10 * 1024 * 1024L))
                    .build()

                val gsonBuilder = GsonBuilder()
                gsonBuilder.registerTypeAdapter(Boolean::class.java, BooleanTypeAdapter())

                Retrofit.Builder()
                    .baseUrl("$BASE_URL$API")
                    .addConverterFactory(ScalarsConverterFactory.create())
                    .addConverterFactory(GsonConverterFactory.create(gsonBuilder.create()))
                    .client(client)
                    .build()
                    .create(Api::class.java)
                    .also { api = it }
            }
        }
    }
}