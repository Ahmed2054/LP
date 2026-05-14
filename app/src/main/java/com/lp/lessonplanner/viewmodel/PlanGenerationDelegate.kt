package com.lp.lessonplanner.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.lp.lessonplanner.data.local.CurriculumEntity
import com.lp.lessonplanner.data.local.SubjectEntity
import com.lp.lessonplanner.data.remote.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ── Generation ────────────────────────────────────────────────────────────────

fun LessonPlanViewModel.generateLessonPlans(apiKey: String, model: String) {
    generationJob?.cancel()
    generationJob = viewModelScope.launch {
        val state = _uiState.value
        val selectedIds = state.selectedIndicatorIds
        if (selectedIds.isEmpty()) return@launch

        if (_userCredits.value < selectedIds.size) {
            _uiState.update {
                it.copy(errorMessage = "Insufficient credits. You need ${selectedIds.size} credits for ${selectedIds.size} plans.")
            }
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
                val metadata = state.indicatorMetadata[indicatorId] ?: IndicatorMetadata()
                val prompt = buildPrompt(state, indicator, subject, metadata)

                val response = repository.generateChat(
                    effectiveApiKey,
                    ChatRequest(
                        model = effectiveModel,
                        messages = listOf(
                            ChatMessage("system", SYSTEM_PROMPT),
                            ChatMessage("user", prompt)
                        ),
                        response_format = ResponseFormat("json_object")
                    )
                )

                if (response.isSuccessful) {
                    val content = response.body()?.choices?.firstOrNull()?.message?.content?.sanitizeJson()
                    if (!content.isNullOrBlank()) {
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

fun LessonPlanViewModel.cancelLessonPlanGeneration() {
    if (generationJob?.isActive == true) {
        generationJob?.cancel(CancellationException("Cancelled by user"))
    }
}

// ── Full plan regeneration ────────────────────────────────────────────────────

fun LessonPlanViewModel.regeneratePlan(planIndex: Int, apiKey: String, model: String, isHistory: Boolean = false) {
    viewModelScope.launch {
        val currentState = _uiState.value
        val currentPlanEntity = if (isHistory) _selectedHistoryPlans.value.getOrNull(planIndex) else null
        val currentJson = if (isHistory) currentPlanEntity?.content else currentState.generatedPlanJsons.getOrNull(planIndex)

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

        val planType = currentJson.detectPlanType()
        val header = currentJson.parsePlanHeader(gson, planType)

        val indicatorCode = currentJson.extractIndicatorCodeFromRaw().ifBlank {
            header?.extractIndicatorCode() ?: currentPlanEntity?.indicatorCode ?: ""
        }
        val indicator = when {
            indicatorCode.isNotBlank() -> repository.getIndicatorByCode(indicatorCode)
            !isHistory -> currentState.selectedIndicatorIds.getOrNull(planIndex)?.let { repository.getIndicatorById(it) }
            else -> null
        }

        if (indicator == null) {
            _uiState.update { it.copy(errorMessage = "Could not find the indicator for this plan.") }
            return@launch
        }

        val subjectName = header?.subject.orEmpty()
        val subject = if (subjectName.isNotBlank()) repository.getSubjectByName(subjectName)
        else currentState.selectedSubjectId?.let { repository.getSubjectById(it) }

        val regenerationState = currentState.copy(
            generationType = planType,
            date = header?.date ?: currentState.date,
            week = header?.week ?: currentState.week,
            weekEnding = header?.weekEnding ?: currentState.weekEnding,
            lessonNumber = header?.lesson ?: currentState.lessonNumber,
            classSize = header?.classSize ?: currentState.classSize,
            duration = header?.duration ?: currentState.duration,
            selectedGrade = header?.`class` ?: currentState.selectedGrade,
            questionCount = header?.count ?: currentState.questionCount,
            questionType = header?.type ?: currentState.questionType
        )

        _uiState.update { it.copy(regeneratingPhaseIndex = planIndex to -1, errorMessage = null) }
        try {
            val metadata = IndicatorMetadata(
                date = regenerationState.date,
                week = regenerationState.week,
                weekEnding = regenerationState.weekEnding,
                lessonNumber = regenerationState.lessonNumber
            )
            val prompt = buildPrompt(regenerationState, indicator, subject, metadata)
            val response = repository.generateChat(
                effectiveApiKey,
                ChatRequest(
                    model = effectiveModel,
                    messages = listOf(
                        ChatMessage("system", SYSTEM_PROMPT),
                        ChatMessage("user", prompt)
                    ),
                    response_format = ResponseFormat("json_object")
                )
            )

            if (response.isSuccessful) {
                val regeneratedJson = response.body()?.choices?.firstOrNull()?.message?.content?.sanitizeJson()
                if (!regeneratedJson.isNullOrBlank()) {
                    if (isHistory) {
                        updateSelectedHistoryPlan(planIndex) {
                            it.copy(
                                content = regeneratedJson,
                                date = regenerationState.date,
                                week = regenerationState.week,
                                lessonNumber = regenerationState.lessonNumber,
                                indicatorCode = indicator.indicatorCode,
                                planType = planType
                            )
                        }
                    } else {
                        _uiState.update { state ->
                            val newList = state.generatedPlanJsons.toMutableList()
                            if (planIndex in newList.indices) newList[planIndex] = regeneratedJson
                            state.copy(generatedPlanJsons = newList)
                        }
                    }
                } else {
                    _uiState.update { it.copy(errorMessage = "AI returned empty content.") }
                }
            } else {
                _uiState.update { it.copy(errorMessage = "AI Error: ${response.code()} ${response.message()}") }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "Failed to regenerate: ${e.localizedMessage}") }
        } finally {
            _uiState.update { it.copy(regeneratingPhaseIndex = null) }
        }
    }
}

// ── Phase regeneration ────────────────────────────────────────────────────────

fun LessonPlanViewModel.regeneratePhase(
    planIndex: Int,
    phaseIndex: Int,
    phaseName: String,
    apiKey: String,
    model: String,
    isHistory: Boolean = false
) {
    viewModelScope.launch {
        val currentState = _uiState.value
        val currentJson = if (isHistory) _selectedHistoryPlans.value.getOrNull(planIndex)?.content
        else currentState.generatedPlanJsons.getOrNull(planIndex)

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

        val currentPlan = try { gson.fromJson(currentJson.sanitizeJson(), LessonPlan::class.java) } catch (_: Exception) { null }
        if (currentPlan?.phases == null) {
            _uiState.update { it.copy(errorMessage = "Failed to parse lesson plan phases.") }
            return@launch
        }

        _uiState.update { it.copy(regeneratingPhaseIndex = planIndex to phaseIndex, errorMessage = null) }
        try {
            val indicatorCode = currentJson.extractIndicatorCodeFromRaw()
            val header = currentPlan.header

            val indicator = (if (!isHistory) repository.getIndicatorByCode(indicatorCode) else null)
                ?: repository.getIndicatorByCode(indicatorCode)

            val exemplars = indicator?.exemplars ?: "N/A"
            val indicatorDesc = indicator?.indicatorDescription
                ?: if ((header?.indicator ?: "").contains(" - ")) (header?.indicator ?: "").substringAfter(" - ")
                else (header?.indicator ?: "")

            val otherPhasesContext = currentPlan.phases.mapIndexedNotNull { i, phase ->
                if (i != phaseIndex) "Phase: ${phase.name}\nActivities: ${phase.activities}" else null
            }.joinToString("\n\n")

            val phaseRequirements = buildPhaseRequirements(phaseIndex, phaseName, exemplars)
            val prompt = """
                TASK: Regenerate the "$phaseName" phase of a lesson plan.
                
                STRICT REQUIREMENT: The content MUST be EXCLUSIVELY based on the following Indicator and Exemplars. Do not deviate.
                
                SUBJECT: ${header?.subject}
                GRADE: ${header?.`class`}
                INDICATOR: $indicatorCode - $indicatorDesc
                EXEMPLARS: $exemplars
                KEYWORDS: ${header?.keywords}
                
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
                val content = response.body()?.choices?.firstOrNull()?.message?.content?.sanitizeJson()
                if (!content.isNullOrBlank()) {
                    val newPhase = try { gson.fromJson(content, LessonPlanPhase::class.java) } catch (_: Exception) { null }
                    if (newPhase != null) {
                        val updatedPhases = currentPlan.phases.mapIndexed { i, phase ->
                            if (i == phaseIndex) phase.copy(
                                activities = newPhase.activities ?: phase.activities,
                                resources = newPhase.resources ?: phase.resources,
                                duration = if (!newPhase.duration.isNullOrEmpty()) newPhase.duration else phase.duration
                            ) else phase
                        }
                        val newJson = gson.toJson(currentPlan.copy(phases = updatedPhases))
                        if (isHistory) {
                            updateSelectedHistoryPlan(planIndex) { it.copy(content = newJson) }
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
                _uiState.update { it.copy(errorMessage = "AI Error: ${response.code()} ${response.message()}") }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "Failed to regenerate phase: ${e.localizedMessage}") }
        } finally {
            _uiState.update { it.copy(regeneratingPhaseIndex = null) }
        }
    }
}

// ── Prompt builder ────────────────────────────────────────────────────────────

internal fun LessonPlanViewModel.buildPrompt(
    state: LessonPlanUiState,
    indicator: CurriculumEntity?,
    subject: SubjectEntity?,
    metadata: IndicatorMetadata
): String {
    val keywordsInstruction = "Keywords: The 'keywords' field in the header MUST be a single string containing at least 5 specific terms relevant to the lesson topic, separated by commas (e.g., 'Numerator, Denominator, Equivalent, Proper, Improper'). DO NOT use a JSON array. DO NOT use generic terms like 'teaching', 'learning', or 'assessment'."
    val exemplars = indicator?.exemplars ?: "N/A"
    val subjectName = subject?.actualName ?: subject?.name ?: ""

    val baseHeader = """
        "header": {
            "school": "${state.school}",
            "facilitatorName": "${state.facilitatorName}",
            "term": "${state.term}",
            "date": "${metadata.date}",
            "week": "${metadata.week}",
            "weekEnding": "${metadata.weekEnding}",
            "subject": "$subjectName",
            "duration": "${state.duration}",
            "strand": "${indicator?.strand ?: ""}",
            "class": "${state.selectedGrade}",
            "classSize": "${state.classSize}",
            "subStrand": "${indicator?.subStrand ?: ""}",
            "contentStandard": "${indicator?.contentStandard ?: ""}",
            "indicator": "${indicator?.indicatorCode ?: ""} - ${indicator?.indicatorDescription ?: ""}",
            "lesson": "${metadata.lessonNumber}",
            "performanceIndicator": "Learners can demonstrate understanding of ${indicator?.indicatorDescription ?: ""}",
            "coreCompetencies": "${indicator?.coreCompetencies ?: ""}",
            "keywords": "[REQUIRED: At least 5 specific keywords]"
        }
    """.trimIndent()

    return when (state.generationType) {
        "Full Note" -> """
            TASK: Generate a professional, comprehensive, and well-organized teaching note based EXCLUSIVELY on the EXEMPLARS of the specified indicator.
            
            Subject: $subjectName
            Grade: ${state.selectedGrade}
            Indicator: ${indicator?.indicatorCode} - ${indicator?.indicatorDescription}
            Exemplars: $exemplars
            
            The note must be a complete teaching resource, thoroughly explaining each concept mentioned in the exemplars.
            
            Organization Structure:
            1. <b>TOPIC:</b> [A clear, descriptive title for the lesson]
            2. <b>LEARNING OBJECTIVES:</b> List 3-4 specific, measurable objectives derived from the indicator and exemplars.
            3. <b>KEY TERMS & DEFINITIONS:</b> Define all technical terms or complex concepts found in the exemplars.
            4. <b>DETAILED LESSON CONTENT:</b> 
               - This is the main body. Thoroughly explain EACH exemplar: $exemplars.
               - Use bold sub-headings for each: <b>[Exemplar Code]: [Descriptive Title]</b>.
               - Provide in-depth explanations, examples, and step-by-step breakdowns for every concept.
               - Ensure the content is sufficient for a full classroom lesson.
            5. <b>SUMMARY & CONCLUSION:</b> A detailed wrap-up of the key takeaways.
            6. <b>CHECK FOR UNDERSTANDING:</b> 3-4 questions or a brief activity to evaluate learner progress.

            Formatting & Quality Rules:
            - **STRICT BASIS**: All teaching content MUST be derived from the provided Exemplars.
            - **CLEAR HIERARCHY**: Use <b> for headings and <ul><li> for lists.
            - **SPACING**: Use <br><br> to separate the 6 main sections.
            - **COMPLETENESS**: The note should be "ready-to-teach," meaning it needs depth and clarity.
            - $keywordsInstruction
            - Respond with valid JSON.

            The response must be a JSON object with this structure:
            {
                "plan_type": "Full Note",
                $baseHeader,
                "content": "<b>TOPIC:</b>...<br><br><b>LEARNING OBJECTIVES:</b>...<br><br><b>KEY TERMS:</b>..."
            }
        """.trimIndent()

        "Questions" -> """
            TASK: Generate ${state.questionCount} ${state.questionType} assessment questions based EXCLUSIVELY on the EXEMPLARS of the specified indicator.
            
            Subject: $subjectName
            Grade: ${state.selectedGrade}
            Indicator: ${indicator?.indicatorCode} - ${indicator?.indicatorDescription}
            Exemplars: $exemplars
            
            Requirements:
            - Questions MUST assess the provided Exemplars: $exemplars.
            - Format: 
                * For "Multiple Choice": Each question MUST include 4 options (A, B, C, D) immediately after the question text.
                * For "True/False": Each question MUST include "True / False" options immediately after the question text.
                * For "Fill in the blank" and "Essay": Just the question text.
            - BE EXTREMELY CONCISE: Short, clear questions. No unnecessary instructions.
            - The "a" field (Answer) in the JSON MUST contain only the correct answer or the correct option letter (e.g., "B" or "True").
            - $keywordsInstruction
            - Respond with valid JSON.

            The response must be a JSON object with this structure:
            {
                "plan_type": "Questions",
                $baseHeader,
                "questions": [
                    {"q": "Question text here?\nA) Option\nB) Option\nC) Option\nD) Option", "a": "Correct Option/Answer"}
                ]
            }
        """.trimIndent()

        else -> """
            TASK: Generate a 3-phase lesson plan.
            
            STRICT REQUIREMENT: ALL content MUST be EXCLUSIVELY based on the following Indicator and its Exemplars.
            
            INDICATOR: ${indicator?.indicatorCode} - ${indicator?.indicatorDescription}
            EXEMPLARS: $exemplars
            SUBJECT: $subjectName
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

// ── Private helpers ───────────────────────────────────────────────────────────

private fun buildPhaseRequirements(phaseIndex: Int, phaseName: String, exemplars: String): String =
    when {
        phaseIndex == 1 || phaseName.contains("Main", ignoreCase = true) -> """
            - THIS PHASE MUST BE STRICTLY BASED ON THESE EXEMPLARS: $exemplars.
            - Activities MUST directly implement the specific teaching/learning steps in the exemplars.
            - Organize into "Activity 1", "Activity 2", etc.
            - Format each activity as "<b>Activity 1: [Title]</b><br>[Details]".
            - MUST end with a section "<b>Assessment</b>" containing exactly three assessment questions directly based on the exemplars.
        """.trimIndent()
        phaseIndex == 2 || phaseName.contains("Conclusion", ignoreCase = true) ->
            "- MUST be very concise, summarizing how the lesson met the exemplars."
        else -> "- Prepare the learners for the activities in the exemplars: $exemplars."
    }


internal const val SYSTEM_PROMPT =
    "You are an expert teacher assistant in Ghana. Create child-centered, activity-based, and EXTREMELY CONCISE lesson plans. No fluff, no wordy introductions, and no repetitive descriptions. Respond ONLY with valid JSON."
