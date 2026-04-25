package com.zhoulesin.zutils.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.util.UUID

@Entity(tableName = "workflows")
data class WorkflowEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val type: String,
    val stepsJson: String,
    val stepCount: Int,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface WorkflowDao {
    @Query("SELECT * FROM workflows ORDER BY createdAt DESC")
    fun loadAll(): Flow<List<WorkflowEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(workflow: WorkflowEntity)

    @Delete
    suspend fun delete(workflow: WorkflowEntity)
}

@Database(entities = [WorkflowEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workflowDao(): WorkflowDao
}

@Serializable
data class SavedStep(
    val function: String,
    val args: JsonObject = JsonObject(emptyMap()),
    val pipeline: Map<String, String> = emptyMap(),
)

@Serializable
data class SavedWorkflow(
    val id: String,
    val title: String,
    val description: String = "",
    val type: String,
    val steps: List<SavedStep>,
    val stepCount: Int,
)

class WorkflowStorage(context: Context) {
    private val dao by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "zutils.db"
        ).build().workflowDao()
    }

    private val json = Json { prettyPrint = true }

    fun loadAll(): Flow<List<WorkflowEntity>> = dao.loadAll()

    suspend fun save(workflow: WorkflowEntity) = dao.insert(workflow)

    suspend fun delete(workflow: WorkflowEntity) = dao.delete(workflow)

    suspend fun saveFromSteps(
        id: String,
        title: String,
        desc: String,
        type: String,
        steps: List<SavedStep>,
    ) {
        val stepsJson = json.encodeToString(steps)
        save(WorkflowEntity(
            id = id, title = title, description = desc,
            type = type, stepsJson = stepsJson, stepCount = steps.size,
        ))
    }

    companion object {
        fun buildId(): String = UUID.randomUUID().toString().take(8)
    }
}
