package com.streammate.tv.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class AndroidKeystoreKeyProvider(
    private val alias: String = DEFAULT_ALIAS,
) {

    /**
     * The keystore handle is stable for the life of the process, so it is
     * cached. Without this, every encrypt and decrypt reloads it from the
     * secure element — once per catalogue row during an import, each one a
     * synchronised binder round trip.
     */
    @Volatile
    private var cachedKey: SecretKey? = null

    fun getOrCreate(): SecretKey = cachedKey ?: loadOrCreate()

    /**
     * Drops the cached handle so the next call reads the keystore again. Call
     * this if a crypto operation fails in a way that suggests the key was
     * replaced or invalidated underneath us.
     */
    @Synchronized
    fun invalidate() {
        cachedKey = null
    }

    @Synchronized
    private fun loadOrCreate(): SecretKey {
        cachedKey?.let { return it }

        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { existing ->
            cachedKey = existing
            return existing
        }

        val specification = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(specification)
            generateKey()
        }.also { cachedKey = it }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val DEFAULT_ALIAS = "sportmate.iptv.v1"
    }
}
