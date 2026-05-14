package com.lp.lessonplanner.ui.utils

fun formatToDDMMYYYY(dateStr: String?): String {
    if (dateStr.isNullOrBlank()) return ""
    val partsDash = dateStr.split("-")
    if (partsDash.size == 3 && partsDash[0].length == 4) {
        return "${partsDash[2]}/${partsDash[1]}/${partsDash[0]}"
    }
    val partsSlash = dateStr.split("/")
    if (partsSlash.size == 3 && partsSlash[0].length == 4) {
        return "${partsSlash[2]}/${partsSlash[1]}/${partsSlash[0]}"
    }
    return dateStr
}
