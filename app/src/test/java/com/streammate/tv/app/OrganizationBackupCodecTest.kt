package com.streammate.tv.app

import com.streammate.tv.core.database.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class OrganizationBackupCodecTest {
    @Test fun roundTripPreservesHiddenStateManualRanksAndAliases() {
        val snapshot = OrganizationSnapshot(listOf(OrganizationRuleEntity("MOVIES", "one", "id:1", "film:one", false, "MANUAL", 5)), listOf(OrganizationAliasEntity("vod:movie:one:a", "film:one")))
        assertEquals(snapshot, organizationFromBackupJson(snapshot.toBackupJson()))
    }
    @Test(expected = IllegalArgumentException::class) fun malformedRankRejected() {
        organizationFromBackupJson(OrganizationSnapshot(listOf(OrganizationRuleEntity("MOVIES", "", "", "a", true, null, -1))).toBackupJson())
    }
    @Test(expected = IllegalArgumentException::class) fun unknownSortRejected() {
        organizationFromBackupJson(OrganizationSnapshot(listOf(OrganizationRuleEntity("MOVIES", "", "", "a", true, "bad", null))).toBackupJson())
    }
    @Test(expected = IllegalArgumentException::class) fun duplicateKeysRejectedBeforeRestore() {
        val row = OrganizationRuleEntity("LIVE", "", "name:sports", "", false, null, null)
        organizationFromBackupJson(OrganizationSnapshot(listOf(row, row)).toBackupJson())
    }
}
