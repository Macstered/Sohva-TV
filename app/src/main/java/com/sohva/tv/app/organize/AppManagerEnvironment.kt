package com.sohva.tv.app.organize

import com.sohva.tv.app.AppGraph
import com.sohva.tv.core.data.database.ManagerSourceRow
import com.sohva.tv.core.data.org.ManagedGroup
import com.sohva.tv.core.data.org.ManagedItem
import com.sohva.tv.core.data.org.OrgPass
import com.sohva.tv.core.data.org.OrgRules
import com.sohva.tv.core.data.org.RuleChange
import com.sohva.tv.core.data.vod.LibraryPasses
import com.sohva.tv.core.model.error.Outcome
import com.sohva.tv.core.model.org.OrgRoom
import com.sohva.tv.core.model.org.OrgRule
import com.sohva.tv.feature.organize.ManagerEnvironment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/** Where the manager goes: channel management ("Advanced") and back. */
interface ManagerNavigation {
    fun openAdvanced()

    fun leave()
}

/**
 * The library manager's side of the graph (plan/03 §4.6). A save writes the rules and resolves
 * them into the library on io, not the bulk thread: it is a viewer-started action that must not
 * wait behind an import (spec 42 §9.5). Guide and walls follow through their invalidation flows.
 */
class AppManagerEnvironment(private val graph: AppGraph, private val navigation: ManagerNavigation) : ManagerEnvironment {
    private val io get() = graph.dispatchers.io
    private val data get() = graph.data

    override suspend fun sources(): List<ManagerSourceRow> = data.organization.sources()

    override suspend fun groups(room: OrgRoom, scope: String?): List<ManagedGroup> = data.organization.groups(room, scope)

    override suspend fun items(room: OrgRoom, group: ManagedGroup, search: String?): List<ManagedItem> = data.organization.items(room, group, search)

    override suspend fun rules(room: OrgRoom): List<OrgRule> = withContext(io) { OrgRules(data.database).of(room) }

    override suspend fun apply(changes: List<RuleChange>): Outcome<List<RuleChange>> = withContext(io) {
        val db = data.database
        val rules = OrgRules(db)
        val undo = rules.change(changes)
        if (undo is Outcome.Ok) OrgPass(db, rules, LibraryPasses(db)).afterChange(changes.map { it.key }, data.preferences.preferredCopy())
        undo
    }

    override val showHidden: Flow<Boolean> get() = flow { emitAll(data.preferences.editorsShowHidden) }.flowOn(io)

    override suspend fun setShowHidden(value: Boolean) = withContext(io) { data.preferences.setEditorsShowHidden(value) }

    override suspend fun location(room: OrgRoom): Pair<String?, String?> = withContext(io) { data.preferences.managerLocation(room.name) }

    override suspend fun saveLocation(room: OrgRoom, group: String?, source: String?) = withContext(io) {
        data.preferences.setManagerLocation(room.name, group, source)
    }

    override fun openAdvanced() = navigation.openAdvanced()

    override fun leave() = navigation.leave()
}
