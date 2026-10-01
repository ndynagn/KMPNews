package com.ndynagn.kmp.news.feature.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.ndynagn.kmp.news.feature.auth.data.AuthSessionStorage
import com.ndynagn.kmp.news.feature.auth.data.AuthStorageRead
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-GCM session file excluded from backup; the non-exportable key belongs to this installation. */
class AndroidAuthStorage(context: Context) : AuthSessionStorage {
    private val file = AtomicFile(File(context.noBackupFilesDir, "auth-session.bin"))
    private val alias = "kmpnews.auth.session"

    @Synchronized
    override fun read(): AuthStorageRead = try {
        if (!file.baseFile.exists()) {
            AuthStorageRead(null, false)
        } else {
            val buffer = ByteBuffer.wrap(file.readFully())
            val ivSize = buffer.int

            require(ivSize == 12)

            val iv = ByteArray(ivSize)

            buffer.get(iv)

            val ciphertext = ByteArray(buffer.remaining())

            buffer.get(ciphertext)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")

            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            AuthStorageRead(cipher.doFinal(ciphertext).toString(Charsets.UTF_8), false)
        }
    } catch (_: Exception) {
        AuthStorageRead(null, true)
    }

    @Synchronized
    override fun write(value: String?): Boolean = try {
        if (value == null) {
            file.delete()
            !file.baseFile.exists()
        } else {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val bytes = ByteBuffer.allocate(4 + cipher.iv.size + encrypted.size)
                .putInt(cipher.iv.size).put(cipher.iv).put(encrypted).array()
            val stream = file.startWrite()
            try {
                stream.write(bytes)
                file.finishWrite(stream)
                true
            } catch (failure: Exception) {
                file.failWrite(stream)
                throw failure
            }
        }
    } catch (_: Exception) {
        false
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore")
        store.load(null)

        (store.getKey(alias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )

        return generator.generateKey()
    }
}
