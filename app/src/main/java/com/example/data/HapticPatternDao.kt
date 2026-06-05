package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HapticPatternDao {
    @Query("SELECT * FROM haptic_patterns ORDER BY name ASC")
    fun getAllPatterns(): Flow<List<HapticPattern>>

    @Query("SELECT * FROM haptic_patterns WHERE id = :id")
    suspend fun getPatternById(id: Int): HapticPattern?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPattern(pattern: HapticPattern): Long

    @Delete
    suspend fun deletePattern(pattern: HapticPattern)
}
