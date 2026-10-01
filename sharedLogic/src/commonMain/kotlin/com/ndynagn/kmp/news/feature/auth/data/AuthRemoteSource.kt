package com.ndynagn.kmp.news.feature.auth.data

import com.ndynagn.kmp.news.feature.auth.domain.AuthFailure
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class AuthUserDto(val id: String, val email: String)

@Serializable
internal data class AuthTokenDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    val user: AuthUserDto,
)

@Serializable
internal data class StoredAuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val user: AuthUserDto,
)

internal sealed interface AuthResponse<out T> {
    data class Success<T>(val value: T) : AuthResponse<T>
    data class Failed(val failure: AuthFailure) : AuthResponse<Nothing>
}

internal interface AuthRemoteSource {
    suspend fun signIn(email: String, password: String): AuthResponse<AuthTokenDto>
    suspend fun register(email: String, password: String): AuthResponse<Unit>
    suspend fun confirm(email: String, code: String): AuthResponse<AuthTokenDto>
    suspend fun resend(email: String): AuthResponse<Unit>
    suspend fun requestRecovery(email: String): AuthResponse<Unit>
    suspend fun verifyRecovery(email: String, code: String): AuthResponse<AuthTokenDto>
    suspend fun resetPassword(token: String, password: String): AuthResponse<AuthUserDto>
    suspend fun refresh(token: String): AuthResponse<AuthTokenDto>
    suspend fun user(token: String): AuthResponse<AuthUserDto>
    suspend fun logout(token: String): AuthResponse<Unit>
}
