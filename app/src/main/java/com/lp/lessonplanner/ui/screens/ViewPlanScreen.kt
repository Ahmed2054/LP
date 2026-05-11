package com.lp.lessonplanner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.lp.lessonplanner.data.local.CurriculumEntity
import com.lp.lessonplanner.data.local.SavedPlanEntity
import com.lp.lessonplanner.ui.components.LessonPlanPreview
import com.lp.lessonplanner.ui.utils.FormatAction
import com.lp.lessonplanner.viewmodel.LessonPlanViewModel
import kotlinx.coroutines.launch

@Composable
fun ViewPlanScreen(
    plan: SavedPlanEntity,
    viewModel: LessonPlanViewModel,
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val model by viewModel.model.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showFormatDialog by remember { mutableStateOf<FormatAction?>(null) }
    var selectedIndicatorForDetail by remember { mutableStateOf<CurriculumEntity?>(null) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Plan") },
            text = { Text("Are you sure you want to delete this plan? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (selectedIndicatorForDetail != null) {
        AlertDialog(
            onDismissRequest = { selectedIndicatorForDetail = null },
            title = { Text(selectedIndicatorForDetail?.indicatorCode ?: "Indicator Details", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { DetailRow("Strand", selectedIndicatorForDetail?.strand) }
                    item { DetailRow("Sub-Strand", selectedIndicatorForDetail?.subStrand) }
                    item { DetailRow("Content Standard", selectedIndicatorForDetail?.contentStandard) }
                    item { DetailRow("Description", selectedIndicatorForDetail?.indicatorDescription) }
                    item { DetailRow("Exemplars", selectedIndicatorForDetail?.exemplars) }
                    item { DetailRow("Core Competencies", selectedIndicatorForDetail?.coreCompetencies) }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedIndicatorForDetail = null }) {
                    Text("Close")
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White
        )
    }

    if (showFormatDialog != null) {
        val action = showFormatDialog!!
        AlertDialog(
            onDismissRequest = { showFormatDialog = null },
            title = {
                Text(
                    when (action) {
                        is FormatAction.Preview -> "Preview Format"
                        is FormatAction.Download -> "Download Format"
                        is FormatAction.Export -> "Share Format"
                    }
                )
            },
            text = { Text("Choose a format:") },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = {
                            when (action) {
                                is FormatAction.Preview -> viewModel.previewPlanAsPdf(plan)
                                is FormatAction.Download -> viewModel.downloadPlanAsPdf(plan)
                                is FormatAction.Export -> viewModel.exportPlanAsPdf(plan)
                            }
                            showFormatDialog = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                    ) {
                        Text("PDF")
                    }
                    Button(
                        onClick = {
                            when (action) {
                                is FormatAction.Preview -> viewModel.previewPlanAsDocx(plan)
                                is FormatAction.Download -> viewModel.downloadPlanAsDocx(plan)
                                is FormatAction.Export -> viewModel.exportPlanAsDocx(plan)
                            }
                            showFormatDialog = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                    ) {
                        Text("Word (.docx)")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showFormatDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearErrorMessage()
        }
    }

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            snackbarHostState.showSnackbar("Changes saved successfully")
            viewModel.resetSaveSuccess()
        }
    }

    val planMap = remember(plan.content) {
        try {
            Gson().fromJson(plan.content, Map::class.java)
        } catch (e: Exception) {
            null
        }
    }
    val header = planMap?.get("header") as? Map<*, *>
    val grade = header?.get("class") as? String ?: ""
    val subjectName = header?.get("subject") as? String ?: ""
    val indicatorLabel = header?.get("indicator") as? String ?: plan.indicatorCode ?: ""
    val indicatorCode = indicatorLabel.split(" - ").firstOrNull()?.trim().orEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StepHeaderItem(number = 3, label = "Preview & Edit", color = Color(0xFF4CAF50))
                        Surface(
                            color = Color(0xFFE8F5E9),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = "1 Plan Generated",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.saveEditedHistoryPlan() },
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save Changes", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { showFormatDialog = FormatAction.Download },
                            modifier = Modifier.weight(1.1f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF673AB7)),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF673AB7))
                            Spacer(Modifier.width(4.dp))
                            Text("Download", color = Color(0xFF673AB7), fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { showFormatDialog = FormatAction.Export },
                            modifier = Modifier.weight(1.1f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2196F3)),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF2196F3))
                            Spacer(Modifier.width(4.dp))
                            Text("Share", color = Color(0xFF2196F3), fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { viewModel.previewPlanAsPdf(plan) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF9800)),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFFF9800))
                            Spacer(Modifier.width(4.dp))
                            Text("Preview", color = Color(0xFFFF9800), fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Updating lesson plan...", color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    coroutineScope.launch {
                                        selectedIndicatorForDetail = viewModel.resolveIndicatorFromSavedPlan(plan)
                                    }
                                },
                            color = Color(0xFFE3F2FD),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBDEFB))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF2196F3), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = listOfNotNull(
                                        grade.ifEmpty { null },
                                        subjectName.ifEmpty { null },
                                        indicatorCode.ifEmpty { null }
                                    ).joinToString(" · "),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1976D2)
                                )
                            }
                        }

                        LessonPlanPreview(
                            generatedPlanJson = plan.content,
                            editingPhaseIndex = uiState.editingPhaseIndex?.second,
                            regeneratingPhaseIndex = uiState.regeneratingPhaseIndex?.second,
                            onEditPhase = { phaseIndex ->
                                viewModel.updateEditingPhase(phaseIndex?.let { 0 to it })
                            },
                            onUpdatePhaseField = { phaseIndex, field, value ->
                                viewModel.updatePhaseField(0, phaseIndex, field, value, isHistory = true)
                            },
                            onRegeneratePhase = { phaseIndex, name ->
                                viewModel.regeneratePhase(0, phaseIndex, name, apiKey, model, isHistory = true)
                            },
                            onUpdateHeaderField = { field, value ->
                                viewModel.updateHeaderField(0, field, value, isHistory = true)
                            }
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}
