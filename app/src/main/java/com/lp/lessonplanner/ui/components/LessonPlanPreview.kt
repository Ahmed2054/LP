package com.lp.lessonplanner.ui.components

import android.app.DatePickerDialog
import android.widget.TextView
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.graphics.toColorInt
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import com.google.gson.Gson
import com.lp.lessonplanner.data.remote.LessonPlan
import com.lp.lessonplanner.data.remote.LessonPlanHeader
import com.lp.lessonplanner.data.remote.LessonPlanPhase
import com.lp.lessonplanner.data.remote.NotePlan
import com.lp.lessonplanner.data.remote.QuestionsPlan
import com.lp.lessonplanner.viewmodel.sanitizeJson
import java.util.Calendar
import java.util.Locale

private val PHASE_COLORS = listOf(
    Color(0xFF2196F3), // Primary
    Color(0xFFFF9800), // Accent
    Color(0xFF8B5CF6), // Purple
    Color(0xFF4CAF50), // Success
    Color(0xFFF59E0B)  // Orange/Amber
)

private fun getPhaseIcon(name: String): ImageVector {
    val n = name.lowercase()
    return when {
        n.contains("starter") || n.contains("intro") || n.contains("warm") -> Icons.Default.FlashOn
        n.contains("new learning") || n.contains("main") || n.contains("core") || n.contains("presentation") -> Icons.Default.Lightbulb
        n.contains("activit") || n.contains("practice") || n.contains("application") -> Icons.Default.Build
        n.contains("closure") || n.contains("plenary") || n.contains("conclusion") || n.contains("reflect") -> Icons.Default.Flag
        n.contains("assess") || n.contains("evaluat") -> Icons.AutoMirrored.Filled.Assignment
        else -> Icons.Default.Layers
    }
}

@Composable
fun LessonPlanPreview(
    generatedPlanJson: String?,
    regeneratingPhaseIndex: Int? = null,
    editingPhaseIndex: Int? = null,
    onRegeneratePhase: (Int, String) -> Unit = { _, _ -> },
    onEditPhase: (Int?) -> Unit = {},
    onUpdatePhaseField: (Int, String, String) -> Unit = { _, _, _ -> },
    onUpdateHeaderField: (String, String) -> Unit = { _, _ -> }
) {
    if (generatedPlanJson == null) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            Text("No plan generated yet.", color = Color.Gray)
        }
        return
    }

    val gson = Gson()
    val sanitizedJson = generatedPlanJson.sanitizeJson()
    
    // Parse as map first to check plan_type
    val rawMap = try {
        gson.fromJson(sanitizedJson, Map::class.java)
    } catch (_: Exception) {
        null
    }

    val planType = rawMap?.get("plan_type") as? String

    val lessonPlan = if (planType == "Lesson Plan" || planType == null) {
        try { gson.fromJson(sanitizedJson, LessonPlan::class.java) } catch (_: Exception) { null }
    } else null

    val notePlan = if (planType == "Full Note") {
        try { gson.fromJson(sanitizedJson, NotePlan::class.java) } catch (_: Exception) { null }
    } else null

    val questionsPlan = if (planType == "Questions") {
        try { gson.fromJson(sanitizedJson, QuestionsPlan::class.java) } catch (_: Exception) { null }
    } else null

    val headerToEdit: LessonPlanHeader? = when {
        lessonPlan != null -> lessonPlan.header
        notePlan != null -> notePlan.header
        questionsPlan != null -> questionsPlan.header
        else -> null
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            headerToEdit?.let { header ->
                HeaderEditSection(header, onUpdateHeaderField)
            }

            when {
                lessonPlan != null -> {
                    lessonPlan.phases?.forEachIndexed { index, phase ->
                        PhaseCard(
                            index = index,
                            phase = phase,
                            isEditing = editingPhaseIndex == index,
                            isRegenerating = regeneratingPhaseIndex == index,
                            onRegenerate = { onRegeneratePhase(index, phase.name ?: "") },
                            onEdit = { onEditPhase(if (editingPhaseIndex == index) null else index) },
                            onUpdateField = { field, value -> onUpdatePhaseField(index, field, value) }
                        )
                    }
                }
                notePlan != null -> {
                    notePlan.content?.let { content ->
                        NoteCard(title = "Note", content = content, color = Color(0xFFFF9800), icon = Icons.Outlined.Description)
                    }
                }
                questionsPlan != null -> {
                    questionsPlan.questions?.let { questions ->
                        // Questions
                        questions.forEachIndexed { index, item ->
                            QuestionCard(index + 1, item.q ?: "", item.a ?: "", showAnswer = false)
                            Spacer(Modifier.height(8.dp))
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        
                        // Answer Key
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                            border = BorderStroke(1.dp, Color(0xFFC8E6C9))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "CORRECT ANSWERS",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF2E7D32),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                questions.forEachIndexed { index, item ->
                                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                        Text("${index + 1}. ", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(item.a ?: "", fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    // Raw text fallback
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            generatedPlanJson,
                            modifier = Modifier.padding(16.dp),
                            fontSize = 14.sp,
                            lineHeight = 22.sp
                        )
                    }
                }
            }
        }

        if (regeneratingPhaseIndex == -1) {
            val infiniteTransition = rememberInfiniteTransition(label = "overlay_regeneration")
            val alphaState = infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 0.5f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "alpha"
            )
            val scaleState = infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale"
            )
            val dotsCount by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 4f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "dots"
            )

            Surface(
                modifier = Modifier.matchParentSize().clickable(enabled = false) {},
                color = Color.White.copy(alpha = 0.7f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = Color(0xFF2196F3),
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Regenerating Plan${".".repeat(dotsCount.toInt())}",
                            color = Color(0xFF2196F3),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = scaleState.value
                                    scaleY = scaleState.value
                                    alpha = alphaState.value
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RegeneratingLabel(color: Color, alphaState: androidx.compose.runtime.State<Float>) {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val dotsCount by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dots"
    )
    val scaleState = infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    Text(
        " • REGENERATING${".".repeat(dotsCount.toInt())}",
        fontWeight = FontWeight.Black,
        fontSize = 9.sp,
        color = color,
        modifier = Modifier
            .padding(start = 4.dp)
            .graphicsLayer { 
                alpha = alphaState.value 
                scaleX = scaleState.value
                scaleY = scaleState.value
            }
    )
}

@Composable
fun HeaderEditSection(
    header: LessonPlanHeader,
    onUpdateHeaderField: (String, String) -> Unit
) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    
    // Parse current date if possible to initialize picker
    try {
        header.date?.split("/")?.let { parts ->
            if (parts.size == 3) {
                calendar.set(Calendar.DAY_OF_MONTH, parts[0].toInt())
                calendar.set(Calendar.MONTH, parts[1].toInt() - 1)
                calendar.set(Calendar.YEAR, parts[2].toInt())
            }
        }
    } catch (_: Exception) {}

    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, day ->
            val formattedDate = String.format(Locale.getDefault(), "%02d/%02d/%04d", day, month + 1, year)
            onUpdateHeaderField("date", formattedDate)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    val weekEndingCalendar = Calendar.getInstance()
    try {
        header.weekEnding?.split("/")?.let { parts ->
            if (parts.size == 3) {
                weekEndingCalendar.set(Calendar.DAY_OF_MONTH, parts[0].toInt())
                weekEndingCalendar.set(Calendar.MONTH, parts[1].toInt() - 1)
                weekEndingCalendar.set(Calendar.YEAR, parts[2].toInt())
            }
        }
    } catch (_: Exception) {}

    val weekEndingDatePickerDialog = DatePickerDialog(
        context,
        { _, year, month, day ->
            val formattedDate = String.format(Locale.getDefault(), "%02d/%02d/%04d", day, month + 1, year)
            onUpdateHeaderField("weekEnding", formattedDate)
        },
        weekEndingCalendar.get(Calendar.YEAR),
        weekEndingCalendar.get(Calendar.MONTH),
        weekEndingCalendar.get(Calendar.DAY_OF_MONTH)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F3F4)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Edit Header Information", fontWeight = FontWeight.Bold, fontSize = 14.sp)

            OutlinedTextField(
                value = header.week ?: "",
                onValueChange = { onUpdateHeaderField("week", it) },
                label = { Text("Week") },
                placeholder = { Text("Optional") },
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = formatToDDMMYYYY(header.date),
                onValueChange = { },
                label = { Text("Date") },
                placeholder = { Text("Optional") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!header.date.isNullOrBlank()) {
                            IconButton(onClick = { onUpdateHeaderField("date", "") }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear date",
                                    tint = Color.Gray
                                )
                            }
                        }
                        IconButton(onClick = { datePickerDialog.show() }) {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = "Pick date"
                            )
                        }
                    }
                }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = formatToDDMMYYYY(header.weekEnding),
                    onValueChange = { },
                    label = { Text("Week Ending") },
                    placeholder = { Text("Optional") },
                    modifier = Modifier.weight(0.65f),
                    readOnly = true,
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!header.weekEnding.isNullOrBlank()) {
                                IconButton(onClick = { onUpdateHeaderField("weekEnding", "") }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear week ending",
                                        tint = Color.Gray
                                    )
                                }
                            }
                            IconButton(onClick = { weekEndingDatePickerDialog.show() }) {
                                Icon(
                                    Icons.Default.CalendarMonth,
                                    contentDescription = "Pick week ending"
                                )
                            }
                        }
                    }
                )
                OutlinedTextField(
                    value = header.lesson ?: "",
                    onValueChange = { onUpdateHeaderField("lesson", it) },
                    label = { Text("Lesson") },
                    placeholder = { Text("Optional") },
                    modifier = Modifier.weight(0.35f)
                )
            }
            
            OutlinedTextField(
                value = when (val k = header.keywords) {
                    is List<*> -> k.joinToString(", ")
                    is String -> k
                    else -> ""
                },
                onValueChange = { onUpdateHeaderField("keywords", it) },
                label = { Text("Keywords") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun PhaseCard(
    index: Int,
    phase: LessonPlanPhase,
    isEditing: Boolean,
    isRegenerating: Boolean,
    onRegenerate: () -> Unit,
    onEdit: () -> Unit,
    onUpdateField: (String, String) -> Unit
) {
    val color = PHASE_COLORS[index % PHASE_COLORS.size]
    
    val infiniteTransition = rememberInfiniteTransition(label = "regeneration")
    val alphaState = infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isRegenerating) Modifier.graphicsLayer { alpha = alphaState.value } else Modifier),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color.copy(alpha = 0.1f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = color,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            getPhaseIcon(phase.name ?: ""),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = phase.name?.uppercase() ?: "",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = color
                        )
                        if (isRegenerating) {
                            RegeneratingLabel(color = color, alphaState = alphaState)
                        }
                    }
                    Text(
                        text = phase.duration ?: "",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }

                if (isRegenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = color)
                } else {
                    IconButton(onClick = onRegenerate, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Regenerate", tint = color, modifier = Modifier.size(18.dp))
                    }
                }
                
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (isEditing) Icons.Default.Check else Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = color,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isEditing) {
                    OutlinedTextField(
                        value = phase.duration ?: "",
                        onValueChange = { onUpdateField("duration", it) },
                        label = { Text("Duration") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = phase.activities ?: "",
                        onValueChange = { onUpdateField("activities", it) },
                        label = { Text("Activities (HTML)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                    OutlinedTextField(
                        value = phase.resources ?: "",
                        onValueChange = { onUpdateField("resources", it) },
                        label = { Text("Resources (HTML)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                } else {
                    ContentSection(title = "LEARNING ACTIVITIES", content = phase.activities ?: "", icon = Icons.Outlined.Lightbulb)
                    HorizontalDivider(color = Color(0xFFF0F0F0))
                    ContentSection(title = "RESOURCES", content = phase.resources ?: "", icon = Icons.Outlined.Inventory2)
                }
            }
        }
    }
}

@Composable
fun NoteCard(title: String, content: String, color: Color, icon: ImageVector) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color.copy(alpha = 0.1f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(title.uppercase(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = color)
            }
            
            AndroidView(
                factory = { context ->
                    TextView(context).apply {
                        textSize = 14f
                        setTextColor(android.graphics.Color.BLACK)
                        setLineSpacing(0f, 1.5f)
                    }
                },
                update = { view ->
                    view.text = HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_COMPACT)
                },
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
fun QuestionCard(number: Int, question: String, answer: String, showAnswer: Boolean = true) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFEEEEEE))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row {
                Text("$number.", fontWeight = FontWeight.Bold, color = Color(0xFF6200EE))
                Spacer(modifier = Modifier.width(8.dp))
                AndroidView(
                    factory = { context ->
                        TextView(context).apply {
                            textSize = 14f
                            setTextColor(android.graphics.Color.BLACK)
                            setLineSpacing(0f, 1.5f)
                        }
                    },
                    update = { view ->
                        view.text = HtmlCompat.fromHtml(question.replace("\n", "<br>"), HtmlCompat.FROM_HTML_MODE_COMPACT)
                    }
                )
            }
            if (showAnswer) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF1F8E9), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Row {
                        Text("Ans: ", fontWeight = FontWeight.Bold, color = Color(0xFF388E3C), fontSize = 12.sp)
                        AndroidView(
                            factory = { context ->
                                TextView(context).apply {
                                    textSize = 13f
                                    setTextColor(android.graphics.Color.DKGRAY)
                                    setLineSpacing(0f, 1.5f)
                                }
                            },
                            update = { view ->
                                view.text = HtmlCompat.fromHtml(answer, HtmlCompat.FROM_HTML_MODE_COMPACT)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentSection(title: String, content: String, icon: ImageVector) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
        }
        
        AndroidView(
            factory = { context ->
                TextView(context).apply {
                    textSize = 14f
                    setTextColor("#333333".toColorInt())
                    setLineSpacing(0f, 1.5f)
                }
            },
            update = { view ->
                view.text = HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_COMPACT)
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun formatToDDMMYYYY(dateStr: String?): String {
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

