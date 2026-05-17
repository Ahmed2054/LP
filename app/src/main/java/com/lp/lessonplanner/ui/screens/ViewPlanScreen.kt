package com.lp.lessonplanner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.lp.lessonplanner.viewmodel.*
import kotlinx.coroutines.launch

@Composable
fun ViewPlanScreen(
    plans: List<SavedPlanEntity>,
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
            title = { Text(if (plans.size == 1) "Delete Plan" else "Delete ${plans.size} Plans") },
            text = { Text(if (plans.size == 1) "Are you sure you want to delete this plan? This action cannot be undone." else "Are you sure you want to delete these ${plans.size} plans? This action cannot be undone.") },
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
                                is FormatAction.Preview -> viewModel.previewPlansAsPdf(plans)
                                is FormatAction.Download -> viewModel.downloadPlansAsPdf(plans)
                                is FormatAction.Export -> viewModel.exportPlansAsPdf(plans)
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
                                is FormatAction.Preview -> viewModel.previewPlansAsDocx(plans)
                                is FormatAction.Download -> viewModel.downloadPlansAsDocx(plans)
                                is FormatAction.Export -> viewModel.exportPlansAsDocx(plans)
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


    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        StepHeaderItem(number = 3, label = "Preview & Edit", color = Color(0xFF4CAF50))
                        Spacer(Modifier.weight(1f))
                        Surface(
                            color = Color(0xFFE8F5E9),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = "${plans.size} Plan${if (plans.size > 1) "s" else ""}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { viewModel.saveEditedHistoryPlan() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Save Changes", fontWeight = FontWeight.Bold)
                        }

                        var showMenu by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(
                                onClick = { showMenu = true },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray)
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = null, tint = Color.Gray)
                                Spacer(Modifier.width(4.dp))
                                Text("Options", color = Color.DarkGray)
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Preview PDF") },
                                    leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color(0xFFFF9800)) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.previewPlansAsPdf(plans)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Download") },
                                    leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color(0xFF673AB7)) },
                                    onClick = {
                                        showMenu = false
                                        showFormatDialog = FormatAction.Download
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Share") },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color(0xFF2196F3)) },
                                    onClick = {
                                        showMenu = false
                                        showFormatDialog = FormatAction.Export
                                    }
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                DropdownMenuItem(
                                    text = { Text("Delete Plan", color = Color.Red) },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.Red) },
                                    onClick = {
                                        showMenu = false
                                        showDeleteDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Shared Info Card
            var commonInfoExpanded by remember { mutableStateOf(false) }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE9ECEF))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { commonInfoExpanded = !commonInfoExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Common Information", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1A73E8))
                        Icon(
                            imageVector = if (commonInfoExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (commonInfoExpanded) "Collapse" else "Expand",
                            tint = Color(0xFF1A73E8)
                        )
                    }
                    
                    if (commonInfoExpanded) {
                        val firstPlanJson = plans.firstOrNull()?.content
                        val firstHeader = remember(firstPlanJson) {
                            try {
                                val map = Gson().fromJson(firstPlanJson, Map::class.java)
                                map["header"] as? Map<*, *>
                            } catch (e: Exception) { null }
                        }

                        OutlinedTextField(
                            value = (firstHeader?.get("school") as? String).orEmpty(),
                            onValueChange = { viewModel.updateHeaderField(0, "school", it, isHistory = true) },
                            label = { Text("School") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = (firstHeader?.get("facilitatorName") as? String).orEmpty(),
                                onValueChange = { viewModel.updateHeaderField(0, "facilitatorName", it, isHistory = true) },
                                label = { Text("Facilitator") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = (firstHeader?.get("term") as? String).orEmpty(),
                                onValueChange = { viewModel.updateHeaderField(0, "term", it, isHistory = true) },
                                label = { Text("Term") },
                                modifier = Modifier.weight(0.4f),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = (firstHeader?.get("classSize") as? String).orEmpty(),
                                onValueChange = { viewModel.updateHeaderField(0, "classSize", it, isHistory = true) },
                                label = { Text("Class Size") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = (firstHeader?.get("duration") as? String).orEmpty(),
                                onValueChange = { viewModel.updateHeaderField(0, "duration", it, isHistory = true) },
                                label = { Text("Duration") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

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
                    itemsIndexed(plans) { planIndex, currentPlan ->
                        val currentPlanMap = remember(currentPlan.content) {
                            try {
                                Gson().fromJson(currentPlan.content, Map::class.java)
                            } catch (e: Exception) { null }
                        }
                        val currentHeader = currentPlanMap?.get("header") as? Map<*, *>
                        val currentSubjectName = currentHeader?.get("subject") as? String ?: ""
                        val currentIndicatorLabel = currentHeader?.get("indicator") as? String ?: currentPlan.indicatorCode ?: ""
                        val currentIndicatorCode = currentIndicatorLabel.split(" - ").firstOrNull()?.trim().orEmpty()
                        val weekNumber = currentHeader?.get("week") as? String ?: ""

                        var expanded by remember { mutableStateOf(false) }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFFE3F2FD),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBDEFB))
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { expanded = !expanded }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (expanded) "Collapse" else "Expand",
                                        tint = Color(0xFF2196F3),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "$currentIndicatorCode · $currentSubjectName",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1976D2),
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        if (weekNumber.isNotBlank()) {
                                            Text(
                                                text = "Week $weekNumber",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFF1976D2).copy(alpha = 0.8f)
                                            )
                                        }
                                    }

                                    // Info
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                selectedIndicatorForDetail = viewModel.resolveIndicatorFromSavedPlan(currentPlan)
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Info,
                                            contentDescription = "Info",
                                            modifier = Modifier.size(16.dp),
                                            tint = Color(0xFF2196F3)
                                        )
                                    }

                                    // Reorder up
                                    IconButton(
                                        onClick = { viewModel.moveSelectedHistoryPlan(planIndex, -1) },
                                        enabled = planIndex > 0,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.KeyboardArrowUp,
                                            contentDescription = "Move Up",
                                            modifier = Modifier.size(18.dp),
                                            tint = if (planIndex > 0) Color(0xFF1976D2) else Color.LightGray
                                        )
                                    }
                                    
                                    // Reorder down
                                    val totalPlans = plans.size
                                    IconButton(
                                        onClick = { viewModel.moveSelectedHistoryPlan(planIndex, 1) },
                                        enabled = planIndex < totalPlans - 1,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Move Down",
                                            modifier = Modifier.size(18.dp),
                                            tint = if (planIndex < totalPlans - 1) Color(0xFF1976D2) else Color.LightGray
                                        )
                                    }

                                    var showMoreMenu by remember { mutableStateOf(false) }
                                    Box {
                                        IconButton(
                                            onClick = { showMoreMenu = true },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.MoreVert,
                                                contentDescription = "More options",
                                                modifier = Modifier.size(18.dp),
                                                tint = Color(0xFF1976D2)
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = showMoreMenu,
                                            onDismissRequest = { showMoreMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Regenerate") },
                                                onClick = {
                                                    showMoreMenu = false
                                                    viewModel.regeneratePlan(planIndex, apiKey, model, isHistory = true)
                                                },
                                                leadingIcon = {
                                                    if (uiState.regeneratingPhaseIndex?.first == planIndex && uiState.regeneratingPhaseIndex?.second == -1) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(18.dp),
                                                            strokeWidth = 2.dp,
                                                            color = Color(0xFF2196F3)
                                                        )
                                                    } else {
                                                        Icon(
                                                            Icons.Default.Refresh,
                                                            contentDescription = null,
                                                            tint = Color(0xFFFF9800),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Duplicate") },
                                                onClick = {
                                                    showMoreMenu = false
                                                    viewModel.duplicatePlan(currentPlan)
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Default.ContentCopy,
                                                        contentDescription = null,
                                                        tint = Color(0xFF4CAF50),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Delete", color = Color.Red) },
                                                onClick = {
                                                    showMoreMenu = false
                                                    viewModel.deletePlan(currentPlan)
                                                    if (plans.size <= 1) onBack()
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = null,
                                                        tint = Color.Red,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        androidx.compose.animation.AnimatedVisibility(visible = expanded) {
                            Column {
                                Spacer(Modifier.height(8.dp))
                                LessonPlanPreview(
                                    generatedPlanJson = currentPlan.content,
                                    editingPhaseIndex = if (uiState.editingPhaseIndex?.first == planIndex) uiState.editingPhaseIndex?.second else null,
                                    regeneratingPhaseIndex = if (uiState.regeneratingPhaseIndex?.first == planIndex) uiState.regeneratingPhaseIndex?.second else null,
                                    onEditPhase = { phaseIndex ->
                                        viewModel.updateEditingPhase(if (phaseIndex == null) null else planIndex to phaseIndex)
                                    },
                                    onUpdatePhaseField = { phaseIndex, field, value ->
                                        viewModel.updatePhaseField(planIndex, phaseIndex, field, value, isHistory = true)
                                    },
                                    onRegeneratePhase = { phaseIndex, name ->
                                        viewModel.regeneratePhase(planIndex, phaseIndex, name, apiKey, model, isHistory = true)
                                    },
                                    onUpdateHeaderField = { field, value ->
                                        viewModel.updateHeaderField(planIndex, field, value, isHistory = true)
                                    }
                                )
                            }
                        }
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
