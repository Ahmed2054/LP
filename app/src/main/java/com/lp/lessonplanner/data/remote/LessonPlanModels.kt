package com.lp.lessonplanner.data.remote

import com.google.gson.annotations.SerializedName

data class CurriculumJsonModel(
    val subject: String?,
    val grade: String?,
    val strand: String?,
    @SerializedName("sub_strand")
    val subStrand: String?,
    @SerializedName("content_standard")
    val contentStandard: String?,
    @SerializedName("indicator_code")
    val indicatorCode: String?,
    @SerializedName("indicator_description")
    val indicatorDescription: String?,
    val examplars: String?,
    @SerializedName("core_competencies")
    val coreCompetencies: String?
)

data class LessonPlan(
    val header: LessonPlanHeader? = null,
    val phases: List<LessonPlanPhase>? = null,
    val plan_type: String? = "Lesson Plan"
)

data class LessonPlanHeader(
    val date: String? = "",
    val week: String? = "",
    val subject: String? = "",
    val duration: String? = "",
    val strand: String? = "",
    val `class`: String? = "",
    val classSize: String? = "",
    val subStrand: String? = "",
    val contentStandard: String? = "",
    val indicator: String? = "",
    val lesson: String? = "",
    val performanceIndicator: String? = "",
    val coreCompetencies: String? = "",
    val keywords: String? = "",
    val type: String? = null,
    val count: Int? = null
)

data class LessonPlanPhase(
    val name: String? = null,
    val duration: String? = null,
    val activities: String? = null,
    val resources: String? = null
)

data class NotePlan(
    val plan_type: String? = "Full Note",
    val header: LessonPlanHeader? = null,
    val content: String? = null
)

data class QuestionsPlan(
    val plan_type: String? = "Questions",
    val header: LessonPlanHeader? = null,
    val questions: List<QuestionItem>? = null
)

data class QuestionItem(
    val q: String? = null,
    val a: String? = null
)
