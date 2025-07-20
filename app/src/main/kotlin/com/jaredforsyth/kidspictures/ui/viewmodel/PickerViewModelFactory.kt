package com.jaredforsyth.kidspictures.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class PickerViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PickerViewModel::class.java)) {
            return PickerViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}