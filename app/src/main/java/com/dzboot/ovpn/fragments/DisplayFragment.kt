package com.dzboot.ovpn.fragments

import android.os.Bundle
import android.view.View
import androidx.viewbinding.ViewBinding
import com.dzboot.ovpn.R
import com.dzboot.ovpn.activities.IntroActivity
import com.dzboot.ovpn.base.BaseActivity
import com.dzboot.ovpn.base.BaseFragment
import com.dzboot.ovpn.databinding.FragmentDisplayBinding
import com.dzboot.ovpn.helpers.visible


class DisplayFragment<D : BaseActivity<out ViewBinding>> : BaseFragment<D, FragmentDisplayBinding>() {

	annotation class DisplayType {
		companion object {

			const val STATIC_TAG = "DisplayFragment"
			const val PRIVACY_POLICY = "privacy"
			const val TERMS = "terms"
		}
	}

	override val TAG = STATIC_TAG

	companion object {

		const val TYPE = "type"
		const val STATIC_TAG = "AllowedAppsFragment"
	}

	private val type by lazy { arguments?.get(TYPE) }

	override fun initializeBinding() = FragmentDisplayBinding.inflate(requireActivity().layoutInflater)

	override fun getPageTitle(): Int = when (type) {
		DisplayType.PRIVACY_POLICY -> R.string.privacy_policy
		DisplayType.TERMS -> R.string.terms_of_service
		else -> 0
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		if (activity is IntroActivity) {
			binding.back.visible()
			binding.back.setOnClickListener { activity?.onBackPressAction() }
		}

		val str = activity?.assets?.open("html/$type.html")?.bufferedReader().use { it?.readText() }
			?.replace("[DEV]", getString(R.string.developer_name))
			?.replace("[APP_NAME]", getString(R.string.app_name))
			?.replace("[SUPPORT_EMAIL]", getString(R.string.support_email))
			?.replace("[WEBSITE]", getString(R.string.website))

		str?.let { binding.webview.loadData(it, "text/html; charset=utf-8", "UTF-8") }
	}
}