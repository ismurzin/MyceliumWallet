package com.mycelium.wallet.activity.modern.vip

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager.widget.ViewPager
import com.mycelium.wallet.R
import com.mycelium.wallet.databinding.FragmentVipBinding
import kotlinx.coroutines.flow.collect

class VipFragment : Fragment() {

    private lateinit var binding: FragmentVipBinding
    private val viewModel by viewModels<VipViewModel>()
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentVipBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupInputs()
        setupObservers()
        setupPageObserver()
    }

    private fun setupInputs() {
        binding.vipCodeInput.doOnTextChanged { text, _, _, _ ->
            text?.let {
                viewModel.updateVipText(it.toString())
            }
        }
    }

    private fun setupObservers() = lifecycleScope.launchWhenStarted {
        viewModel.stateFlow.collect { state ->
            binding.vipProgress.isVisible = state.progress
            updateButtons(state)
            handleError(state.error)
            handleSuccess(state.success)
        }
    }

    private fun setupPageObserver() {
        val pager = requireActivity().findViewById<ViewPager>(R.id.pager)
        pager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageSelected(position: Int) {
                if (position == VIP_FRAGMENT_POSITION) {
                    binding.icon.playAnimation()
                }
            }

            override fun onPageScrolled(p: Int, po: Float, pop: Int) {}

            override fun onPageScrollStateChanged(state: Int) {}
        })
    }

    private fun updateButtons(state: VipViewModel.State) {
        binding.vipApplyButton.apply {
            isEnabled = state.text.isNotEmpty() && !state.progress && !state.error
            text = if (state.progress) "" else getString(R.string.apply_vip_code)
            setOnClickListener { viewModel.applyCode() }
        }
    }

    private fun handleError(error: Boolean) = binding.apply {
        if (error) {
            errorText.isVisible = true
            vipCodeInput.setBackgroundResource(R.drawable.bg_input_text_filled_error)
        } else {
            errorText.isVisible = false
            vipCodeInput.setBackgroundResource(R.drawable.bg_input_text_filled)
        }
    }

    private fun handleSuccess(success: Boolean) {
        if (!success) return
        binding.apply {
            vipApplyButton.isVisible = false
            vipInputGroup.isVisible = false
            vipSuccessGroup.isVisible = true
            vipTitle.setText(R.string.vip_title_success)
            vipCodeInput.apply {
                hint = null
                text = null
                isFocusable = false
                clearFocus()
            }
            hideKeyBoard()
        }
    }

    private fun hideKeyBoard() {
        val imm = context?.getSystemService(Activity.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(requireView().windowToken, 0)
    }

    private companion object {
        const val VIP_FRAGMENT_POSITION = 7
    }
}
