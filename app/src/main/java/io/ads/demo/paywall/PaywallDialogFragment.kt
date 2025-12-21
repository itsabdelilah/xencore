package io.ads.demo.paywall

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import io.ads.demo.databinding.FragmentDialogPaywallBinding
import io.ads.mediation.billing.PurchaseState
import io.ads.mediation.billing.SubscriptionManager
import io.ads.mediation.billing.SubscriptionState
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Modern paywall dialog with free trial
 *
 * Features:
 * - Single monthly subscription with free trial
 * - Google Play Billing integration
 * - Restore purchase functionality
 * - Direct singleton access (no ViewModel)
 */
class PaywallDialogFragment : DialogFragment() {

    companion object {
        private const val TAG = "PaywallDialogFragment"

        fun newInstance(): PaywallDialogFragment {
            return PaywallDialogFragment()
        }
    }

    private var _binding: FragmentDialogPaywallBinding? = null
    private val binding get() = _binding!!

    // Callback for successful subscription
    var onSubscriptionSuccess: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDialogPaywallBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Reset purchase state to prevent stale state issues
        SubscriptionManager.resetPurchaseState()

        setupViews()
        observeStates()
    }

    /**
     * Setup UI views and click listeners
     */
    private fun setupViews() {
        // Update pricing info
        updatePricingInfo()

        // Start trial / Subscribe button click
        binding.startTrialButton.setOnClickListener {
            launchPurchaseFlow()
        }

        // Restore button click
        binding.restoreButton.setOnClickListener {
            restorePurchases()
        }

        // Close button click
        binding.closeButton.setOnClickListener {
            dismiss()
        }
    }

    /**
     * Update pricing information from Google Play Billing
     */
    private fun updatePricingInfo() {
        val trialInfo = SubscriptionManager.getTrialInfo()
        val price = SubscriptionManager.getPrice()

        // Update trial display - prominent free trial hero
        if (trialInfo != null) {
            // Show trial prominently (e.g., "3 days free" -> "3 Days Free")
            val formattedTrial = trialInfo.split(" ").joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercase() }
            }
            binding.trialBadge.text = formattedTrial
            binding.startTrialButton.text = "Start My Free Trial"
            binding.pricingText.text = "Then $price/month • Cancel anytime"
        } else {
            // No trial available - show direct pricing
            binding.trialBadge.text = "Premium"
            binding.startTrialButton.text = "Subscribe Now - $price/month"
            binding.pricingText.text = "Full access to all features"
        }

        Log.d(TAG, "Pricing updated - Trial: $trialInfo, Price: $price")
    }

    /**
     * Launch purchase flow
     */
    private fun launchPurchaseFlow() {
        // Disable button to prevent multiple clicks
        binding.startTrialButton.isEnabled = false
        binding.startTrialButton.alpha = 0.5f

        // Launch purchase using SubscriptionManager singleton
        SubscriptionManager.launchPurchaseFlow(requireActivity())
    }

    /**
     * Restore previous purchases
     */
    private fun restorePurchases() {
        // Disable button to prevent multiple clicks
        binding.restoreButton.isEnabled = false
        binding.restoreButton.alpha = 0.5f

        lifecycleScope.launch {
            SubscriptionManager.restorePurchases()
        }
    }

    /**
     * Observe SubscriptionManager state changes
     */
    private fun observeStates() {
        // Observe subscription state
        SubscriptionManager.subscriptionState
            .drop(1)
            .onEach { state ->
                when (state) {
                    is SubscriptionState.Active -> {
                        onSubscriptionSuccess?.invoke()
                        dismiss()
                    }
                    is SubscriptionState.Error -> {
                        Toast.makeText(
                            requireContext(),
                            "Error: ${state.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    else -> { }
                }
            }
            .launchIn(lifecycleScope)

        // Observe purchase state
        SubscriptionManager.purchaseState
            .drop(1)
            .onEach { state ->
                when (state) {
                    is PurchaseState.Processing -> {
                        binding.startTrialButton.text = "Processing..."
                        Log.d(TAG, "Purchase processing...")
                    }

                    is PurchaseState.Success -> {
                        Toast.makeText(
                            requireContext(),
                            state.message,
                            Toast.LENGTH_SHORT
                        ).show()
                        Log.d(TAG, "Purchase successful: ${state.message}")
                        resetButtons()
                    }

                    is PurchaseState.Cancelled -> {
                        Log.d(TAG, "Purchase cancelled by user")
                        resetButtons()
                    }

                    is PurchaseState.NetworkError -> {
                        Toast.makeText(
                            requireContext(),
                            state.message,
                            Toast.LENGTH_LONG
                        ).show()
                        Log.w(TAG, "Network error: ${state.message}")
                        resetButtons()
                        binding.startTrialButton.text = "Retry"
                    }

                    is PurchaseState.Pending -> {
                        Toast.makeText(
                            requireContext(),
                            state.message,
                            Toast.LENGTH_LONG
                        ).show()
                        Log.d(TAG, "Purchase pending: ${state.message}")
                        resetButtons()
                    }

                    is PurchaseState.Error -> {
                        Toast.makeText(
                            requireContext(),
                            state.message,
                            Toast.LENGTH_LONG
                        ).show()
                        Log.e(TAG, "Purchase error: ${state.message}")
                        resetButtons()
                    }

                    else -> {
                        Log.d(TAG, "Purchase state: Idle")
                    }
                }
            }
            .launchIn(lifecycleScope)
    }

    /**
     * Reset button states
     */
    private fun resetButtons() {
        binding.startTrialButton.isEnabled = true
        binding.startTrialButton.alpha = 1f
        binding.restoreButton.isEnabled = true
        binding.restoreButton.alpha = 1f
        updatePricingInfo()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
