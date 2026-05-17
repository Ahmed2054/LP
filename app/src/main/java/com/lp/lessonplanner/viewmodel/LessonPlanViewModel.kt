package com.lp.lessonplanner.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.lp.lessonplanner.data.local.*
import com.lp.lessonplanner.data.remote.*
import com.lp.lessonplanner.data.repository.LessonPlanRepository
import com.lp.lessonplanner.ui.utils.DocxGenerator
import com.lp.lessonplanner.ui.utils.PdfGenerator
import com.lp.lessonplanner.utils.cleanCurriculumText
import com.lp.lessonplanner.utils.removeCitationMarkers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class LessonPlanViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        internal const val DOCX_MIME_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    }

    // ── Dependencies ──────────────────────────────────────────────────────────

    internal val repository: LessonPlanRepository by lazy {
        val db = AppDatabase.getDatabase(application)
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(180, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(180, TimeUnit.SECONDS)
            .addInterceptor(okhttp3.logging.HttpLoggingInterceptor().apply {
                level = okhttp3.logging.HttpLoggingInterceptor.Level.BODY
            })
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.deepseek.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val cloudflareRetrofit = Retrofit.Builder()
            .baseUrl("https://deepseek-vault-bridge.ofosuahmed.workers.dev/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        LessonPlanRepository(
            db.subjectDao(),
            db.curriculumDao(),
            db.savedPlanDao(),
            db.settingsDao(),
            db.creditRedemptionDao(),
            db.userDao(),
            retrofit.create(DeepSeekApi::class.java),
            cloudflareRetrofit.create(CloudflareVaultApi::class.java)
        )
    }

    internal val gson = Gson()
    internal val pdfGenerator = PdfGenerator(application)
    internal val docxGenerator = DocxGenerator(application)
    internal val updateManager = com.lp.lessonplanner.utils.UpdateManager(application)
    internal var generationJob: Job? = null

    // ── State ─────────────────────────────────────────────────────────────────

    internal val _uiState = MutableStateFlow(LessonPlanUiState())
    val uiState: StateFlow<LessonPlanUiState> = _uiState.asStateFlow()

    internal val _apiKey = MutableStateFlow("")
    val apiKey = _apiKey.asStateFlow()

    internal val _cloudUserId = MutableStateFlow("")
    val cloudUserId = _cloudUserId.asStateFlow()

    internal val _model = MutableStateFlow("deepseek-chat")
    val model = _model.asStateFlow()

    internal val _creditPlans = MutableStateFlow(
        listOf(
            CreditPackage("Basic", 10, 3.0, "GHS 3.00"),
            CreditPackage("Standard", 20, 5.5, "GHS 5.50"),
            CreditPackage("Premium", 50, 13.0, "GHS 13.00")
        )
    )
    val creditPlans = _creditPlans.asStateFlow()

    internal val _isFetchingPlans = MutableStateFlow(false)
    val isFetchingPlans = _isFetchingPlans.asStateFlow()

    internal val _userCredits = MutableStateFlow(0)
    val userCredits = _userCredits.asStateFlow()

    internal val _phoneNumber = MutableStateFlow("")
    val phoneNumber = _phoneNumber.asStateFlow()

    internal val _pin = MutableStateFlow("")
    val pin = _pin.asStateFlow()

    internal val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn = _isLoggedIn.asStateFlow()

    internal val _showManualPaymentDialog = MutableStateFlow<CreditPackage?>(null)
    val showManualPaymentDialog = _showManualPaymentDialog.asStateFlow()

    internal val _selectedHistoryPlan = MutableStateFlow<SavedPlanEntity?>(null)
    val selectedHistoryPlan = _selectedHistoryPlan.asStateFlow()

    internal val _selectedHistoryPlans = MutableStateFlow<List<SavedPlanEntity>>(emptyList())
    val selectedHistoryPlans = _selectedHistoryPlans.asStateFlow()

    internal val _historyOrderIds = MutableStateFlow<List<Int>>(emptyList())
    val historyOrderIds = _historyOrderIds.asStateFlow()

    internal val _saveSuccess = MutableStateFlow(false)
    val saveSuccess = _saveSuccess.asStateFlow()

    internal val _redemptionHistory = MutableStateFlow<List<CreditRedemptionEntity>>(emptyList())
    val redemptionHistory = _redemptionHistory.asStateFlow()

    internal val _totalRedemptions = MutableStateFlow(0)
    val totalRedemptions = _totalRedemptions.asStateFlow()

    internal val _updateAvailable = MutableStateFlow<UpdateResponse?>(null)
    val updateAvailable = _updateAvailable.asStateFlow()

    internal val _currentRedemptionPage = MutableStateFlow(0)
    val currentRedemptionPage = _currentRedemptionPage.asStateFlow()

    // ── Derived flows ─────────────────────────────────────────────────────────

    val grades = repository.allGrades
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val subjects = _uiState.flatMapLatest { state ->
        if (state.selectedGrade.isNotEmpty()) repository.getSubjectsByGrade(state.selectedGrade)
        else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val subjectsWithCount = combine(
        repository.getSubjectsWithCount(),
        repository.getAllCurriculumFlow()
    ) { subjects, curriculum ->
        subjects.map { subjectWithCount ->
            val gradesCounts = curriculum
                .filter { it.subjectId == subjectWithCount.subject.id }
                .groupBy { it.grade }
                .mapValues { it.value.size }
            subjectWithCount.apply { this.gradesCounts = gradesCounts }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val indicators = _uiState.flatMapLatest { state ->
        if (state.selectedSubjectId != null && state.selectedGrade.isNotEmpty()) {
            flow { emit(repository.getCurriculumBySubjectAndGrade(state.selectedSubjectId, state.selectedGrade)) }
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history = repository.allSavedPlans
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        loadSettings()
        checkAndImportDefaultCurriculum()
        fetchCreditPlans(isSilent = true)
        checkForUpdates(isSilent = true)
        loadHistoryPaginated(0)
    }

    // ── UI state helpers ──────────────────────────────────────────────────────

    fun resetSaveSuccess() { _saveSuccess.value = false }

    fun clearErrorMessage() { _uiState.update { it.copy(errorMessage = null) } }

    fun showError(message: String) { _uiState.update { it.copy(errorMessage = message) } }

    fun updateStep(step: Int) { _uiState.update { it.copy(step = step) } }

    fun updateGrade(grade: String) {
        _uiState.update { it.copy(selectedGrade = grade, selectedSubjectId = null, selectedIndicatorIds = emptyList()) }
    }

    fun updateSubject(subjectId: Int) {
        _uiState.update { it.copy(selectedSubjectId = subjectId, selectedIndicatorIds = emptyList()) }
    }

    fun updateIndicatorMetadata(indicatorId: Int, field: String, value: String) {
        _uiState.update { state ->
            val current = state.indicatorMetadata[indicatorId] ?: IndicatorMetadata()
            val updated = when (field) {
                "week" -> current.copy(week = value)
                "date" -> current.copy(date = value)
                "weekEnding" -> current.copy(weekEnding = value)
                "lessonNumber" -> current.copy(lessonNumber = value)
                else -> current
            }
            state.copy(indicatorMetadata = state.indicatorMetadata + (indicatorId to updated))
        }
    }

    fun toggleIndicatorDokLevel(indicatorId: Int, dokLevel: String) {
        _uiState.update { state ->
            val current = state.indicatorMetadata[indicatorId] ?: IndicatorMetadata()
            val updatedLevels = if (current.dokLevels.contains(dokLevel)) {
                current.dokLevels.filter { it != dokLevel }
            } else {
                current.dokLevels + dokLevel
            }
            state.copy(indicatorMetadata = state.indicatorMetadata + (indicatorId to current.copy(dokLevels = updatedLevels)))
        }
    }

    fun updateSchool(school: String) {
        _uiState.update { it.copy(school = school) }
        updateHeaderField("school", school)
    }

    fun updateFacilitatorName(name: String) {
        _uiState.update { it.copy(facilitatorName = name) }
        viewModelScope.launch { repository.setSetting("facilitatorName", name) }
        updateHeaderField("facilitatorName", name)
    }

    fun updateTerm(term: String) {
        _uiState.update { it.copy(term = term) }
        updateHeaderField("term", term)
    }

    fun updateClassSize(size: String) {
        _uiState.update { it.copy(classSize = size) }
        updateHeaderField("classSize", size)
    }

    fun updateDuration(duration: String) {
        _uiState.update { it.copy(duration = duration) }
        updateHeaderField("duration", duration)
    }

    fun updateIndicator(indicatorId: Int) {
        _uiState.update { state ->
            val next = if (state.selectedIndicatorIds.contains(indicatorId)) {
                state.selectedIndicatorIds.filter { it != indicatorId }
            } else {
                state.selectedIndicatorIds + indicatorId
            }
            state.copy(selectedIndicatorIds = next)
        }
    }

    fun clearAllIndicators() { _uiState.update { it.copy(selectedIndicatorIds = emptyList()) } }

    fun updateGenerationType(type: String) { _uiState.update { it.copy(generationType = type) } }

    fun updateQuestionCount(count: Int) { _uiState.update { it.copy(questionCount = count) } }

    fun updateQuestionType(type: String) { _uiState.update { it.copy(questionType = type) } }

    fun updateEditingPhase(index: Pair<Int, Int>?) { _uiState.update { it.copy(editingPhaseIndex = index) } }

    // ── Plan editing ──────────────────────────────────────────────────────────

    fun updatePhaseField(planIndex: Int, phaseIndex: Int, field: String, value: String, isHistory: Boolean = false) {
        val json = if (isHistory) _selectedHistoryPlans.value.getOrNull(planIndex)?.content
        else _uiState.value.generatedPlanJsons.getOrNull(planIndex)
        if (json == null) return

        try {
            val plan = gson.fromJson(json, LessonPlan::class.java)
            val updatedPhases = plan.phases?.mapIndexed { i, phase ->
                if (i == phaseIndex) when (field) {
                    "activities" -> phase.copy(activities = value)
                    "resources" -> phase.copy(resources = value)
                    "duration" -> phase.copy(duration = value)
                    else -> phase
                } else phase
            }
            val newJson = gson.toJson(plan.copy(phases = updatedPhases))
            if (isHistory) {
                updateSelectedHistoryPlan(planIndex) { it.copy(content = newJson) }
            } else {
                _uiState.update { state ->
                    val newList = state.generatedPlanJsons.toMutableList()
                    newList[planIndex] = newJson
                    state.copy(generatedPlanJsons = newList)
                }
            }
        } catch (_: Exception) { }
    }

    fun updateHeaderField(field: String, value: String, isHistory: Boolean = false) =
        updateHeaderField(0, field, value, isHistory)

    fun updateHeaderField(planIndex: Int, field: String, value: String, isHistory: Boolean = false) {
        val json = if (isHistory) _selectedHistoryPlans.value.getOrNull(planIndex)?.content
        else _uiState.value.generatedPlanJsons.getOrNull(planIndex)
        if (json == null) return

        try {
            val updatedJson = json.updateHeaderField(gson, field, value)

            if (isHistory) {
                val targetField = when (field) {
                    "school", "facilitatorName", "term", "classSize", "duration" -> true
                    else -> false
                }

                if (targetField) {
                    // Update all plans in history view if it's a shared field
                    val updatedList = _selectedHistoryPlans.value.map { plan ->
                        plan.copy(content = plan.content.updateHeaderField(gson, field, value))
                    }
                    _selectedHistoryPlans.value = updatedList
                } else {
                    updateSelectedHistoryPlan(planIndex) { plan ->
                        plan.copy(
                            content = updatedJson,
                            date = if (field == "date") value else plan.date,
                            week = if (field == "week") value else plan.week,
                            lessonNumber = if (field == "lesson") value else plan.lessonNumber
                        )
                    }
                }
            } else {
                _uiState.update { state ->
                    val newList = state.generatedPlanJsons.toMutableList()
                    val targetField = when (field) {
                        "school", "facilitatorName", "term", "classSize", "duration" -> true
                        else -> false
                    }

                    if (targetField) {
                        // Update all plans if it's a shared field
                        for (i in newList.indices) {
                            newList[i] = newList[i].updateHeaderField(gson, field, value)
                        }
                    } else {
                        newList[planIndex] = updatedJson
                    }

                    state.copy(
                        generatedPlanJsons = newList,
                        date = if (field == "date") value else state.date,
                        week = if (field == "week") value else state.week,
                        lessonNumber = if (field == "lesson") value else state.lessonNumber,
                        weekEnding = if (field == "weekEnding") value else state.weekEnding,
                        school = if (field == "school") value else state.school,
                        facilitatorName = if (field == "facilitatorName") value else state.facilitatorName,
                        term = if (field == "term") value else state.term,
                        duration = if (field == "duration") value else state.duration,
                        classSize = if (field == "classSize") value else state.classSize
                    )
                }
            }
        } catch (_: Exception) { }
    }

    // ── Curriculum management ─────────────────────────────────────────────────

    fun importCurriculum(uri: Uri) {
        val contentResolver = getApplication<Application>().contentResolver
        val fileName = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst()) cursor.getString(nameIndex) else null
        }
        if (fileName != null && !fileName.endsWith(".json", ignoreCase = true)) {
            _uiState.update { it.copy(errorMessage = "Only .json curriculum files are supported.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val inputStream = contentResolver.openInputStream(uri)
                if (inputStream != null) importFromInputStream(inputStream)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Import failed: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun addIndicator(
        subjectId: Int, grade: String, strand: String, subStrand: String,
        contentStandard: String, indicatorCode: String, indicatorDescription: String,
        exemplars: String, coreCompetencies: String
    ) {
        viewModelScope.launch {
            repository.insertIndicator(
                CurriculumEntity(
                    subjectId = subjectId, grade = grade, strand = strand.cleanCurriculumText(),
                    subStrand = subStrand.cleanCurriculumText(),
                    contentStandard = contentStandard.cleanCurriculumText(),
                    indicatorCode = indicatorCode.cleanCurriculumText(),
                    indicatorDescription = indicatorDescription.cleanCurriculumText(),
                    exemplars = exemplars.cleanCurriculumText(),
                    coreCompetencies = coreCompetencies.cleanCurriculumText()
                )
            )
        }
    }

    suspend fun getIndicatorsForSubject(subjectId: Int): List<CurriculumEntity> =
        repository.getCurriculumBySubject(subjectId)

    suspend fun getOrCreateSubject(name: String): Int = withContext(Dispatchers.IO) {
        repository.getSubjectByName(name)?.id
            ?: repository.insertSubject(SubjectEntity(name = name, actualName = name, tribe = null)).toInt()
    }

    suspend fun checkIndicatorExists(subjectId: Int, grade: String, code: String): Boolean =
        withContext(Dispatchers.IO) { repository.getIndicator(subjectId, grade, code) != null }

    suspend fun resolveIndicatorFromSavedPlan(plan: SavedPlanEntity): CurriculumEntity? {
        return try {
            val header = gson.fromJson(plan.content, Map::class.java)?.get("header") as? Map<*, *>
            val subjectName = (header?.get("subject") as? String).orEmpty().trim()
            val grade = (header?.get("class") as? String).orEmpty().trim()
            val indicatorLabel = ((header?.get("indicator") as? String) ?: plan.indicatorCode ?: "").trim()
            val indicatorCode = indicatorLabel.substringBefore(" - ").trim()
            if (indicatorCode.isEmpty()) return null
            val subject = if (subjectName.isNotEmpty()) repository.getSubjectByName(subjectName) else null
            when {
                subject != null && grade.isNotEmpty() ->
                    repository.getIndicator(subject.id, grade, indicatorCode) ?: repository.getIndicatorByCode(indicatorCode)
                else -> repository.getIndicatorByCode(indicatorCode)
            }
        } catch (_: Exception) { null }
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    private fun loadSettings() {
        viewModelScope.launch {
            _apiKey.value = repository.getSetting("api_key") ?: ""
            _model.value = repository.getSetting("model") ?: "deepseek-chat"
            _phoneNumber.value = repository.getSetting("phone_number") ?: ""
            _pin.value = repository.getSetting("pin") ?: ""
            _userCredits.value = repository.getSetting("user_credits")?.toIntOrNull() ?: 0

            val savedFacilitatorName = repository.getSetting("facilitatorName") ?: ""
            _uiState.update { it.copy(facilitatorName = savedFacilitatorName) }

            val savedOrder = repository.getSetting("history_order_ids")
            if (!savedOrder.isNullOrBlank()) {
                _historyOrderIds.value = savedOrder.split(",").mapNotNull { it.trim().toIntOrNull() }
            }

            if (_phoneNumber.value.isNotEmpty() && _pin.value.isNotEmpty()) {
                loginOrRegister(_phoneNumber.value, _pin.value)
            }
        }
    }

    // ── Default curriculum import ─────────────────────────────────────────────

    private fun checkAndImportDefaultCurriculum() {
        viewModelScope.launch {
            if (grades.value.isEmpty()) {
                _uiState.update { it.copy(isLoading = true) }
                importCurriculumsFromAssets()
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun refreshCurriculum() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                withContext(Dispatchers.IO) {
                    repository.clearCurriculumData()
                    importCurriculumsFromAssets()
                }
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                Log.e("RefreshCurriculum", "Failed to refresh curriculum", e)
                _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to refresh curriculum") }
            }
        }
    }

    internal suspend fun importCurriculumsFromAssets() {
        withContext(Dispatchers.IO) {
            try {
                val files = getApplication<Application>().assets.list("curriculums") ?: return@withContext
                files.forEach { fileName ->
                    if (fileName.endsWith(".json")) {
                        try {
                            importFromInputStream(getApplication<Application>().assets.open("curriculums/$fileName"))
                        } catch (e: Exception) {
                            Log.e("ImportDefault", "Error importing $fileName", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ImportDefault", "Error listing curriculum assets", e)
            }
        }
    }

    private suspend fun importFromInputStream(inputStream: java.io.InputStream) {
        withContext(Dispatchers.IO) {
            try {
                val content = inputStream.bufferedReader().use { it.readText() }
                val result = repository.importCurriculumFromJson(content)
                if (result.isFailure) {
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(errorMessage = "Invalid JSON curriculum file.") }
                    }
                }
            } catch (_: Exception) {
                Log.e("ImportCurriculum", "Failed to read curriculum stream")
            }
        }
    }
}

// ── Plan JSON helpers (package-internal) ──────────────────────────────────────

fun String.detectPlanType(): String {
    val s = this.sanitizeJson()
    
    // 1. Try to find plan_type/planType field with Regex
    val typeRegex = Regex("\"plan_?type\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
    val match = typeRegex.find(s)
    if (match != null) {
        val typeValue = match.groupValues[1].lowercase()
        return when {
            typeValue.contains("note") -> "Full Note"
            typeValue.contains("question") || typeValue.contains("exercise") -> "Questions"
            else -> "Lesson Plan"
        }
    }
    
    // 2. Fallback: check for defining keys in the JSON
    return when {
        s.contains("\"questions\"") -> "Questions"
        s.contains("\"content\"") && !s.contains("\"phases\"") -> "Full Note"
        s.contains("\"phases\"") -> "Lesson Plan"
        else -> "Lesson Plan"
    }
}

fun String.sanitizeJson(): String {
    var s = this.trim()
    // 1. Extract the JSON object if there's surrounding text
    if (s.contains("{") && s.contains("}")) {
        val firstBrace = s.indexOf('{')
        val lastBrace = s.lastIndexOf('}')
        if (lastBrace > firstBrace) {
            s = s.substring(firstBrace, lastBrace + 1)
        }
    }

    // 2. Remove markdown markers
    if (s.startsWith("```json")) s = s.removePrefix("```json")
    if (s.startsWith("```")) s = s.removePrefix("```")
    if (s.endsWith("```")) s = s.removeSuffix("```")
    s = s.trim()

    // 3. Fix raw newlines inside JSON strings
    val result = StringBuilder()
    var inString = false
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '"' && (i == 0 || s[i - 1] != '\\')) {
            inString = !inString
            result.append(c)
        } else if (c == '\n' || c == '\r') {
            if (inString) {
                result.append("\\n")
            } else {
                result.append(c)
            }
        } else {
            result.append(c)
        }
        i++
    }
    s = result.toString()

    // 4. Fix trailing commas before closing braces/brackets
    s = s.replace(Regex(",\\s*([}\\]])"), "$1")

    return s.removeCitationMarkers()
}

internal fun String.parsePlanHeader(gson: Gson, planType: String): LessonPlanHeader? = try {
    val sanitized = this.sanitizeJson()
    when (planType) {
        "Full Note" -> gson.fromJson(sanitized, NotePlan::class.java).header
        "Questions" -> gson.fromJson(sanitized, QuestionsPlan::class.java).header
        else -> gson.fromJson(sanitized, LessonPlan::class.java).header
    }
} catch (_: Exception) { null }

internal fun String.extractIndicatorCodeFromRaw(): String {
    // Try to find the "indicator" field first in the JSON
    val indicatorValue = Regex("\"indicator\"\\s*:\\s*\"([^\"]+)\"").find(this)?.groupValues?.get(1)
    if (indicatorValue != null) {
        return Regex("[A-Z]\\d+\\.\\d+\\.\\d+\\.\\d+\\.\\d+").find(indicatorValue)?.value
            ?: indicatorValue.split(" - ").firstOrNull()?.trim()
            ?: ""
    }
    // Fallback: search the whole string for the pattern [A-Z] followed by digits and dots (e.g., B7.1.1.1.1)
    return Regex("[A-Z]\\d+\\.\\d+\\.\\d+\\.\\d+\\.\\d+").find(this)?.value ?: ""
}

internal fun LessonPlanHeader.extractIndicatorCode(): String =
    Regex("[A-Z]\\d+\\.\\d+\\.\\d+\\.\\d+\\.\\d+").find(indicator ?: "")?.value
        ?: indicator?.split(" - ")?.firstOrNull()?.trim()
        ?: ""

internal fun String.updateHeaderField(gson: Gson, field: String, value: String): String {
    fun LessonPlanHeader.applyField() = when (field) {
        "school" -> copy(school = value)
        "facilitatorName" -> copy(facilitatorName = value)
        "term" -> copy(term = value)
        "date" -> copy(date = value)
        "week" -> copy(week = value)
        "weekEnding" -> copy(weekEnding = value)
        "duration" -> copy(duration = value)
        "lesson" -> copy(lesson = value)
        "classSize" -> copy(classSize = value)
        "keywords" -> copy(keywords = value)
        else -> this
    }
    return when {
        contains("\"plan_type\":\"Full Note\"") -> {
            val plan = gson.fromJson(this, NotePlan::class.java)
            gson.toJson(plan.copy(header = (plan.header ?: LessonPlanHeader()).applyField()))
        }
        contains("\"plan_type\":\"Questions\"") -> {
            val plan = gson.fromJson(this, QuestionsPlan::class.java)
            gson.toJson(plan.copy(header = (plan.header ?: LessonPlanHeader()).applyField()))
        }
        else -> {
            val plan = gson.fromJson(this, LessonPlan::class.java)
            gson.toJson(plan.copy(header = (plan.header ?: LessonPlanHeader()).applyField()))
        }
    }
}
