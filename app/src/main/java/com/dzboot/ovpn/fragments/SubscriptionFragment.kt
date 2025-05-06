package com.dzboot.ovpn.fragments

import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.dzboot.ovpn.R
import com.dzboot.ovpn.activities.MainActivity
import com.dzboot.ovpn.base.BaseFragment
import com.dzboot.ovpn.databinding.FragmentProductsBinding
import com.dzboot.ovpn.helpers.SubscriptionManager
import com.dzboot.ovpn.helpers.invisible
import com.dzboot.ovpn.helpers.visible


class SubscriptionFragment : BaseFragment<MainActivity, FragmentProductsBinding>() {

	companion object {

		const val STATIC_TAG = "SubscriptionFragment"
	}

	override val TAG = STATIC_TAG

	override fun initializeBinding() = FragmentProductsBinding.inflate(requireActivity().layoutInflater)

	override fun getPageTitle(): Int = R.string.subscribe

	private lateinit var monthlySku: SkuDetails
	private lateinit var yearlySku: SkuDetails
	private lateinit var semesterlySku: SkuDetails

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		SubscriptionManager.getInstance()
			.setInitializationCallback(object : SubscriptionManager.InitializationCallback() {
				override fun onInitSuccess() {
					//do nothing
				}

				override fun onInitFail(result: BillingResult) {
					super.onInitFail(result)
					onError()
				}
			}).querySkuDetails(object : SubscriptionManager.QuerySkuDetailsCallback() {
				override fun onSkuResultsOK(skuDetailsList: List<SkuDetails>) {
					if (!isAdded)
						return

					for (skuDetail in skuDetailsList) {
						when (skuDetail.sku) {
							SubscriptionManager.MONTHLY_SUBSCRIPTION -> monthlySku = skuDetail
							SubscriptionManager.SEMESTERLY_SUBSCRIPTION -> semesterlySku = skuDetail
							SubscriptionManager.YEARLY_SUBSCRIPTION -> yearlySku = skuDetail
						}
					}

					binding.subscribePrompt.text = getString(R.string.subscribe_prompt)
					binding.loading.invisible()
					if (this@SubscriptionFragment::monthlySku.isInitialized) {
						binding.monthlySubscribe.text = getString(R.string.monthly_price, monthlySku.price)
						binding.monthlySubscribe.visible()
						binding.subscriptionPrompt.visible()
					}

					if (this@SubscriptionFragment::semesterlySku.isInitialized) {
						binding.semesterlySubscribe.text = getString(R.string.semesterly_price, semesterlySku.price)
						binding.semesterlySubscribe.visible()
						binding.subscriptionPrompt.visible()
					}

					if (this@SubscriptionFragment::yearlySku.isInitialized) {
						binding.yearlySubscribe.text = getString(R.string.yearly_price, yearlySku.price)
						binding.yearlySubscribe.visible()
					}
				}

				override fun onInvalidSkuResult() {
					super.onInvalidSkuResult()
					onError()
				}

				override fun onSkuResultsError(result: BillingResult) {
					super.onSkuResultsError(result)
					onError()
				}
			})

		binding.monthlySubscribe.setOnClickListener { launchPurchase(monthlySku) }
		binding.semesterlySubscribe.setOnClickListener { launchPurchase(semesterlySku) }
		binding.yearlySubscribe.setOnClickListener { launchPurchase(yearlySku) }
	}

	private fun onError() {
		if (!isAdded)
			return

		Toast.makeText(requireContext(), R.string.unknown_error, Toast.LENGTH_SHORT).show()
		activity?.switchToMainScreen()
	}

	private fun launchPurchase(skuDetails: SkuDetails) {
		activity?.let { activity ->
			SubscriptionManager.getInstance()
				.launchPurchaseFlow(activity, skuDetails, object : SubscriptionManager.PurchaseFlowCallback() {
					override fun onPurchaseSuccess(purchases: List<Purchase>) {
						activity.onPurchaseSuccess()
					}

					override fun onPurchaseNone() {
						if (!isAdded)
							return

						Toast.makeText(activity, R.string.purchase_flow_fail, Toast.LENGTH_SHORT).show()
					}

					override fun onPurchaseFail(responseCode: Int) {
						onPurchaseNone()
					}
				})
		}
	}
}