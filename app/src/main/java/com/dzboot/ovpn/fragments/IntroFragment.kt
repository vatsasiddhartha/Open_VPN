package com.dzboot.ovpn.fragments

import android.os.Bundle
import android.view.View
import com.dzboot.ovpn.base.BaseFragment
import com.dzboot.ovpn.databinding.FragmentIntroBinding
import com.dzboot.ovpn.activities.IntroActivity


class IntroFragment : BaseFragment<IntroActivity, FragmentIntroBinding>() {

    companion object {

        const val STATIC_TAG = "IntroFragment"
    }

    override val TAG = STATIC_TAG

    override fun initializeBinding() = FragmentIntroBinding.inflate(requireActivity().layoutInflater)

    override fun getPageTitle() = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.continueToApp.setOnClickListener { activity?.changeScreen(FirstLoadFragment()) }

        binding.privacyInnerLayout.setOnClickListener {
            activity?.changeToDisplayFragment(DisplayFragment.DisplayType.PRIVACY_POLICY)
        }

        binding.termsInnerLayout.setOnClickListener {
            activity?.changeToDisplayFragment(DisplayFragment.DisplayType.TERMS)
        }

        binding.close.setOnClickListener { activity?.finishAffinity() }
    }
}