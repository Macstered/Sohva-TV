package com.streammate.tv.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface SportsCacheDao {
    @Query("SELECT * FROM sports_api_cache WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun cached(cacheKey: String): SportsApiCacheEntity?

    @Upsert
    suspend fun upsert(entry: SportsApiCacheEntity)

    @Query("DELETE FROM sports_api_cache WHERE staleUntilEpochMillis <= :nowEpochMillis")
    suspend fun deleteExpired(nowEpochMillis: Long)

    @Query("DELETE FROM sports_api_cache")
    suspend fun clear()
}
