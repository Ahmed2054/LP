package com.lp.lessonplanner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lp.lessonplanner.data.local.SubjectEntity
import com.lp.lessonplanner.viewmodel.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CurriculumScreen(viewModel: LessonPlanViewModel) {
    val subjectsWithCount by viewModel.subjectsWithCount.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    
    var viewingSubject by remember { mutableStateOf<SubjectEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        AddIndicatorDialog(
            viewModel = viewModel,
            subjects = subjectsWithCount.map { it.subject },
            onDismiss = { showAddDialog = false }
        )
    }

    if (viewingSubject != null) {
        ViewIndicatorsDialog(
            subject = viewingSubject!!,
            viewModel = viewModel,
            onDismiss = { viewingSubject = null }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Curriculum", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Manage subjects and indicators", fontSize = 14.sp, color = Color.Gray)
            }
            
            IconButton(
                onClick = { viewModel.refreshCurriculum() }
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh Curriculum", tint = MaterialTheme.colorScheme.primary)
            }
        }

        if (subjectsWithCount.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                    Spacer(Modifier.height(16.dp))
                    Text("No subjects found", color = Color.Gray)
                    Text("Add a subject to get started", fontSize = 12.sp, color = Color.LightGray)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("Subjects (${subjectsWithCount.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.DarkGray)
                }
                items(subjectsWithCount) { subjectWithCount ->
                    SubjectListItem(
                        subjectWithCount = subjectWithCount,
                        onClick = { viewingSubject = subjectWithCount.subject }
                    )
                }
            }
        }
    }
}

@Composable
fun SubjectListItem(subjectWithCount: com.lp.lessonplanner.data.local.SubjectWithCount, onClick: () -> Unit) {
    val subject = subjectWithCount.subject
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFF4CAF50).copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color(0xFF4CAF50))
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(subject.actualName ?: subject.name, fontWeight = FontWeight.Bold)
                
                if (subjectWithCount.gradesCounts.isNotEmpty()) {
                    val breakdown = subjectWithCount.gradesCounts.entries
                        .sortedBy { it.key }
                        .joinToString(" | ") { "${it.key}: ${it.value}" }
                    Text(breakdown, fontSize = 11.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Medium)
                }

                Text("${subjectWithCount.indicatorCount} Total Indicators", fontSize = 12.sp, color = Color.Gray)
                if (!subject.tribe.isNullOrEmpty()) {
                    Text(subject.tribe.orEmpty(), fontSize = 12.sp, color = Color.Gray)
                }
            }
            
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.LightGray)
        }
    }
}

@Composable
fun ViewIndicatorsDialog(
    subject: SubjectEntity,
    viewModel: LessonPlanViewModel,
    onDismiss: () -> Unit
) {
    var indicators by remember { mutableStateOf<List<com.lp.lessonplanner.data.local.CurriculumEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(subject.id) {
        isLoading = true
        indicators = viewModel.getIndicatorsForSubject(subject.id)
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(subject.actualName ?: subject.name, fontWeight = FontWeight.Bold) },
        text = {
            Box(modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp)) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (indicators.isEmpty()) {
                    Text("No indicators found.", modifier = Modifier.padding(16.dp))
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(indicators) { indicator ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(indicator.indicatorCode ?: "", fontWeight = FontWeight.Bold, color = Color(0xFF2196F3), fontSize = 14.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text(indicator.indicatorDescription ?: "", fontSize = 13.sp, lineHeight = 18.sp)
                                    if (!indicator.strand.isNullOrEmpty()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text("Strand: ${indicator.strand}", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddIndicatorDialog(
    viewModel: LessonPlanViewModel,
    subjects: List<SubjectEntity>,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedSubject by remember { mutableStateOf<SubjectEntity?>(null) }
    var newSubjectName by remember { mutableStateOf("") }
    var isAddingNewSubject by remember { mutableStateOf(false) }
    var selectedGrade by remember { mutableStateOf("") }
    var newGradeName by remember { mutableStateOf("") }
    var isAddingNewGrade by remember { mutableStateOf(false) }
    var strand by remember { mutableStateOf("") }
    var subStrand by remember { mutableStateOf("") }
    var contentStandard by remember { mutableStateOf("") }
    var indicatorCode by remember { mutableStateOf("") }
    var indicatorDescription by remember { mutableStateOf("") }
    var exemplars by remember { mutableStateOf("") }
    var coreCompetencies by remember { mutableStateOf("") }

    var indicatorExists by remember { mutableStateOf(false) }
    var subjectExpanded by remember { mutableStateOf(false) }
    var gradeExpanded by remember { mutableStateOf(false) }
    
    val grades by viewModel.grades.collectAsState()
    val allGrades = listOf("B1", "B2", "B3", "B4", "B5", "B6", "B7", "B8", "B9") // Fallback if grades is empty

    val currentGrade = if (isAddingNewGrade) newGradeName else selectedGrade

    LaunchedEffect(selectedSubject, currentGrade, indicatorCode) {
        if (selectedSubject != null && currentGrade.isNotEmpty() && indicatorCode.isNotEmpty()) {
            delay(300) // Debounce
            indicatorExists = viewModel.checkIndicatorExists(selectedSubject!!.id, currentGrade, indicatorCode)
        } else {
            indicatorExists = false
        }
    }

    val isFormValid = (selectedSubject != null || (isAddingNewSubject && newSubjectName.isNotBlank())) &&
            (selectedGrade.isNotEmpty() || (isAddingNewGrade && newGradeName.isNotBlank())) &&
            strand.isNotEmpty() &&
            subStrand.isNotEmpty() &&
            contentStandard.isNotEmpty() &&
            indicatorCode.isNotEmpty() &&
            indicatorDescription.isNotEmpty() &&
            exemplars.isNotEmpty() &&
            coreCompetencies.isNotEmpty() &&
            !indicatorExists

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Indicator") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Subject Dropdown or Manual Entry
                if (isAddingNewSubject) {
                    OutlinedTextField(
                        value = newSubjectName,
                        onValueChange = { newSubjectName = it },
                        label = { Text("New Subject Name") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { isAddingNewSubject = false }) {
                                Icon(Icons.Default.Book, contentDescription = "Select existing")
                            }
                        }
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = subjectExpanded,
                        onExpandedChange = { subjectExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedSubject?.name ?: "Select Subject",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Subject") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subjectExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = subjectExpanded,
                            onDismissRequest = { subjectExpanded = false }
                        ) {
                            subjects.forEach { subject ->
                                DropdownMenuItem(
                                    text = { Text(subject.actualName ?: subject.name) },
                                    onClick = {
                                        selectedSubject = subject
                                        subjectExpanded = false
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("+ Add New Subject", color = MaterialTheme.colorScheme.primary) },
                                onClick = {
                                    isAddingNewSubject = true
                                    selectedSubject = null
                                    subjectExpanded = false
                                }
                            )
                        }
                    }
                }

                // Grade Dropdown or Manual Entry
                if (isAddingNewGrade) {
                    OutlinedTextField(
                        value = newGradeName,
                        onValueChange = { newGradeName = it },
                        label = { Text("New Grade/Class") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { isAddingNewGrade = false }) {
                                Icon(Icons.Default.School, contentDescription = "Select existing")
                            }
                        }
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = gradeExpanded,
                        onExpandedChange = { gradeExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedGrade.ifEmpty { "Select Grade/Class" },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Grade/Class") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = gradeExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = gradeExpanded,
                            onDismissRequest = { gradeExpanded = false }
                        ) {
                            (if (grades.isEmpty()) allGrades else grades).forEach { grade ->
                                DropdownMenuItem(
                                    text = { Text(grade) },
                                    onClick = {
                                        selectedGrade = grade
                                        gradeExpanded = false
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("+ Add New Grade/Class", color = MaterialTheme.colorScheme.primary) },
                                onClick = {
                                    isAddingNewGrade = true
                                    selectedGrade = ""
                                    gradeExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(value = strand, onValueChange = { strand = it }, label = { Text("Strand") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = subStrand, onValueChange = { subStrand = it }, label = { Text("Sub-Strand") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = contentStandard, onValueChange = { contentStandard = it }, label = { Text("Content Standard") }, modifier = Modifier.fillMaxWidth())
                
                Column {
                    OutlinedTextField(
                        value = indicatorCode,
                        onValueChange = { indicatorCode = it },
                        label = { Text("Indicator Code") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = indicatorExists
                    )
                    if (indicatorExists) {
                        Text(
                            "This indicator code already exists for this subject and grade.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                    }
                }

                OutlinedTextField(value = indicatorDescription, onValueChange = { indicatorDescription = it }, label = { Text("Indicator Description") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(value = exemplars, onValueChange = { exemplars = it }, label = { Text("Exemplars") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(value = coreCompetencies, onValueChange = { coreCompetencies = it }, label = { Text("Core Competencies") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        val subjectId = if (isAddingNewSubject) {
                            viewModel.getOrCreateSubject(newSubjectName)
                        } else {
                            selectedSubject?.id
                        }

                        if (subjectId != null) {
                            viewModel.addIndicator(
                                subjectId = subjectId,
                                grade = currentGrade,
                                strand = strand,
                                subStrand = subStrand,
                                contentStandard = contentStandard,
                                indicatorCode = indicatorCode,
                                indicatorDescription = indicatorDescription,
                                exemplars = exemplars,
                                coreCompetencies = coreCompetencies
                            )
                        }
                        onDismiss()
                    }
                },
                enabled = isFormValid
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
