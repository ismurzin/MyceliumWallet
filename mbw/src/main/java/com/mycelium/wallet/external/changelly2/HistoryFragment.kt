package com.mycelium.wallet.external.changelly2

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.forEach
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.mycelium.wallet.Constants.TAG
import com.mycelium.wallet.R
import com.mycelium.wallet.activity.view.DividerItemDecoration
import com.mycelium.wallet.activity.view.loader
import com.mycelium.wallet.databinding.FragmentChangelly2HistoryBinding
import com.mycelium.wallet.external.adapter.TxHistoryAdapter
import com.mycelium.wallet.external.adapter.TxItem
import com.mycelium.wallet.external.changelly2.remote.Changelly2Repository
import com.mycelium.wallet.external.changelly2.remote.fixedCurrencyFrom
import com.mycelium.wallet.external.changelly2.remote.fixedCurrencyTo
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.UUID


class HistoryFragment : DialogFragment() {

    private val historyDateFormat = DateFormat.getDateInstance(DateFormat.LONG)
    var binding: FragmentChangelly2HistoryBinding? = null
    val pref by lazy { requireContext().getSharedPreferences(ExchangeFragment.PREF_FILE, Context.MODE_PRIVATE) }
    val adapter = TxHistoryAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Dialog_Changelly)
        setHasOptionsMenu(false)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
            FragmentChangelly2HistoryBinding.inflate(inflater).apply {
                binding = this
            }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding?.toolbar?.setNavigationOnClickListener {
            dismissAllowingStateLoss()
        }
        adapter.clickListener = {
            ExchangeResultFragment().apply {
                arguments = Bundle().apply {
                    putString(ExchangeResultFragment.KEY_CHANGELLY_TX_ID, it.id)
                    putString(ExchangeResultFragment.KEY_CHAIN_TX, pref.getString("tx_id_${it.id}", null))
                    putSerializable(ExchangeResultFragment.KEY_ACCOUNT_FROM_ID,
                            pref.getString("account_from_id_${it.id}", null)?.let { UUID.fromString(it) })
                    putSerializable(ExchangeResultFragment.KEY_ACCOUNT_TO_ID,
                            pref.getString("account_to_id_${it.id}", null)?.let { UUID.fromString(it) })
                }
            }.show(parentFragmentManager, "")
        }
        binding?.list?.addItemDecoration(DividerItemDecoration(resources.getDrawable(R.drawable.divider_bequant), LinearLayout.VERTICAL))
        binding?.list?.adapter = adapter
        update()
        updateEmpty()
    }

    private fun update() {
        val txIds = (pref.getStringSet(ExchangeFragment.KEY_HISTORY, null) ?: setOf()).toList()
            .filterNotNull()
            .filterNot { it.isEmpty() }
        if (txIds.isEmpty()) return
        loader(true)
        lifecycleScope.launch {
            try {
                val result = Changelly2Repository.getTransactions(txIds).result ?: return@launch
                adapter.submitList(result.map {
                    TxItem(
                        it.id,
                        it.amountExpectedFrom.toString(),
                        it.amountExpectedTo.toString(),
                        it.fixedCurrencyFrom(),
                        it.fixedCurrencyTo(),
                        historyDateFormat.format(Date(it.createdAt * 1000L)),
                        it.getReadableStatus()
                    )
                })

            } catch (e: Exception) {
                Log.e(TAG, "${e.message}");
            } finally {
                updateEmpty()
                loader(false)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        binding?.toolbar?.menu?.clear()
        inflater.inflate(R.menu.exchange_changelly2_result, binding?.toolbar?.menu)
        binding?.toolbar?.menu?.forEach {
            it.setOnMenuItemClickListener {
                onOptionsItemSelected(it)
            }
        }
    }

    private fun updateEmpty() {
        (if (adapter.itemCount == 0) View.VISIBLE else View.GONE).let {
            binding?.emptyTitle?.visibility = it
            binding?.emptyText?.visibility = it
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
            when (item.itemId) {
                R.id.refresh -> {
                    update()
                    true
                }
                else -> super.onOptionsItemSelected(item)
            }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}