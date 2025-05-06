package com.dzboot.ovpn.custom

import android.content.Context
import android.content.res.Resources
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding


open class ContextViewHolder<D : ViewBinding>(val binding: D) : RecyclerView.ViewHolder(binding.root) {

    val context: Context = itemView.context
    val resources: Resources = itemView.resources


    fun getString(@StringRes resId: Int): String = resources.getString(resId)

    fun getString(@StringRes resId: Int, vararg formatArg: Any?): String = resources.getString(resId, *formatArg)

    fun getColor(@ColorRes resId: Int) = ContextCompat.getColor(context, resId)

    fun getDrawable(@DrawableRes resId: Int) = ContextCompat.getDrawable(context, resId)
}