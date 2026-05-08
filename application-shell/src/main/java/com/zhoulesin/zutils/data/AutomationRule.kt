package com.zhoulesin.zutils.data

import androidx.room.*

@Entity(tableName = "automation_rules")
data class AutomationRule(
    @PrimaryKey val id: String,
    val name: String,
    val cron: String,
    val stepsJson: String,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface AutomationRuleDao {
    @Query("SELECT * FROM automation_rules ORDER BY createdAt DESC")
    suspend fun loadAll(): List<AutomationRule>

    @Query("SELECT * FROM automation_rules WHERE isEnabled = 1")
    suspend fun loadEnabled(): List<AutomationRule>

    @Query("SELECT * FROM automation_rules WHERE id = :id")
    suspend fun getById(id: String): AutomationRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: AutomationRule)

    @Query("UPDATE automation_rules SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("DELETE FROM automation_rules WHERE id = :id")
    suspend fun delete(id: String)
}
