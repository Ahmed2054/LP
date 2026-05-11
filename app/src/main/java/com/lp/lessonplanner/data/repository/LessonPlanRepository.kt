package com.lp.lessonplanner.data.repository

import com.google.gson.Gson
import com.lp.lessonplanner.data.local.*
import com.lp.lessonplanner.data.remote.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import retrofit2.Response

class LessonPlanRepository(
    private val subjectDao: SubjectDao,
    private val curriculumDao: CurriculumDao,
    private val savedPlanDao: SavedPlanDao,
    private val settingsDao: SettingsDao,
    private val creditRedemptionDao: CreditRedemptionDao,
    private val api: DeepSeekApi,
    private val cloudflareVaultApi: CloudflareVaultApi
) {
    private val gson = Gson()

    // Curriculum & Subjects
    val allGrades = curriculumDao.getAllGrades()
    
    fun getSubjectsByGrade(grade: String): Flow<List<SubjectEntity>> = 
        subjectDao.getSubjectsByGrade(grade)

    fun getSubjectsWithCount() = subjectDao.getSubjectsWithCount()
    
    fun getAllCurriculumFlow() = curriculumDao.getAllCurriculumFlow()

    suspend fun getCurriculumBySubjectAndGrade(subjectId: Int, grade: String) =
        curriculumDao.getCurriculumBySubjectAndGrade(subjectId, grade)

    suspend fun getIndicatorById(id: Int) = curriculumDao.getIndicatorById(id)
    
    suspend fun getIndicatorByCode(code: String) = curriculumDao.getIndicatorByCode(code)

    suspend fun getIndicator(subjectId: Int, grade: String, code: String) = curriculumDao.getIndicator(subjectId, grade, code)

    suspend fun insertIndicator(indicator: CurriculumEntity) = curriculumDao.insertIndicator(indicator)

    suspend fun getSubjectById(id: Int) = subjectDao.getSubjectById(id)

    suspend fun insertSubjects(subjects: List<SubjectEntity>) {
        subjects.forEach { subjectDao.insertSubject(it) }
    }
    
    suspend fun insertCurriculums(curriculums: List<CurriculumEntity>) = curriculumDao.insertAll(curriculums)

    suspend fun getSubjectByName(name: String) = subjectDao.getSubjectByName(name)
    
    suspend fun insertSubject(subject: SubjectEntity) = subjectDao.insertSubject(subject)

    // Saved Plans
    val allSavedPlans = savedPlanDao.getAllSavedPlans()
    
    suspend fun savePlan(plan: SavedPlanEntity) = savedPlanDao.insertPlan(plan)
    
    suspend fun updatePlan(plan: SavedPlanEntity) = savedPlanDao.updatePlan(plan)
    
    suspend fun deletePlan(plan: SavedPlanEntity) = savedPlanDao.deletePlan(plan)

    // Credits & Payments
    suspend fun getCreditPlans(token: String): Response<List<CreditPackageResponse>> =
        cloudflareVaultApi.getCreditPlans(token)

    // AI Generation
    suspend fun generateChat(apiKey: String, request: ChatRequest): Response<ChatResponse> =
        api.generateChat("Bearer $apiKey", request)

    suspend fun getBalance(apiKey: String) = api.getBalance("Bearer $apiKey")

    // Cloud Vault
    suspend fun login(token: String, request: AuthRequest) = cloudflareVaultApi.login(token, request)
    
    suspend fun register(token: String, request: AuthRequest) = cloudflareVaultApi.register(token, request)
    
    suspend fun syncUserData(token: String, request: AuthRequest) = cloudflareVaultApi.syncUserData(token, request)
    
    suspend fun getKey(token: String, userId: String) = cloudflareVaultApi.getKey(token, userId)
    
    suspend fun redeemCode(token: String, request: RedeemRequest) = cloudflareVaultApi.redeemCode(token, request)
    
    suspend fun checkForUpdate(token: String) = cloudflareVaultApi.checkForUpdate(token)

    // Redemption History
    fun getAllRedemptions() = creditRedemptionDao.getAllRedemptions()
    
    suspend fun getRedemptionsPaginated(limit: Int, offset: Int) = 
        creditRedemptionDao.getRedemptionsPaginated(limit, offset)
    
    suspend fun getRedemptionCount() = creditRedemptionDao.getCount()
    
    suspend fun insertRedemption(redemption: CreditRedemptionEntity) = 
        creditRedemptionDao.insertRedemption(redemption)

    // Settings
    suspend fun getSetting(key: String) = settingsDao.getSetting(key)
    
    suspend fun setSetting(key: String, value: String) = 
        settingsDao.setSetting(SettingEntity(key, value))

    suspend fun clearAllData() {
        subjectDao.deleteAllSubjects()
        curriculumDao.deleteAllCurriculum()
        savedPlanDao.deleteAllPlans()
        creditRedemptionDao.deleteAllRedemptions()
    }

    suspend fun importCurriculumFromJson(jsonContent: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val curriculumDataList = gson.fromJson(jsonContent, Array<CurriculumJsonModel>::class.java).toList()
            
            if (curriculumDataList.isNotEmpty()) {
                val curriculumEntities = mutableListOf<CurriculumEntity>()
                val subjectCache = mutableMapOf<String, Int>()

                curriculumDataList.forEach { data ->
                    val subjectName = data.subject?.trim() ?: return@forEach
                    val grade = data.grade?.trim() ?: return@forEach
                    
                    val subjectId = subjectCache.getOrPut(subjectName) {
                        subjectDao.getSubjectByName(subjectName)?.id
                            ?: subjectDao.insertSubject(SubjectEntity(name = subjectName, actualName = subjectName, tribe = null)).toInt()
                    }

                    curriculumEntities.add(
                        CurriculumEntity(
                            subjectId = subjectId,
                            grade = grade,
                            strand = data.strand,
                            subStrand = data.subStrand,
                            contentStandard = data.contentStandard,
                            indicatorCode = data.indicatorCode,
                            indicatorDescription = data.indicatorDescription,
                            exemplars = data.examplars,
                            coreCompetencies = data.coreCompetencies
                        )
                    )
                }
                
                if (curriculumEntities.isNotEmpty()) {
                    curriculumDao.insertAll(curriculumEntities)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
