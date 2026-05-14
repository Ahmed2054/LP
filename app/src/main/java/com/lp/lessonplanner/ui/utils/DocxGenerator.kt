package com.lp.lessonplanner.ui.utils

import android.content.Context
import android.os.Environment
import androidx.core.text.HtmlCompat
import com.google.gson.Gson
import com.lp.lessonplanner.data.remote.LessonPlan
import com.lp.lessonplanner.data.remote.NotePlan
import com.lp.lessonplanner.data.remote.QuestionsPlan
import com.lp.lessonplanner.viewmodel.detectPlanType
import com.lp.lessonplanner.viewmodel.sanitizeJson
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.TableRowAlign
import org.apache.poi.xwpf.usermodel.TableWidthType
import org.apache.poi.xwpf.usermodel.UnderlinePatterns
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.apache.poi.xwpf.usermodel.XWPFTableCell
import org.apache.poi.xwpf.usermodel.XWPFTableRow
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger

class DocxGenerator(private val context: Context) {
    private val gson = Gson()
    private val bulletSymbol = "\u2022"

    private companion object {
        const val A4_PAGE_WIDTH_TWIPS = 11900
        const val A4_PAGE_HEIGHT_TWIPS = 16840
        const val PAGE_MARGIN_TWIPS = 600
        const val TABLE_CONTENT_WIDTH_TWIPS = A4_PAGE_WIDTH_TWIPS - (PAGE_MARGIN_TWIPS * 2)
        const val HEADER_GRID_COLUMNS = 300
        const val BODY_FONT_SIZE = 10
        const val TITLE_FONT_SIZE = 14
        const val OUTPUT_HEADER_FONT_SIZE = 13
        const val LINE_SPACING = 1.5
    }

    fun generateDocx(jsonContent: String, fileName: String): File? {
        return generateCombinedDocx(listOf(jsonContent), fileName)
    }

    fun generateCombinedDocx(jsonContents: List<String>, fileName: String): File? {
        val document = XWPFDocument()

        val sectPr = document.document.body.addNewSectPr()
        val pgSz = sectPr.addNewPgSz()
        pgSz.w = BigInteger.valueOf(A4_PAGE_WIDTH_TWIPS.toLong())
        pgSz.h = BigInteger.valueOf(A4_PAGE_HEIGHT_TWIPS.toLong())
        val pgMar = sectPr.addNewPgMar()
        pgMar.left = BigInteger.valueOf(PAGE_MARGIN_TWIPS.toLong())
        pgMar.right = BigInteger.valueOf(PAGE_MARGIN_TWIPS.toLong())
        pgMar.top = BigInteger.valueOf(PAGE_MARGIN_TWIPS.toLong())
        pgMar.bottom = BigInteger.valueOf(PAGE_MARGIN_TWIPS.toLong())

        jsonContents.forEach { jsonContent ->
            val sanitizedJson = jsonContent.sanitizeJson()
            val jsonMap = try {
                gson.fromJson(sanitizedJson, Map::class.java)
            } catch (_: Exception) {
                null
            }
            val planType = sanitizedJson.detectPlanType()

            val lessonPlan = if (planType == "Lesson Plan") {
                try {
                    gson.fromJson(sanitizedJson, LessonPlan::class.java)
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }
            val notePlan = if (planType == "Full Note") {
                try {
                    gson.fromJson(sanitizedJson, NotePlan::class.java)
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }
            val questionsPlan = if (planType == "Questions") {
                try {
                    gson.fromJson(sanitizedJson, QuestionsPlan::class.java)
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }

            when {
                lessonPlan != null -> generateLessonPlanDocx(document, lessonPlan)
                notePlan != null -> generateNotePlanDocx(document, notePlan)
                questionsPlan != null -> generateQuestionsPlanDocx(document, questionsPlan)
                else -> {
                    val paragraph = document.createParagraph()
                    val run = paragraph.createRun()
                    run.fontFamily = "Times New Roman"
                    run.fontSize = BODY_FONT_SIZE
                    run.setText(jsonContent)
                }
            }

            if (jsonContents.size > 1 && jsonContent != jsonContents.last()) {
                document.createParagraph().isPageBreak = true
            }
        }

        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "$fileName.docx")
        return try {
            FileOutputStream(file).use { out ->
                document.write(out)
            }
            document.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                document.close()
            } catch (_: Exception) {
            }
            null
        }
    }

    private fun generateLessonPlanDocx(document: XWPFDocument, plan: LessonPlan) {
        addCenteredTitle(document, "LESSON PLAN RECORD", spacingAfter = 100)

        val header = plan.header ?: return
        addOutputHeaderDetails(document, header.school, header.facilitatorName, header.term, header.week)
        val table = document.createTable()
        applyHeaderTableStyle(table)
        table.removeRow(0)

        addHeaderRow(table, listOf("DATE" to formatToDDMMYYYY(header.date), "WEEK ENDING" to formatToDDMMYYYY(header.weekEnding), "DURATION" to (header.duration ?: "")))
        addHeaderRow(table, listOf("SUBJECT" to (header.subject ?: ""), "CLASS" to (header.`class` ?: ""), "CLASS SIZE" to (header.classSize ?: "")))
        addHeaderRow(table, listOf("STRAND" to (header.strand ?: ""), "SUB STRAND" to (header.subStrand ?: "")))
        addHeaderRow(table, listOf("CONTENT STANDARD" to (header.contentStandard ?: "")))
        addHeaderRow(table, listOf("INDICATOR" to (header.indicator ?: ""), "LESSON" to (header.lesson ?: "")), floatArrayOf(0.7f, 0.3f))
        addHeaderRow(table, listOf("PERFORMANCE INDICATOR" to (header.performanceIndicator ?: ""), "CORE COMPETENCIES" to (header.coreCompetencies ?: "")), floatArrayOf(0.7f, 0.3f))
        val keywordsStr = when (val k = header.keywords) {
            is List<*> -> k.joinToString(", ")
            is String -> k
            else -> ""
        }
        addHeaderRow(table, listOf("KEYWORDS" to keywordsStr))

        val colWeights = floatArrayOf(0.18f, 0.62f, 0.20f)
        val phaseGridSpans = getGridSpans(colWeights.size, colWeights, HEADER_GRID_COLUMNS)

        val headerRow = table.createRow()
        ensureRowCellCount(headerRow, 3)
        val phaseHeaders = listOf("PHASE", "LEARNING ACTIVITIES", "RESOURCES")
        phaseHeaders.forEachIndexed { index, title ->
            val cell = headerRow.getCell(index)
            cell.color = "F0F0F0"
            setCellWidth(cell, colWeights[index], colWeights.sum())
            setCellGridSpan(cell, phaseGridSpans[index])
            cell.verticalAlignment = XWPFTableCell.XWPFVertAlign.TOP
            val paragraph = resetCellParagraph(cell, ParagraphAlignment.CENTER)
            val run = paragraph.createRun()
            run.isBold = true
            run.fontFamily = "Times New Roman"
            run.fontSize = BODY_FONT_SIZE
            run.setText(title)
        }

        plan.phases?.forEach { phase ->
            val row = table.createRow()
            ensureRowCellCount(row, 3)
            val phaseText = if (!phase.duration.isNullOrEmpty()) "${phase.name}\n(${phase.duration})" else phase.name.orEmpty()

            row.getCell(0).apply {
                setCellWidth(this, colWeights[0], colWeights.sum())
                setCellGridSpan(this, phaseGridSpans[0])
                verticalAlignment = XWPFTableCell.XWPFVertAlign.TOP
                val paragraph = resetCellParagraph(this, ParagraphAlignment.CENTER)
                val run = paragraph.createRun()
                run.isBold = true
                run.fontFamily = "Times New Roman"
                run.fontSize = BODY_FONT_SIZE
                phaseText.split("\n").forEachIndexed { lineIndex, line ->
                    if (lineIndex > 0) run.addBreak()
                    run.setText(line)
                }
            }

            row.getCell(1).apply {
                setCellWidth(this, colWeights[1], colWeights.sum())
                setCellGridSpan(this, phaseGridSpans[1])
                verticalAlignment = XWPFTableCell.XWPFVertAlign.TOP
                appendHtmlContent(this, phase.activities ?: "")
            }

            row.getCell(2).apply {
                setCellWidth(this, colWeights[2], colWeights.sum())
                setCellGridSpan(this, phaseGridSpans[2])
                verticalAlignment = XWPFTableCell.XWPFVertAlign.TOP
                appendHtmlContent(this, phase.resources ?: "")
            }
        }
    }

    private fun generateNotePlanDocx(document: XWPFDocument, plan: NotePlan) {
        addCenteredTitle(document, "TEACHING NOTE")

        val header = plan.header ?: return
        val table = document.createTable()
        applyHeaderTableStyle(table)
        table.removeRow(0)

        addHeaderRow(table, listOf("DATE" to formatToDDMMYYYY(header.date), "WEEK ENDING" to formatToDDMMYYYY(header.weekEnding), "DURATION" to (header.duration ?: "")))
        addHeaderRow(table, listOf("SUBJECT" to (header.subject ?: ""), "CLASS" to (header.`class` ?: ""), "CLASS SIZE" to (header.classSize ?: "")))
        addHeaderRow(table, listOf("STRAND" to (header.strand ?: ""), "SUB STRAND" to (header.subStrand ?: "")))
        addHeaderRow(table, listOf("CONTENT STANDARD" to (header.contentStandard ?: "")))
        addHeaderRow(table, listOf("INDICATOR" to (header.indicator ?: ""), "LESSON" to (header.lesson ?: "")), floatArrayOf(0.7f, 0.3f))

        document.createParagraph().spacingBefore = 400
        val contentPara = document.createParagraph()
        appendHtmlContent(contentPara, plan.content ?: "")
    }

    private fun generateQuestionsPlanDocx(document: XWPFDocument, plan: QuestionsPlan) {
        addCenteredTitle(document, "EXERCISE & ANSWERS")

        val header = plan.header ?: return
        val table = document.createTable()
        applyHeaderTableStyle(table)
        table.removeRow(0)

        addHeaderRow(table, listOf("DATE" to formatToDDMMYYYY(header.date), "WEEK ENDING" to formatToDDMMYYYY(header.weekEnding), "DURATION" to (header.duration ?: "")))
        addHeaderRow(table, listOf("SUBJECT" to (header.subject ?: ""), "CLASS" to (header.`class` ?: ""), "CLASS SIZE" to (header.classSize ?: "")))
        addHeaderRow(table, listOf("STRAND" to (header.strand ?: ""), "SUB STRAND" to (header.subStrand ?: "")))
        addHeaderRow(table, listOf("CONTENT STANDARD" to (header.contentStandard ?: "")))
        addHeaderRow(table, listOf("INDICATOR" to (header.indicator ?: ""), "LESSON" to (header.lesson ?: "")), floatArrayOf(0.7f, 0.3f))
        addHeaderRow(table, listOf("TYPE" to (header.type ?: ""), "COUNT" to (header.count?.toString().orEmpty())))

        document.createParagraph().spacingBefore = 400
        val qTitlePara = document.createParagraph()
        qTitlePara.alignment = ParagraphAlignment.CENTER
        val qTitleRun = qTitlePara.createRun()
        qTitleRun.isBold = true
        qTitleRun.fontFamily = "Times New Roman"
        qTitleRun.fontSize = BODY_FONT_SIZE
        qTitleRun.underline = UnderlinePatterns.SINGLE
        qTitleRun.setText("QUESTIONS")

        plan.questions?.forEachIndexed { index, item ->
            val paragraph = document.createParagraph()
            paragraph.spacingAfter = 200
            appendHtmlContent(paragraph, "${index + 1}. ${item.q}")
        }

        document.createParagraph().spacingBefore = 400
        val aTitlePara = document.createParagraph()
        aTitlePara.alignment = ParagraphAlignment.CENTER
        val aTitleRun = aTitlePara.createRun()
        aTitleRun.isBold = true
        aTitleRun.fontFamily = "Times New Roman"
        aTitleRun.fontSize = BODY_FONT_SIZE
        aTitleRun.underline = UnderlinePatterns.SINGLE
        aTitleRun.setText("ANSWERS")

        plan.questions?.forEachIndexed { index, item ->
            val paragraph = document.createParagraph()
            paragraph.spacingAfter = 100
            appendHtmlContent(paragraph, "${index + 1}. ${item.a}")
        }
    }

    private fun addCenteredTitle(document: XWPFDocument, title: String, spacingAfter: Int = 400) {
        val titlePara = document.createParagraph()
        titlePara.alignment = ParagraphAlignment.CENTER
        titlePara.spacingBefore = 200
        titlePara.spacingAfter = spacingAfter
        val titleRun = titlePara.createRun()
        titleRun.isBold = true
        titleRun.fontFamily = "Times New Roman"
        titleRun.fontSize = TITLE_FONT_SIZE
        titleRun.setText(title)
    }

    private fun addOutputHeaderDetails(
        document: XWPFDocument,
        school: String?,
        facilitatorName: String?,
        term: String?,
        week: String?
    ) {
        val details = listOfNotNull(
            school?.takeIf { it.isNotBlank() }?.let { "SCHOOL: " to it },
            facilitatorName?.takeIf { it.isNotBlank() }?.let { "FACILITATOR: " to it },
            term?.takeIf { it.isNotBlank() }?.let { "TERM: " to it },
            week?.takeIf { it.isNotBlank() }?.let { "WEEK: " to it }
        )

        if (details.isEmpty()) {
            document.createParagraph().spacingAfter = 300
            return
        }

        details.forEachIndexed { index, (label, value) ->
            val paragraph = document.createParagraph()
            paragraph.alignment = ParagraphAlignment.CENTER
            paragraph.spacingBefore = 0
            paragraph.spacingAfter = if (index == details.lastIndex) 300 else 20

            val labelRun = paragraph.createRun()
            labelRun.isBold = true
            labelRun.fontFamily = "Times New Roman"
            labelRun.fontSize = OUTPUT_HEADER_FONT_SIZE
            labelRun.setText(label)

            val valueRun = paragraph.createRun()
            valueRun.fontFamily = "Times New Roman"
            valueRun.fontSize = OUTPUT_HEADER_FONT_SIZE
            valueRun.setText(value)
        }
    }

    private fun addHeaderRow(table: XWPFTable, items: List<Pair<String, String>>, weights: FloatArray? = null) {
        if (items.isEmpty()) return

        val row = table.createRow()
        ensureRowCellCount(row, items.size)

        val totalWeight = weights?.sum() ?: items.size.toFloat()
        val gridSpans = getGridSpans(items.size, weights, HEADER_GRID_COLUMNS)
        items.forEachIndexed { index, (label, value) ->
            val cell = row.getCell(index)
            if (cell == null) return@forEachIndexed

            val weight = weights?.get(index) ?: 1f
            setCellWidth(cell, weight, totalWeight)
            setCellGridSpan(cell, gridSpans[index])
            cell.verticalAlignment = XWPFTableCell.XWPFVertAlign.TOP
            appendHtmlContent(cell, "<b>$label:</b> $value")
        }
    }

    private fun ensureRowCellCount(row: XWPFTableRow, count: Int) {
        while (row.tableCells.size > count) {
            row.removeCell(row.tableCells.size - 1)
        }
        while (row.tableCells.size < count) {
            row.addNewTableCell()
        }
    }

    private fun appendHtmlContent(cell: XWPFTableCell, html: String) {
        val paragraph = resetCellParagraph(cell)
        appendHtmlContent(paragraph, html)
    }

    private fun appendHtmlContent(paragraph: XWPFParagraph, html: String) {
        val processedHtml = preprocessHtml(html)
        val spanned = HtmlCompat.fromHtml(processedHtml, HtmlCompat.FROM_HTML_MODE_COMPACT)
        val trimmed = trimSpanned(spanned)
        val builder = android.text.SpannableStringBuilder(trimmed)
        val textString = builder.toString()

        var bulletIdx = textString.indexOf(bulletSymbol)
        while (bulletIdx != -1) {
            builder.setSpan(
                android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                bulletIdx,
                bulletIdx + 1,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            bulletIdx = textString.indexOf(bulletSymbol, bulletIdx + 1)
        }

        paragraph.spacingBefore = 0
        paragraph.spacingAfter = 0
        paragraph.spacingBetween = LINE_SPACING
        renderSpannedToParagraph(paragraph, builder)
    }

    private fun resetCellParagraph(
        cell: XWPFTableCell,
        alignment: ParagraphAlignment = ParagraphAlignment.LEFT
    ): XWPFParagraph {
        while (cell.paragraphs.size > 1) {
            cell.removeParagraph(cell.paragraphs.lastIndex)
        }
        if (cell.paragraphs.isNotEmpty()) {
            cell.removeParagraph(0)
        }
        return cell.addParagraph().apply {
            this.alignment = alignment
            spacingBefore = 0
            spacingAfter = 0
            spacingBetween = LINE_SPACING
        }
    }

    private fun trimSpanned(spanned: android.text.Spanned): android.text.Spanned {
        var start = 0
        while (start < spanned.length && Character.isWhitespace(spanned[start])) start++
        var end = spanned.length
        while (end > start && Character.isWhitespace(spanned[end - 1])) end--
        return if (start > 0 || end < spanned.length) {
            android.text.SpannableStringBuilder(spanned, start, end)
        } else {
            spanned
        }
    }

    private fun renderSpannedToParagraph(paragraph: XWPFParagraph, spanned: android.text.Spanned) {
        val text = spanned.toString()
        val styleSpans = spanned.getSpans(0, spanned.length, android.text.style.StyleSpan::class.java)
        val underlineSpans = spanned.getSpans(0, spanned.length, android.text.style.UnderlineSpan::class.java)
        val points = mutableListOf<Int>().apply {
            add(0)
            add(spanned.length)
            styleSpans.forEach {
                add(spanned.getSpanStart(it))
                add(spanned.getSpanEnd(it))
            }
            underlineSpans.forEach {
                add(spanned.getSpanStart(it))
                add(spanned.getSpanEnd(it))
            }
        }
        val sortedPoints = points.distinct().filter { it in 0..spanned.length }.sorted()
        for (index in 0 until sortedPoints.size - 1) {
            val start = sortedPoints[index]
            val end = sortedPoints[index + 1]
            if (start == end) continue

            val partText = text.substring(start, end)
            var isBold = false
            var isItalic = false
            var isUnderline = false

            spanned.getSpans(start, end, android.text.style.StyleSpan::class.java).forEach {
                if (spanned.getSpanStart(it) <= start && spanned.getSpanEnd(it) >= end) {
                    if (it.style == android.graphics.Typeface.BOLD || it.style == android.graphics.Typeface.BOLD_ITALIC) {
                        isBold = true
                    }
                    if (it.style == android.graphics.Typeface.ITALIC || it.style == android.graphics.Typeface.BOLD_ITALIC) {
                        isItalic = true
                    }
                }
            }
            spanned.getSpans(start, end, android.text.style.UnderlineSpan::class.java).forEach {
                if (spanned.getSpanStart(it) <= start && spanned.getSpanEnd(it) >= end) {
                    isUnderline = true
                }
            }

            appendTextWithBreaks(paragraph, partText, isBold, isItalic, isUnderline)
        }
    }

    private fun appendTextWithBreaks(
        paragraph: XWPFParagraph,
        text: String,
        isBold: Boolean,
        isItalic: Boolean = false,
        isUnderline: Boolean = false
    ) {
        val lines = text.split("\n")
        lines.forEachIndexed { index, line ->
            if (index > 0) {
                paragraph.createRun().addBreak()
            }
            if (line.isNotEmpty()) {
                val run = paragraph.createRun()
                run.isBold = isBold
                run.isItalic = isItalic
                if (isUnderline) {
                    run.underline = UnderlinePatterns.SINGLE
                }
                run.fontFamily = "Times New Roman"
                run.fontSize = BODY_FONT_SIZE
                run.setText(line)
            }
        }
    }

    private fun applyTableStyle(table: XWPFTable, columnWeights: FloatArray? = null) {
        table.width = TABLE_CONTENT_WIDTH_TWIPS
        table.setWidthType(TableWidthType.DXA)
        table.setTableAlignment(TableRowAlign.CENTER)
        table.setCellMargins(60, 100, 60, 100)
        setFixedTableLayout(table, columnWeights)

        table.setInsideHBorder(XWPFTable.XWPFBorderType.SINGLE, 8, 0, "000000")
        table.setInsideVBorder(XWPFTable.XWPFBorderType.SINGLE, 8, 0, "000000")
        table.setLeftBorder(XWPFTable.XWPFBorderType.SINGLE, 8, 0, "000000")
        table.setRightBorder(XWPFTable.XWPFBorderType.SINGLE, 8, 0, "000000")
        table.setTopBorder(XWPFTable.XWPFBorderType.SINGLE, 8, 0, "000000")
        table.setBottomBorder(XWPFTable.XWPFBorderType.SINGLE, 8, 0, "000000")
    }

    private fun applyHeaderTableStyle(table: XWPFTable) {
        applyTableStyle(table, FloatArray(HEADER_GRID_COLUMNS) { 1f })
    }

    private fun setFixedTableLayout(table: XWPFTable, columnWeights: FloatArray?) {
        val ctTbl = table.ctTbl
        val tblPr = ctTbl.getTblPr() ?: ctTbl.addNewTblPr()
        val tblLayout = if (tblPr.isSetTblLayout) tblPr.tblLayout else tblPr.addNewTblLayout()
        tblLayout.type = STTblLayoutType.FIXED

        if (columnWeights == null) return

        val tblGrid = ctTbl.getTblGrid() ?: ctTbl.addNewTblGrid()
        while (tblGrid.sizeOfGridColArray() > 0) {
            tblGrid.removeGridCol(0)
        }

        val totalWeight = columnWeights.sum()
        var allocatedWidth = 0L
        columnWeights.forEachIndexed { index, weight ->
            val columnWidth = if (index == columnWeights.lastIndex) {
                TABLE_CONTENT_WIDTH_TWIPS.toLong() - allocatedWidth
            } else {
                ((weight / totalWeight) * TABLE_CONTENT_WIDTH_TWIPS).toLong()
            }
            allocatedWidth += columnWidth
            tblGrid.addNewGridCol().w = BigInteger.valueOf(columnWidth)
        }
    }

    private fun getGridSpans(itemCount: Int, weights: FloatArray?, gridColumns: Int): IntArray {
        if (weights != null) {
            var allocatedSpan = 0
            return IntArray(itemCount) { index ->
                if (index == itemCount - 1) {
                    gridColumns - allocatedSpan
                } else {
                    val span = ((weights[index] / weights.sum()) * gridColumns).toInt()
                    allocatedSpan += span
                    span
                }
            }
        }

        var allocatedSpan = 0
        return IntArray(itemCount) { index ->
            if (index == itemCount - 1) {
                gridColumns - allocatedSpan
            } else {
                val span = gridColumns / itemCount
                allocatedSpan += span
                span
            }
        }
    }

    private fun setCellWidth(cell: XWPFTableCell, weight: Float, totalWeight: Float) {
        val cellWidth = ((weight / totalWeight) * TABLE_CONTENT_WIDTH_TWIPS).toInt()
        cell.setWidth(cellWidth.toString())
        cell.setWidthType(TableWidthType.DXA)
    }

    private fun setCellGridSpan(cell: XWPFTableCell, span: Int) {
        val tcPr = cell.ctTc.tcPr ?: cell.ctTc.addNewTcPr()
        val gridSpan = if (tcPr.isSetGridSpan) tcPr.gridSpan else tcPr.addNewGridSpan()
        gridSpan.`val` = BigInteger.valueOf(span.toLong())
    }

    private fun preprocessHtml(html: String): String {
        var processedHtml = html
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("\r", "")
            .replace("\n", " ")
            .replace("<li>", "$bulletSymbol ")
            .replace("</li>", "<br>")
            .replace("<ul>", "")
            .replace("</ul>", "")
            .trim()

        val headerPattern = "(?i)\\b(Activity\\s+\\d+|Assessment)\\b"
        val headerRegex = Regex(headerPattern)
        val matches = headerRegex.findAll(processedHtml).toList()
        val toRemove = mutableListOf<IntRange>()
        for (index in matches.indices) {
            val start = matches[index].range.first
            val end = if (index + 1 < matches.size) matches[index + 1].range.first else processedHtml.length
            val contentAfterLabel = processedHtml.substring(matches[index].range.last + 1, end)
            val hasContent = contentAfterLabel.replace(Regex("<[^>]*>"), "").any { it.isLetterOrDigit() }
            if (!hasContent) {
                toRemove.add(IntRange(start, end - 1))
            }
        }

        var currentHtml = processedHtml
        var deletedCount = 0
        for (range in toRemove) {
            currentHtml = currentHtml.removeRange(range.first - deletedCount, range.last + 1 - deletedCount)
            deletedCount += range.last - range.first + 1
        }

        processedHtml = currentHtml.trim()
        val activityRegex = Regex("(?i)\\b(Activity\\s+\\d+|Assessment)\\b")
        processedHtml = processedHtml.replace(activityRegex) { matchResult ->
            val prefix = processedHtml.substring(0, matchResult.range.first)
            val visibleText = prefix.replace(Regex("<[^>]*>"), "").trim()
            if (visibleText.isEmpty()) matchResult.value else "___DBL_BRK___${matchResult.value}"
        }
        processedHtml = processedHtml.replace("___DBL_BRK___", "<br><br>")
        processedHtml = processedHtml.replace(Regex("(<br>\\s*){3,}"), "<br><br>")
        repeat(3) {
            processedHtml = processedHtml.replace(
                Regex("<br>((?:\\s|<[^>]*>)*)<br>((?:\\s|<[^>]*>)*)<br>"),
                "$1<br>$2<br>"
            )
        }
        return processedHtml
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
