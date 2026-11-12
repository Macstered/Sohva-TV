package com.sohva.tv.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sohva.tv.core.data.security.EnvelopeCipher
import com.sohva.tv.core.data.security.EnvelopeSpec
import com.sohva.tv.core.data.security.KeyProvider
import com.sohva.tv.core.data.security.SecretStore
import com.sohva.tv.core.data.security.WrappedKeyStore
import com.sohva.tv.core.model.error.AppError
import com.sohva.tv.core.model.error.Outcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.ProviderException

/**
 * A Keystore that fails in a way the platform does not declare (seen as `ProviderException` and
 * `IllegalStateException` on TV boxes with a broken keymaster) is a failed result, never an
 * exception that reaches the caller's scope and ends the process.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SecretStoreFailuresTest {
    private class MemoryKeyStore : WrappedKeyStore {
        var value: String? = null
        override fun read(): String? = value
        override fun write(value: String): Boolean {
            this.value = value
            return true
        }
    }

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun store(keys: KeyProvider): SecretStore =
        SecretStore(context, EnvelopeCipher(EnvelopeSpec.MAIN, keys, MemoryKeyStore()), Dispatchers.Unconfined)

    private fun stored(key: String) {
        context.getSharedPreferences(SecretStore.FILE_NAME, Context.MODE_PRIVATE).edit().putString(key, "v2:00:00").commit()
    }

    @Test
    fun aProviderFailureIsUnreadableSecretsNotACrash() = runBlocking {
        val store = store { throw ProviderException("keymaster is gone") }
        stored("pin")
        assertEquals(Outcome.Failed(AppError.SecretsUnreadable), store.read("pin"))
        assertEquals(Outcome.Failed(AppError.SecretsUnreadable), store.write("pin", "1234"))
    }

    @Test
    fun writableSaysWhetherASecretCouldBeStoredAndStoresNothing() = runBlocking {
        val software = javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        assertEquals(true, store { software }.writable())
        assertEquals(false, store { throw ProviderException("keymaster is gone") }.writable())
        assertEquals(emptyMap<String, Any?>(), context.getSharedPreferences(SecretStore.FILE_NAME, Context.MODE_PRIVATE).all)
    }

    @Test
    fun aMissingKeyThatMayNotBeCreatedIsAFailedResult() = runBlocking {
        val store = store { error("keystore key is missing") }
        stored("pin")
        assertEquals(Outcome.Failed(AppError.Unknown), store.read("pin"))
        assertEquals(Outcome.Failed(AppError.Unknown), store.write("pin", "1234"))
    }
}
