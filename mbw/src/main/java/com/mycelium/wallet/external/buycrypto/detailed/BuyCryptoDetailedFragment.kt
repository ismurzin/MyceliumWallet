package com.mycelium.wallet.external.buycrypto.detailed

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions
import com.mycelium.wallet.R
import com.mycelium.wallet.Utils
import com.mycelium.wallet.activity.modern.Toaster
import com.mycelium.wallet.databinding.FragmentBuyCryptoDetailedBinding
import com.mycelium.wallet.external.fiat.model.ChangellyMethod
import com.mycelium.wallet.external.fiat.model.ChangellyOffer

class BuyCryptoDetailedFragment : DialogFragment() {

    private lateinit var binding: FragmentBuyCryptoDetailedBinding
    private val viewModel: BuyCryptoDetailedViewModel by viewModels()
    private var offer: ChangellyOffer? = null
    private var method: ChangellyMethod? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Dialog_Changelly)
        offer = arguments?.getSerializable(ACCOUNT_OFFER_KEY) as? ChangellyOffer
        method = arguments?.getSerializable(ACCOUNT_METHOD_KEY) as? ChangellyMethod
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentBuyCryptoDetailedBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupReceiveAccount()
        setupTransactionDetails()
        setupPoweredBy()
        setupSelectedMethod()
        setupButtons()
        setupObservers()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }

    private fun setupReceiveAccount() = binding.apply {
        accountName.text = arguments?.getString(ACCOUNT_NAME_KEY)
        accountAddress.text = arguments?.getString(ACCOUNT_ADDRESS_KEY)
        accountBalance.text = arguments?.getString(ACCOUNT_BALANCE_KEY)
        accountBalanceFiat.text = arguments?.getString(ACCOUNT_FIAT_BALANCE_KEY)
    }

    private fun setupTransactionDetails() = binding.apply {
        val data = offer?.data ?: return@apply
        sendAmount.text = getString(
            R.string.buy_crypto_amount,
            arguments?.getString(ACCOUNT_SEND_AMOUNT_KEY),
            method?.currencyFrom,
        )
        rate.text = getString(
            R.string.buy_crypto_detailed_rate,
            method?.currencyTo,
            data.invertedRate,
            method?.currencyFrom,
        )
        receiveAmount.text = getString(
            R.string.buy_crypto_amount,
            data.amountExpectedTo,
            method?.currencyTo,
        )
    }

    private fun setupPoweredBy() = binding.apply {
        Glide.with(requireContext())
            .load(Utils.tokenLogoPath(offer?.name ?: ""))
            .apply(RequestOptions().transforms(CircleCrop()))
            .into(poweredByIcon)
        poweredByName.text = offer?.name
    }

    private fun setupSelectedMethod() = binding.apply {
        Glide.with(requireContext())
            .load(Utils.tokenLogoPath(method?.paymentMethod ?: ""))
            .into(selectedMethodIcon)
    }

    private fun setupButtons() = binding.apply {
        val offerTyped = offer ?: return@apply
        val methodTyped = method ?: return@apply
        back.setOnClickListener { dismiss() }
        confirm.setOnClickListener {
            val walletAddress =
                arguments?.getString(ACCOUNT_ADDRESS_KEY) ?: return@setOnClickListener
            val sendAmount =
                arguments?.getString(ACCOUNT_SEND_AMOUNT_KEY) ?: return@setOnClickListener
            viewModel.createOrder(
                offerTyped.providerCode,
                methodTyped.currencyFrom,
                methodTyped.currencyTo,
                sendAmount,
                walletAddress,
                methodTyped.paymentMethod,
                onSuccess = {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
                    dismiss()
                }
            )
        }
    }

    private fun setupObservers() = binding.apply {
        viewModel.isLoading.observe(this@BuyCryptoDetailedFragment) {
            back.isEnabled = !it
            confirm.isEnabled = !it
            confirm.text = if (it) "" else getString(R.string.buy_crypto_detailed_confirm)
            confirmProgress.isVisible = it
        }
        viewModel.error.observe(this@BuyCryptoDetailedFragment) {
            Toaster(requireContext()).toast(it, true)
        }
    }

    companion object {
        const val ACCOUNT_NAME_KEY = "ACCOUNT_NAME_KEY"
        const val ACCOUNT_ADDRESS_KEY = "ACCOUNT_ADDRESS_KEY"
        const val ACCOUNT_BALANCE_KEY = "ACCOUNT_BALANCE_KEY"
        const val ACCOUNT_FIAT_BALANCE_KEY = "ACCOUNT_FIAT_BALANCE_KEY"
        const val ACCOUNT_SEND_AMOUNT_KEY = "ACCOUNT_SEND_AMOUNT_KEY"
        const val ACCOUNT_METHOD_KEY = "ACCOUNT_METHOD_KEY"
        const val ACCOUNT_OFFER_KEY = "ACCOUNT_OFFER_KEY"
    }
}
