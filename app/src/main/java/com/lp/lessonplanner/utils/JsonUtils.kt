package com.lp.lessonplanner.utils

import com.google.gson.Gson
import com.lp.lessonplanner.data.remote.LessonPlan
import com.lp.lessonplanner.data.remote.LessonPlanHeader
import com.lp.lessonplanner.data.remote.NotePlan
import com.lp.lessonplanner.data.remote.QuestionsPlan

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

fun String.detectPlanType(): String = when {
    contains("\"plan_type\":\"Full Note\"") -> "Full Note"
    contains("\"plan_type\":\"Questions\"") -> "Questions"
    else -> "Lesson Plan"
}

fun String.parsePlanHeader(gson: Gson, planType: String): LessonPlanHeader? = try {
    val sanitized = this.sanitizeJson()
    when (planType) {
        "Full Note" -> gson.fromJson(sanitized, NotePlan::class.java).header
        "Questions" -> gson.fromJson(sanitized, QuestionsPlan::class.java).header
        else -> gson.fromJson(sanitized, LessonPlan::class.java).header
    }
} catch (_: Exception) { null }

fun String.extractIndicatorCodeFromRaw(): String {
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
