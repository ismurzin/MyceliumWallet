package com.mycelium.wallet.external.changelly2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.mycelium.wallet.R
import com.mycelium.wallet.databinding.FragmentChangelly2SelectFiatBinding
import com.mycelium.wallet.external.fiat.model.ChangellyFiatCurrenciesResponse


class SelectFiatFragment(
    currencies: List<ChangellyFiatCurrenciesResponse>,
    onSelect: (ChangellyFiatCurrenciesResponse) -> Unit,
) : DialogFragment() {

    private val fiatAdapter = CurrencyFiatAdapter(currencies) { item ->
        onSelect(item)
        dismissAllowingStateLoss()
    }
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
        binding.list.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = fiatAdapter
            itemAnimator = null
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }
}
