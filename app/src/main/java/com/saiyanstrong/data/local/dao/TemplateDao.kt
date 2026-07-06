package com.saiyanstrong.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.saiyanstrong.data.local.entity.TemplateEntity
import com.saiyanstrong.data.local.entity.TemplateExerciseEntity
import kotlinx.coroutines.flow.Flow

data class TemplateExerciseEntry(
    @ColumnInfo(name = "template_id") val templateId: Long,
    @ColumnInfo(name = "exercise_id") val exerciseId: Int,
    val name: String
)

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates ORDER BY created_ms DESC")
    fun getAll(): Flow<List<TemplateEntity>>

    @Query("""
        SELECT te.template_id, te.exercise_id, e.name
        FROM template_exercises te
        INNER JOIN exercises e ON te.exercise_id = e.id
        ORDER BY te.order_index ASC
    """)
    fun getAllTemplateExerciseEntries(): Flow<List<TemplateExerciseEntry>>

    @Insert
    suspend fun insert(template: TemplateEntity): Long

    @Insert
    suspend fun insertExercise(templateExercise: TemplateExerciseEntity): Long

    @Query("DELETE FROM templates WHERE id = :templateId")
    suspend fun deleteById(templateId: Long)
}
