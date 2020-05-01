package com.mycelium.bequant.kyc.inputPhone

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import com.mycelium.bequant.Constants.COUNTRY_MODEL_KEY
import com.mycelium.bequant.common.loader
import com.mycelium.bequant.kyc.BequantKycViewModel
import com.mycelium.bequant.kyc.inputPhone.coutrySelector.CountrySelectorFragment
import com.mycelium.bequant.remote.client.apis.KYCApi
import com.mycelium.wallet.R
import com.mycelium.wallet.databinding.ActivityBequantKycPhoneInputBinding
import kotlinx.android.synthetic.main.activity_bequant_kyc_phone_input.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class InputPhoneFragment : Fragment(R.layout.activity_bequant_kyc_phone_input) {

    companion object {
        val CHOOSE_COUNTRY_REQUEST_CODE: Int = 102
    }

    private lateinit var activityViewModel: BequantKycViewModel
    lateinit var viewModel: InputPhoneViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProviders.of(this).get(InputPhoneViewModel::class.java)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        activity?.run {
            activityViewModel = ViewModelProviders.of(this).get(BequantKycViewModel::class.java)
        } ?: throw Throwable("invalid activity")

        activityViewModel.updateActionBarTitle("")

        activityViewModel.country.observe(viewLifecycleOwner, Observer {
            viewModel.countryModel.value = it
        })
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            DataBindingUtil.inflate<ActivityBequantKycPhoneInputBinding>(inflater, R.layout.activity_bequant_kyc_phone_input, container, false)
                    .apply {
                        viewModel = this@InputPhoneFragment.viewModel
                        lifecycleOwner = this@InputPhoneFragment
                    }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        btGetCode.setOnClickListener {
            sendCode()
        }

        tvCountry.setOnClickListener {
//            val countrySelectorFragment = CountrySelectorFragment()
//            countrySelectorFragment.setTargetFragment(this, CHOOSE_COUNTRY_REQUEST_CODE)
//            parentFragmentManager.beginTransaction()
//                    .add(countrySelectorFragment,"selector")
//                    .commit()
            findNavController().navigate(R.id.action_phoneInputToChooseCountry)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            viewModel.countryModel.value = data?.getParcelableExtra(COUNTRY_MODEL_KEY)
        }
    }

    private fun sendCode() {
        tvErrorCode.visibility = View.GONE
        loader(true)
        viewModel.getRequest()?.let {
            viewModel.viewModelScope.launch(Dispatchers.IO) {
                val postKycSaveMobilePhone = KYCApi.create().postKycSaveMobilePhone(it)
                if (postKycSaveMobilePhone.isSuccessful) {
                    findNavController().navigate(InputPhoneFragmentDirections.actionPhoneInputToPhoneVerify(it))
                } else {
                    showError(postKycSaveMobilePhone.code())
                }
            }.invokeOnCompletion {
                loader(false)
                goNext()
            }
        } ?: run {
            goNext()
            tvErrorCode.visibility = View.VISIBLE
        }
    }

    private fun goNext() {
        findNavController().navigate(R.id.action_phoneInputToPhoneVerify)
    }

    private fun showError(code: Int) {
        lifecycleScope.launch(Dispatchers.Main) {
            tvErrorCode.visibility = View.VISIBLE
        }
    }
}