package com.sohva.tv.app.settings

import android.content.Context
import androidx.core.os.ConfigurationCompat
import com.sohva.tv.app.AppGraph
import com.sohva.tv.app.AppLocales
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.phone.PhoneSetupState
import com.sohva.tv.core.net.phone.PhoneAnswers
import com.sohva.tv.core.net.phone.PhonePageTexts
import com.sohva.tv.core.net.phone.PhoneReceiver
import com.sohva.tv.core.net.phone.PhoneServer
import com.sohva.tv.core.net.phone.PhoneState
import com.sohva.tv.core.net.phone.PhoneSubmission
import com.sohva.tv.ui.design.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/**
 * The phone setup page's app side (spec 11): nothing exists until Settings first opens it
 * (§9: zero cost when closed). A received source is saved through the source store with the
 * Settings rules and synced at once; keys go to the encrypted service keys (PHONE-FR-31).
 */
class PhoneSetup(private val graph: AppGraph) {
    val server: PhoneServer by lazy { PhoneServer(ResourceAnswers(graph.app), Receiver()) }

    fun state(): Flow<PhoneSetupState> = server.state.map { state ->
        when (state) {
            PhoneState.Stopped -> PhoneSetupState.Closed
            PhoneState.NoNetwork -> PhoneSetupState.NoNetwork
            is PhoneState.Running -> PhoneSetupState.Open(state.url, state.received, state.lastSource, state.lastWasKeys)
        }
    }

    /** On the page's own thread, which blocks until the save is done (PHONE-FR-31). */
    private inner class Receiver : PhoneReceiver {
        override fun receive(submission: PhoneSubmission): Boolean = runBlocking {
            when (submission) {
                is PhoneSubmission.NewSource -> when (val saved = graph.data.sources.save(submission.config)) {
                    is Outcome.Failed -> false
                    is Outcome.Ok -> {
                        graph.sync.scheduler.syncNow(saved.value.source.id)
                        true
                    }
                }
                is PhoneSubmission.Keys -> {
                    val keys = graph.data.serviceKeys
                    val tmdb = submission.tmdbToken?.let { keys.saveTmdb(it) }
                    val sports = submission.apiSportsKey?.let { keys.saveApiSports(it) }
                    tmdb !is Outcome.Failed && sports !is Outcome.Failed
                }
            }
        }
    }
}

/** The page's texts in the interface language, read afresh for every request (spec 11 lesson 9). */
private class ResourceAnswers(private val app: Context) : PhoneAnswers {
    private fun context(): Context = AppLocales.wrap(app)

    override fun page(): PhonePageTexts {
        val c = context()
        return PhonePageTexts(
            languageTag = (ConfigurationCompat.getLocales(c.resources.configuration)[0] ?: java.util.Locale.ENGLISH).toLanguageTag(),
            title = c.getString(R.string.phone_setup_page_title),
            intro = c.getString(R.string.phone_setup_page_intro),
            xtream = c.getString(R.string.phone_setup_page_xtream),
            m3u = c.getString(R.string.phone_setup_page_m3u),
            name = c.getString(R.string.phone_setup_page_name),
            xtreamUrl = c.getString(R.string.phone_setup_page_xtream_url),
            username = c.getString(R.string.phone_setup_page_username),
            password = c.getString(R.string.phone_setup_page_password),
            m3uUrl = c.getString(R.string.phone_setup_page_m3u_url),
            xmltvUrl = c.getString(R.string.phone_setup_page_xmltv_url),
            keys = c.getString(R.string.phone_setup_page_keys),
            keysHelp = c.getString(R.string.phone_setup_page_keys_help),
            tmdb = c.getString(R.string.phone_setup_page_tmdb),
            apiSports = c.getString(R.string.phone_setup_page_api_sports),
            send = c.getString(R.string.phone_setup_page_send),
            privacy = c.getString(R.string.phone_setup_page_privacy),
        )
    }

    override fun saved(sourceName: String): String = context().getString(R.string.phone_setup_page_saved, sourceName)

    override fun keysSaved(): String = context().getString(R.string.phone_setup_page_keys_saved)

    override fun invalid(): String = context().getString(R.string.phone_setup_page_invalid)

    override fun failed(): String = context().getString(R.string.phone_setup_page_failed)

    override fun forbidden(): String = context().getString(R.string.phone_setup_page_forbidden)

    override fun badRequest(): String = context().getString(R.string.phone_setup_page_bad_request)
}
