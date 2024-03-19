package com.mycelium.wallet.external.buycrypto

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions
import com.mycelium.wallet.MbwManager
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
import com.squareup.otto.Subscribe


class BuyCryptoFragment : Fragment(), BackListener {

    private lateinit var binding: FragmentBuyCryptoBinding
    private val viewModel: BuyCryptoViewModel by viewModels()

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
            viewModel.getMethodsWithDebounce()
        }
        viewModel.methods.observe(viewLifecycleOwner) {
            binding.offersList.adapter = BuyCryptoOffersAdapter(it.first().offers)
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
        buyLayout.coinValue
    }

    private fun setupRecycler() = binding.apply {
        binding.offersList.layoutManager = LinearLayoutManager(requireContext())
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
}
