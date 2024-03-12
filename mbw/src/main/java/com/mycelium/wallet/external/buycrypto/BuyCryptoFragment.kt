package com.mycelium.wallet.external.buycrypto

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions
import com.mycelium.view.RingDrawable
import com.mycelium.wallet.MbwManager
import com.mycelium.wallet.R
import com.mycelium.wallet.activity.modern.event.BackHandler
import com.mycelium.wallet.activity.modern.event.BackListener
import com.mycelium.wallet.activity.settings.SettingsPreference
import com.mycelium.wallet.activity.util.resizeTextView
import com.mycelium.wallet.activity.util.startCursor
import com.mycelium.wallet.activity.util.stopCursor
import com.mycelium.wallet.activity.util.toStringWithUnit
import com.mycelium.wallet.activity.view.ValueKeyboard
import com.mycelium.wallet.databinding.FragmentBuyCryptoBinding
import com.mycelium.wallet.event.ExchangeRatesRefreshed
import com.mycelium.wallet.event.ExchangeSourceChanged
import com.mycelium.wallet.event.PageSelectedEvent
import com.mycelium.wallet.event.SelectedCurrencyChanged
import com.mycelium.wallet.external.changelly.model.ChangellyResponse
import com.mycelium.wallet.external.changelly.model.ChangellyTransactionOffer
import com.mycelium.wallet.external.changelly.model.FixRate
import com.mycelium.wallet.external.changelly2.SelectAccountFragment
import com.mycelium.wallet.external.changelly2.remote.Changelly2Repository
import com.mycelium.wallet.startCoroutineTimer
import com.mycelium.wapi.wallet.Transaction
import com.mycelium.wapi.wallet.Util
import com.mycelium.wapi.wallet.coins.CryptoCurrency
import com.squareup.otto.Subscribe
import kotlinx.coroutines.Job
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import java.util.concurrent.TimeUnit


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
        updateAmount()
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
                maxValue = viewModel.exchangeInfo.value?.maxFrom
                minValue = viewModel.exchangeInfo.value?.minFrom
                setEntry(viewModel.sellValue.value ?: "")
                maxDecimals = 1000 // todo how much?
                visibility = View.VISIBLE
            }
            viewModel.keyboardActive.value = true
        }

        val selectSellAccount = { _: View ->
            layoutValueKeyboard.numericKeyboard.done()
            SelectAccountFragment(viewModel).apply {
                arguments = Bundle().apply {
                    putString(SelectAccountFragment.KEY_TYPE, SelectAccountFragment.VALUE_SELL)
                }
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
                maxValue = viewModel.exchangeInfo.value?.maxTo
                minValue = viewModel.exchangeInfo.value?.minTo
                spendableValue = null
                setEntry(viewModel.buyValue.value ?: "")
                maxDecimals = viewModel.toCurrency.value?.friendlyDigits ?: 0
                visibility = View.VISIBLE
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
            val keyboardValue = binding.layoutValueKeyboard.numericKeyboard.inputTextView
            val buyCoinValue = binding.buyLayout.coinValue
            if (keyboardValue != buyCoinValue) {
                updateAmountIfChanged()
                computeBuyValue()
            }
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
                    .load(iconPath(coin))
                    .apply(RequestOptions().transforms(CircleCrop()))
                    .into(it)
            }
            updateExchangeRate()
        }
        viewModel.toCurrency.observe(viewLifecycleOwner) { coin ->
            binding.buyLayout.coinIcon.let {
                Glide.with(it).clear(it)
                Glide.with(it)
                    .load(iconPath(coin))
                    .apply(RequestOptions().transforms(CircleCrop()))
                    .into(it)
            }
            updateExchangeRate()
        }
        viewModel.rateLoading.observe(viewLifecycleOwner) {
            if (it) {
                counterJob?.cancel()
                binding.progress.setImageDrawable(
                    ResourcesCompat.getDrawable(
                        resources,
                        R.drawable.ic_progress,
                        null
                    )
                )
                binding.progress.startAnimation(RotateAnimation(
                    0f,
                    360f,
                    Animation.RELATIVE_TO_SELF,
                    0.5f,
                    Animation.RELATIVE_TO_SELF,
                    0.5f
                )
                    .apply {
                        interpolator = LinearInterpolator()
                        repeatCount = Animation.INFINITE
                        duration = 700
                    })
            } else {
                binding.progress.setImageDrawable(null)
                binding.progress.clearAnimation()
            }
        }
        viewModel.exchangeInfo.observe(viewLifecycleOwner) { computeBuyValue() }
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
                    viewModel.errorKeyboard.value = resources.getString(
                        R.string.exchange_max_msg,
                        viewModel.exchangeInfo.value?.maxFrom?.stripTrailingZeros()
                            ?.toPlainString(),
                        viewModel.exchangeInfo.value?.from?.toUpperCase(Locale.ROOT)
                    )
                }

                override fun minError(minValue: BigDecimal) {
                    viewModel.errorKeyboard.value = resources.getString(
                        R.string.exchange_min_msg,
                        viewModel.exchangeInfo.value?.minFrom?.stripTrailingZeros()
                            ?.toPlainString(),
                        viewModel.exchangeInfo.value?.from?.toUpperCase(Locale.ROOT)
                    )
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

    private fun computeBuyValue() {
        val amount = viewModel.sellValue.value
        viewModel.buyValue.value = if (amount?.isNotEmpty() == true
            && viewModel.exchangeInfo.value?.result != null
        ) {
            try {
                (amount.toBigDecimal() * viewModel.exchangeInfo.value?.result!!)
                    .setScale(viewModel.toCurrency.value?.friendlyDigits!!, RoundingMode.HALF_UP)
                    .stripTrailingZeros()
                    .toPlainString()
            } catch (_: NumberFormatException) {
                "N/A"
            }
        } else {
            null
        }
    }

    private fun acceptDialog(
        unsignedTx: Transaction?,
        result: ChangellyResponse<ChangellyTransactionOffer>,
        action: () -> Unit,
    ) {
        if (!SettingsPreference.exchangeConfirmationEnabled) {
            viewModel.mbwManager.runPinProtectedFunction(activity) {
                action()
            }?.setOnDismissListener { updateAmount() }
        } else {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.exchange_accept_dialog_title))
                .setMessage(
                    getString(
                        R.string.exchange_accept_dialog_msg,
                        result.result?.amountExpectedFrom?.stripTrailingZeros()?.toPlainString(),
                        result.result?.currencyFrom?.toUpperCase(Locale.ROOT),
                        unsignedTx?.totalFee()?.toStringWithUnit(),
                        result.result?.amountTo?.stripTrailingZeros()?.toPlainString(),
                        result.result?.currencyTo?.toUpperCase(Locale.ROOT)
                    )
                )
                .setPositiveButton(R.string.button_ok) { _, _ ->
                    viewModel.mbwManager.runPinProtectedFunction(activity) {
                        action()
                    }?.setOnDismissListener { updateAmount() }
                }
                .setNegativeButton(R.string.cancel, null)
                .setOnDismissListener { updateAmount() }
                .show()
        }
    }

    private var rateJob: Job? = null

    private fun updateExchangeRate() {
        if (viewModel.toCurrency.value?.symbol != null) {
            rateJob?.cancel()
            viewModel.rateLoading.value = true
            rateJob = Changelly2Repository.fixRate(lifecycleScope,
                Util.trimTestnetSymbolDecoration(viewModel.toCurrency.value?.symbol!!),
                Util.trimTestnetSymbolDecoration("USDT"), // todo rate for fiat
                { result ->
                    if (result?.result != null) {
                        viewModel.exchangeInfo.value = result.result
                        viewModel.errorRemote.value = ""
                    } else {
                        viewModel.errorRemote.value = result?.error?.message ?: ""
                    }
                },
                { code, msg ->
                    if (code != 400) {
                        viewModel.errorRemote.value = msg
                    }
                },
                {
                    viewModel.rateLoading.value = false
                    refreshRateCounter()
                })
        }
    }

    private var prevAmount: BigDecimal? = null

    private var amountJob: Job? = null

    private fun updateAmountIfChanged() {
        try {
            viewModel.sellValue.value?.toBigDecimal()?.let { fromAmount ->
                if (prevAmount != fromAmount && fromAmount > BigDecimal.ZERO) {
                    updateAmount()
                }
            }
        } catch (_: NumberFormatException) {
        }
    }

    private fun updateAmount() {
        if (viewModel.toCurrency.value?.symbol != null) {
            try {
                viewModel.sellValue.value?.toBigDecimal()?.let { fromAmount ->
                    if (fromAmount > BigDecimal.ZERO) {
                        amountJob?.cancel()
                        viewModel.rateLoading.value = true
                        amountJob =
                            Changelly2Repository.exchangeAmount(lifecycleScope, // todo rate for fiat
                                Util.trimTestnetSymbolDecoration("BTC"),
                                Util.trimTestnetSymbolDecoration(viewModel.toCurrency.value?.symbol!!),
                                fromAmount,
                                { result ->
                                    result?.result?.let {
                                        val info = viewModel.exchangeInfo.value
                                        viewModel.exchangeInfo.postValue(
                                            FixRate(
                                                it.id, it.result, it.from, it.to,
                                                info!!.maxFrom, info.maxTo, info.minFrom, info.minTo
                                            )
                                        )
                                        viewModel.errorRemote.value = ""
                                    } ?: run {
                                        viewModel.errorRemote.value = result?.error?.message ?: ""
                                    }
                                },
                                { code, msg ->
                                    if (code != 400) {
                                        viewModel.errorRemote.value = msg
                                    }
                                },
                                {
                                    viewModel.rateLoading.value = false
                                    refreshRateCounter()
                                })
                        prevAmount = fromAmount
                    } else {
                        updateExchangeRate()
                    }
                } ?: run {
                    updateExchangeRate()
                }
            } catch (e: NumberFormatException) {
                updateExchangeRate()
            }
        }
    }

    private var counterJob: Job? = null

    private fun refreshRateCounter() {
        counterJob?.cancel()
        counterJob = startCoroutineTimer(
            lifecycleScope,
            repeatMillis = TimeUnit.SECONDS.toMillis(1)
        ) { counter ->
            if (viewModel.rateLoading.value == false) {
                if (counter < 30) {
                    binding.progress.setImageDrawable(
                        RingDrawable(
                            counter * 360f / 30f,
                            Color.parseColor("#777C80")
                        )
                    )
                } else {
                    counterJob?.cancel()
                    updateAmount()
                }
            } else {
                counterJob?.cancel()
            }
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

        fun iconPath(coin: CryptoCurrency): Uri =
            iconPath(Util.trimTestnetSymbolDecoration(coin.symbol))

        fun iconPath(coin: String): Uri = Uri.parse(
            "file:///android_asset/token-logos/" + coin.toLowerCase(
                Locale.ROOT
            ) + "_logo.png"
        )
    }
}
