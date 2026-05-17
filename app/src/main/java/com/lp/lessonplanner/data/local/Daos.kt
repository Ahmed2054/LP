package com.lp.lessonplanner.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects WHERE id = :id")
    suspend fun getSubjectById(id: Int): SubjectEntity?

    @Query("SELECT * FROM subjects ORDER BY name ASC")
    fun getAllSubjects(): Flow<List<SubjectEntity>>

    @Query("""
        SELECT subjects.*, COUNT(curriculum.id) as indicatorCount 
        FROM subjects 
        LEFT JOIN curriculum ON subjects.id = curriculum.subjectId 
        GROUP BY subjects.id 
        ORDER BY subjects.name ASC
    """)
    fun getSubjectsWithCount(): Flow<List<SubjectWithCount>>

    @Query("SELECT * FROM subjects WHERE LOWER(name) = LOWER(:name) OR LOWER(actualName) = LOWER(:name) LIMIT 1")
    suspend fun getSubjectByName(name: String): SubjectEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSubject(subject: SubjectEntity): Long

    @Query("UPDATE subjects SET name = :newName, tribe = :tribe WHERE id = :id")
    suspend fun updateSubject(id: Int, newName: String, tribe: String?): Int

    @Delete
    suspend fun deleteSubject(subject: SubjectEntity): Int

    @Query("DELETE FROM subjects")
    suspend fun deleteAllSubjects(): Int

    @Query("""
        SELECT DISTINCT subjects.* FROM subjects 
        INNER JOIN curriculum ON subjects.id = curriculum.subjectId 
        WHERE curriculum.grade = :grade 
        ORDER BY subjects.name ASC
    """)
    fun getSubjectsByGrade(grade: String): Flow<List<SubjectEntity>>
}

@Dao
interface CurriculumDao {
    @Query("SELECT DISTINCT grade FROM curriculum ORDER BY grade ASC")
    fun getAllGrades(): Flow<List<String>>

    @Query("SELECT * FROM curriculum WHERE subjectId = :subjectId AND grade = :grade")
    suspend fun getCurriculumBySubjectAndGrade(subjectId: Int, grade: String): List<CurriculumEntity>

    @Query("SELECT * FROM curriculum WHERE subjectId = :subjectId")
    suspend fun getCurriculumBySubject(subjectId: Int): List<CurriculumEntity>

    @Query("SELECT * FROM curriculum")
    fun getAllCurriculumFlow(): Flow<List<CurriculumEntity>>

    @Query("SELECT * FROM curriculum WHERE id = :id")
    suspend fun getIndicatorById(id: Int): CurriculumEntity?

    @Query("SELECT * FROM curriculum WHERE indicatorCode = :code LIMIT 1")
    suspend fun getIndicatorByCode(code: String): CurriculumEntity?

    @Query("SELECT * FROM curriculum WHERE subjectId = :subjectId AND grade = :grade AND indicatorCode = :code LIMIT 1")
    suspend fun getIndicator(subjectId: Int, grade: String, code: String): CurriculumEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIndicator(indicator: CurriculumEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(curriculum: List<CurriculumEntity>): List<Long>

    @Query("DELETE FROM curriculum WHERE subjectId = :subjectId")
    suspend fun deleteBySubject(subjectId: Int): Int

    @Query("DELETE FROM curriculum")
    suspend fun deleteAllCurriculum(): Int
}

@Dao
interface SavedPlanDao {
    @Query("SELECT * FROM saved_plans ORDER BY createdAt DESC, id ASC")
    fun getAllSavedPlans(): Flow<List<SavedPlanEntity>>

    @Query("SELECT * FROM saved_plans ORDER BY createdAt DESC, id ASC LIMIT :limit OFFSET :offset")
    suspend fun getSavedPlansPaginated(limit: Int, offset: Int): List<SavedPlanEntity>

    @Query("""
        SELECT * FROM saved_plans 
        WHERE (indicatorCode LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' OR date LIKE '%' || :query || '%' OR planType LIKE '%' || :query || '%')
        AND (:planType IS NULL OR planType = :planType)
        ORDER BY createdAt DESC, id ASC 
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getFilteredSavedPlansPaginated(query: String, planType: String?, limit: Int, offset: Int): List<SavedPlanEntity>

    @Query("SELECT COUNT(*) FROM saved_plans")
    suspend fun getSavedPlanCount(): Int

    @Query("""
        SELECT COUNT(*) FROM saved_plans 
        WHERE (indicatorCode LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' OR date LIKE '%' || :query || '%' OR planType LIKE '%' || :query || '%')
        AND (:planType IS NULL OR planType = :planType)
    """)
    suspend fun getFilteredSavedPlanCount(query: String, planType: String?): Int

    @Insert
    suspend fun insertPlan(plan: SavedPlanEntity): Long

    @Update
    suspend fun updatePlan(plan: SavedPlanEntity): Int

    @Delete
    suspend fun deletePlan(plan: SavedPlanEntity): Int

    @Query("DELETE FROM saved_plans")
    suspend fun deleteAllPlans(): Int
}

@Dao
interface SettingsDao {
    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun getSetting(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSetting(setting: SettingEntity): Long

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun deleteSetting(key: String): Int
}

@Dao
interface CreditRedemptionDao {
    @Query("SELECT * FROM credit_redemptions ORDER BY date DESC")
    fun getAllRedemptions(): Flow<List<CreditRedemptionEntity>>

    @Query("SELECT * FROM credit_redemptions ORDER BY date DESC LIMIT :limit OFFSET :offset")
    suspend fun getRedemptionsPaginated(limit: Int, offset: Int): List<CreditRedemptionEntity>

    @Query("SELECT COUNT(*) FROM credit_redemptions")
    suspend fun getCount(): Int

    @Insert
    suspend fun insertRedemption(redemption: CreditRedemptionEntity): Long

    @Query("DELETE FROM credit_redemptions")
    suspend fun deleteAllRedemptions(): Int
}
