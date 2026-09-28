package com.sohva.tv.app.settings

import com.sohva.tv.app.AppGraph
import com.sohva.tv.core.model.home.HomeLayout
import com.sohva.tv.core.net.phone.LocalAddress
import com.sohva.tv.core.net.phone.PhoneMode
import com.sohva.tv.core.net.phone.PhoneState
import com.sohva.tv.core.net.phone.QrCodes
import com.sohva.tv.feature.settings.HomeLayoutServices
import com.sohva.tv.feature.settings.HomeLayoutView
import com.sohva.tv.feature.settings.ListChoice
import com.sohva.tv.feature.settings.ListFinding
import com.sohva.tv.feature.settings.ListPhone
import com.sohva.tv.feature.trakt.protocol.TraktListRef
import com.sohva.tv.feature.trakt.shelf.TraktRowSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings › Home from the app graph (spec 02 HOME-FR-90): the active profile's layout, read and
 * written off the main thread. A restricted profile, or a build without Trakt or Sohva Sport, does
 * not list the rows it can never have (HOME-FR-88). Trakt rows can be added when Trakt is there;
 * watchlists only while the profile has an account (HOME-FR-94); public lists by address, number,
 * name or from the phone (HOME-FR-99, -100).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppHomeLayoutSettings(private val graph: AppGraph) : HomeLayoutServices {
    private val io get() = graph.dispatchers.io

    override val current: Flow<HomeLayoutView> = graph.data.profiles.household.flatMapLatest { household ->
        val active = household.active
        val restricted = active.id in graph.data.profiles.restrictedIds()
        val host = graph.trakt?.takeIf { graph.flags.trakt && !restricted && it.configured }
        val unavailable = buildSet {
            if (!graph.flags.trakt || restricted) addAll(listOf(HomeLayout.WATCH_NEXT, HomeLayout.RECOMMENDED))
            if (!graph.flags.sport) add(HomeLayout.SPORT)
        }
        val account: Flow<Boolean> = if (host == null) {
            flowOf(false)
        } else {
            flow {
                host.account(active.id)
                emitAll(host.accounts.map { it[active.id] != null })
            }.distinctUntilChanged()
        }
        val stored = host?.rowLists?.lists ?: flowOf(emptyMap())
        combine(graph.data.preferences.homeLayout(active.id), account, stored) { layout, signedIn, lists ->
            val addable = if (host == null) emptyList() else HomeLayout.ADDABLE.filter { signedIn || it !in HomeLayout.NEEDS_ACCOUNT }
            // A public list's name: read from the store once, then served from memory with its titles.
            val names = HashMap<String, String>()
            for (id in layout.added) {
                val source = HomeLayout.traktListId(id)?.let(TraktRowSource::list) ?: continue
                val title = if (source.key(active.id) in lists) lists[source.key(active.id)]?.title else host?.rowLists?.read(active.id, source)?.title
                title?.let { names[id] = it }
            }
            val listsOk = host != null
            val gone = layout.added.filter { it !in addable && !(listsOk && HomeLayout.traktListId(it) != null) }
            HomeLayoutView(active.name.takeIf { household.several }, layout, unavailable + gone, addable, names, lists = listsOk)
        }
    }.flowOn(io)

    override suspend fun save(layout: HomeLayout) = withContext(io) {
        val profile = graph.data.profiles.activeId
        graph.data.preferences.setHomeLayout(profile, layout)
        // A Trakt row shown again or just added is fetched now rather than at the next 15-minute cycle (HOME-FR-87, -94).
        val traktShown = layout.isShown(HomeLayout.WATCH_NEXT) || layout.isShown(HomeLayout.RECOMMENDED) || layout.added.any(layout::isShown)
        if (traktShown) graph.trakt?.requestSync(profile)
    }

    override suspend fun findLists(text: String): ListFinding = AppTraktLists.find(graph, text)

    override suspend fun addList(list: ListChoice) {
        AppTraktLists.add(graph, list)
    }

    // ---- The phone page in Trakt list mode (HOME-FR-100) ----

    private val added = MutableStateFlow<List<String>>(emptyList())

    override val listPhone: Flow<ListPhone> = combine(graph.phone.server.state, added) { state, names ->
        when (state) {
            PhoneState.NoNetwork -> ListPhone.NoNetwork
            is PhoneState.Running -> ListPhone.Open(state.url, withContext(io) { QrCodes.of(state.url) }, names)
            PhoneState.Stopped -> ListPhone.Closed
        }
    }.distinctUntilChanged()

    override fun openListPhone() {
        added.value = emptyList()
        graph.phone.onTraktList = { text -> AppTraktLists.addFromPhone(graph, text)?.also { name -> added.value = added.value + name } }
        graph.appScope.launch(io) { graph.phone.server.start(LocalAddress.current(), PhoneMode.TraktList { TraktListRef.parseAddress(it) != null }) }
    }

    override fun closeListPhone() {
        graph.phone.onTraktList = null
        graph.appScope.launch(io) { graph.phone.server.stop() }
    }
}
