package com.lp.lessonplanner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lp.lessonplanner.viewmodel.LessonPlanViewModel
import com.lp.lessonplanner.data.local.SavedPlanEntity
import com.lp.lessonplanner.ui.utils.FormatAction
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(viewModel: LessonPlanViewModel, onPlanClick: (SavedPlanEntity) -> Unit) {
    val history: List<SavedPlanEntity> by viewModel.history.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var planToDelete by remember { mutableStateOf<SavedPlanEntity?>(null) }
    
    // Selection state
    var selectedPlanIds by remember { mutableStateOf(setOf<Int>()) }
    val isSelectionMode = selectedPlanIds.isNotEmpty()

    // Tab state
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Plans", "Notes", "Questions")

    val filteredHistory = history.filter {
        val subjectName = try {
            org.json.JSONObject(it.content).getJSONObject("header").optString("subject", "")
        } catch (e: Exception) { "" }

        val matchesSearch = it.indicatorCode?.contains(searchQuery, ignoreCase = true) == true ||
                subjectName.contains(searchQuery, ignoreCase = true) ||
                it.date.contains(searchQuery) ||
                it.planType.contains(searchQuery, ignoreCase = true)
        
        val matchesTab = when (selectedTabIndex) {
            0 -> it.planType == "Lesson Plan"
            1 -> it.planType == "Full Note"
            2 -> it.planType == "Questions"
            else -> true
        }
        
        matchesSearch && matchesTab
    }

    if (planToDelete != null) {
        AlertDialog(
            onDismissRequest = { planToDelete = null },
            title = { Text("Delete Plan") },
            text = { Text("Are you sure you want to delete this lesson plan? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        planToDelete?.let { viewModel.deletePlan(it) }
                        planToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { planToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog state for download/export format
    var showFormatDialog by remember { mutableStateOf<FormatAction?>(null) }
    
    if (showFormatDialog != null) {
        val action = showFormatDialog!!
        AlertDialog(
            onDismissRequest = { showFormatDialog = null },
            title = {
                Text(
                    when (action) {
                        is FormatAction.Download -> "Download Format"
                        is FormatAction.Export -> "Share Format"
                        else -> "Choose Format"
                    }
                )
            },
            text = { Text("Choose a format for the selected ${selectedPlanIds.size} item(s):") },
            confirmButton = {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(
                        onClick = {
                            val selectedPlans = selectedPlanIds.mapNotNull { id -> history.find { it.id == id } }
                            when (action) {
                                is FormatAction.Download -> viewModel.downloadPlansAsPdf(selectedPlans)
                                is FormatAction.Export -> viewModel.exportPlansAsPdf(selectedPlans)
                                else -> Unit
                            }
                            showFormatDialog = null
                            selectedPlanIds = emptySet()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                    ) {
                        Text("PDF")
                    }
                    Button(
                        onClick = {
                            val selectedPlans = selectedPlanIds.mapNotNull { id -> history.find { it.id == id } }
                            when (action) {
                                is FormatAction.Download -> viewModel.downloadPlansAsDocx(selectedPlans)
                                is FormatAction.Export -> viewModel.exportPlansAsDocx(selectedPlans)
                                else -> Unit
                            }
                            showFormatDialog = null
                            selectedPlanIds = emptySet()
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
    ) {
        HistoryHeader(
            searchQuery, 
            onSearchChange = { searchQuery = it },
            selectedCount = selectedPlanIds.size,
            onClearSelection = { selectedPlanIds = emptySet() },
            onPreviewSelected = {
                val selectedPlans = selectedPlanIds.mapNotNull { id -> history.find { it.id == id } }
                if (selectedPlans.isNotEmpty()) {
                    viewModel.previewPlansAsPdf(selectedPlans)
                    selectedPlanIds = emptySet()
                }
            },
            onDownloadSelected = { showFormatDialog = FormatAction.Download },
            onExportSelected = { showFormatDialog = FormatAction.Export }
        )

        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = Color.White,
            contentColor = Color(0xFF2196F3),
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    color = Color(0xFF2196F3)
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { 
                        Text(
                            title, 
                            fontSize = 14.sp, 
                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                        ) 
                    }
                )
            }
        }

        if (history.isEmpty()) {
            EmptyHistory()
        } else if (filteredHistory.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No plans match your search", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredHistory, key = { it.id }) { plan ->
                    val isSelected = selectedPlanIds.contains(plan.id)
                    HistoryItem(
                        plan = plan,
                        onClick = {
                            if (isSelectionMode) {
                                selectedPlanIds = if (isSelected) {
                                    selectedPlanIds - plan.id
                                } else {
                                    selectedPlanIds + plan.id
                                }
                            } else {
                                onPlanClick(plan)
                            }
                        },
                        onLongClick = {
                            selectedPlanIds = selectedPlanIds + plan.id
                        },
                        onDeleteClick = { planToDelete = plan },
                        isSelected = isSelected,
                        isSelectionMode = isSelectionMode
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryHeader(
    searchQuery: String, 
    onSearchChange: (String) -> Unit,
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onPreviewSelected: () -> Unit,
    onDownloadSelected: () -> Unit,
    onExportSelected: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(16.dp)
    ) {
        if (selectedCount > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                }
                Text(
                    "$selectedCount selected",
                    modifier = Modifier.weight(1f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onPreviewSelected) {
                    Icon(Icons.Default.Visibility, contentDescription = "Preview Selected", tint = Color(0xFFFF9800))
                }
                IconButton(onClick = onDownloadSelected) {
                    Icon(Icons.Default.Download, contentDescription = "Download Selected", tint = Color(0xFF673AB7))
                }
                IconButton(onClick = onExportSelected) {
                    Icon(Icons.Default.Share, contentDescription = "Export Selected", tint = Color(0xFF2196F3))
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("History", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Previously generated plans", fontSize = 14.sp, color = Color.Gray)
                }
            }
        }
        
        Spacer(Modifier.height(12.dp))
        
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth().heightIn(max = 52.dp),
            placeholder = { Text("Search by indicator, date, or type...", fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color(0xFFE0E0E0),
                focusedBorderColor = Color(0xFF2196F3)
            )
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HistoryItem(
    plan: SavedPlanEntity, 
    onClick: () -> Unit, 
    onLongClick: () -> Unit,
    onDeleteClick: () -> Unit,
    isSelected: Boolean,
    isSelectionMode: Boolean
) {
    val date = plan.date

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFE3F2FD) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 0.dp else 1.dp),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF2196F3)) else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.padding(end = 8.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF2196F3).copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        when (plan.planType) {
                            "Full Note" -> Icons.Default.Description
                            "Questions" -> Icons.Default.HelpCenter
                            else -> Icons.Default.Assignment
                        },
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color(0xFF2196F3)
                    )
                }
                Spacer(Modifier.width(16.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                val subjectName = remember(plan.content) {
                    try {
                        val jsonObject = org.json.JSONObject(plan.content)
                        val header = jsonObject.getJSONObject("header")
                        header.optString("subject", "")
                    } catch (e: Exception) {
                        ""
                    }
                }

                Text(
                    text = subjectName.ifEmpty { "Lesson Plan" },
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = plan.indicatorCode ?: "",
                    fontSize = 13.sp,
                    color = Color.DarkGray,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    "Week ${plan.week}${if (plan.lessonNumber != null) " • Lesson ${plan.lessonNumber}" else ""} • $date",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            if (!isSelectionMode) {
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = Color.Gray.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyHistory() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = Color.LightGray
            )
            Spacer(Modifier.height(16.dp))
            Text("No saved plans yet", color = Color.Gray)
        }
    }
}

