package com.sohva.tv.core.data.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * The Keystore wrapping key (spec 73 SEC-FR-01): AES-256, GCM, no padding, no user
 * authentication, provider-chosen IVs. Loaded once per process; a Keystore load is a binder
 * round trip, slow on a Cortex-A35 box with a software keymaster.
 */
class AndroidKeystoreKeyProvider(
    private val alias: String,
    private val createIfMissing: Boolean = true,
) : KeyProvider {
    @Volatile
    private var key: SecretKey? = null

    override fun wrappingKey(): SecretKey = key ?: synchronized(this) {
        key ?: load().also { key = it }
    }

    private fun load(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        check(createIfMissing) { "keystore key is missing" }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}

/** Keeps the wrapped data key in its own preferences file, written with a synchronous commit. */
class PrefsWrappedKeyStore(context: Context, private val spec: EnvelopeSpec) : WrappedKeyStore {
    private val prefs: SharedPreferences = context.getSharedPreferences(spec.prefsFile, Context.MODE_PRIVATE)

    override fun read(): String? = prefs.getString(spec.wrappedKeyName, null)

    override fun write(value: String): Boolean = prefs.edit().putString(spec.wrappedKeyName, value).commit()
}
