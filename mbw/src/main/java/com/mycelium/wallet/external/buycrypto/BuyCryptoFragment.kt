package com.mycelium.wallet.external.buycrypto

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions
import com.mycelium.wallet.MbwManager
import com.mycelium.wallet.R
import com.mycelium.wallet.Utils
import com.mycelium.wallet.activity.modern.event.BackHandler
import com.mycelium.wallet.activity.modern.event.BackListener
import com.mycelium.wallet.activity.util.resizeTextView
import com.mycelium.wallet.activity.util.startCursor
import com.mycelium.wallet.activity.util.stopCursor
import com.mycelium.wallet.activity.view.ValueKeyboard
import com.mycelium.wallet.databinding.FragmentBuyCryptoBinding
import com.mycelium.wallet.event.ExchangeRatesRefreshed
import com.mycelium.wallet.event.ExchangeSourceChanged
import com.mycelium.wallet.event.PageSelectedEvent
import com.mycelium.wallet.event.SelectedCurrencyChanged
import com.mycelium.wallet.external.changelly2.SelectAccountFragment
import com.mycelium.wallet.external.changelly2.SelectFiatFragment
import com.mycelium.wapi.wallet.Util
import com.mycelium.wapi.wallet.coins.CryptoCurrency
import com.squareup.otto.Subscribe
import java.math.BigDecimal
import java.util.Locale


class BuyCryptoFragment : Fragment(), BackListener {

    lateinit var binding: FragmentBuyCryptoBinding
    val viewModel: BuyCryptoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        viewModel.initAccounts()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = FragmentBuyCryptoBinding.inflate(inflater).apply {
        binding = this
        vm = viewModel
        lifecycleOwner = this@BuyCryptoFragment
    }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.sellLayout.accountGroup.isVisible = false
        setupSellLayoutClickListeners()
        setupBuyLayoutClickListeners()
        setupButtons()
        setupInputs()
        setupObservers()
    }

    override fun onStart() {
        super.onStart()
        MbwManager.getEventBus().register(this)
        val pActivity = activity
        if (pActivity is BackHandler) {
            pActivity.addBackListener(this)
        }
    }

    override fun onStop() {
        val pActivity = activity
        if (pActivity is BackHandler) {
            pActivity.removeBackListener(this)
        }
        MbwManager.getEventBus().unregister(this)
        super.onStop()
    }

    override fun onBackPressed(): Boolean =
        if (binding.layoutValueKeyboard.numericKeyboard.visibility == View.VISIBLE) {
            binding.layoutValueKeyboard.numericKeyboard.done()
            true
        } else {
            false
        }

    private fun setupSellLayoutClickListeners() = binding.apply {
        sellLayout.root.setOnClickListener {
            buyLayout.coinValue.stopCursor()
            sellLayout.coinValue.startCursor()
            layoutValueKeyboard.numericKeyboard.apply {
                inputTextView = sellLayout.coinValue
                isVisible = true
                setEntry(viewModel.sellValue.value ?: "")
            }
            viewModel.keyboardActive.value = true
        }

        val selectSellAccount = { _: View ->
            layoutValueKeyboard.numericKeyboard.done()
            SelectFiatFragment(viewModel.fiatCurrencies) {
                viewModel.fromFiat.value = it
            }.show(parentFragmentManager, TAG_SELECT_ACCOUNT_SELL)
        }
        sellLayout.coinSymbol.setOnClickListener(selectSellAccount)
        sellLayout.layoutAccount.setOnClickListener(selectSellAccount)
    }

    private fun setupBuyLayoutClickListeners() = binding.apply {
        buyLayout.root.setOnClickListener {
            sellLayout.coinValue.stopCursor()
            buyLayout.coinValue.startCursor()
            layoutValueKeyboard.numericKeyboard.run {
                inputTextView = buyLayout.coinValue
                maxDecimals = viewModel.toCurrency.value?.friendlyDigits ?: 0
                spendableValue = null
                isVisible = true
                setEntry(viewModel.buyValue.value ?: "")
            }
            viewModel.keyboardActive.value = true
        }
        val selectBuyAccount = { _: View ->
            SelectAccountFragment(viewModel).apply {
                arguments = Bundle().apply {
                    putString(SelectAccountFragment.KEY_TYPE, SelectAccountFragment.VALUE_BUY)
                }
            }.show(parentFragmentManager, TAG_SELECT_ACCOUNT_BUY)
        }


        buyLayout.coinSymbol.setOnClickListener(selectBuyAccount)
        buyLayout.layoutAccount.setOnClickListener(selectBuyAccount)
    }

    private fun setupButtons() {
        binding.nextStep.setOnClickListener {
            // todo next step
        }
    }

    private fun setupObservers() {
        viewModel.sellValue.observe(viewLifecycleOwner) { amount ->
            val sellCoinValue = binding.sellLayout.coinValue
            if (sellCoinValue.text?.toString() != amount) {
                sellCoinValue.text = amount
            }
            sellCoinValue.resizeTextView()
        }
        viewModel.buyValue.observe(viewLifecycleOwner) { amount ->
            val keyboardValue = binding.layoutValueKeyboard.numericKeyboard.inputTextView
            val buyCoinValue = binding.buyLayout.coinValue
            if (keyboardValue == buyCoinValue) {
                viewModel.sellValue.value = if (amount?.isNotEmpty() == true) {
                    try {
                        // todo calculate sellValue
                        N_A
                    } catch (e: NumberFormatException) {
                        N_A
                    }
                } else {
                    null
                }
            }
            if (buyCoinValue.text?.toString() != amount) {
                buyCoinValue.text = amount
            }
            buyCoinValue.resizeTextView()
        }
        viewModel.fromCurrency.observe(viewLifecycleOwner) { coin ->
            binding.sellLayout.coinIcon.let {
                Glide.with(it).clear(it)
                Glide.with(it)
                    .load(Utils.tokenLogoPath(coin))
                    .apply(RequestOptions().transforms(CircleCrop()))
                    .into(it)
            }
        }
        viewModel.toCurrency.observe(viewLifecycleOwner) { coin ->
            binding.buyLayout.coinIcon.let {
                Glide.with(it).clear(it)
                Glide.with(it)
                    .load(Utils.tokenLogoPath(coin))
                    .apply(RequestOptions().transforms(CircleCrop()))
                    .into(it)
            }
        }
    }

    private fun setupInputs() = binding.apply {
        layoutValueKeyboard.numericKeyboard.apply {
            inputListener = object : ValueKeyboard.SimpleInputListener() {
                override fun done() {
                    binding.sellLayout.coinValue.stopCursor()
                    binding.buyLayout.coinValue.stopCursor()
                    viewModel.keyboardActive.value = false
                }
            }
            errorListener = object : ValueKeyboard.ErrorListener {
                override fun maxError(maxValue: BigDecimal) {
//                    viewModel.errorKeyboard.value = resources.getString(
//                        R.string.exchange_max_msg,
//                        viewModel.exchangeInfo.value?.maxFrom?.stripTrailingZeros()
//                            ?.toPlainString(),
//                        viewModel.exchangeInfo.value?.from?.toUpperCase(Locale.ROOT)
//                    )
                }

                override fun minError(minValue: BigDecimal) {
//                    viewModel.errorKeyboard.value = resources.getString(
//                        R.string.exchange_min_msg,
//                        viewModel.exchangeInfo.value?.minFrom?.stripTrailingZeros()
//                            ?.toPlainString(),
//                        viewModel.exchangeInfo.value?.from?.toUpperCase(Locale.ROOT)
//                    )
                }

                override fun formatError() {
                    viewModel.errorKeyboard.value = ""
                }

                override fun noError() {
                    viewModel.errorKeyboard.value = ""
                }
            }
            setMaxText(getString(R.string.max), 14f)
            setPasteVisibility(false)
            visibility = View.GONE
        }
        buyLayout.coinValue.doOnTextChanged { text, _, _, _ ->
            viewModel.buyValue.value = text?.toString()
        }
        sellLayout.coinValue.doOnTextChanged { text, _, _, _ ->
            viewModel.sellValue.value = text?.toString()
        }
    }


    @Subscribe
    fun exchangeRatesRefreshed(event: ExchangeRatesRefreshed) {
        viewModel.toAccount.value = viewModel.toAccount.value
    }

    @Subscribe
    fun exchangeSourceChanged(event: ExchangeSourceChanged) {
        viewModel.toAccount.value = viewModel.toAccount.value
    }

    @Subscribe
    fun onSelectedCurrencyChange(event: SelectedCurrencyChanged) {
        viewModel.toAccount.value = viewModel.toAccount.value
    }

    @Subscribe
    fun pageSelectedEvent(event: PageSelectedEvent) {
        binding.layoutValueKeyboard.numericKeyboard.done()
    }

    companion object {
        const val TAG_SELECT_ACCOUNT_BUY = "select_account_for_buy"
        const val TAG_SELECT_ACCOUNT_SELL = "select_account_for_sell"
        private const val N_A = "N/A"
    }
}
