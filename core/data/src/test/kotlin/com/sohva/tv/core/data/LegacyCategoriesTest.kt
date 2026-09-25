package com.sohva.tv.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.migration.Beta23HiddenCategories
import com.sohva.tv.core.data.org.Field
import com.sohva.tv.core.data.org.LegacyCategories
import com.sohva.tv.core.data.org.OrgRules
import com.sohva.tv.core.data.org.RuleChange
import com.sohva.tv.core.model.org.OrgRoom
import com.sohva.tv.core.model.org.OrgRule
import com.sohva.tv.core.model.org.RuleKey
import com.sohva.tv.core.model.org.RuleValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Spec 42 ORG-15 / ORG-FR-33: beta 23's hidden categories become rules once, never over a rule. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LegacyCategoriesTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, SohvaDatabase::class.java).allowMainThreadQueries().build()

    @After
    fun close() {
        db.close()
        context.preferencesDataStoreFile(Beta23HiddenCategories.OLD_FILE).delete()
    }

    @Test
    fun hiddenNamesBecomeOffRulesExceptWhereARuleExistsAndOnlyOnce() {
        val kept = OrgRule(RuleKey(OrgRoom.MOVIES, "", "name:drama", ""), RuleValue(enabled = true))
        val changes = LegacyCategories.changes(listOf(kept), mapOf(OrgRoom.LIVE to setOf(" Sport ", ""), OrgRoom.MOVIES to setOf("Drama", "Kids")))
        assertEquals(
            listOf(
                RuleChange(RuleKey(OrgRoom.LIVE, "", "name:sport", ""), enabled = Field.Set(false)),
                RuleChange(RuleKey(OrgRoom.MOVIES, "", "name:kids", ""), enabled = Field.Set(false)),
                RuleChange(LegacyCategories.MARKER, enabled = Field.Set(true)),
            ),
            changes,
        )
        val marked = listOf(OrgRule(LegacyCategories.MARKER, RuleValue(enabled = true)))
        assertTrue(LegacyCategories.changes(marked, mapOf(OrgRoom.LIVE to setOf("News"))).isEmpty())
    }

    @Test
    fun theOldPreferencesFileIsReadOnce() = runBlocking {
        val file = context.preferencesDataStoreFile(Beta23HiddenCategories.OLD_FILE)
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }).edit {
            it[stringSetPreferencesKey("hidden_live_categories")] = setOf("Adults")
            it[stringSetPreferencesKey("hidden_series_categories")] = setOf("Soaps")
        }
        scope.coroutineContext[Job]!!.cancelAndJoin()
        val migration = Beta23HiddenCategories(context, db, Dispatchers.IO)
        migration.run()
        val rules = OrgRules(db)
        val keys = { rules.all().map { it.key.room.name + "|" + it.key.groupKey + "|" + it.value.enabled }.sorted() }
        assertEquals(listOf("LIVE|@legacy-v1|true", "LIVE|name:adults|false", "SERIES|name:soaps|false"), keys())
        // Shown again by the viewer: a second run must not hide it again.
        rules.change(listOf(RuleChange(RuleKey(OrgRoom.LIVE, "", "name:adults", ""), enabled = Field.Set(null))))
        migration.run()
        assertEquals(listOf("LIVE|@legacy-v1|true", "SERIES|name:soaps|false"), keys())
    }
}
