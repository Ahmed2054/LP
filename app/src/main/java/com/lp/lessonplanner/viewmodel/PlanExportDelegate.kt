package com.lp.lessonplanner.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.viewModelScope
import com.lp.lessonplanner.data.local.SavedPlanEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── Download ──────────────────────────────────────────────────────────────────

fun LessonPlanViewModel.downloadPlanAsPdf(plan: SavedPlanEntity) =
    downloadPlansAsPdf(listOf(plan))

fun LessonPlanViewModel.downloadPlanAsDocx(plan: SavedPlanEntity) =
    downloadPlansAsDocx(listOf(plan))

fun LessonPlanViewModel.downloadPlansAsPdf(plans: List<SavedPlanEntity>) {
    if (plans.isEmpty()) return
    viewModelScope.launch {
        val fileName = buildFileName(plans, "LessonPlan")
        val contents = plans.map { it.content }
        val file = withContext(Dispatchers.IO) { pdfGenerator.generateCombinedPdf(contents, fileName) }
        if (file != null) saveFileToDownloads(file, "$fileName.pdf", "application/pdf")
    }
}

fun LessonPlanViewModel.downloadPlansAsDocx(plans: List<SavedPlanEntity>) {
    if (plans.isEmpty()) return
    viewModelScope.launch {
        runDocxAction("Failed to download Word document.") {
            val fileName = buildFileName(plans, "LessonPlan")
            val contents = plans.map { it.content }
            val file = withContext(Dispatchers.IO) { docxGenerator.generateCombinedDocx(contents, fileName) }
                ?: throw IllegalStateException("Word document generation returned no file.")
            saveFileToDownloads(file, "$fileName.docx", LessonPlanViewModel.DOCX_MIME_TYPE)
        }
    }
}

fun LessonPlanViewModel.downloadCurrentPlansAsPdf() {
    val state = _uiState.value
    val contents = state.generatedPlanJsons
    if (contents.isEmpty()) return
    viewModelScope.launch {
        val fileName = buildCurrentPlansFileName(state, "LessonPlan")
        val file = withContext(Dispatchers.IO) { pdfGenerator.generateCombinedPdf(contents, fileName) }
        if (file != null) saveFileToDownloads(file, "$fileName.pdf", "application/pdf")
    }
}

fun LessonPlanViewModel.downloadCurrentPlansAsDocx() {
    val state = _uiState.value
    val contents = state.generatedPlanJsons
    if (contents.isEmpty()) return
    viewModelScope.launch {
        runDocxAction("Failed to download Word document.") {
            val fileName = buildCurrentPlansFileName(state, "LessonPlan")
            val file = withContext(Dispatchers.IO) { docxGenerator.generateCombinedDocx(contents, fileName) }
                ?: throw IllegalStateException("Word document generation returned no file.")
            saveFileToDownloads(file, "$fileName.docx", LessonPlanViewModel.DOCX_MIME_TYPE)
        }
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

fun LessonPlanViewModel.previewPlanAsPdf(plan: SavedPlanEntity) =
    previewPlansAsPdf(listOf(plan))

fun LessonPlanViewModel.previewPlansAsPdf(plans: List<SavedPlanEntity>) {
    if (plans.isEmpty()) return
    viewModelScope.launch {
        val fileName = buildFileName(plans, "Preview")
        val contents = plans.map { it.content }
        val file = withContext(Dispatchers.IO) { pdfGenerator.generateCombinedPdf(contents, fileName) }
        if (file != null) {
            val uri = getFileProviderUri(file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startChooserActivity(intent, "Open Lesson Plan")
        }
    }
}

fun LessonPlanViewModel.previewPlanAsDocx(plan: SavedPlanEntity) =
    previewPlansAsDocx(listOf(plan))

fun LessonPlanViewModel.previewPlansAsDocx(plans: List<SavedPlanEntity>) {
    if (plans.isEmpty()) return
    viewModelScope.launch {
        runDocxAction("Failed to preview Word document.") {
            val fileName = buildFileName(plans, "Preview")
            val contents = plans.map { it.content }
            val file = withContext(Dispatchers.IO) { docxGenerator.generateCombinedDocx(contents, fileName) }
                ?: throw IllegalStateException("Word document generation returned no file.")
            openGeneratedFile(file, LessonPlanViewModel.DOCX_MIME_TYPE, "Open Lesson Plan")
        }
    }
}

fun LessonPlanViewModel.previewCurrentPlansAsPdf() {
    val state = _uiState.value
    val contents = state.generatedPlanJsons
    if (contents.isEmpty()) return
    viewModelScope.launch {
        val fileName = buildCurrentPlansFileName(state, "Preview")
        val file = withContext(Dispatchers.IO) { pdfGenerator.generateCombinedPdf(contents, fileName) }
        if (file != null) {
            val uri = getFileProviderUri(file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startChooserActivity(intent, "Open Lesson Plans")
        }
    }
}

fun LessonPlanViewModel.previewCurrentPlansAsDocx() {
    val state = _uiState.value
    val contents = state.generatedPlanJsons
    if (contents.isEmpty()) return
    viewModelScope.launch {
        runDocxAction("Failed to preview Word document.") {
            val fileName = buildCurrentPlansFileName(state, "Preview")
            val file = withContext(Dispatchers.IO) { docxGenerator.generateCombinedDocx(contents, fileName) }
                ?: throw IllegalStateException("Word document generation returned no file.")
            openGeneratedFile(file, LessonPlanViewModel.DOCX_MIME_TYPE, "Open Lesson Plans")
        }
    }
}

// ── Export / Share ────────────────────────────────────────────────────────────

fun LessonPlanViewModel.exportPlanAsPdf(plan: SavedPlanEntity) =
    exportPlansAsPdf(listOf(plan))

fun LessonPlanViewModel.exportPlansAsPdf(plans: List<SavedPlanEntity>) {
    if (plans.isEmpty()) return
    viewModelScope.launch {
        val fileName = buildFileName(plans, "LessonPlan")
        val contents = plans.map { it.content }
        val file = withContext(Dispatchers.IO) { pdfGenerator.generateCombinedPdf(contents, fileName) }
        if (file != null) {
            val uri = getFileProviderUri(file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startChooserActivity(intent, "Share Lesson Plan")
        }
    }
}

fun LessonPlanViewModel.exportPlanAsDocx(plan: SavedPlanEntity) =
    exportPlansAsDocx(listOf(plan))

fun LessonPlanViewModel.exportPlansAsDocx(plans: List<SavedPlanEntity>) {
    if (plans.isEmpty()) return
    viewModelScope.launch {
        runDocxAction("Failed to share Word document.") {
            val fileName = buildFileName(plans, "LessonPlan")
            val contents = plans.map { it.content }
            val file = withContext(Dispatchers.IO) { docxGenerator.generateCombinedDocx(contents, fileName) }
                ?: throw IllegalStateException("Word document generation returned no file.")
            shareGeneratedFile(file, LessonPlanViewModel.DOCX_MIME_TYPE, "Share Lesson Plan")
        }
    }
}

fun LessonPlanViewModel.exportCurrentPlansAsPdf() {
    val state = _uiState.value
    val contents = state.generatedPlanJsons
    if (contents.isEmpty()) return
    viewModelScope.launch {
        val fileName = buildCurrentPlansFileName(state, "LessonPlan")
        val file = withContext(Dispatchers.IO) { pdfGenerator.generateCombinedPdf(contents, fileName) }
        if (file != null) {
            val uri = getFileProviderUri(file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startChooserActivity(intent, "Share Lesson Plans")
        }
    }
}

fun LessonPlanViewModel.exportCurrentPlansAsDocx() {
    val state = _uiState.value
    val contents = state.generatedPlanJsons
    if (contents.isEmpty()) return
    viewModelScope.launch {
        runDocxAction("Failed to share Word document.") {
            val fileName = buildCurrentPlansFileName(state, "LessonPlan")
            val file = withContext(Dispatchers.IO) { docxGenerator.generateCombinedDocx(contents, fileName) }
                ?: throw IllegalStateException("Word document generation returned no file.")
            shareGeneratedFile(file, LessonPlanViewModel.DOCX_MIME_TYPE, "Share Lesson Plans")
        }
    }
}

// ── Internal helpers ──────────────────────────────────────────────────────────

internal fun LessonPlanViewModel.saveFileToDownloads(file: java.io.File, fileName: String, mimeType: String) {
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
            resolver.openOutputStream(uri).use { out ->
                java.io.FileInputStream(file).use { it.copyTo(out!!) }
            }
            Toast.makeText(context, "Saved to Downloads", Toast.LENGTH_LONG).show()
        }
    } catch (_: Exception) {
        Log.e("LessonPlanViewModel", "Error saving file to Downloads")
    }
}

internal fun LessonPlanViewModel.openGeneratedFile(file: java.io.File, mimeType: String, chooserTitle: String) {
    val application = getApplication<Application>()
    val uri = getFileProviderUri(file)
    val pm = application.packageManager

    val candidateIntents = listOf(
        createViewIntent(uri, mimeType),
        createViewIntent(uri, "application/msword"),
        createViewIntent(uri, "*/*")
    )
    val resolved = candidateIntents.firstOrNull { pm.queryIntentActivities(it, 0).isNotEmpty() }
        ?: throw IllegalStateException("No app available to open this file type.")

    pm.queryIntentActivities(resolved, 0).forEach { info ->
        application.grantUriPermission(info.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    application.startActivity(
        Intent.createChooser(resolved, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri(file.name, uri)
        }
    )
}

internal fun LessonPlanViewModel.shareGeneratedFile(file: java.io.File, mimeType: String, chooserTitle: String) {
    val application = getApplication<Application>()
    val uri = getFileProviderUri(file)
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

internal fun LessonPlanViewModel.getFileProviderUri(file: java.io.File): Uri {
    val application = getApplication<Application>()
    return FileProvider.getUriForFile(application, "${application.packageName}.fileprovider", file)
}

internal fun LessonPlanViewModel.startChooserActivity(intent: Intent, title: String) {
    val chooser = Intent.createChooser(intent, title)
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    getApplication<Application>().startActivity(chooser)
}

private fun LessonPlanViewModel.createViewIntent(uri: Uri, mimeType: String): Intent =
    Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        clipData = ClipData.newRawUri("document", uri)
        addCategory(Intent.CATEGORY_DEFAULT)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

internal suspend fun LessonPlanViewModel.runDocxAction(userMessage: String, action: suspend () -> Unit) {
    try {
        action()
    } catch (e: Exception) {
        Log.e("LessonPlanViewModel", userMessage, e)
        _uiState.update { it.copy(errorMessage = userMessage) }
    }
}

// ── File name builders ────────────────────────────────────────────────────────

private fun buildFileName(plans: List<SavedPlanEntity>, prefix: String): String =
    if (plans.size == 1) {
        "${prefix}_${plans[0].indicatorCode ?: "Plan"}_${plans[0].date}"
            .replace("/", "_").replace(":", "_")
    } else {
        "Combined_Plans_${System.currentTimeMillis()}"
    }

private suspend fun LessonPlanViewModel.buildCurrentPlansFileName(state: LessonPlanUiState, prefix: String): String {
    val indicators = state.selectedIndicatorIds.mapNotNull { repository.getIndicatorById(it) }
    return if (indicators.size == 1) {
        "${prefix}_${indicators[0].indicatorCode}_${state.date}"
            .replace("/", "_").replace(":", "_")
    } else {
        "${prefix}_Batch_${state.date.replace("/", "_")}"
    }
}
