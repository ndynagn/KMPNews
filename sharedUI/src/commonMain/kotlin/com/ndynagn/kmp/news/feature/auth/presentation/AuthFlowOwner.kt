package com.ndynagn.kmp.news.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/** Retains form ViewModels across host recreation; explicit navigation exit clears credentials and requests. */
internal class AuthFlowOwner :
    ViewModel(),
    ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()

    fun closeFlow() {
        viewModelStore.clear()
    }

    override fun onCleared() {
        closeFlow()
    }
}
