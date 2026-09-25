package com.sohva.tv.core.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * One organisation rule (spec 42 ORG-FR-08, plan/04 §15.7): the household's choice for a group or
 * an item of a room. The source of truth and the backup format; a background pass resolves the
 * rules into `content_group` (shown, position, sort) and the items' `visible` and `item_position`,
 * so no screen evaluates rules per row (§9.1). Null fields have no opinion.
 */
@Entity(
    tableName = "organization_rule",
    primaryKeys = ["room", "source_id", "group_key", "item_key"],
    indices = [Index(value = ["item_key"])],
)
data class OrganizationRuleEntity(
    val room: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "group_key") val groupKey: String,
    @ColumnInfo(name = "item_key") val itemKey: String,
    val enabled: Boolean?,
    @ColumnInfo(name = "sort_mode") val sortMode: String?,
    val position: Long?,
)
