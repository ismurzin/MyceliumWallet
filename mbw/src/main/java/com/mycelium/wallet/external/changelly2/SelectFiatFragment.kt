package com.mycelium.wallet.external.changelly2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.mycelium.wallet.R
import com.mycelium.wallet.databinding.FragmentChangelly2SelectFiatBinding
import com.mycelium.wallet.external.fiat.model.ChangellyFiatCurrenciesResponse


class SelectFiatFragment(
    private val currencies: List<ChangellyFiatCurrenciesResponse>,
    private val onSelect: (ChangellyFiatCurrenciesResponse) -> Unit,
) : DialogFragment() {

    lateinit var binding: FragmentChangelly2SelectFiatBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Dialog_Changelly)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        FragmentChangelly2SelectFiatBinding.inflate(inflater).apply {
            binding = this
        }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { dismissAllowingStateLoss() }
        if (currencies.isEmpty()) {
            binding.popularFiatsTitle.isVisible = false
            binding.allSupportedFiatsTitle.isVisible = false
            binding.noFunds.isVisible = true
            return
        }
        setupAllSupportedFiats()
        val popularFiats = getPopularFiats()
        if (popularFiats.isEmpty()) {
            binding.popularFiatsTitle.isVisible = false
            return
        }
        setupPopularFiats(popularFiats)
    }

    private fun setupAllSupportedFiats() = binding.allSupportedFiats.apply {
        layoutManager = LinearLayoutManager(requireContext())
        adapter = CurrencyFiatAdapter(currencies) {
            onSelect(it)
            dismissAllowingStateLoss()
        }
    }

    private fun setupPopularFiats(fiats: List<ChangellyFiatCurrenciesResponse>) {
        binding.popularFiats.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = CurrencyFiatAdapter(fiats) {
                onSelect(it)
                dismissAllowingStateLoss()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    private fun getPopularFiats() = currencies.filter { popularFiats.contains(it.ticker) }

    private companion object {
        val popularFiats = listOf("USD", "EUR", "AED")
    }
}
