package com.lp.lessonplanner.ui.screens

import android.app.DatePickerDialog
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.lp.lessonplanner.ui.utils.FormatAction
import com.lp.lessonplanner.ui.utils.formatToDDMMYYYY
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lp.lessonplanner.viewmodel.*
import com.lp.lessonplanner.data.local.SubjectEntity
import com.lp.lessonplanner.data.local.CurriculumEntity
import com.lp.lessonplanner.ui.components.LessonPlanPreview
import com.google.gson.Gson
import java.util.*

@Composable
fun CreateScreen(viewModel: LessonPlanViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val subjects by viewModel.subjects.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            // Show toast or snackbar
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearErrorMessage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        CreatorHeader(
            step = uiState.step,
            selectedGrade = uiState.selectedGrade,
            selectedSubjectName = subjects.find { it.id == uiState.selectedSubjectId }?.actualName,
            onBack = { if (uiState.step > 1) viewModel.updateStep(uiState.step - 1) },
            onForward = { if (uiState.step < 3) viewModel.updateStep(uiState.step + 1) },
            isForwardEnabled = when (uiState.step) {
                1 -> uiState.selectedSubjectId != null
                2 -> uiState.selectedIndicatorIds.isNotEmpty()
                else -> false
            }
        )

        when (uiState.step) {
            1 -> SetupStep(viewModel)
            2 -> IndicatorStep(viewModel)
            3 -> PreviewStep(viewModel)
        }
    }
}

@Composable
fun CreatorHeader(
    step: Int,
    selectedGrade: String,
    selectedSubjectName: String?,
    onBack: () -> Unit,
    onForward: () -> Unit,
    isForwardEnabled: Boolean
) {
    val stepColors = listOf(Color(0xFF2196F3), Color(0xFFFF9800), Color(0xFF4CAF50))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Creator", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            val subtitle = if (selectedGrade.isNotEmpty() || selectedSubjectName != null) {
                listOfNotNull(selectedGrade.ifEmpty { null }, selectedSubjectName).joinToString(" · ")
            } else {
                "Craft your lesson plan"
            }
            Text(subtitle, fontSize = 14.sp, color = Color.Gray)
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (step > 1) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp).background(Color(0xFFF5F5F5), CircleShape)) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 1..3) {
                    Box(
                        modifier = Modifier
                            .size(if (i == step) 24.dp else 8.dp)
                            .clip(CircleShape)
                            .background(if (i == step) stepColors[i - 1] else Color(0xFFE0E0E0)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (i == step) {
                            Text(i.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (step < 3) {
                IconButton(
                    onClick = onForward,
                    enabled = isForwardEnabled,
                    modifier = Modifier.size(32.dp).background(if (isForwardEnabled) Color(0xFFF5F5F5) else Color.Transparent, CircleShape)
                ) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (isForwardEnabled) Color.Black else Color.LightGray
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupStep(viewModel: LessonPlanViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val subjects by viewModel.subjects.collectAsState()
    val grades: List<String> by viewModel.grades.collectAsState()
    val context = LocalContext.current
    val selectedSubject = subjects.find { it.id == uiState.selectedSubjectId }

    var gradeExpanded by remember { mutableStateOf(false) }
    var subjectExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column {
                    // Modern Header Image Area
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // Decorative pattern or icon
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier
                                .size(120.dp)
                                .alpha(0.15f)
                                .align(Alignment.CenterEnd)
                                .padding(end = 16.dp),
                            tint = Color.White
                        )
                        
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.2f),
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    Icons.Default.School,
                                    contentDescription = null,
                                    modifier = Modifier.padding(12.dp),
                                    tint = Color.White
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Welcome to Lesson Planner",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "Effortlessly generate comprehensive lesson plans tailored to the Ghanaian Basic Education curriculum obtained from NaCCA.",
                            style = MaterialTheme.typography.bodyLarge,
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        Spacer(Modifier.height(16.dp))

                        Surface(
                            color = Color(0xFFFFF3E0),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFE0B2))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFE65100),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "AI-generated content. Please verify all details before use.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFE65100),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(
                            "Start by choosing your class and subject below to begin your journey.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            SetupSectionCard(
                icon = Icons.Default.School,
                title = "Class & Subject",
                subtitle = "Choose the class first, then pick the subject that matches it.",
                accentColor = Color(0xFF7B4DCC)
            ) {
                ExposedDropdownMenuBox(
                    expanded = gradeExpanded,
                    onExpandedChange = { gradeExpanded = !gradeExpanded }
                ) {
                    OutlinedTextField(
                        value = uiState.selectedGrade,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Class / Grade") },
                        placeholder = { Text("Choose a class") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = gradeExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                            focusedBorderColor = Color(0xFFD4C5F5),
                            unfocusedBorderColor = Color(0xFFE3DAF7)
                        ),
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                    ExposedDropdownMenu(
                        expanded = gradeExpanded,
                        onDismissRequest = { gradeExpanded = false }
                    ) {
                        grades.forEach { grade ->
                            DropdownMenuItem(
                                text = { Text(grade) },
                                onClick = {
                                    viewModel.updateGrade(grade)
                                    gradeExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                ExposedDropdownMenuBox(
                    expanded = subjectExpanded,
                    onExpandedChange = { subjectExpanded = !subjectExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedSubject?.let { it.actualName ?: it.name } ?: "",
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Subject") },
                        placeholder = { Text(if (uiState.selectedGrade.isBlank()) "Choose class first" else "Select subject") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subjectExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                            focusedBorderColor = Color(0xFFD4C5F5),
                            unfocusedBorderColor = Color(0xFFE3DAF7)
                        ),
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                    ExposedDropdownMenu(
                        expanded = subjectExpanded,
                        onDismissRequest = { subjectExpanded = false }
                    ) {
                        subjects.forEach { subject ->
                            DropdownMenuItem(
                                text = { Text(subject.actualName ?: subject.name) },
                                onClick = {
                                    viewModel.updateSubject(subject.id)
                                    subjectExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        item {
            if (uiState.selectedSubjectId != null) {
                Button(
                    onClick = { viewModel.updateStep(2) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Next: Select Indicator", fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.padding(start = 8.dp))
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFF5F7FA),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF607D8B), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Pick a class and subject to unlock Step 2.",
                            color = Color(0xFF607D8B),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupSectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.14f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                    Text(subtitle, fontSize = 12.sp, color = Color(0xFF6B7280), lineHeight = 17.sp)
                }
            }

            Spacer(Modifier.height(16.dp))
            content()
        })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndicatorStep(viewModel: LessonPlanViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val indicators by viewModel.indicators.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val model by viewModel.model.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedIndicatorForDetail by remember { mutableStateOf<CurriculumEntity?>(null) }
    var showGenerationDialog by remember { mutableStateOf(false) }

    val filteredIndicators = indicators.filter {
        it.indicatorCode?.contains(searchQuery, ignoreCase = true) == true ||
        it.indicatorDescription?.contains(searchQuery, ignoreCase = true) == true ||
        it.strand?.contains(searchQuery, ignoreCase = true) == true
    }

    if (showGenerationDialog) {
        AlertDialog(
            onDismissRequest = { showGenerationDialog = false },
            title = { Text("What to generate?", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val types = listOf("Lesson Plan", "Full Note", "Questions")
                    types.forEach { type ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (uiState.generationType == type) Color(0xFF2196F3).copy(alpha = 0.1f) else Color.Transparent)
                                .clickable { viewModel.updateGenerationType(type) }
                                .padding(8.dp)
                        ) {
                            RadioButton(
                                selected = uiState.generationType == type,
                                onClick = { viewModel.updateGenerationType(type) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(type, fontSize = 15.sp)
                        }
                    }

                    if (uiState.generationType == "Questions") {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text("Question Details", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        
                        Text("Number of questions", fontSize = 12.sp, color = Color.Gray)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(5, 10, 15, 20).forEach { count ->
                                FilterChip(
                                    selected = uiState.questionCount == count,
                                    onClick = { viewModel.updateQuestionCount(count) },
                                    label = { Text(count.toString()) }
                                )
                            }
                        }

                        Text("Type of questions", fontSize = 12.sp, color = Color.Gray)
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            listOf("Multiple Choice", "True/False", "Fill in the blank", "Essay").forEach { qType ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.updateQuestionType(qType) }
                                        .padding(vertical = 4.dp)
                                ) {
                                    RadioButton(
                                        selected = uiState.questionType == qType,
                                        onClick = { viewModel.updateQuestionType(qType) }
                                    )
                                    Text(qType, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showGenerationDialog = false
                        viewModel.generateLessonPlans(apiKey, model)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                ) {
                    Text("Generate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGenerationDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                StepHeaderItem(number = 2, label = "Select Indicator", color = Color(0xFFFF9800))
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(0.6f),
                        placeholder = { Text("Search...", fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                    )

                    Button(
                        onClick = {
                            if (uiState.isLoading) {
                                viewModel.cancelLessonPlanGeneration()
                            } else {
                                showGenerationDialog = true
                            }
                        },
                        enabled = uiState.isLoading || uiState.selectedIndicatorIds.isNotEmpty(),
                        modifier = Modifier.weight(0.4f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.isLoading) Color(0xFFD32F2F) else Color(0xFF2196F3)
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 11.dp)
                    ) {
                        if (uiState.isLoading) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        } else {
                            Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(5.dp))
                        Text(
                            if (uiState.isLoading) "Stop"
                            else if (uiState.selectedIndicatorIds.isNotEmpty()) "Generate (${uiState.selectedIndicatorIds.size})"
                            else "Generate", 
                            fontSize = 13.sp, 
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${indicators.size} indicators · ${uiState.selectedIndicatorIds.size} selected",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (uiState.selectedIndicatorIds.isNotEmpty()) Color(0xFF2196F3) else Color.Gray
            )

            if (uiState.selectedIndicatorIds.isNotEmpty()) {
                Text(
                    text = "Clear All",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Red,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { viewModel.clearAllIndicators() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        if (uiState.isLoading) {
            Spacer(Modifier.height(4.dp))
            GeneratingStatusCard(
                generationType = uiState.generationType,
                selectedCount = uiState.selectedIndicatorIds.size
            )
            Spacer(Modifier.height(8.dp))
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            items(filteredIndicators) { indicator ->
                val isSelected = uiState.selectedIndicatorIds.contains(indicator.id)
                val metadata = uiState.indicatorMetadata[indicator.id] ?: com.lp.lessonplanner.viewmodel.IndicatorMetadata()
                IndicatorItem(
                    indicator = indicator,
                    isSelected = isSelected,
                    metadata = metadata,
                    onMetadataChange = { field, value -> viewModel.updateIndicatorMetadata(indicator.id, field, value) },
                    onClick = { viewModel.updateIndicator(indicator.id) },
                    onDetailClick = { selectedIndicatorForDetail = indicator }
                )
            }
        }
    }
}

@Composable
private fun GeneratingStatusCard(
    generationType: String,
    selectedCount: Int
) {
    val accentColor = Color(0xFFFF9800)
    val pulseTransition = rememberInfiniteTransition(label = "generationPulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "generationPulseScale"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBF5)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFE0B2)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .graphicsLayer(
                            scaleX = pulseScale,
                            scaleY = pulseScale
                        )
                        .background(accentColor.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(17.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI is generating ${generationType.lowercase(Locale.US)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F2937)
                    )
                    Text(
                        text = "Preparing $selectedCount ${if (selectedCount == 1) "lesson plan" else "lesson plans"} now.",
                        fontSize = 11.sp,
                        color = Color(0xFF6B7280),
                        lineHeight = 15.sp,
                        maxLines = 1
                    )
                }

                Surface(
                    color = Color(0xFFFFF3E0),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = "$selectedCount selected",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF8A5A00)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GeneratingDots(accentColor = accentColor)
                LinearProgressIndicator(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = accentColor,
                    trackColor = Color(0xFFFFEED8)
                )
            }
        }
    }
}

@Composable
private fun GeneratingDots(accentColor: Color) {
    val dotTransition = rememberInfiniteTransition(label = "generationDots")

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { index ->
            val dotAlpha by dotTransition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 900
                        0.25f at 0
                        0.25f at (index * 140)
                        1f at (index * 140 + 180)
                        0.4f at (index * 140 + 360)
                        0.25f at 900
                    },
                    repeatMode = RepeatMode.Restart
                ),
                label = "generationDotAlpha$index"
            )
            val dotScale by dotTransition.animateFloat(
                initialValue = 0.72f,
                targetValue = 1.16f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 900
                        0.72f at 0
                        0.72f at (index * 140)
                        1.16f at (index * 140 + 180)
                        0.82f at (index * 140 + 360)
                        0.72f at 900
                    },
                    repeatMode = RepeatMode.Restart
                ),
                label = "generationDotScale$index"
            )

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer(
                        alpha = dotAlpha,
                        scaleX = dotScale,
                        scaleY = dotScale
                    )
                    .clip(CircleShape)
                    .background(accentColor)
            )
        }
    }
}

@Composable
fun DetailRow(label: String, value: String?) {
    if (!value.isNullOrEmpty()) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2196F3))
            Text(value, fontSize = 14.sp, color = Color.DarkGray)
        }
    }
}

@Composable
fun IndicatorItem(
    indicator: CurriculumEntity,
    isSelected: Boolean,
    metadata: com.lp.lessonplanner.viewmodel.IndicatorMetadata,
    onMetadataChange: (String, String) -> Unit,
    onClick: () -> Unit,
    onDetailClick: () -> Unit
) {
    val primaryColor = Color(0xFF2196F3)
    val context = LocalContext.current
    val calendar = Calendar.getInstance()

    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            onMetadataChange("date", "$year-${month + 1}-$dayOfMonth")
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    val weekEndingDatePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            onMetadataChange("weekEnding", "$year-${month + 1}-$dayOfMonth")
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) primaryColor.copy(alpha = 0.05f) else Color(0xFFF8F9FA))
            .border(1.5.dp, if (isSelected) primaryColor else Color(0xFFE8EAED), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    indicator.indicatorCode ?: "",
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isSelected) primaryColor else Color.Black,
                    fontSize = 15.sp
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onDetailClick,
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFEEEEEE), RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color(0xFFFF9800))
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .border(2.dp, if (isSelected) primaryColor else Color.LightGray, CircleShape)
                            .background(if (isSelected) primaryColor else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color.White)
                        }
                    }
                }
            }

            Text(
                indicator.indicatorDescription ?: "",
                fontSize = 13.sp,
                color = Color.DarkGray,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 6.dp),
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )

            if (!indicator.strand.isNullOrEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Icon(Icons.Default.AccountTree, contentDescription = null, modifier = Modifier.size(11.dp), tint = Color.LightGray)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${indicator.strand}${if (!indicator.subStrand.isNullOrEmpty()) " › ${indicator.subStrand}" else ""}",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(visible = isSelected) {
                Column(modifier = Modifier.padding(top = 16.dp).clickable(enabled = false) { }) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp), color = primaryColor.copy(alpha = 0.2f))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = metadata.week,
                            onValueChange = { onMetadataChange("week", it) },
                            modifier = Modifier.weight(0.7f).height(60.dp),
                            label = { Text("Week", fontSize = 10.sp, maxLines = 1) },
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = Color.Black),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black,
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = primaryColor,
                                unfocusedBorderColor = Color(0xFFE8EAED),
                                focusedLabelColor = primaryColor,
                                unfocusedLabelColor = Color.Gray
                            )
                        )
                        OutlinedTextField(
                            value = formatToDDMMYYYY(metadata.weekEnding),
                            onValueChange = { },
                            modifier = Modifier.weight(1.3f).height(60.dp).clickable { weekEndingDatePickerDialog.show() },
                            label = { Text("W/E", fontSize = 10.sp, maxLines = 1) },
                            placeholder = { Text("W/E", fontSize = 10.sp) },
                            readOnly = true,
                            enabled = false,
                            shape = RoundedCornerShape(8.dp),
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color.Black),
                            trailingIcon = {
                                IconButton(onClick = { weekEndingDatePickerDialog.show() }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = Color.Black,
                                disabledBorderColor = Color(0xFFE8EAED),
                                disabledContainerColor = Color.White,
                                disabledLabelColor = Color.Gray,
                                disabledTrailingIconColor = primaryColor
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = metadata.lessonNumber,
                            onValueChange = { onMetadataChange("lessonNumber", it) },
                            modifier = Modifier.weight(0.7f).height(60.dp),
                            label = { Text("Lesson", fontSize = 10.sp, maxLines = 1) },
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = Color.Black),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black,
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = primaryColor,
                                unfocusedBorderColor = Color(0xFFE8EAED),
                                focusedLabelColor = primaryColor,
                                unfocusedLabelColor = Color.Gray
                            )
                        )
                        OutlinedTextField(
                            value = formatToDDMMYYYY(metadata.date),
                            onValueChange = { },
                            modifier = Modifier.weight(1.3f).height(60.dp).clickable { datePickerDialog.show() },
                            label = { Text("Date", fontSize = 10.sp, maxLines = 1) },
                            placeholder = { Text("Date", fontSize = 10.sp) },
                            readOnly = true,
                            enabled = false,
                            shape = RoundedCornerShape(8.dp),
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color.Black),
                            trailingIcon = {
                                IconButton(onClick = { datePickerDialog.show() }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = Color.Black,
                                disabledBorderColor = Color(0xFFE8EAED),
                                disabledContainerColor = Color.White,
                                disabledLabelColor = Color.Gray,
                                disabledTrailingIconColor = primaryColor
                            )
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewStep(viewModel: LessonPlanViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val subjects by viewModel.subjects.collectAsState()
    val indicators by viewModel.indicators.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val model by viewModel.model.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedIndicatorForDetail by remember { mutableStateOf<CurriculumEntity?>(null) }

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            snackbarHostState.showSnackbar("Lesson plan saved successfully")
            viewModel.resetSaveSuccess()
        }
    }

    var showFormatDialog by remember { mutableStateOf<FormatAction?>(null) }

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

    var planToDelete by remember { mutableStateOf<Int?>(null) }

    if (planToDelete != null) {
        AlertDialog(
            onDismissRequest = { planToDelete = null },
            title = { Text("Delete Lesson Plan", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete this generated lesson plan?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        planToDelete?.let { viewModel.deleteGeneratedPlan(it) }
                        planToDelete = null
                        if (uiState.generatedPlanJsons.size <= 1) {
                            viewModel.updateStep(2)
                        }
                    }
                ) {
                    Text("Delete", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { planToDelete = null }) {
                    Text("Cancel")
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
            text = {
                Text(
                    if (action is FormatAction.Preview) "Preview opens as PDF only."
                    else "Choose a format:"
                )
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = {
                            when (action) {
                                is FormatAction.Preview -> viewModel.previewCurrentPlansAsPdf()
                                is FormatAction.Download -> viewModel.downloadCurrentPlansAsPdf()
                                is FormatAction.Export -> viewModel.exportCurrentPlansAsPdf()
                            }
                            showFormatDialog = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                    ) {
                        Text("PDF")
                    }

                    if (action !is FormatAction.Preview) {
                        Button(
                            onClick = {
                                when (action) {
                                    is FormatAction.Preview -> viewModel.previewCurrentPlansAsDocx()
                                    is FormatAction.Download -> viewModel.downloadCurrentPlansAsDocx()
                                    is FormatAction.Export -> viewModel.exportCurrentPlansAsDocx()
                                }
                                showFormatDialog = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                        ) {
                            Text("Word (.docx)")
                        }
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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
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
                                text = "${uiState.generatedPlanJsons.size} Plans Generated",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        color = Color(0xFFFFF3E0),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFE65100),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "AI-generated content. Please cross-check for accuracy.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFE65100),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.savePlans() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save All", fontSize = 12.sp)
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
                            onClick = { viewModel.previewCurrentPlansAsPdf() },
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
                        OutlinedTextField(
                            value = uiState.school,
                            onValueChange = { viewModel.updateSchool(it) },
                            label = { Text("School") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = uiState.facilitatorName,
                                onValueChange = { viewModel.updateFacilitatorName(it) },
                                label = { Text("Facilitator") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = uiState.term,
                                onValueChange = { viewModel.updateTerm(it) },
                                label = { Text("Term") },
                                modifier = Modifier.weight(0.4f),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = uiState.classSize,
                                onValueChange = { viewModel.updateClassSize(it) },
                                label = { Text("Class Size") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = uiState.duration,
                                onValueChange = { viewModel.updateDuration(it) },
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
                        Text("Generating ${uiState.selectedIndicatorIds.size} plans...", color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(
                        items = uiState.generatedPlanJsons,
                        key = { index, _ -> index }
                    ) { planIndex, planJson ->
                        val planMap = remember(planJson) {
                            try { Gson().fromJson(planJson, Map::class.java) } catch (e: Exception) { null }
                        }
                        val header = planMap?.get("header") as? Map<*, *>
                        val grade = header?.get("class") as? String ?: uiState.selectedGrade
                        val subjectName = header?.get("subject") as? String
                            ?: subjects.find { it.id == uiState.selectedSubjectId }?.actualName ?: ""
                        val indicatorLabel = header?.get("indicator") as? String ?: ""
                        val indicatorCode = indicatorLabel.split(" - ").firstOrNull()?.trim() ?: ""

                        // Each plan is collapsed by default
                        var expanded by remember { mutableStateOf(false) }
                        val totalPlans = uiState.generatedPlanJsons.size

                        // ── Label / header row ──────────────────────────────────────────
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
                                    // Collapse / expand chevron
                                    Icon(
                                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (expanded) "Collapse" else "Expand",
                                        tint = Color(0xFF2196F3),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "$indicatorCode · $subjectName",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1976D2),
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        val weekNumber = header?.get("week") as? String ?: ""
                                        if (weekNumber.isNotBlank()) {
                                            Text(
                                                text = "Week $weekNumber",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFF1976D2).copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                    // ── Action buttons ─────────────────────────────────
                                    // Info
                                    IconButton(
                                        onClick = {
                                            selectedIndicatorForDetail = indicators.find { it.indicatorCode == indicatorCode }
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
                                        onClick = { viewModel.moveGeneratedPlan(planIndex, -1) },
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
                                    IconButton(
                                        onClick = { viewModel.moveGeneratedPlan(planIndex, 1) },
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
                                    // Duplicate
                                    IconButton(
                                        onClick = { viewModel.duplicateGeneratedPlan(planIndex) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = "Duplicate Plan",
                                            modifier = Modifier.size(16.dp),
                                            tint = Color(0xFF4CAF50)
                                        )
                                    }
                                    // Regenerate
                                    IconButton(
                                        onClick = { viewModel.regeneratePlan(planIndex, apiKey, model) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = "Regenerate Plan",
                                            modifier = Modifier.size(16.dp),
                                            tint = Color(0xFFFF9800)
                                        )
                                    }

                                    // Delete
                                    IconButton(
                                        onClick = { planToDelete = planIndex },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete Plan",
                                            modifier = Modifier.size(16.dp),
                                            tint = Color.Red
                                        )
                                    }
                                }
                            }
                        }

                        // ── Expandable plan content ─────────────────────────────────────
                        androidx.compose.animation.AnimatedVisibility(visible = expanded) {
                            Column {
                                Spacer(Modifier.height(8.dp))
                                LessonPlanPreview(
                                    generatedPlanJson = planJson,
                                    editingPhaseIndex = if (uiState.editingPhaseIndex?.first == planIndex) uiState.editingPhaseIndex?.second else null,
                                    regeneratingPhaseIndex = if (uiState.regeneratingPhaseIndex?.first == planIndex) uiState.regeneratingPhaseIndex?.second else null,
                                    onEditPhase = { phaseIndex ->
                                        viewModel.updateEditingPhase(if (phaseIndex == null) null else planIndex to phaseIndex)
                                    },
                                    onUpdatePhaseField = { phaseIndex, field, value ->
                                        viewModel.updatePhaseField(planIndex, phaseIndex, field, value)
                                    },
                                    onRegeneratePhase = { phaseIndex, name ->
                                        viewModel.regeneratePhase(planIndex, phaseIndex, name, apiKey, model)
                                    },
                                    onUpdateHeaderField = { field, value ->
                                        viewModel.updateHeaderField(planIndex, field, value)
                                    }
                                )
                            }
                        }

                        if (planIndex < totalPlans - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                thickness = 1.dp,
                                color = Color(0xFFE0E0E0)
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
        )
    }
}

@Composable
fun StepHeaderItem(number: Int, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(number.toString(), color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("STEP $number", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
fun SectionLabel(icon: ImageVector, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp), tint = color)
        }
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
    }
}

@Composable
fun Pill(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) Color(0xFF2196F3) else Color(0xFFF5F5F5))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (isSelected) Color.White else Color.DarkGray,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
