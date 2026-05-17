package com.lp.lessonplanner.utils

fun String.removeCitationMarkers(): String {
    val citationPattern = Regex("\\s*\\[\\s*cite\\s*:\\s*[^\\]]*\\]", RegexOption.IGNORE_CASE)
    return replace(citationPattern, "")
        .lines()
        .joinToString("\n") { line ->
            line.replace(Regex("[ \\t]{2,}"), " ").trimEnd()
        }
        .trim()
}

fun String?.cleanCurriculumText(): String = this?.removeCitationMarkers()?.trim().orEmpty()

fun String.moveLeadingDokTagsToEnd(): String {
    val dokAtQuestionStart = Regex(
        "(^|<br\\s*/?>|\\n|<li>)" +
            "(\\s*(?:\\d+[.)]\\s*)?)" +
            "\\[(DoK\\s*L[1-4])\\]\\s*" +
            "([^<\\n]+?)" +
            "(\\s*(?=<br\\s*/?>|\\n|</li>|$))",
        setOf(RegexOption.IGNORE_CASE)
    )

    return replace(dokAtQuestionStart) { match ->
        val prefix = match.groupValues[1]
        val number = match.groupValues[2]
        val dok = match.groupValues[3].replace(Regex("\\s+"), " ")
        val question = match.groupValues[4].trimEnd()
        val suffix = match.groupValues[5]
        "$prefix$number$question [$dok]$suffix"
    }
}
