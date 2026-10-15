package com.ndynagn.kmp.news.feature.profile.data

import com.ndynagn.kmp.news.feature.profile.domain.ProfileFailure
import kotlinx.serialization.json.JsonObject

internal data class ProfileUserDto(val id: String, val email: String, val metadata: JsonObject)

internal sealed interface ProfileResponse<out T> {
    data class Success<T>(val value: T) : ProfileResponse<T>
    data class Failed(val failure: ProfileFailure) : ProfileResponse<Nothing>
}

internal interface ProfileRemoteSource {
    suspend fun fetch(token: String): ProfileResponse<ProfileUserDto>
    suspend fun update(token: String, metadata: JsonObject): ProfileResponse<ProfileUserDto>
    suspend fun upload(token: String, path: String, jpeg: ByteArray): ProfileResponse<Unit>
    suspend fun delete(token: String, paths: List<String>): ProfileResponse<Unit>
    suspend fun sign(token: String, path: String): ProfileResponse<String>
}
