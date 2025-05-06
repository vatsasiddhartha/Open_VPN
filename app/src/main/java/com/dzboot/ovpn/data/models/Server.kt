package com.dzboot.ovpn.data.models

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.core.content.res.ResourcesCompat
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.dzboot.ovpn.R
import com.dzboot.ovpn.helpers.PrefsHelper
import com.flags.CountryUtils
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import java.io.Serializable


@Entity(tableName = "servers")
data class Server(
    @PrimaryKey var id: Int = AUTO_ID,
    @SerializedName("country_code") var countryCode: String = AUTO,
    @SerializedName("order") var order: Int = 0,
    @SerializedName("free_connect_duration") var freeConnectDuration: Int = 0,
    @SerializedName("ip") var ip: String = "",
    @SerializedName("port") var port: Int = 0,
    @SerializedName("vpn_username") var username: String = "",
    @SerializedName("vpn_password") var password: String = "",
    @SerializedName("city") var city: String = "",
    @SerializedName("connected_devices") var connectedDevices: Int = 0,
    @SerializedName("use_file") var useFile: Boolean = false,
    @SerializedName("protocol") var protocol: String = ConnectionProtocol.UDP,
    @SerializedName("is_free") var isFree: Boolean = true,
) : Serializable {

    companion object {

        const val AUTO_ID = -1
        const val DISTANCE = "distance"
        const val LOAD = "load"
        const val RANDOM = "random"
        const val AUTO = "auto"

        private val AUTO_MODES = linkedMapOf(
            DISTANCE to R.string.auto_mode_distance,
            LOAD to R.string.auto_mode_load,
            RANDOM to R.string.auto_mode_random
        )

        fun getAutoModeEntries(context: Context) = AUTO_MODES.map { context.getString(it.value) }.toTypedArray()

        fun getAutoModeValues() = AUTO_MODES.keys.toTypedArray()

        fun getAutoModeString(context: Context, mode: String?) =
            context.getString(AUTO_MODES[mode] ?: R.string.auto_mode_load)

        fun auto(): Server {
            return Server()
        }
    }

    annotation class ConnectionProtocol {

        companion object {

            var UDP = "udp"
            var TCP = "tcp"
        }
    }

    @Expose(serialize = false, deserialize = false)
    var ping: Long = 0

    @Expose(serialize = false, deserialize = false)
    @Ignore
    var selected = false

    fun getFlagDrawable(context: Context, autoDrawable: Drawable) =
        if (isAuto())
            autoDrawable
        else ResourcesCompat.getDrawable(
            context.resources,
            CountryUtils.getFlagResIDFromCountryCode(context, countryCode).let {
                if (it == 0)
                    R.drawable.ic_drawer
                else
                    it
            },
            null
        )

    @DrawableRes
    fun getFlagResId(context: Context, @DrawableRes autoDrawableId: Int) =
        if (isAuto()) autoDrawableId
        else CountryUtils.getFlagResIDFromCountryCode(context, countryCode)

    fun getLocationName(context: Context): String =
        if (isAuto()) context.getString(R.string.auto)
        else CountryUtils.getLocalizedNameFromCountryCode(PrefsHelper.getLanguage(), countryCode)

    fun isAuto() = countryCode == AUTO

    fun getProfileName(): String = countryCode + order

    override fun equals(other: Any?): Boolean {
        if (other !is Server)
            return false

        return countryCode == other.countryCode
    }

    override fun hashCode(): Int {
        return countryCode.hashCode()
    }
}
