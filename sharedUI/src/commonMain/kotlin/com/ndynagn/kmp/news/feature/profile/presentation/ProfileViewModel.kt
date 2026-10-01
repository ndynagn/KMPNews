package com.ndynagn.kmp.news.feature.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ndynagn.kmp.news.feature.auth.domain.AuthFailure
import com.ndynagn.kmp.news.feature.auth.domain.AuthRepository
import com.ndynagn.kmp.news.feature.auth.domain.AuthSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class ProfileViewModel(private val authRepository: AuthRepository) : ViewModel() {
    val session = authRepository.session
    private val mutableNotice = MutableStateFlow<AuthFailure?>(null)
    val notice = mutableNotice.asStateFlow()
    private val mutableBusy = MutableStateFlow(false)
    val isBusy = mutableBusy.asStateFlow()
    private var operation: Job? = null

    init {
        viewModelScope.launch {
            session.collect { if (it is AuthSession.Authenticated) mutableNotice.value = null }
        }
    }

    fun restore() {
        if (operation?.isActive == true) return
        operation = viewModelScope.launch {
            mutableBusy.value = true
            try {
                authRepository.restore()
            } finally {
                mutableBusy.value = false
            }
        }
    }

    fun signOut() {
        if (mutableBusy.value) return
        operation?.cancel()
        operation = viewModelScope.launch {
            mutableBusy.value = true
            try {
                mutableNotice.value = authRepository.signOut().failure
            } finally {
                mutableBusy.value = false
            }
        }
    }
}
