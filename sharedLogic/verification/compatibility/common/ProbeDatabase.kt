package com.ndynagn.kmp.news.verification

import androidx.room.ConstructedBy
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import kotlinx.coroutines.flow.Flow

@Entity
internal data class ProbeEntity(@PrimaryKey val id: Int, val value: Int)

@Dao
internal interface ProbeDao {
    @Insert
    suspend fun insert(entity: ProbeEntity)

    @Query("SELECT * FROM ProbeEntity ORDER BY id")
    suspend fun read(): List<ProbeEntity>

    @Query("SELECT * FROM ProbeEntity ORDER BY id")
    fun observe(): Flow<List<ProbeEntity>>
}

@Database(entities = [ProbeEntity::class], version = 1, exportSchema = true)
@ConstructedBy(ProbeDatabaseConstructor::class)
internal abstract class ProbeDatabase : RoomDatabase() {
    abstract fun entries(): ProbeDao
}

@Suppress("KotlinNoActualForExpect") // Room KSP supplies the actual constructor on each selected target.
internal expect object ProbeDatabaseConstructor : RoomDatabaseConstructor<ProbeDatabase> {
    override fun initialize(): ProbeDatabase
}
