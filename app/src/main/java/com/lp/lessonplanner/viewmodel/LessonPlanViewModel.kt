package com.lp.lessonplanner.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import androidx.core.net.toUri
import com.lp.lessonplanner.data.local.*
import com.lp.lessonplanner.data.remote.*
import com.lp.lessonplanner.data.repository.LessonPlanRepository
import com.lp.lessonplanner.ui.utils.PdfGenerator
import com.lp.lessonplanner.ui.utils.DocxGenerator
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.lp.lessonplanner.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class LessonPlanViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val DOCX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    }

    private val repository: LessonPlanRepository by lazy {
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
        val api = retrofit.create(DeepSeekApi::class.java)

        val cloudflareRetrofit = Retrofit.Builder()
            .baseUrl("https://deepseek-vault-bridge.ofosuahmed.workers.dev/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val cloudflareVaultApi = cloudflareRetrofit.create(CloudflareVaultApi::class.java)

        LessonPlanRepository(
            db.subjectDao(),
            db.curriculumDao(),
            db.savedPlanDao(),
            db.settingsDao(),
            db.creditRedemptionDao(),
            api,
            cloudflareVaultApi
        )
    }

    private val gson = Gson()
    private val pdfGenerator = PdfGenerator(application)
    private val docxGenerator = DocxGenerator(application)
    private val updateManager = com.lp.lessonplanner.utils.UpdateManager(application)
    private var generationJob: Job? = null

    private val _uiState = MutableStateFlow(LessonPlanUiState())
    val uiState: StateFlow<LessonPlanUiState> = _uiState.asStateFlow()

    val grades = repository.allGrades
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val subjects = _uiState.flatMapLatest { state ->
        if (state.selectedGrade.isNotEmpty()) {
            repository.getSubjectsByGrade(state.selectedGrade)
        } else {
            flowOf(emptyList())
        }
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
            
            subjectWithCount.apply {
                this.gradesCounts = gradesCounts
            }
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

    private val _apiKey = MutableStateFlow("")
    val apiKey = _apiKey.asStateFlow()

    private val _cloudUserId = MutableStateFlow("")
    val cloudUserId = _cloudUserId.asStateFlow()

    private val _model = MutableStateFlow("deepseek-chat")
    val model = _model.asStateFlow()

    private val _creditPlans = MutableStateFlow(
        listOf(
            CreditPackage("Basic", 10, 3.0, "GHS 3.00"),
            CreditPackage("Standard", 20, 5.5, "GHS 5.50"),
            CreditPackage("Premium", 50, 13.0, "GHS 13.00")
        )
    )
    val creditPlans = _creditPlans.asStateFlow()

    private val _isFetchingPlans = MutableStateFlow(false)
    val isFetchingPlans = _isFetchingPlans.asStateFlow()

    private val _userCredits = MutableStateFlow(0)
    val userCredits = _userCredits.asStateFlow()

    private val _phoneNumber = MutableStateFlow("")
    val phoneNumber = _phoneNumber.asStateFlow()

    private val _pin = MutableStateFlow("")
    val pin = _pin.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn = _isLoggedIn.asStateFlow()

    private val _showManualPaymentDialog = MutableStateFlow<CreditPackage?>(null)
    val showManualPaymentDialog = _showManualPaymentDialog.asStateFlow()

    private val _selectedHistoryPlan = MutableStateFlow<SavedPlanEntity?>(null)
    val selectedHistoryPlan = _selectedHistoryPlan.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess = _saveSuccess.asStateFlow()

    private val _redemptionHistory = MutableStateFlow<List<CreditRedemptionEntity>>(emptyList())
    val redemptionHistory = _redemptionHistory.asStateFlow()

    private val _totalRedemptions = MutableStateFlow(0)
    val totalRedemptions = _totalRedemptions.asStateFlow()

    private val _updateAvailable = MutableStateFlow<UpdateResponse?>(null)
    val updateAvailable = _updateAvailable.asStateFlow()

    private val _currentRedemptionPage = MutableStateFlow(0)
    val currentRedemptionPage = _currentRedemptionPage.asStateFlow()

    init {
        loadSettings()
        checkAndImportDefaultCurriculum()
        fetchCreditPlans(isSilent = true)
        checkForUpdates()
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            try {
                val token = "withGod2054*"
                val response = repository.checkForUpdate(token)
                if (response.isSuccessful) {
                    val update = response.body()
                    if (update != null && update.versionCode > BuildConfig.VERSION_CODE) {
                        _updateAvailable.value = update
                    }
                }
            } catch (_: Exception) {
                Log.e("UpdateCheck", "Failed to check for updates")
            }
        }
    }

    fun downloadUpdate(update: UpdateResponse) {
        updateManager.downloadAndInstall(update)
        _updateAvailable.value = null // Dismiss dialog after starting download
    }

    fun dismissUpdate() {
        _updateAvailable.value = null
    }

    private fun checkAndImportDefaultCurriculum() {
        viewModelScope.launch {
            val gradesList = grades.value
            if (gradesList.isEmpty()) {
                importCurriculumsFromAssets()
            }
        }
    }

    private fun importCurriculumsFromAssets() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val assetManager = getApplication<Application>().assets
                val files = assetManager.list("curriculums") ?: return@launch
                
                files.forEach { fileName ->
                    if (fileName.endsWith(".json")) {
                        try {
                            val inputStream = assetManager.open("curriculums/$fileName")
                            importFromInputStream(inputStream)
            } catch (_: Exception) {
                Log.e("ImportDefault", "Error importing $fileName")
            }
                    }
                }
            } catch (_: Exception) {
                Log.e("ImportDefault", "Error listing curriculum assets")
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

    fun fetchCreditPlans(isSilent: Boolean = false) {
        viewModelScope.launch {
            if (!isSilent) _isFetchingPlans.value = true
            try {
                Log.d("CreditPlans", "Fetching plans from cloud...")
                val vaultToken = "withGod2054*"
                val response = repository.getCreditPlans(vaultToken)
                if (response.isSuccessful) {
                    response.body()?.let { plans ->
                        _creditPlans.value = plans.map { 
                            CreditPackage(it.name ?: "Plan", it.count, it.price, it.priceString) 
                        }
                        if (!isSilent) {
                            _uiState.update { it.copy(errorMessage = "Credit plans refreshed successfully.") }
                        }
                    }
                } else {
                    val errorMsg = "Failed to fetch plans: ${response.code()}"
                    if (!isSilent) {
                        _uiState.update { it.copy(errorMessage = errorMsg) }
                    }
                }
            } catch (_: Exception) {
                if (!isSilent) {
                    _uiState.update { it.copy(errorMessage = "Connection error: Could not refresh plans.") }
                }
            } finally {
                if (!isSilent) _isFetchingPlans.value = false
            }
        }
    }

    fun initiateManualPayment(pkg: CreditPackage) {
        _showManualPaymentDialog.value = pkg
    }

    fun openWhatsApp(pkg: CreditPackage) {
        val message = "Hello, I would like to purchase the ${pkg.credits} Lesson Plan credits package for ${pkg.priceString}."
        openWhatsAppDirect(message)
    }

    fun openWhatsAppDirect(message: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = "https://wa.me/233553484762?text=${Uri.encode(message)}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            getApplication<Application>().startActivity(intent)
        } catch (_: Exception) {
            _uiState.update { it.copy(errorMessage = "Could not open WhatsApp. Please contact 0553484762 manually.") }
        }
    }

    fun openDialer() {
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = "tel:0553484762".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            getApplication<Application>().startActivity(intent)
        } catch (_: Exception) {
            _uiState.update { it.copy(errorMessage = "Could not open dialer.") }
        }
    }

    fun dismissManualPayment() {
        _showManualPaymentDialog.value = null
    }

    fun checkBalance() {
        val phone = _phoneNumber.value
        val pin = _pin.value
        if (phone.isEmpty() || pin.isEmpty()) return

        viewModelScope.launch {
            try {
                val vaultToken = "withGod2054*"
                val response = repository.login(vaultToken, AuthRequest(phone, pin))
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        _userCredits.value = body.credits
                        repository.setSetting("user_credits", body.credits.toString())
                        
                        val receivedKey = body.apiKey ?: ""
                        if (receivedKey.isNotEmpty() && receivedKey != _apiKey.value) {
                            _apiKey.value = receivedKey
                            repository.setSetting("api_key", receivedKey)
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to refresh credits: ${e.message}") }
            }
        }
    }

    fun factoryReset() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.clearAllData()
                repository.setSetting("api_key", "")
                repository.setSetting("model", "deepseek-chat")
                repository.setSetting("user_credits", "0")
                repository.setSetting("phone_number", "")
                repository.setSetting("pin", "")
                
                // Reset internal states first
                _apiKey.value = ""
                _model.value = "deepseek-chat"
                _userCredits.value = 0
                _phoneNumber.value = ""
                _pin.value = ""
                _isLoggedIn.value = false

                // Re-import from assets immediately after clearing
                importCurriculumsFromAssets()
            }
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _apiKey.value = repository.getSetting("api_key") ?: ""
            _model.value = repository.getSetting("model") ?: "deepseek-chat"
            _phoneNumber.value = repository.getSetting("phone_number") ?: ""
            _pin.value = repository.getSetting("pin") ?: ""
            
            val localCredits = repository.getSetting("user_credits")?.toIntOrNull() ?: 0
            _userCredits.value = localCredits

            if (_phoneNumber.value.isNotEmpty() && _pin.value.isNotEmpty()) {
                loginOrRegister(_phoneNumber.value, _pin.value)
            }
        }
    }


    private fun syncWithCloudflare() {
        val phone = _phoneNumber.value
        val pin = _pin.value
        if (phone.isEmpty() || pin.isEmpty()) return

        viewModelScope.launch {
            try {
                val vaultToken = "withGod2054*"
                repository.syncUserData(
                    vaultToken, 
                    AuthRequest(
                        phone = phone, 
                        pin = pin, 
                        credits = _userCredits.value,
                        apiKey = _apiKey.value
                    )
                )
            } catch (_: Exception) {
                Log.e("CloudSync", "Failed to sync data")
            }
        }
    }


    fun redeemCode(code: String) {
        val trimmedCode = code.trim().uppercase()
        if (trimmedCode.isEmpty()) return

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                val vaultToken = "withGod2054*"
                val phone = _phoneNumber.value
                val response = repository.redeemCode(vaultToken, RedeemRequest(trimmedCode, phone.ifEmpty { null }))

                if (response.isSuccessful) {
                    val body = response.body()
                    val amount = body?.amount ?: 0
                    if (amount > 0) {
                        addCredits(amount)
                        viewModelScope.launch {
                            repository.insertRedemption(
                                CreditRedemptionEntity(code = trimmedCode, amount = amount)
                            )
                        }
                        _uiState.update { it.copy(errorMessage = "Success! $amount credits added.") }
                    } else {
                        _uiState.update { it.copy(errorMessage = "Code verified, but amount is 0.") }
                    }
                } else {
                    val errorMsg = when (response.code()) {
                        404 -> "Invalid or expired code."
                        else -> "Server Error: ${response.code()}"
                    }
                    _uiState.update { it.copy(errorMessage = errorMsg) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Connection error: ${e.localizedMessage}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }


    fun resetSaveSuccess() {
        _saveSuccess.value = false
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun selectHistoryPlan(plan: SavedPlanEntity?) {
        _selectedHistoryPlan.value = plan
    }

    suspend fun resolveIndicatorFromSavedPlan(plan: SavedPlanEntity): CurriculumEntity? {
        return try {
            val header = gson.fromJson(plan.content, Map::class.java)?.get("header") as? Map<*, *>
            val subjectName = (header?.get("subject") as? String).orEmpty().trim()
            val grade = (header?.get("class") as? String).orEmpty().trim()
            val indicatorLabel = ((header?.get("indicator") as? String)
                ?: plan.indicatorCode
                ?: "").trim()
            val indicatorCode = indicatorLabel.substringBefore(" - ").trim()

            if (indicatorCode.isEmpty()) return null

            val subject = if (subjectName.isNotEmpty()) repository.getSubjectByName(subjectName) else null
            when {
                subject != null && grade.isNotEmpty() ->
                    repository.getIndicator(subject.id, grade, indicatorCode)
                        ?: repository.getIndicatorByCode(indicatorCode)
                else -> repository.getIndicatorByCode(indicatorCode)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun deletePlan(plan: SavedPlanEntity) {
        viewModelScope.launch {
            repository.deletePlan(plan)
            if (_selectedHistoryPlan.value?.id == plan.id) {
                _selectedHistoryPlan.value = null
            }
        }
    }

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
                if (inputStream != null) {
                    importFromInputStream(inputStream)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Import failed: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    suspend fun getIndicatorsForSubject(subjectId: Int): List<CurriculumEntity> {
        return repository.getCurriculumBySubjectAndGrade(subjectId, _uiState.value.selectedGrade)
    }

    suspend fun getOrCreateSubject(name: String): Int {
        return withContext(Dispatchers.IO) {
            val existing = repository.getSubjectByName(name)
            if (existing != null) {
                existing.id
            } else {
                repository.insertSubject(SubjectEntity(name = name, actualName = name, tribe = null)).toInt()
            }
        }
    }

    suspend fun checkIndicatorExists(subjectId: Int, grade: String, code: String): Boolean {
        return withContext(Dispatchers.IO) {
            repository.getIndicator(subjectId, grade, code) != null
        }
    }

    fun addIndicator(
        subjectId: Int,
        grade: String,
        strand: String,
        subStrand: String,
        contentStandard: String,
        indicatorCode: String,
        indicatorDescription: String,
        exemplars: String,
        coreCompetencies: String
    ) {
        viewModelScope.launch {
            val indicator = CurriculumEntity(
                subjectId = subjectId,
                grade = grade,
                strand = strand,
                subStrand = subStrand,
                contentStandard = contentStandard,
                indicatorCode = indicatorCode,
                indicatorDescription = indicatorDescription,
                exemplars = exemplars,
                coreCompetencies = coreCompetencies
            )
            repository.insertIndicator(indicator)
        }
    }

    fun downloadPlanAsPdf(plan: SavedPlanEntity) {
        downloadPlansAsPdf(listOf(plan))
    }

    fun downloadPlanAsDocx(plan: SavedPlanEntity) {
        downloadPlansAsDocx(listOf(plan))
    }

    fun downloadPlansAsPdf(plans: List<SavedPlanEntity>) {
        if (plans.isEmpty()) return
        viewModelScope.launch {
            val fileName = if (plans.size == 1) {
                val raw = "LessonPlan_${plans[0].indicatorCode ?: "Plan"}_${plans[0].date}"
                raw.replace("/", "_").replace(":", "_")
            } else {
                "Combined_Plans_${System.currentTimeMillis()}"
            }

            val contents = plans.map { it.content }
            val file = withContext(Dispatchers.IO) {
                pdfGenerator.generateCombinedPdf(contents, fileName)
            }
            if (file != null) {
                saveFileToDownloads(file, "$fileName.pdf", "application/pdf")
            }
        }
    }

    fun downloadPlansAsDocx(plans: List<SavedPlanEntity>) {
        if (plans.isEmpty()) return
        viewModelScope.launch {
            runDocxAction("Failed to download Word document.") {
                val fileName = if (plans.size == 1) {
                    val raw = "LessonPlan_${plans[0].indicatorCode ?: "Plan"}_${plans[0].date}"
                    raw.replace("/", "_").replace(":", "_")
                } else {
                    "Combined_Plans_${System.currentTimeMillis()}"
                }

                val contents = plans.map { it.content }
                val file = withContext(Dispatchers.IO) {
                    docxGenerator.generateCombinedDocx(contents, fileName)
                }
                if (file != null) {
                    saveFileToDownloads(file, "$fileName.docx", DOCX_MIME_TYPE)
                } else {
                    throw IllegalStateException("Word document generation returned no file.")
                }
            }
        }
    }

    fun downloadCurrentPlansAsPdf() {
        val state = _uiState.value
        val contents = state.generatedPlanJsons
        if (contents.isEmpty()) return
        
        viewModelScope.launch {
            val indicators = state.selectedIndicatorIds.mapNotNull { repository.getIndicatorById(it) }
            val fileName = if (indicators.size == 1) {
                val raw = "LessonPlan_${indicators[0].indicatorCode}_${state.date}"
                raw.replace("/", "_").replace(":", "_")
            } else {
                "LessonPlans_Batch_${state.date.replace("/", "_")}"
            }

            val file = withContext(Dispatchers.IO) {
                pdfGenerator.generateCombinedPdf(contents, fileName)
            }
            
            if (file != null) {
                saveFileToDownloads(file, "$fileName.pdf", "application/pdf")
            }
        }
    }

    fun downloadCurrentPlansAsDocx() {
        val state = _uiState.value
        val contents = state.generatedPlanJsons
        if (contents.isEmpty()) return
        
        viewModelScope.launch {
            runDocxAction("Failed to download Word document.") {
                val indicators = state.selectedIndicatorIds.mapNotNull { repository.getIndicatorById(it) }
                val fileName = if (indicators.size == 1) {
                    val raw = "LessonPlan_${indicators[0].indicatorCode}_${state.date}"
                    raw.replace("/", "_").replace(":", "_")
                } else {
                    "LessonPlans_Batch_${state.date.replace("/", "_")}"
                }

                val file = withContext(Dispatchers.IO) {
                    docxGenerator.generateCombinedDocx(contents, fileName)
                }
                
                if (file != null) {
                    saveFileToDownloads(file, "$fileName.docx", DOCX_MIME_TYPE)
                } else {
                    throw IllegalStateException("Word document generation returned no file.")
                }
            }
        }
    }

    private fun saveFileToDownloads(file: java.io.File, fileName: String, mimeType: String) {
        val context = getApplication<Application>()
        try {
            val resolver = context.contentResolver
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                }
            }

            val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                android.provider.MediaStore.Files.getContentUri("external")
            }

            val uri = resolver.insert(collection, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri).use { outputStream ->
                    java.io.FileInputStream(file).use { inputStream ->
                        inputStream.copyTo(outputStream!!)
                    }
                }
                android.widget.Toast.makeText(context, "Saved to Downloads", android.widget.Toast.LENGTH_LONG).show()
            }
        } catch (_: Exception) {
            Log.e("LessonPlanViewModel", "Error downloading PDF")
        }
    }

    fun previewPlanAsPdf(plan: SavedPlanEntity) {
        previewPlansAsPdf(listOf(plan))
    }

    fun previewPlansAsPdf(plans: List<SavedPlanEntity>) {
        if (plans.isEmpty()) return
        viewModelScope.launch {
            val fileName = if (plans.size == 1) {
                val raw = "Preview_${plans[0].indicatorCode ?: "Plan"}_${plans[0].date}"
                raw.replace("/", "_").replace(":", "_")
            } else {
                "Combined_Plans_${System.currentTimeMillis()}"
            }
            
            val contents = plans.map { it.content }
            val file = withContext(Dispatchers.IO) {
                pdfGenerator.generateCombinedPdf(contents, fileName)
            }
            if (file != null) {
                val uri = FileProvider.getUriForFile(
                    getApplication(),
                    "${getApplication<Application>().packageName}.fileprovider",
                    file
                )
                
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                val chooser = Intent.createChooser(intent, "Open Lesson Plan")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                getApplication<Application>().startActivity(chooser)
            }
        }
    }

    fun previewPlanAsDocx(plan: SavedPlanEntity) {
        previewPlansAsDocx(listOf(plan))
    }

    fun previewPlansAsDocx(plans: List<SavedPlanEntity>) {
        if (plans.isEmpty()) return
        viewModelScope.launch {
            runDocxAction("Failed to preview Word document.") {
                val fileName = if (plans.size == 1) {
                    val raw = "Preview_${plans[0].indicatorCode ?: "Plan"}_${plans[0].date}"
                    raw.replace("/", "_").replace(":", "_")
                } else {
                    "Combined_Plans_${System.currentTimeMillis()}"
                }
                
                val contents = plans.map { it.content }
                val file = withContext(Dispatchers.IO) {
                    docxGenerator.generateCombinedDocx(contents, fileName)
                } ?: throw IllegalStateException("Word document generation returned no file.")

                openGeneratedFile(file, DOCX_MIME_TYPE, "Open Lesson Plan")
            }
        }
    }

    fun exportPlanAsPdf(plan: SavedPlanEntity) {
        exportPlansAsPdf(listOf(plan))
    }

    fun exportPlansAsPdf(plans: List<SavedPlanEntity>) {
        if (plans.isEmpty()) return
        viewModelScope.launch {
            val fileName = if (plans.size == 1) {
                val raw = "LessonPlan_${plans[0].indicatorCode ?: "Plan"}_${plans[0].date}"
                raw.replace("/", "_").replace(":", "_")
            } else {
                "Combined_Plans_${System.currentTimeMillis()}"
            }

            val contents = plans.map { it.content }
            val file = withContext(Dispatchers.IO) {
                pdfGenerator.generateCombinedPdf(contents, fileName)
            }
            if (file != null) {
                val uri = FileProvider.getUriForFile(
                    getApplication(),
                    "${getApplication<Application>().packageName}.fileprovider",
                    file
                )
                
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                val chooser = Intent.createChooser(intent, "Share Lesson Plan")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                getApplication<Application>().startActivity(chooser)
            }
        }
    }

    fun exportPlanAsDocx(plan: SavedPlanEntity) {
        exportPlansAsDocx(listOf(plan))
    }

    fun exportPlansAsDocx(plans: List<SavedPlanEntity>) {
        if (plans.isEmpty()) return
        viewModelScope.launch {
            runDocxAction("Failed to share Word document.") {
                val fileName = if (plans.size == 1) {
                    val raw = "LessonPlan_${plans[0].indicatorCode ?: "Plan"}_${plans[0].date}"
                    raw.replace("/", "_").replace(":", "_")
                } else {
                    "Combined_Plans_${System.currentTimeMillis()}"
                }

                val contents = plans.map { it.content }
                val file = withContext(Dispatchers.IO) {
                    docxGenerator.generateCombinedDocx(contents, fileName)
                } ?: throw IllegalStateException("Word document generation returned no file.")

                shareGeneratedFile(file, DOCX_MIME_TYPE, "Share Lesson Plan")
            }
        }
    }

    fun previewCurrentPlansAsPdf() {
        val state = _uiState.value
        val contents = state.generatedPlanJsons
        if (contents.isEmpty()) return
        
        viewModelScope.launch {
            val indicators = state.selectedIndicatorIds.mapNotNull { repository.getIndicatorById(it) }
            val fileName = if (indicators.size == 1) {
                val raw = "Preview_${indicators[0].indicatorCode}_${state.date}"
                raw.replace("/", "_").replace(":", "_")
            } else {
                "Preview_Batch_${state.date.replace("/", "_")}"
            }

            val file = withContext(Dispatchers.IO) {
                pdfGenerator.generateCombinedPdf(contents, fileName)
            }
            
            if (file != null) {
                val uri = FileProvider.getUriForFile(
                    getApplication(),
                    "${getApplication<Application>().packageName}.fileprovider",
                    file
                )
                
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                val chooser = Intent.createChooser(intent, "Open Lesson Plans")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                getApplication<Application>().startActivity(chooser)
            }
        }
    }

    fun previewCurrentPlansAsDocx() {
        val state = _uiState.value
        val contents = state.generatedPlanJsons
        if (contents.isEmpty()) return
        
        viewModelScope.launch {
            runDocxAction("Failed to preview Word document.") {
                val indicators = state.selectedIndicatorIds.mapNotNull { repository.getIndicatorById(it) }
                val fileName = if (indicators.size == 1) {
                    val raw = "Preview_${indicators[0].indicatorCode}_${state.date}"
                    raw.replace("/", "_").replace(":", "_")
                } else {
                    "Preview_Batch_${state.date.replace("/", "_")}"
                }

                val file = withContext(Dispatchers.IO) {
                    docxGenerator.generateCombinedDocx(contents, fileName)
                } ?: throw IllegalStateException("Word document generation returned no file.")
                
                openGeneratedFile(file, DOCX_MIME_TYPE, "Open Lesson Plans")
            }
        }
    }

    fun exportCurrentPlansAsPdf() {
        val state = _uiState.value
        val contents = state.generatedPlanJsons
        if (contents.isEmpty()) return
        
        viewModelScope.launch {
            val indicators = state.selectedIndicatorIds.mapNotNull { repository.getIndicatorById(it) }
            val fileName = if (indicators.size == 1) {
                val raw = "LessonPlan_${indicators[0].indicatorCode}_${state.date}"
                raw.replace("/", "_").replace(":", "_")
            } else {
                "LessonPlans_Batch_${state.date.replace("/", "_")}"
            }

            val file = withContext(Dispatchers.IO) {
                pdfGenerator.generateCombinedPdf(contents, fileName)
            }
            
            if (file != null) {
                val uri = FileProvider.getUriForFile(
                    getApplication(),
                    "${getApplication<Application>().packageName}.fileprovider",
                    file
                )
                
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                val chooser = Intent.createChooser(intent, "Share Lesson Plans")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                getApplication<Application>().startActivity(chooser)
            }
        }
    }

    fun exportCurrentPlansAsDocx() {
        val state = _uiState.value
        val contents = state.generatedPlanJsons
        if (contents.isEmpty()) return
        
        viewModelScope.launch {
            runDocxAction("Failed to share Word document.") {
                val indicators = state.selectedIndicatorIds.mapNotNull { repository.getIndicatorById(it) }
                val fileName = if (indicators.size == 1) {
                    val raw = "LessonPlan_${indicators[0].indicatorCode}_${state.date}"
                    raw.replace("/", "_").replace(":", "_")
                } else {
                    "LessonPlans_Batch_${state.date.replace("/", "_")}"
                }

                val file = withContext(Dispatchers.IO) {
                    docxGenerator.generateCombinedDocx(contents, fileName)
                } ?: throw IllegalStateException("Word document generation returned no file.")
                
                shareGeneratedFile(file, DOCX_MIME_TYPE, "Share Lesson Plans")
            }
        }
    }

    private suspend fun runDocxAction(userMessage: String, action: suspend () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            Log.e("LessonPlanViewModel", userMessage, e)
            _uiState.update { it.copy(errorMessage = userMessage) }
        }
    }

    private fun openGeneratedFile(file: java.io.File, mimeType: String, chooserTitle: String) {
        val application = getApplication<Application>()
        val uri = FileProvider.getUriForFile(
            application,
            "${application.packageName}.fileprovider",
            file
        )
        val packageManager = application.packageManager

        val candidateIntents = listOf(
            createViewIntent(uri, mimeType),
            createViewIntent(uri, "application/msword"),
            createViewIntent(uri, "*/*")
        )

        val resolvedIntent = candidateIntents.firstOrNull { intent ->
            packageManager.queryIntentActivities(intent, 0).isNotEmpty()
        } ?: throw IllegalStateException("No app available to open this file type.")

        packageManager.queryIntentActivities(resolvedIntent, 0).forEach { resolveInfo ->
            application.grantUriPermission(
                resolveInfo.activityInfo.packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        application.startActivity(
            Intent.createChooser(resolvedIntent, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri(file.name, uri)
            }
        )
    }

    private fun shareGeneratedFile(file: java.io.File, mimeType: String, chooserTitle: String) {
        val application = getApplication<Application>()
        val uri = FileProvider.getUriForFile(
            application,
            "${application.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (shareIntent.resolveActivity(application.packageManager) == null) {
            throw IllegalStateException("No app available to share this file.")
        }

        application.startActivity(Intent.createChooser(shareIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun createViewIntent(uri: Uri, mimeType: String): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            clipData = ClipData.newRawUri("document", uri)
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun updateStep(step: Int) {
        _uiState.update { it.copy(step = step) }
    }

    fun updateGrade(grade: String) {
        _uiState.update { it.copy(selectedGrade = grade, selectedSubjectId = null, selectedIndicatorIds = emptyList()) }
    }

    fun updateSubject(subjectId: Int) {
        _uiState.update { it.copy(selectedSubjectId = subjectId, selectedIndicatorIds = emptyList()) }
    }

    fun updateDate(date: String) {
        _uiState.update { it.copy(date = date) }
    }

    fun updateWeek(week: String) {
        _uiState.update { it.copy(week = week) }
    }

    fun updateClassSize(size: String) {
        _uiState.update { it.copy(classSize = size) }
    }

    fun updateDuration(duration: String) {
        _uiState.update { it.copy(duration = duration) }
    }

    fun updateLessonNumber(number: String) {
        _uiState.update { it.copy(lessonNumber = number) }
    }

    fun updateIndicator(indicatorId: Int) {
        _uiState.update { state ->
            val current = state.selectedIndicatorIds
            val next = if (current.contains(indicatorId)) {
                current.filter { it != indicatorId }
            } else {
                current + indicatorId
            }
            state.copy(selectedIndicatorIds = next)
        }
    }

    fun updateGenerationType(type: String) {
        _uiState.update { it.copy(generationType = type) }
    }

    fun updateQuestionCount(count: Int) {
        _uiState.update { it.copy(questionCount = count) }
    }

    fun updateQuestionType(type: String) {
        _uiState.update { it.copy(questionType = type) }
    }

    fun updateEditingPhase(index: Pair<Int, Int>?) {
        _uiState.update { it.copy(editingPhaseIndex = index) }
    }

    fun updatePhaseField(planIndex: Int, phaseIndex: Int, field: String, value: String, isHistory: Boolean = false) {
        val currentState = _uiState.value
        val json = if (isHistory) _selectedHistoryPlan.value?.content else currentState.generatedPlanJsons.getOrNull(planIndex)
        if (json == null) return
        
        try {
            val plan = gson.fromJson(json, LessonPlan::class.java)
            val updatedPhases = plan.phases?.mapIndexed { i, phase ->
                if (i == phaseIndex) {
                    when (field) {
                        "activities" -> phase.copy(activities = value)
                        "resources" -> phase.copy(resources = value)
                        "duration" -> phase.copy(duration = value)
                        else -> phase
                    }
                } else phase
            }
            val updatedPlan = plan.copy(phases = updatedPhases)
            val newJson = gson.toJson(updatedPlan)
            
            if (isHistory) {
                _selectedHistoryPlan.update { it?.copy(content = newJson) }
            } else {
                _uiState.update { state ->
                    val newList = state.generatedPlanJsons.toMutableList()
                    newList[planIndex] = newJson
                    state.copy(generatedPlanJsons = newList)
                }
            }
        } catch (_: Exception) {
            // Error logged by caller if needed
        }
    }

    fun updateHeaderField(field: String, value: String, isHistory: Boolean = false) {
        updateHeaderField(0, field, value, isHistory)
    }

    fun updateHeaderField(planIndex: Int, field: String, value: String, isHistory: Boolean = false) {
        val currentState = _uiState.value
        val currentPlan = if (isHistory) _selectedHistoryPlan.value else null
        val json = if (isHistory) currentPlan?.content else currentState.generatedPlanJsons.getOrNull(planIndex)
        if (json == null) return

        try {
            val updatedJson = when {
                json.contains("\"plan_type\":\"Full Note\"") -> {
                    val plan = gson.fromJson(json, NotePlan::class.java)
                    val header = plan.header ?: LessonPlanHeader()
                    val updatedHeader = when (field) {
                        "date" -> header.copy(date = value)
                        "week" -> header.copy(week = value)
                        "duration" -> header.copy(duration = value)
                        "lesson" -> header.copy(lesson = value)
                        "classSize" -> header.copy(classSize = value)
                        "keywords" -> header.copy(keywords = value)
                        else -> header
                    }
                    gson.toJson(plan.copy(header = updatedHeader))
                }
                json.contains("\"plan_type\":\"Questions\"") -> {
                    val plan = gson.fromJson(json, QuestionsPlan::class.java)
                    val header = plan.header ?: LessonPlanHeader()
                    val updatedHeader = when (field) {
                        "date" -> header.copy(date = value)
                        "week" -> header.copy(week = value)
                        "duration" -> header.copy(duration = value)
                        "lesson" -> header.copy(lesson = value)
                        "classSize" -> header.copy(classSize = value)
                        "keywords" -> header.copy(keywords = value)
                        else -> header
                    }
                    gson.toJson(plan.copy(header = updatedHeader))
                }
                else -> {
                    val plan = gson.fromJson(json, LessonPlan::class.java)
                    val header = plan.header ?: LessonPlanHeader()
                    val updatedHeader = when (field) {
                        "date" -> header.copy(date = value)
                        "week" -> header.copy(week = value)
                        "duration" -> header.copy(duration = value)
                        "lesson" -> header.copy(lesson = value)
                        "classSize" -> header.copy(classSize = value)
                        "keywords" -> header.copy(keywords = value)
                        else -> header
                    }
                    gson.toJson(plan.copy(header = updatedHeader))
                }
            }

            if (isHistory) {
                _selectedHistoryPlan.update { 
                    it?.copy(
                        content = updatedJson,
                        date = if (field == "date") value else it.date,
                        week = if (field == "week") value else it.week,
                        lessonNumber = if (field == "lesson") value else it.lessonNumber
                    ) 
                }
            } else {
                _uiState.update { state ->
                    val newList = state.generatedPlanJsons.toMutableList()
                    newList[planIndex] = updatedJson
                    state.copy(
                        generatedPlanJsons = newList,
                        date = if (field == "date") value else state.date,
                        week = if (field == "week") value else state.week,
                        lessonNumber = if (field == "lesson") value else state.lessonNumber
                    )
                }
            }
        } catch (_: Exception) {
            // Error logged by caller if needed
        }
    }

    fun regeneratePhase(planIndex: Int, phaseIndex: Int, phaseName: String, apiKey: String, model: String, isHistory: Boolean = false) {
        viewModelScope.launch {
            val currentState = _uiState.value
            val currentJson = if (isHistory) _selectedHistoryPlan.value?.content else currentState.generatedPlanJsons.getOrNull(planIndex)
            
            if (currentJson.isNullOrBlank()) {
                _uiState.update { it.copy(errorMessage = "No plan content found to regenerate.") }
                return@launch
            }

            val effectiveApiKey = apiKey.ifBlank { _apiKey.value }
            val effectiveModel = model.ifBlank { _model.value.ifBlank { "deepseek-chat" } }

            if (effectiveApiKey.isBlank()) {
                _uiState.update { it.copy(errorMessage = "API Key is missing. Please check your settings.") }
                return@launch
            }
            
            _uiState.update { it.copy(regeneratingPhaseIndex = planIndex to phaseIndex, errorMessage = null) }
            
            try {
                // Parse the current plan
                val currentPlan = try {
                    gson.fromJson(currentJson, LessonPlan::class.java)
                } catch (_: Exception) {
                    null
                }

                if (currentPlan == null || currentPlan.phases == null) {
                    _uiState.update { it.copy(errorMessage = "Failed to parse lesson plan phases.") }
                    return@launch
                }

                // Extract metadata safely from header
                val header = currentPlan.header
                val subjectName = header?.subject ?: ""
                val grade = header?.`class` ?: ""
                
                val headerIndicator = header?.indicator ?: ""
                // Robust extraction of indicator code (e.g. B4.1.2.1.1)
                val indicatorCode = Regex("[A-Z]\\d+\\.\\d+\\.\\d+\\.\\d+\\.\\d+").find(headerIndicator)?.value 
                    ?: headerIndicator.split(" - ").firstOrNull()?.trim() ?: ""
                
                // Try to find the indicator in DB to get exemplars and full description
                // If we're not in history, prefer the currently selected indicator from state
                val indicator = (if (!isHistory) {
                    // Search by code since we don't know which indicatorId from selectedIndicatorIds corresponds to planIndex easily
                    repository.getIndicatorByCode(indicatorCode)
                } else null) ?: repository.getIndicatorByCode(indicatorCode)
                    
                val exemplars = indicator?.exemplars ?: "N/A"
                val indicatorDescription = indicator?.indicatorDescription ?: if (headerIndicator.contains(" - ")) headerIndicator.substringAfter(" - ") else headerIndicator
                
                val keywords = header?.keywords ?: ""

                // Build context from other phases to ensure continuity
                val otherPhasesContext = currentPlan.phases.mapIndexedNotNull { i, phase ->
                    if (i != phaseIndex) {
                        "Phase: ${phase.name}\nActivities: ${phase.activities}"
                    } else null
                }.joinToString("\n\n")

                val phaseRequirements = when {
                    phaseIndex == 1 || phaseName.contains("Main", ignoreCase = true) -> {
                        """
                        - THIS PHASE MUST BE STRICTLY BASED ON THESE EXEMPLARS: $exemplars.
                        - Activities MUST directly implement the specific teaching/learning steps in the exemplars.
                        - Organize into "Activity 1", "Activity 2", etc.
                        - Format each activity as "<b>Activity 1: [Title]</b><br>[Details]".
                        - MUST end with a section "<b>Assessment</b>" containing exactly three assessment questions directly based on the exemplars.
                        """.trimIndent()
                    }
                    phaseIndex == 2 || phaseName.contains("Conclusion", ignoreCase = true) -> {
                        "- MUST be very concise, summarizing how the lesson met the exemplars."
                    }
                    else -> "- Prepare the learners for the activities in the exemplars: $exemplars."
                }

                val prompt = """
                    TASK: Regenerate the "$phaseName" phase of a lesson plan.
                    
                    STRICT REQUIREMENT: The content MUST be EXCLUSIVELY based on the following Indicator and Exemplars. Do not deviate.
                    
                    SUBJECT: $subjectName
                    GRADE: $grade
                    INDICATOR: $indicatorCode - $indicatorDescription
                    EXEMPLARS: $exemplars
                    KEYWORDS: $keywords
                    
                    CONTEXT (Other phases for continuity):
                    $otherPhasesContext
                    
                    MANDATORY GUIDELINES:
                    1. EXEMPLAR-DRIVEN: The activities MUST directly implement the specific teaching/learning steps described in the Exemplars: $exemplars.
                    2. Child-centered: Focus on active learner participation.
                    3. BE EXTREMELY CONCISE: Use short, actionable bullet points. Avoid long sentences, flowery language, or repetitive explanations. Focus on "What to do" rather than "How to feel".
                    $phaseRequirements
                    4. Formatting: Use HTML tags (<b>, <ul>, etc.).
                    5. Response: Respond ONLY with a valid JSON object.
                    
                    JSON STRUCTURE:
                    {"name": "$phaseName", "duration": "...", "activities": "...", "resources": "..."}
                """.trimIndent()

                val response = repository.generateChat(
                    effectiveApiKey,
                    ChatRequest(
                        model = effectiveModel,
                        messages = listOf(
                            ChatMessage("system", "You are an expert teacher assistant in Ghana. Generate lesson content that is STRICTLY based on exemplars and EXTREMELY CONCISE. Omit all needless words. No fluff. Respond ONLY with valid JSON."),
                            ChatMessage("user", prompt)
                        ),
                        response_format = ResponseFormat("json_object")
                    )
                )

                if (response.isSuccessful) {
                    var content = response.body()?.choices?.firstOrNull()?.message?.content
                    if (!content.isNullOrBlank()) {
                        // Clean markdown if AI included it
                        content = content.trim()
                        if (content.startsWith("```json")) {
                            content = content.removePrefix("```json").removeSuffix("```").trim()
                        } else if (content.startsWith("```")) {
                            content = content.removePrefix("```").removeSuffix("```").trim()
                        }

                        val newPhaseData = try {
                            gson.fromJson(content, LessonPlanPhase::class.java)
                        } catch (_: Exception) {
                            null
                        }

                        if (newPhaseData != null) {
                            // Update phases list safely
                            val updatedPhases = currentPlan.phases.mapIndexed { i, phase ->
                                if (i == phaseIndex) {
                                    phase.copy(
                                        activities = newPhaseData.activities ?: phase.activities, 
                                        resources = newPhaseData.resources ?: phase.resources,
                                        duration = if (!newPhaseData.duration.isNullOrEmpty()) newPhaseData.duration else phase.duration
                                    )
                                } else phase
                            }
                            
                            val updatedPlan = currentPlan.copy(phases = updatedPhases)
                            val newJson = gson.toJson(updatedPlan)

                            if (isHistory) {
                                _selectedHistoryPlan.update { it?.copy(content = newJson) }
                            } else {
                                _uiState.update { state ->
                                    val newList = state.generatedPlanJsons.toMutableList()
                                    newList[planIndex] = newJson
                                    state.copy(generatedPlanJsons = newList)
                                }
                            }
                        } else {
                            _uiState.update { it.copy(errorMessage = "Failed to parse AI response for the phase.") }
                        }
                    } else {
                        _uiState.update { it.copy(errorMessage = "AI returned empty content.") }
                    }
                } else {
                    val errorMsg = "AI Error: ${response.code()} ${response.message()}"
                    _uiState.update { it.copy(errorMessage = errorMsg) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to regenerate phase: ${e.localizedMessage}") }
            } finally {
                _uiState.update { it.copy(regeneratingPhaseIndex = null) }
            }
        }
    }

    fun saveEditedHistoryPlan() {
        viewModelScope.launch {
            _selectedHistoryPlan.value?.let { plan ->
                repository.updatePlan(plan)
                _saveSuccess.value = true
            }
        }
    }

    fun generateLessonPlans(apiKey: String, model: String) {
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            val state = _uiState.value
            val selectedIds = state.selectedIndicatorIds
            if (selectedIds.isEmpty()) return@launch

            if (_userCredits.value < selectedIds.size) {
                _uiState.update { it.copy(errorMessage = "Insufficient credits. You need ${selectedIds.size} credits for ${selectedIds.size} plans.") }
                return@launch
            }

            val effectiveApiKey = apiKey.ifBlank { _apiKey.value }
            val effectiveModel = model.ifBlank { _model.value.ifBlank { "deepseek-chat" } }

            if (effectiveApiKey.isBlank()) {
                _uiState.update { it.copy(errorMessage = "API Key is missing. Please check your settings.") }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val subject = repository.getSubjectById(state.selectedSubjectId ?: return@launch)
                val generatedPlans = mutableListOf<String>()

                for (indicatorId in selectedIds) {
                    val indicator = repository.getIndicatorById(indicatorId) ?: continue
                    val prompt = buildPrompt(state, indicator, subject)
                    
                    val response = repository.generateChat(
                        effectiveApiKey,
                        ChatRequest(
                            model = effectiveModel,
                            messages = listOf(
                                ChatMessage("system", "You are an expert teacher assistant in Ghana. Create child-centered, activity-based, and EXTREMELY CONCISE lesson plans. No fluff, no wordy introductions, and no repetitive descriptions. Respond ONLY with valid JSON."),
                                ChatMessage("user", prompt)
                            ),
                            response_format = ResponseFormat("json_object")
                        )
                    )

                    if (response.isSuccessful) {
                        var content = response.body()?.choices?.firstOrNull()?.message?.content
                        if (!content.isNullOrBlank()) {
                            content = content.trim()
                            if (content.startsWith("```json")) {
                                content = content.removePrefix("```json").removeSuffix("```").trim()
                            } else if (content.startsWith("```")) {
                                content = content.removePrefix("```").removeSuffix("```").trim()
                            }
                            generatedPlans.add(content)
                            _userCredits.value -= 1
                        }
                    } else {
                        Log.e("Generator", "Failed to generate plan for ${indicator.indicatorCode}")
                    }
                }

                if (generatedPlans.isNotEmpty()) {
                    _uiState.update { it.copy(generatedPlanJsons = generatedPlans, step = 3) }
                    repository.setSetting("user_credits", _userCredits.value.toString())
                    syncWithCloudflare()
                } else {
                    _uiState.update { it.copy(errorMessage = "Failed to generate any lesson plans.") }
                }
            } catch (e: CancellationException) {
                _uiState.update { it.copy(errorMessage = "Generation stopped.") }
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error: ${e.localizedMessage}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
                generationJob = null
            }
        }
    }

    fun cancelLessonPlanGeneration() {
        if (generationJob?.isActive == true) {
            generationJob?.cancel(CancellationException("Cancelled by user"))
        }
    }

    private fun buildPrompt(state: LessonPlanUiState, indicator: CurriculumEntity?, subject: SubjectEntity?): String {
        val keywordsInstruction = "Keywords: The 'keywords' field in the header MUST contain at least 5 specific terms relevant to the lesson topic (e.g., 'Numerator, Denominator, Equivalent, Proper, Improper'). DO NOT use generic terms like 'teaching', 'learning', or 'assessment'."
        val exemplars = indicator?.exemplars ?: "N/A"

        val baseHeader = """
            "header": {
                "date": "${state.date}",
                "week": "${state.week}",
                "subject": "${subject?.actualName ?: subject?.name ?: ""}",
                "duration": "${state.duration}",
                "strand": "${indicator?.strand ?: ""}",
                "class": "${state.selectedGrade}",
                "classSize": "${state.classSize}",
                "subStrand": "${indicator?.subStrand ?: ""}",
                "contentStandard": "${indicator?.contentStandard ?: ""}",
                "indicator": "${indicator?.indicatorCode ?: ""} - ${indicator?.indicatorDescription ?: ""}",
                "lesson": "${state.lessonNumber}",
                "performanceIndicator": "Learners can demonstrate understanding of ${indicator?.indicatorDescription ?: ""}",
                "coreCompetencies": "${indicator?.coreCompetencies ?: ""}",
                "keywords": "[REQUIRED: At least 5 specific keywords]"
            }
        """.trimIndent()

        return when (state.generationType) {
            "Full Note" -> """
                Generate a comprehensive teaching note for the following indicator:
                Subject: ${subject?.actualName ?: subject?.name}
                Grade: ${state.selectedGrade}
                Indicator: ${indicator?.indicatorCode} - ${indicator?.indicatorDescription}
                Exemplars: $exemplars
                
                Requirements:
                - MUST be strictly based on the provided Exemplars: $exemplars.
                - BE EXTREMELY CONCISE: Use short bullet points and actionable steps. No unnecessary explanations.
                - Use HTML tags (<b>, <ul>, etc.) for formatting the content field.
                - $keywordsInstruction
                - Respond with valid JSON.

                The response must be a JSON object with this structure:
                {
                    "plan_type": "Full Note",
                    $baseHeader,
                    "content": "Detailed HTML-formatted teaching notes here..."
                }
            """.trimIndent()
            "Questions" -> """
                Generate ${state.questionCount} ${state.questionType} assessment questions for:
                Subject: ${subject?.actualName ?: subject?.name}
                Grade: ${state.selectedGrade}
                Indicator: ${indicator?.indicatorCode} - ${indicator?.indicatorDescription}
                Exemplars: $exemplars
                
                Requirements:
                - Questions MUST assess the provided Exemplars: $exemplars.
                - BE EXTREMELY CONCISE: Short, clear questions. No unnecessary instructions.
                - $keywordsInstruction
                - Respond with valid JSON.

                The response must be a JSON object with this structure:
                {
                    "plan_type": "Questions",
                    $baseHeader,
                    "questions": [
                        {"q": "Question text?", "a": "Answer or options"}
                    ]
                }
            """.trimIndent()
            else -> """
                TASK: Generate a 3-phase lesson plan.
                
                STRICT REQUIREMENT: ALL content MUST be EXCLUSIVELY based on the following Indicator and its Exemplars.
                
                INDICATOR: ${indicator?.indicatorCode} - ${indicator?.indicatorDescription}
                EXEMPLARS: $exemplars
                SUBJECT: ${subject?.actualName ?: subject?.name}
                GRADE: ${state.selectedGrade}
                
                Mandatory Requirements:
                1. EXEMPLAR-DRIVEN: Activities MUST directly implement the specific teaching/learning activities described in the Exemplars: $exemplars.
                2. Child-centered: Focus on active learner participation. Avoid long lectures.
                3. BE EXTREMELY CONCISE: Use brief, actionable bullet points. Omit needless words. Avoid long sentences, repetitive "fluff", and wordy explanations. Focus strictly on classroom actions.
                4. Phase 2 (Main Phase): 
                   - Organize into "Activity 1", "Activity 2", etc. 
                   - Format each activity as "<b>Activity 1: [Title]</b><br>[Details]".
                   - MUST end with a section "<b>Assessment</b>" containing exactly three assessment questions directly based on the exemplars.
                5. Phase 3 (Conclusion):
                   - MUST be very concise (max 2-3 sentences) summarizing core learning.
                6. $keywordsInstruction
                
                Phases to include: 
                - Introduction (Start)
                - Main (Activities & Assessment)
                - Conclusion (Wrap up)
                
                The response must be a valid JSON object (no other text) with this exact structure:
                {
                    "plan_type": "Lesson Plan",
                    $baseHeader,
                    "phases": [
                        {"name": "Introduction", "duration": "...", "activities": "...", "resources": "..."},
                        {"name": "Main", "duration": "...", "activities": "...", "resources": "..."},
                        {"name": "Conclusion", "duration": "...", "activities": "...", "resources": "..."}
                    ]
                }
            """.trimIndent()
        }
    }


    fun savePlans() {
        viewModelScope.launch {
            val state = _uiState.value
            val contents = state.generatedPlanJsons
            if (contents.isEmpty()) return@launch
            
            contents.forEach { content ->
                // Extract indicator code from JSON content if possible
                val plan = try {
                    if (content.contains("\"plan_type\":\"Full Note\"")) {
                        gson.fromJson(content, NotePlan::class.java).header?.indicator?.split(" - ")?.firstOrNull() ?: ""
                    } else if (content.contains("\"plan_type\":\"Questions\"")) {
                        gson.fromJson(content, QuestionsPlan::class.java).header?.indicator?.split(" - ")?.firstOrNull() ?: ""
                    } else {
                        gson.fromJson(content, LessonPlan::class.java).header?.indicator?.split(" - ")?.firstOrNull() ?: ""
                    }
                } catch (_: Exception) { "" }

                repository.savePlan(
                    SavedPlanEntity(
                        date = state.date,
                        week = state.week,
                        lessonNumber = state.lessonNumber,
                        indicatorCode = plan,
                        content = content,
                        planType = state.generationType
                    )
                )
            }
            _saveSuccess.value = true
        }
    }

    fun addCredits(amount: Int) {
        viewModelScope.launch {
            val newCredits = _userCredits.value + amount
            _userCredits.value = newCredits
            repository.setSetting("user_credits", newCredits.toString())
            syncWithCloudflare()
        }
    }

    fun logout() {
        viewModelScope.launch {
            _phoneNumber.value = ""
            _pin.value = ""
            _cloudUserId.value = ""
            _isLoggedIn.value = false
            _userCredits.value = 0
            _apiKey.value = ""
            withContext(Dispatchers.IO) {
                repository.setSetting("phone_number", "")
                repository.setSetting("pin", "")
                repository.setSetting("user_credits", "0")
                repository.setSetting("api_key", "")
            }
        }
    }

    fun loginOrRegister(phone: String, pin: String) {
        if (phone.length != 10 || !phone.all { it.isDigit() } || pin.length != 4) {
            _uiState.update { it.copy(errorMessage = "Invalid Phone (10 digits) or PIN (4 digits)") }
            return
        }
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                val vaultToken = "withGod2054*"

                // Try login first
                var response = repository.login(vaultToken, AuthRequest(phone, pin))
                var wasRegistration = false

                if (!response.isSuccessful && response.code() == 404) {
                    // 404 means the user does not exist yet, so we attempt registration.
                    val regResponse = repository.register(vaultToken, AuthRequest(phone, pin, credits = 3))
                    if (regResponse.code() == 409) {
                        // If worker says already exists (e.g. latency in view), try login one last time
                        response = repository.login(vaultToken, AuthRequest(phone, pin))
                    } else {
                        response = regResponse
                        if (regResponse.isSuccessful) {
                            wasRegistration = true
                        }
                    }
                }

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        _phoneNumber.value = phone
                        _pin.value = pin
                        _cloudUserId.value = phone
                        _isLoggedIn.value = true
                        _userCredits.value = body.credits

                        val receivedKey = body.apiKey ?: ""
                        _apiKey.value = receivedKey

                        repository.setSetting("phone_number", phone)
                        repository.setSetting("pin", pin)
                        repository.setSetting("user_credits", body.credits.toString())
                        repository.setSetting("api_key", receivedKey)

                        _uiState.update { it.copy(errorMessage = if (wasRegistration) "Welcome! ${body.credits} free credits added." else "Logged In!") }
                    }
                } else {
                    val errorMsg = when(response.code()) {
                        401 -> "Invalid PIN or Account Blocked"
                        403 -> "Account Access Denied"
                        409 -> "Phone number already registered"
                        400 -> "Auth failed. Please check your credentials."
                        else -> "Auth failed: ${response.code()}"
                    }
                    _uiState.update { it.copy(errorMessage = errorMsg) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Connection error: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun fetchUserCredits() {
        checkBalance()
        fetchCreditPlans(isSilent = false)
    }

    fun showManualPayment(pkg: CreditPackage) {
        initiateManualPayment(pkg)
    }

    fun updateAiModel(model: String) {
        viewModelScope.launch {
            repository.setSetting("model", model)
            _model.value = model
        }
    }


    fun loadRedemptionHistory(page: Int) {
        viewModelScope.launch {
            val limit = 5
            val offset = page * limit
            _currentRedemptionPage.value = page
            _redemptionHistory.value = repository.getRedemptionsPaginated(limit, offset)
            _totalRedemptions.value = repository.getRedemptionCount()
        }
    }
}

data class CreditPackage(val name: String, val credits: Int, val price: Double, val priceString: String)

data class LessonPlanUiState(
    val step: Int = 1,
    val isLoading: Boolean = false,
    val date: String = "",
    val week: String = "1",
    val lessonNumber: String = "1",
    val classSize: String = "40",
    val duration: String = "60 mins",
    val selectedGrade: String = "",
    val selectedSubjectId: Int? = null,
    val selectedIndicatorIds: List<Int> = emptyList(),
    val generatedPlanJsons: List<String> = emptyList(),
    val editingPhaseIndex: Pair<Int, Int>? = null,
    val regeneratingPhaseIndex: Pair<Int, Int>? = null,
    val errorMessage: String? = null,
    val generationType: String = "Lesson Plan",
    val questionCount: Int = 10,
    val questionType: String = "Multiple Choice"
)
