package com.sohva.tv.feature.trakt.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sohva.tv.core.model.phone.QrMatrix
import com.sohva.tv.core.net.phone.QrCodes
import com.sohva.tv.feature.trakt.TraktHost
import com.sohva.tv.feature.trakt.account.SignInFailure
import com.sohva.tv.feature.trakt.account.SignInResult
import com.sohva.tv.feature.trakt.store.TraktAccount
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The pairing block while a sign-in waits (FR-02 step 3); the address and code are Trakt's. */
class TraktPrompt(val url: String, val code: String, val qr: QrMatrix?) {
    override fun toString(): String = "TraktPrompt"
}

/** The message line (FR-04): a failure, or the background stop. */
enum class TraktMessage { DECLINED, EXPIRED, UNUSABLE, RATE_LIMITED, CONFIGURATION, BACKGROUND, OTHER }

data class TraktPanelState(
    val configured: Boolean,
    val account: TraktAccount? = null,
    val signingIn: Boolean = false,
    val prompt: TraktPrompt? = null,
    val message: TraktMessage? = null,
    /** With RATE_LIMITED: the whole minutes Trakt asked us to wait, rounded up. */
    val waitMinutes: Int? = null,
)

/**
 * Settings › Accounts (spec 51 §4.3, FR-07): keyed by the active profile, one sign-in at a time
 * owned by the panel. Cancel, Back, leaving the panel or the app going to the background stop it
 * and its HTTP call.
 */
class TraktPanelModel(private val host: TraktHost, private val profile: String) : ViewModel() {
    private val _state = MutableStateFlow(TraktPanelState(configured = host.configured))
    val state: StateFlow<TraktPanelState> = _state.asStateFlow()
    private var attempt: Job? = null

    init {
        viewModelScope.launch { _state.update { it.copy(account = host.account(profile)) } }
        viewModelScope.launch { host.accounts.collect { all -> if (profile in all) _state.update { it.copy(account = all[profile]) } } }
    }

    /** The primary button: Connect / Sign in again, or Cancel sign-in while one runs. */
    fun primary() = if (_state.value.signingIn) cancel(null) else start()

    private fun start() {
        if (!host.configured) return
        attempt?.cancel()
        _state.update { it.copy(signingIn = true, prompt = null, message = null) }
        // Off the main thread: the clients parse their answers where they are called (AGENTS.md §4 rule 1).
        attempt = viewModelScope.launch(host.dispatchers.io) {
            val result = host.signIn(profile).run { code ->
                _state.update { it.copy(prompt = TraktPrompt(code.verificationUrl, code.userCode, null)) }
                viewModelScope.launch {
                    val qr = withContext(host.dispatchers.io) { QrCodes.of(code.verificationUrl) }
                    _state.update { s -> s.prompt?.takeIf { it.url == code.verificationUrl }?.let { s.copy(prompt = TraktPrompt(it.url, it.code, qr)) } ?: s }
                }
            }
            when (result) {
                is SignInResult.Success -> {
                    host.saveSignIn(profile, result)
                    _state.update { it.copy(signingIn = false, prompt = null, message = null) }
                }
                is SignInResult.Failed -> {
                    val wait = host.gate.waitSeconds().takeIf { result.reason == SignInFailure.RATE_LIMITED && it > 0 }?.let { ((it + 59) / 60).toInt() }
                    _state.update { it.copy(signingIn = false, prompt = null, message = message(result.reason), waitMinutes = wait) }
                }
            }
        }
    }

    private fun message(reason: SignInFailure): TraktMessage? = when (reason) {
        SignInFailure.DECLINED -> TraktMessage.DECLINED
        SignInFailure.EXPIRED -> TraktMessage.EXPIRED
        SignInFailure.UNUSABLE -> TraktMessage.UNUSABLE
        SignInFailure.RATE_LIMITED -> TraktMessage.RATE_LIMITED
        SignInFailure.CONFIGURATION -> TraktMessage.CONFIGURATION
        SignInFailure.ACCESS -> null
        SignInFailure.OTHER -> TraktMessage.OTHER
    }

    /** Cancel sign-in, Back while signing in (first Back), or ON_STOP with its own message. */
    fun cancel(message: TraktMessage?) {
        val running = attempt?.isActive == true
        attempt?.cancel()
        attempt = null
        _state.update { it.copy(signingIn = false, prompt = null, message = if (running) message else it.message) }
    }

    fun disconnect() {
        cancel(null)
        viewModelScope.launch {
            host.disconnect(profile)
            _state.update { it.copy(account = null, message = null) }
        }
    }

    override fun onCleared() {
        attempt?.cancel()
    }
}
