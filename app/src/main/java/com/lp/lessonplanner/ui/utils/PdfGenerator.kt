package com.lp.lessonplanner.ui.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.text.HtmlCompat
import com.google.gson.Gson
import com.lp.lessonplanner.data.remote.LessonPlan
import com.lp.lessonplanner.data.remote.NotePlan
import com.lp.lessonplanner.data.remote.QuestionsPlan
import com.lp.lessonplanner.viewmodel.detectPlanType
import com.lp.lessonplanner.viewmodel.sanitizeJson
import java.io.File
import java.io.FileOutputStream

class PdfGenerator(private val context: Context) {
    private val gson = Gson()

    fun generatePdf(jsonContent: String, fileName: String): File? {
        return generateCombinedPdf(listOf(jsonContent), fileName)
    }

    fun generateCombinedPdf(jsonContents: List<String>, fileName: String): File? {
        val document = PdfDocument()
        val pageWidth = 595 // A4 Width
        val pageHeight = 842 // A4 Height
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()

        val paint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        val textPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 10f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }

        val boldPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 10f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }

        val titlePaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 14f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }

        val outputHeaderPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 13f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }

        val outputHeaderLabelPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 13f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }

        val margin = 30f

        jsonContents.forEachIndexed { _, jsonContent ->
            var page = document.startPage(pageInfo)
            var canvas = page.canvas
            var y = margin
            val contentWidth = pageWidth - (margin * 2)

            val sanitizedJson = jsonContent.sanitizeJson()
            val planType = sanitizedJson.detectPlanType()

            val lessonPlan = if (planType == "Lesson Plan") try { gson.fromJson(sanitizedJson, LessonPlan::class.java) } catch (e: Exception) { null } else null
            val notePlan = if (planType == "Full Note") try { gson.fromJson(sanitizedJson, NotePlan::class.java) } catch (e: Exception) { null } else null
            val questionsPlan = if (planType == "Questions") try { gson.fromJson(sanitizedJson, QuestionsPlan::class.java) } catch (e: Exception) { null } else null

            if (lessonPlan != null) {
                // Draw Title
                y += 10f
                val title = "LESSON PLAN RECORD"
                canvas.drawText(title, (pageWidth / 2f) - (titlePaint.measureText(title) / 2f), y, titlePaint)

                val header = lessonPlan.header
                val outputHeaderLines = listOfNotNull(
                    header?.school?.takeIf { it.isNotBlank() }?.let { "SCHOOL: " to it },
                    header?.facilitatorName?.takeIf { it.isNotBlank() }?.let { "FACILITATOR: " to it },
                    header?.term?.takeIf { it.isNotBlank() }?.let { "TERM: " to it },
                    header?.week?.takeIf { it.isNotBlank() }?.let { "WEEK: " to it }
                )
                outputHeaderLines.forEach { (label, value) ->
                    y += 16f
                    val labelWidth = outputHeaderLabelPaint.measureText(label)
                    val valueWidth = outputHeaderPaint.measureText(value)
                    val startX = (pageWidth / 2f) - ((labelWidth + valueWidth) / 2f)
                    canvas.drawText(label, startX, y, outputHeaderLabelPaint)
                    canvas.drawText(value, startX + labelWidth, y, outputHeaderPaint)
                }
                y += 20f
                
                val keywordsStr = when (val k = header?.keywords) {
                    is List<*> -> k.joinToString(", ")
                    is String -> k
                    else -> ""
                }
                
                // Header Table
                val headerRows = listOf(
                    listOf("DATE" to formatToDDMMYYYY(header?.date), "WEEK ENDING" to formatToDDMMYYYY(header?.weekEnding), "DURATION" to (header?.duration ?: "")),
                    listOf("SUBJECT" to (header?.subject ?: ""), "CLASS" to (header?.`class` ?: ""), "CLASS SIZE" to (header?.classSize ?: "")),
                    listOf("STRAND" to (header?.strand ?: ""), "SUB STRAND" to (header?.subStrand ?: "")),
                    listOf("CONTENT STANDARD" to (header?.contentStandard ?: "")),
                    listOf("INDICATOR" to (header?.indicator ?: ""), "LESSON" to (header?.lesson ?: "")),
                    listOf("PERFORMANCE INDICATOR" to (header?.performanceIndicator ?: ""), "CORE COMPETENCIES" to (header?.coreCompetencies ?: "")),
                    listOf("KEYWORDS" to keywordsStr)
                )

                headerRows.forEachIndexed { index, rowItems ->
                    // Check for page break in header (unlikely but safe)
                    if (y > pageHeight - margin - 40f) {
                        document.finishPage(page)
                        page = document.startPage(pageInfo)
                        canvas = page.canvas
                        y = margin
                    }
                    
                    val weights = when (index) {
                        4 -> floatArrayOf(0.7f, 0.3f) // INDICATOR and LESSON
                        5 -> floatArrayOf(0.7f, 0.3f) // PERFORMANCE INDICATOR and CORE COMPETENCIES
                        else -> null
                    }
                    y += drawTableRow(canvas, y, margin, contentWidth, rowItems, textPaint, boldPaint, paint, weights)
                }

                // Phases Table Header
                val colWidths = floatArrayOf(
                    contentWidth * 0.18f, // PHASE (includes duration)
                    contentWidth * 0.62f, // LEARNING ACTIVITIES
                    contentWidth * 0.20f  // RESOURCES
                )
                
                if (y > pageHeight - margin - 40f) {
                    document.finishPage(page)
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    y = margin
                }
                y += drawPhaseHeader(canvas, y, margin, colWidths, boldPaint, paint)

                // Phases Content
                lessonPlan.phases?.forEach { phase ->
                    val phaseText = if (!phase.duration.isNullOrEmpty()) "${phase.name}\n(${phase.duration})" else phase.name ?: ""
                    val layouts = listOf(
                        createStaticLayout(phaseText, boldPaint, colWidths[0].toInt() - 10, Layout.Alignment.ALIGN_CENTER),
                        createHtmlStaticLayout(phase.activities ?: "", textPaint, colWidths[1].toInt() - 10),
                        createHtmlStaticLayout(phase.resources ?: "", textPaint, colWidths[2].toInt() - 10)
                    )

                    var remainingHeight = layouts.maxOf { it.height }.toFloat() + 6f
                    var offset = 0f
                    var isFirstSlice = true
                    
                    while (remainingHeight > 0) {
                        var availableSpace = pageHeight - margin - y
                        
                        // If we're starting a new phase and space is very tight, move to next page
                        if (isFirstSlice && availableSpace < 50f) {
                            document.finishPage(page)
                            page = document.startPage(pageInfo)
                            canvas = page.canvas
                            y = margin
                            y += drawPhaseHeader(canvas, y, margin, colWidths, boldPaint, paint)
                            availableSpace = pageHeight - margin - y
                        } else if (!isFirstSlice && availableSpace < 20f) {
                            // Continuation slice needs a new page
                            document.finishPage(page)
                            page = document.startPage(pageInfo)
                            canvas = page.canvas
                            y = margin
                            y += drawPhaseHeader(canvas, y, margin, colWidths, boldPaint, paint)
                            availableSpace = pageHeight - margin - y
                        }
                        
                        val drawHeight = minOf(remainingHeight, availableSpace)
                        
                        var currentX = margin
                        layouts.forEachIndexed { i, layout ->
                            paint.style = Paint.Style.STROKE
                            paint.color = Color.BLACK
                            canvas.drawRect(currentX, y, currentX + colWidths[i], y + drawHeight, paint)
                            
                            canvas.save()
                            canvas.clipRect(currentX, y, currentX + colWidths[i], y + drawHeight)
                            
                            canvas.translate(currentX + 5, y + 3 - offset)
                            layout.draw(canvas)
                            
                            canvas.restore()
                            currentX += colWidths[i]
                        }
                        
                        y += drawHeight
                        remainingHeight -= drawHeight
                        offset += drawHeight
                        isFirstSlice = false
                    }
                }

            } else if (notePlan != null) {
                // Draw Title
                y += 10f
                val title = "TEACHING NOTE"
                canvas.drawText(title, (pageWidth / 2f) - (titlePaint.measureText(title) / 2f), y, titlePaint)

                val header = notePlan.header
                val outputHeaderLines = listOfNotNull(
                    header?.school?.takeIf { it.isNotBlank() }?.let { "SCHOOL: " to it },
                    header?.facilitatorName?.takeIf { it.isNotBlank() }?.let { "FACILITATOR: " to it },
                    header?.term?.takeIf { it.isNotBlank() }?.let { "TERM: " to it },
                    header?.week?.takeIf { it.isNotBlank() }?.let { "WEEK: " to it }
                )
                outputHeaderLines.forEach { (label, value) ->
                    y += 16f
                    val labelWidth = outputHeaderLabelPaint.measureText(label)
                    val valueWidth = outputHeaderPaint.measureText(value)
                    val startX = (pageWidth / 2f) - ((labelWidth + valueWidth) / 2f)
                    canvas.drawText(label, startX, y, outputHeaderLabelPaint)
                    canvas.drawText(value, startX + labelWidth, y, outputHeaderPaint)
                }
                y += 20f

                val headerRows = listOf(
                    listOf("DATE" to formatToDDMMYYYY(header?.date), "WEEK ENDING" to formatToDDMMYYYY(header?.weekEnding), "DURATION" to (header?.duration ?: "")),
                    listOf("SUBJECT" to (header?.subject ?: ""), "CLASS" to (header?.`class` ?: ""), "CLASS SIZE" to (header?.classSize ?: "")),
                    listOf("STRAND" to (header?.strand ?: ""), "SUB STRAND" to (header?.subStrand ?: "")),
                    listOf("CONTENT STANDARD" to (header?.contentStandard ?: "")),
                    listOf("INDICATOR" to (header?.indicator ?: ""), "LESSON" to (header?.lesson ?: ""))
                )

                headerRows.forEachIndexed { index, rowItems ->
                    if (y > pageHeight - margin - 40f) {
                        document.finishPage(page)
                        page = document.startPage(pageInfo)
                        canvas = page.canvas
                        y = margin
                    }
                    val weights = if (index == 4) floatArrayOf(0.7f, 0.3f) else null
                    y += drawTableRow(canvas, y, margin, contentWidth, rowItems, textPaint, boldPaint, paint, weights)
                }

                y += 20f
                val layout = createHtmlStaticLayout(notePlan.content ?: "", textPaint, contentWidth.toInt())
                val (updatedPage, updatedY) = drawMultilineText(document, page, canvas, layout, y, margin, contentWidth, pageHeight, pageInfo)
                page = updatedPage
                canvas = page.canvas
                y = updatedY

            } else if (questionsPlan != null) {
                // Draw Title
                y += 10f
                val title = "EXERCISE & ANSWERS"
                canvas.drawText(title, (pageWidth / 2f) - (titlePaint.measureText(title) / 2f), y, titlePaint)

                val header = questionsPlan.header
                val outputHeaderLines = listOfNotNull(
                    header?.school?.takeIf { it.isNotBlank() }?.let { "SCHOOL: " to it },
                    header?.facilitatorName?.takeIf { it.isNotBlank() }?.let { "FACILITATOR: " to it },
                    header?.term?.takeIf { it.isNotBlank() }?.let { "TERM: " to it },
                    header?.week?.takeIf { it.isNotBlank() }?.let { "WEEK: " to it }
                )
                outputHeaderLines.forEach { (label, value) ->
                    y += 16f
                    val labelWidth = outputHeaderLabelPaint.measureText(label)
                    val valueWidth = outputHeaderPaint.measureText(value)
                    val startX = (pageWidth / 2f) - ((labelWidth + valueWidth) / 2f)
                    canvas.drawText(label, startX, y, outputHeaderLabelPaint)
                    canvas.drawText(value, startX + labelWidth, y, outputHeaderPaint)
                }
                y += 20f

                val headerRows = listOf(
                    listOf("DATE" to formatToDDMMYYYY(header?.date), "WEEK ENDING" to formatToDDMMYYYY(header?.weekEnding), "DURATION" to (header?.duration ?: "")),
                    listOf("SUBJECT" to (header?.subject ?: ""), "CLASS" to (header?.`class` ?: ""), "CLASS SIZE" to (header?.classSize ?: "")),
                    listOf("STRAND" to (header?.strand ?: ""), "SUB STRAND" to (header?.subStrand ?: "")),
                    listOf("CONTENT STANDARD" to (header?.contentStandard ?: "")),
                    listOf("INDICATOR" to (header?.indicator ?: ""), "LESSON" to (header?.lesson ?: "")),
                    listOf("TYPE" to (header?.type ?: ""), "COUNT" to (header?.count?.toString() ?: ""))
                )

                headerRows.forEachIndexed { index, rowItems ->
                    if (y > pageHeight - margin - 40f) {
                        document.finishPage(page)
                        page = document.startPage(pageInfo)
                        canvas = page.canvas
                        y = margin
                    }
                    val weights = if (index == 4) floatArrayOf(0.7f, 0.3f) else null
                    y += drawTableRow(canvas, y, margin, contentWidth, rowItems, textPaint, boldPaint, paint, weights)
                }

                y += 20f
                
                // Questions section
                val questionsTitle = createCenteredUnderlinedLayout("QUESTIONS", boldPaint, contentWidth.toInt())
                canvas.save()
                canvas.translate(margin, y)
                questionsTitle.draw(canvas)
                canvas.restore()
                y += questionsTitle.height + 10f

                questionsPlan.questions?.forEachIndexed { index, item ->
                    val qLayout = createHtmlStaticLayout("${index + 1}. ${item.q?.replace("\n", "<br>")}", textPaint, contentWidth.toInt())
                    val (updatedPageQ, updatedYQ) = drawMultilineText(document, page, canvas, qLayout, y, margin, contentWidth, pageHeight, pageInfo)
                    page = updatedPageQ
                    canvas = page.canvas
                    y = updatedYQ
                    y += 10f
                }

                if (y > pageHeight - margin - 40f) {
                    document.finishPage(page)
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    y = margin
                } else {
                    y += 20f
                }

                val answersTitle = createCenteredUnderlinedLayout("ANSWERS", boldPaint, contentWidth.toInt())
                canvas.save()
                canvas.translate(margin, y)
                answersTitle.draw(canvas)
                canvas.restore()
                y += answersTitle.height + 10f

                questionsPlan.questions?.forEachIndexed { index, item ->
                    val aLayout = createHtmlStaticLayout("${index + 1}. ${item.a}", textPaint, contentWidth.toInt())
                    val (updatedPageA, updatedYA) = drawMultilineText(document, page, canvas, aLayout, y, margin, contentWidth, pageHeight, pageInfo)
                    page = updatedPageA
                    canvas = page.canvas
                    y = updatedYA
                    y += 5f
                }
            } else {
                // Raw text fallback
                val layout = createHtmlStaticLayout(jsonContent, textPaint, contentWidth.toInt())
                drawMultilineText(document, page, canvas, layout, y, margin, contentWidth, pageHeight, pageInfo)
            }
            document.finishPage(page)
        }

        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "$fileName.pdf")
        return try {
            document.writeTo(FileOutputStream(file))
            document.close()
            file
        } catch (e: Exception) {
            document.close()
            null
        }
    }

    private fun drawTableRow(
        canvas: Canvas,
        y: Float,
        x: Float,
        totalWidth: Float,
        items: List<Pair<String, String>>,
        textPaint: TextPaint,
        boldPaint: TextPaint,
        borderPaint: Paint,
        weights: FloatArray? = null
    ): Float {
        val totalWeight = weights?.sum() ?: items.size.toFloat()
        val layouts = items.mapIndexed { i, (label, value) ->
            val weight = weights?.get(i) ?: 1f
            val cellWidth = (totalWidth * weight) / totalWeight
            createHtmlStaticLayout("<b>$label:</b> $value", textPaint, cellWidth.toInt() - 10)
        }
        val maxHeight = layouts.maxOf { it.height }.toFloat() + 6f

        var currentX = x
        layouts.forEachIndexed { i, layout ->
            val weight = weights?.get(i) ?: 1f
            val cellWidth = (totalWidth * weight) / totalWeight
            canvas.drawRect(currentX, y, currentX + cellWidth, y + maxHeight, borderPaint)
            canvas.save()
            canvas.translate(currentX + 5, y + 3)
            layout.draw(canvas)
            canvas.restore()
            currentX += cellWidth
        }
        return maxHeight
    }

    private fun createStaticLayout(text: String, paint: TextPaint, width: Int, alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): StaticLayout {
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(alignment)
            .setLineSpacing(0f, 1.5f)
            .setIncludePad(false)
            .build()
    }

    private fun createHtmlStaticLayout(html: String, paint: TextPaint, width: Int, alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): StaticLayout {
        var processedHtml = html
            .replace("\r", "")
            .replace("\n", " ")
            .replace("<li>", "• ")
            .replace("</li>", "<br>")
            .replace("<ul>", "")
            .replace("</ul>", "")
            .trim()

        // Remove empty sections
        val headerPattern = "(?i)\\b(Activity\\s+\\d+|Assessment)\\b"
        val headerRegex = Regex(headerPattern)
        val matches = headerRegex.findAll(processedHtml).toList()
        val toRemove = mutableListOf<IntRange>()
        
        for (i in matches.indices) {
            val start = matches[i].range.first
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else processedHtml.length
            val contentAfterLabel = processedHtml.substring(matches[i].range.last + 1, end)
            val hasContent = contentAfterLabel.replace(Regex("<[^>]*>"), "").any { it.isLetterOrDigit() }
            if (!hasContent) {
                toRemove.add(IntRange(start, end - 1))
            }
        }
        
        var currentHtml = processedHtml
        var deletedCount = 0
        for (range in toRemove) {
            currentHtml = currentHtml.removeRange(range.first - deletedCount, range.last + 1 - deletedCount)
            deletedCount += (range.last - range.first + 1)
        }
        processedHtml = currentHtml.trim()

        // Spacing between remaining activities: exactly one blank line before Activity X or Assessment
        val activityRegex = Regex("(?i)\\b(Activity\\s+\\d+|Assessment)\\b")
        processedHtml = processedHtml.replace(activityRegex) { matchResult ->
            val prefix = processedHtml.substring(0, matchResult.range.first)
            val visibleText = prefix.replace(Regex("<[^>]*>"), "").trim()
            // Use a placeholder to ensure we can precisely control the spacing before headers
            if (visibleText.isEmpty()) matchResult.value else "___DBL_BRK___${matchResult.value}"
        }

        // 1. Expand placeholder to two breaks (creates one blank line)
        processedHtml = processedHtml.replace("___DBL_BRK___", "<br><br>")

        // 2. Collapse any sequences of 3 or more contiguous breaks into exactly 2
        processedHtml = processedHtml.replace(Regex("(<br>\\s*){3,}"), "<br><br>")

        // 3. Collapse 3 or more breaks even if they are separated by tags (like <b></b>) or whitespace
        // This ensures that "Activity X" always has exactly one blank line above it,
        // even if the previous content ended with its own break.
        repeat(3) {
            processedHtml = processedHtml.replace(Regex("<br>((?:\\s|<[^>]*>)*)<br>((?:\\s|<[^>]*>)*)<br>"), "$1<br>$2<br>")
        }

        val spanned = HtmlCompat.fromHtml(processedHtml, HtmlCompat.FROM_HTML_MODE_COMPACT)
        
        var start = 0
        while (start < spanned.length && Character.isWhitespace(spanned[start])) start++
        var end = spanned.length
        while (end > start && Character.isWhitespace(spanned[end - 1])) end--
        
        val trimmedSpanned = if (start > 0 || end < spanned.length) {
            android.text.SpannableStringBuilder(spanned, start, end)
        } else {
            android.text.SpannableStringBuilder(spanned)
        }
        
        val textString = trimmedSpanned.toString()
        var bulletIdx = textString.indexOf("•")
        while (bulletIdx != -1) {
            trimmedSpanned.setSpan(android.text.style.StyleSpan(Typeface.BOLD), bulletIdx, bulletIdx + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            bulletIdx = textString.indexOf("•", bulletIdx + 1)
        }

        return StaticLayout.Builder.obtain(trimmedSpanned, 0, trimmedSpanned.length, paint, width)
            .setAlignment(alignment)
            .setLineSpacing(0f, 1.5f)
            .setIncludePad(false)
            .build()
    }

    private fun drawMultilineText(
        document: PdfDocument,
        initialPage: PdfDocument.Page,
        initialCanvas: Canvas,
        layout: StaticLayout,
        startY: Float,
        margin: Float,
        contentWidth: Float,
        pageHeight: Int,
        pageInfo: PdfDocument.PageInfo
    ): Pair<PdfDocument.Page, Float> {
        var page = initialPage
        var canvas = initialCanvas
        var y = startY
        var remainingHeight = layout.height.toFloat()
        var offset = 0f

        while (remainingHeight > 0) {
            val availableSpace = pageHeight - margin - y
            if (availableSpace < 20f) {
                document.finishPage(page)
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = margin
            }

            val currentSpace = pageHeight - margin - y
            val drawHeight = minOf(remainingHeight, currentSpace)

            canvas.save()
            canvas.clipRect(margin, y, margin + contentWidth, y + drawHeight)
            canvas.translate(margin, y - offset)
            layout.draw(canvas)
            canvas.restore()

            y += drawHeight
            remainingHeight -= drawHeight
            offset += drawHeight
        }
        return Pair(page, y)
    }

    private fun createCenteredUnderlinedLayout(text: String, paint: TextPaint, width: Int): StaticLayout {
        val spannable = android.text.SpannableString(text)
        spannable.setSpan(android.text.style.UnderlineSpan(), 0, text.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return StaticLayout.Builder.obtain(spannable, 0, spannable.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.5f)
            .setIncludePad(false)
            .build()
    }

    private fun drawPhaseHeader(canvas: Canvas, y: Float, x: Float, colWidths: FloatArray, boldPaint: TextPaint, paint: Paint): Float {
        val phaseHeaders = listOf("PHASE", "LEARNING ACTIVITIES", "RESOURCES")
        var maxHeight = 0f
        val layouts = phaseHeaders.mapIndexed { i, h ->
            val layout = StaticLayout.Builder.obtain(h, 0, h.length, boldPaint, colWidths[i].toInt() - 10)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1.5f)
                .setIncludePad(false)
                .build()
            if (layout.height + 6f > maxHeight) maxHeight = layout.height + 6f
            layout
        }

        var currentX = x
        layouts.forEachIndexed { i, layout ->
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#F0F0F0")
            canvas.drawRect(currentX, y, currentX + colWidths[i], y + maxHeight, paint)
            
            paint.style = Paint.Style.STROKE
            paint.color = Color.BLACK
            canvas.drawRect(currentX, y, currentX + colWidths[i], y + maxHeight, paint)
            
            canvas.save()
            canvas.translate(currentX + 5, y + 3)
            layout.draw(canvas)
            canvas.restore()
            currentX += colWidths[i]
        }
        return maxHeight
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
}
