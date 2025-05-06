package com.dzboot.ovpn.fragments

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.dzboot.ovpn.R
import com.dzboot.ovpn.base.BaseFragment
import com.dzboot.ovpn.data.db.AppDB
import com.dzboot.ovpn.data.remote.NetworkCaller
import com.dzboot.ovpn.databinding.FragmentFirstLoadBinding
import com.dzboot.ovpn.helpers.PrefsHelper
import com.dzboot.ovpn.helpers.invisible
import com.dzboot.ovpn.helpers.visible
import com.dzboot.ovpn.activities.IntroActivity
import com.dzboot.ovpn.activities.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber


class FirstLoadFragment : BaseFragment<IntroActivity, FragmentFirstLoadBinding>() {

	companion object {

		const val STATIC_TAG = "FirstLoadFragment"
	}

	override val TAG = STATIC_TAG

	private var isLoadComplete = false

	override fun initializeBinding() = FragmentFirstLoadBinding.inflate(requireActivity().layoutInflater)

	override fun getPageTitle() = 0

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		binding.retry.setOnClickListener {
			it.isEnabled = false
			if (isLoadComplete) {
				val mainActivityIntent = Intent(requireContext(), MainActivity::class.java)
				mainActivityIntent.putExtra(MainActivity.FIRST_RUN, true)
				startActivity(mainActivityIntent)
				activity?.finish()
			} else
				loadServers()
		}
		loadServers()
	}

	private fun loadServers() {
		binding.loading.visible()
		binding.tvLog.text = getString(R.string.first_time_load)

		viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
			try {
				val serversResponse = NetworkCaller.getApi(requireContext()).getServers()
				val responseBody = serversResponse.body()

				if (serversResponse.isSuccessful && responseBody != null) {
					AppDB.getInstance(requireContext()).serversDao().insert(responseBody)
					PrefsHelper.disableFirstRun()
					isLoadComplete = true
					withContext(Dispatchers.Main) {
						binding.loading.invisible()
						binding.tvLog.invisible()
						binding.retry.isEnabled = true
						binding.retry.text = getString(R.string.continue_to_app)
						binding.retry.requestFocus()
					}
				} else throw Exception("Error while loading servers list")
			} catch (e: Exception) {
				Timber.e(e, e.message)
				withContext(Dispatchers.Main) {
					binding.loading.invisible()
					binding.retry.isEnabled = true
					binding.retry.text = getString(R.string.retry)
					binding.retry.requestFocus()
					binding.tvLog.text = getString(R.string.load_servers_error)
				}
			}
		}
	}
}