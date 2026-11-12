package com.sohva.tv.core.model.sport

/** The external namespace is complete: equal numeric IDs in different products prove nothing. */
data class ProviderReference(
    val provider: String,
    val product: String,
    val kind: ReferenceKind,
    val externalId: String,
)

enum class ReferenceKind(val stored: String) {
    TEAM("team"), FIGHTER("fighter"), GYM("gym"), DRIVER("driver"), COMPETITION("competition"), CIRCUIT("circuit");

    companion object {
        fun fromStored(value: String): ReferenceKind? = entries.firstOrNull { it.stored == value }
    }
}
