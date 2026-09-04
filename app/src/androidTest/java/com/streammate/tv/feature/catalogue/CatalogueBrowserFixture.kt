package com.streammate.tv.feature.catalogue

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.streammate.tv.app.CataloguePreferredCopy
import com.streammate.tv.core.database.StreamMateDatabase
import com.streammate.tv.core.model.CatalogueCustomGroup
import com.streammate.tv.feature.catalogue.v2.CatalogueBrowseEntry
import com.streammate.tv.feature.catalogue.v2.CatalogueBrowsePartition
import com.streammate.tv.feature.catalogue.v2.CatalogueBrowserSession
import com.streammate.tv.feature.catalogue.v2.CatalogueBrowserV2
import com.streammate.tv.iptv.repository.CatalogueRepository

/** Repository-backed production browser with deterministic Room collector teardown. */
internal class CatalogueBrowserFixture {
    private val attached = mutableStateOf(true)

    @Composable
    fun Content(
        database: StreamMateDatabase,
        preferredCopy: CataloguePreferredCopy = CataloguePreferredCopy.NONE,
        customGroups: List<CatalogueCustomGroup> = emptyList(),
        session: CatalogueBrowserSession? = null,
        initialPartition: CatalogueBrowsePartition = CatalogueBrowsePartition.PlaylistGroup(null),
        onManageGroups: ((String?) -> Unit)? = null,
        onOpenEntry: (CatalogueBrowseEntry) -> Unit = {},
    ) {
        if (!attached.value) return
        val repository = remember(database) { CatalogueRepository(database.catalogueDao()) }
        CatalogueBrowserV2(
            mode = CatalogueMode.MOVIES,
            repository = repository,
            preferredCopy = preferredCopy,
            customGroups = customGroups,
            initialPartition = initialPartition,
            onManageGroups = onManageGroups,
            session = session,
            onOpenEntry = onOpenEntry,
            onBack = {},
        )
    }

    fun dispose(rule: ComposeContentTestRule) {
        rule.runOnIdle { attached.value = false }
        rule.waitForIdle()
    }
}
