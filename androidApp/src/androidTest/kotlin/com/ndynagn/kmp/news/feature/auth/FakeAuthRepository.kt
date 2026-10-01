package com.ndynagn.kmp.news.feature.auth

import com.ndynagn.kmp.news.feature.auth.domain.AuthRepository
import com.ndynagn.kmp.news.feature.auth.domain.AuthResult
import com.ndynagn.kmp.news.feature.auth.domain.AuthSession
import com.ndynagn.kmp.news.feature.auth.domain.AuthUser
import com.ndynagn.kmp.news.feature.auth.domain.PasswordRecovery
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeAuthRepository : AuthRepository {
    override val session = MutableStateFlow<AuthSession>(AuthSession.Guest)
    override suspend fun restore() = AuthResult()
    override suspend fun signIn(email: String, password: String): AuthResult {
        session.value = AuthSession.Authenticated(AuthUser("fixture", email))
        return AuthResult()
    }
    override suspend fun register(email: String, password: String) = AuthResult()
    override suspend fun confirm(email: String, code: String) = signIn(email, "")
    override suspend fun resend(email: String) = AuthResult()

    override fun passwordRecovery(): PasswordRecovery = error("Recovery is not used by Android UI fixtures")

    override suspend fun signOut(): AuthResult {
        session.value = AuthSession.Guest
        return AuthResult()
    }
}
