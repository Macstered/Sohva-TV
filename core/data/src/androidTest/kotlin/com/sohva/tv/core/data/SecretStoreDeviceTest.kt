package com.sohva.tv.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sohva.tv.core.data.security.AndroidKeystoreKeyProvider
import com.sohva.tv.core.data.security.EnvelopeCipher
import com.sohva.tv.core.data.security.EnvelopeSpec
import com.sohva.tv.core.data.security.PrefsWrappedKeyStore
import com.sohva.tv.core.data.security.SecretStore
import com.sohva.tv.core.model.error.Outcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

/** The real Android Keystore path of spec 73 SEC-FR-01..07, on its own test alias and files. */
@RunWith(AndroidJUnit4::class)
class SecretStoreDeviceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val spec = EnvelopeSpec.MAIN.copy(
        keystoreAlias = "sohva.test.secrets",
        prefsFile = "sohva_test_secret_envelope",
    )

    private fun cipher() = EnvelopeCipher(spec, AndroidKeystoreKeyProvider(spec.keystoreAlias), PrefsWrappedKeyStore(context, spec))

    @After
    fun cleanUp() {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(spec.keystoreAlias)
        context.deleteSharedPreferences(spec.prefsFile)
        context.deleteSharedPreferences(SecretStore.FILE_NAME)
    }

    @Test
    fun valuesSurviveANewProcessWorthOfObjects() {
        val sealed = cipher().encrypt("hunter2")
        assertNotEquals("hunter2", sealed)
        // New cipher, new key provider: as after a process restart.
        assertEquals("hunter2", cipher().decrypt(sealed))
    }

    @Test
    fun storeRoundTripOffTheMainThread() = runBlocking {
        // The test's IO dispatcher stands in for AppDispatchers.io.
        @Suppress("InjectDispatcher")
        val store = SecretStore(context, cipher(), Dispatchers.IO)
        assertEquals(Outcome.Ok(Unit), store.write("pin", "1234"))
        assertEquals(Outcome.Ok("1234"), store.read("pin"))
        assertEquals(Outcome.Ok(Unit), store.write("pin", null))
        assertEquals(Outcome.Ok(null), store.read("pin"))
    }
}
