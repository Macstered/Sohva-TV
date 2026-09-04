package com.streammate.tv.app

import com.streammate.tv.core.database.OrganizationAliasEntity
import com.streammate.tv.core.database.OrganizationRuleEntity
import com.streammate.tv.core.database.OrganizationSnapshot
import com.streammate.tv.core.database.validateOrganizationSnapshot
import kotlinx.serialization.json.*

internal fun OrganizationSnapshot.toBackupJson(): JsonObject = buildJsonObject {
    put("rules", JsonArray(rules.map { rule -> buildJsonObject {
        put("room", rule.room)
        put("sourceId", rule.sourceId)
        put("groupKey", rule.groupKey)
        put("itemKey", rule.itemKey)
        put("enabled", rule.enabled?.let(::JsonPrimitive) ?: JsonNull)
        put("sortMode", rule.sortMode?.let(::JsonPrimitive) ?: JsonNull)
        put("position", rule.position?.let(::JsonPrimitive) ?: JsonNull)
    } }))
    put("aliases", JsonArray(aliases.map { alias -> buildJsonObject {
        put("alias", alias.alias)
        put("identity", alias.identity)
    } }))
}

internal fun organizationFromBackupJson(json: JsonObject): OrganizationSnapshot {
    fun JsonObject.text(key: String): String = requireNotNull(get(key)?.jsonPrimitive?.contentOrNull) { "Missing organization field" }
    return OrganizationSnapshot(
        rules = requireNotNull(json["rules"]).jsonArray.map { entry ->
            val value = entry.jsonObject
            OrganizationRuleEntity(
                value.text("room"), value.text("sourceId"), value.text("groupKey"), value.text("itemKey"),
                value["enabled"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.boolean,
                value["sortMode"]?.jsonPrimitive?.contentOrNull,
                value["position"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.long,
            )
        },
        aliases = requireNotNull(json["aliases"]).jsonArray.map { entry ->
            OrganizationAliasEntity(entry.jsonObject.text("alias"), entry.jsonObject.text("identity"))
        },
    ).also(::validateOrganizationSnapshot)
}
