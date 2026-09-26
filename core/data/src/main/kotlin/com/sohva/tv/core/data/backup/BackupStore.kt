package com.sohva.tv.core.data.backup

import com.sohva.tv.core.data.database.AppMetaEntity
import com.sohva.tv.core.data.database.SohvaDatabase
import com.sohva.tv.core.data.org.OrgPass
import com.sohva.tv.core.data.org.OrgRules
import com.sohva.tv.core.data.vod.LibraryPasses
import com.sohva.tv.core.model.org.RuleKey
import com.sohva.tv.core.model.vod.PreferredCopy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The backup's database steps for the app's restore (spec 71 §4.5): [BackupData] off the main
 * thread, the restore-in-progress marker (§8), and the passes that apply restored rules.
 */
class BackupStore(private val db: SohvaDatabase, private val io: CoroutineDispatcher) {
    private val rows = BackupData(db)

    suspend fun channels(sources: Set<String>, logo: (String) -> ByteArray?): List<BackupChannel> = withContext(io) { rows.channels(sources, logo) }

    suspend fun lists(): List<BackupList> = withContext(io) { rows.lists() }

    suspend fun members(): List<BackupMember> = withContext(io) { rows.members() }

    suspend fun organisation(): Pair<List<BackupRule>, List<BackupAlias>> = withContext(io) { rows.organisation() }

    suspend fun profileRows(profileId: String): ProfileKept = withContext(io) { rows.profileRows(profileId) }

    suspend fun replace(payload: BackupPayload, now: Long): RestoredRows = withContext(io) { rows.replace(payload, now) }

    suspend fun setLogo(channelKey: String, url: String?) = withContext(io) { rows.setLogo(channelKey, url) }

    /** A profile the backup does not have leaves with everything it kept (PROF-FR-06). */
    suspend fun deleteProfile(profileId: String) = db.profiles().deleteProfile(profileId)

    suspend fun markRestoring(on: Boolean) = db.appMeta().put(AppMetaEntity(RESTORING, if (on) "1" else ""))

    /** Whether the last restore stopped half-way (spec 71 §8). */
    suspend fun unfinished(): Boolean = !db.appMeta().value(RESTORING).isNullOrEmpty()

    /** Resolves the organisation again for the rules before and after the restore (ORG-05). */
    suspend fun applyRules(keys: Collection<RuleKey>, preferred: PreferredCopy) = withContext(io) {
        OrgPass(db, OrgRules(db), LibraryPasses(db)).afterChange(keys, preferred)
    }

    private companion object {
        const val RESTORING = "restore_in_progress"
    }
}
