package com.lp.lessonplanner.viewmodel

import com.lp.lessonplanner.data.local.SavedPlanEntity

data class CreditPackage(val name: String, val credits: Int, val price: Double, val priceString: String)

data class IndicatorMetadata(
    val week: String = "",
    val date: String = "",
    val weekEnding: String = "",
    val lessonNumber: String = "1",
    val dokLevels: List<String> = emptyList()
)

data class LessonPlanUiState(
    val step: Int = 1,
    val isLoading: Boolean = false,
    val school: String = "",
    val facilitatorName: String = "",
    val term: String = "1",
    val classSize: String = "40",
    val duration: String = "60 mins",
    val selectedGrade: String = "",
    val selectedSubjectId: Int? = null,
    val selectedIndicatorIds: List<Int> = emptyList(),
    val indicatorMetadata: Map<Int, IndicatorMetadata> = emptyMap(),
    val generatedPlanJsons: List<String> = emptyList(),
    val editingPhaseIndex: Pair<Int, Int>? = null,
    val regeneratingPhaseIndex: Pair<Int, Int>? = null,
    val errorMessage: String? = null,
    val generationType: String = "Lesson Plan",
    val questionCount: Int = 10,
    val questionType: String = "Multiple Choice",
    val date: String = "",
    val week: String = "",
    val weekEnding: String = "",
    val lessonNumber: String = "1",
    val historyPage: Int = 0,
    val totalHistoryCount: Int = 0,
    val paginatedHistory: List<SavedPlanEntity> = emptyList(),
    val historySearchQuery: String = "",
    val historyTabIndex: Int = 0
)
