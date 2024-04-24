package com.mycelium.wallet.external.buycrypto

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
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
import com.mycelium.wallet.activity.util.toStringFriendlyWithUnit
import com.mycelium.wallet.activity.view.ValueKeyboard
import com.mycelium.wallet.databinding.FragmentBuyCryptoBinding
import com.mycelium.wallet.event.ExchangeRatesRefreshed
import com.mycelium.wallet.event.ExchangeSourceChanged
import com.mycelium.wallet.event.PageSelectedEvent
import com.mycelium.wallet.event.SelectedAccountChanged
import com.mycelium.wallet.event.SelectedCurrencyChanged
import com.mycelium.wallet.external.buycrypto.detailed.BuyCryptoDetailedFragment
import com.mycelium.wallet.external.changelly2.SelectAccountFragment
import com.mycelium.wallet.external.changelly2.SelectFiatFragment
import com.mycelium.wallet.external.fiat.ChangellyFiatRepository.FAILED_PAYMENT_METHOD
import com.mycelium.wallet.external.fiat.model.ChangellyOffer
import com.squareup.otto.Subscribe

class BuyCryptoFragment : Fragment(), BackListener {

    private lateinit var binding: FragmentBuyCryptoBinding
    private val viewModel: BuyCryptoViewModel by viewModels()
    private val methodsAdapter by lazy { BuyCryptoMethodsAdapter(viewModel::selectMethod) }
    private val offersAdapter by lazy { BuyCryptoOffersAdapter(::selectOffer) }

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
        setupInputs()
        setupObservers()
        setupRecycler()
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
                maxDecimals = 2
            }
            viewModel.keyboardActive.value = true
        }

        val selectSellAccount = { _: View ->
            layoutValueKeyboard.numericKeyboard.done()
            SelectFiatFragment(viewModel.fiatCurrencies) {
                viewModel.fromFiat.value = it
            }.show(parentFragmentManager, null)
        }
        sellLayout.coinSymbol.setOnClickListener(selectSellAccount)
        sellLayout.layoutAccount.setOnClickListener(selectSellAccount)
    }

    private fun setupBuyLayoutClickListeners() = binding.apply {
        val selectBuyAccount = { _: View ->
            SelectAccountFragment(viewModel).apply {
                arguments = Bundle().apply {
                    putString(SelectAccountFragment.KEY_TYPE, SelectAccountFragment.VALUE_BUY)
                }
            }.show(parentFragmentManager, null)
        }
        buyLayout.coinSymbol.setOnClickListener(selectBuyAccount)
        buyLayout.layoutAccount.setOnClickListener(selectBuyAccount)
    }


    private fun setupObservers() {
        viewModel.sellValue.observe(viewLifecycleOwner) { amount ->
            val sellCoinValue = binding.sellLayout.coinValue
            if (sellCoinValue.text?.toString() != amount) {
                sellCoinValue.text = amount
            }
            sellCoinValue.resizeTextView()
        }
        viewModel.buyValue.observe(viewLifecycleOwner) {
            binding.buyLayout.coinValue.resizeTextView()
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
        viewModel.isValid.observe(viewLifecycleOwner) {
            if (!it) return@observe
            viewModel.getMethods()
        }
        viewModel.methods.observe(viewLifecycleOwner) {
            if (it.isEmpty()) return@observe
            if (it.size == 1 && it.first().paymentMethod == FAILED_PAYMENT_METHOD) {
                binding.exchangeGroup.isInvisible = true
                binding.methodsList.isVisible = false
            } else {
                binding.exchangeGroup.isInvisible = false
                binding.methodsList.isVisible = true
            }
            methodsAdapter.methods = it
            viewModel.selectMethod(it.first())
        }
        viewModel.currentMethod.observe(viewLifecycleOwner) {
            if (it == null) return@observe
            offersAdapter.currentMethod = it
            val formattedRate = "%.2f".format(it.invertedRate?.toDouble())
            binding.apply {
                exchangeRateFrom.text =
                    getString(R.string.buy_crypto_rate_currency_to, it.currencyTo)
                exchangeRateToCurrency.text = it.currencyFrom
                exchangeRateToValue.text = formattedRate
                buyLayout.coinValue.text = it.amountExpectedTo ?: "0"
            }
        }
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.apply {
                if (loading) {
                    buyLayout.coinValue.text = "0"
                    methodsList.isInvisible = true
                    binding.offersTitle.isVisible = true
                }
                offersList.isInvisible = loading
                buyLayout.coinValueShimmer.isVisible = loading
                shimmerGroup.isVisible = loading
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
            setPasteVisibility(false)
            visibility = View.GONE
        }
        sellLayout.coinValue.doOnTextChanged { text, _, _, _ ->
            viewModel.sellValue.value = text?.toString()
        }
    }

    private fun setupRecycler() = binding.apply {
        offersList.layoutManager = LinearLayoutManager(requireContext())
        offersList.adapter = offersAdapter
        methodsList.adapter = methodsAdapter
        methodsList.layoutManager = LinearLayoutManager(
            requireContext(),
            LinearLayoutManager.HORIZONTAL,
            false,
        )
        methodsList.itemAnimator = null
    }

    private fun selectOffer(offer: ChangellyOffer) {
        if (offer.error != null) return
        val toAccount = viewModel.toAccount.value ?: return
        val sendAmount = viewModel.sellValue.value ?: return
        val method = viewModel.currentMethod.value ?: return
        BuyCryptoDetailedFragment().apply {
            arguments = Bundle().apply {
                putString(BuyCryptoDetailedFragment.ACCOUNT_NAME_KEY, toAccount.label)
                putString(
                    BuyCryptoDetailedFragment.ACCOUNT_ADDRESS_KEY,
                    toAccount.receiveAddress?.toString()
                )
                putString(
                    BuyCryptoDetailedFragment.ACCOUNT_BALANCE_KEY,
                    toAccount.accountBalance.spendable?.toStringFriendlyWithUnit()
                )
                putString(
                    BuyCryptoDetailedFragment.ACCOUNT_FIAT_BALANCE_KEY,
                    viewModel.getFiatBalance()
                )
                putString(BuyCryptoDetailedFragment.ACCOUNT_SEND_AMOUNT_KEY, sendAmount)
                putSerializable(BuyCryptoDetailedFragment.ACCOUNT_METHOD_KEY, method)
                putSerializable(BuyCryptoDetailedFragment.ACCOUNT_OFFER_KEY, offer)
            }
        }.show(parentFragmentManager, null)
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

    @Subscribe
    fun selectedAccountChanged(event: SelectedAccountChanged) {
        viewModel.refreshReceiveAccount()
    }
}
