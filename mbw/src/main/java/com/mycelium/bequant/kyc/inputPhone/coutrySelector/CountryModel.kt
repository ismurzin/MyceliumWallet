package com.mycelium.bequant.kyc.inputPhone.coutrySelector

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class CountryModel(val name: String, val acronym:String, val code: Int) : Parcelable