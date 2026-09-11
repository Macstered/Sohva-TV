package com.sohva.tv.addons.storage

import androidx.room.withTransaction

/** Transaction boundary, replaceable with a deterministic fixture in JVM tests. */
interface AddonPersistence {
    val dao: AddonDao
    suspend fun <T> transaction(block: suspend () -> T): T
}

class RoomAddonPersistence(private val database: AddonDatabase) : AddonPersistence {
    override val dao: AddonDao get() = database.installations()
    override suspend fun <T> transaction(block: suspend () -> T): T = database.withTransaction(block)
}
