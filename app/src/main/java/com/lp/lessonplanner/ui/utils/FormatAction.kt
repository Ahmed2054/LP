package com.lp.lessonplanner.ui.utils

sealed class FormatAction {
    object Preview : FormatAction()
    object Download : FormatAction()
    object Export : FormatAction()
}
