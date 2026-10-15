package com.ndynagn.kmp.news.feature.componentcatalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/** Retains one demo flow over rotation and disposes it on explicit exit. */
internal class CatalogAuthFlowOwner :
    ViewModel(),
    ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()

    fun close() {
        viewModelStore.clear()
    }

    override fun onCleared() {
        close()
    }
}
