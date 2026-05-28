package com.example.pulserds_prog7313.utils

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.pulserds_prog7313.data.AccountSpending
import com.example.pulserds_prog7313.data.BankAccount
import com.example.pulserds_prog7313.data.Category
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AnalyticsPdfExporter {

    private const val PAGE_WIDTH  = 595   // A4 at 72 dpi
    private const val PAGE_HEIGHT = 842
    private const val MARGIN      = 40f
    private const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2

    // Brand colours
    private val TEAL   = Color.parseColor("#2EC4B6")
    private val ORANGE = Color.parseColor("#FF9F1C")
    private val DARK   = Color.parseColor("#1A1A1A")
    private val LIGHT_BG = Color.parseColor("#F4FDFC")
    private val DIVIDER  = Color.parseColor("#D0EEE9")

    fun export(
        context: Context,
        periodLabel: String,
        income: Double,
        expenses: Double,
        categories: List<Category>,
        totals: Map<String, Double>,
        accounts: List<BankAccount>,
        accountSpending: List<AccountSpending>
    ): Intent {
        val doc  = PdfDocument()
        val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = doc.startPage(info)
        val canvas = page.canvas

        var y = drawHeader(canvas, periodLabel)
        y = drawSummaryCard(canvas, y, income, expenses)
        y = drawCategorySection(canvas, y, categories, totals)
        y = drawAccountSection(canvas, y, accounts, accountSpending)
        drawFooter(canvas)

        doc.finishPage(page)

        val dir  = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "")
        dir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(dir, "Analytics_${stamp}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Analytics Report – $periodLabel")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    // -------------------------------------------------------------------------
    // Header
    // -------------------------------------------------------------------------

    private fun drawHeader(canvas: Canvas, periodLabel: String): Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Teal banner
        paint.color = TEAL
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 90f, paint)

        // App name
        paint.color = Color.WHITE
        paint.textSize = 22f
        paint.isFakeBoldText = true
        canvas.drawText("Pulser Analytics Report", MARGIN, 38f, paint)

        // Period label
        paint.textSize = 13f
        paint.isFakeBoldText = false
        canvas.drawText(periodLabel, MARGIN, 58f, paint)

        // Generated date
        val generated = "Generated: ${SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date())}"
        paint.textSize = 10f
        paint.color = Color.parseColor("#D0EEE9")
        canvas.drawText(generated, MARGIN, 78f, paint)

        return 110f
    }

    // -------------------------------------------------------------------------
    // Summary card
    // -------------------------------------------------------------------------

    private fun drawSummaryCard(canvas: Canvas, startY: Float, income: Double, expenses: Double): Float {
        val saved = (income - expenses).coerceAtLeast(0.0)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Card background
        paint.color = LIGHT_BG
        val rect = RectF(MARGIN, startY, PAGE_WIDTH - MARGIN, startY + 90f)
        canvas.drawRoundRect(rect, 10f, 10f, paint)

        // Left border accent
        paint.color = ORANGE
        canvas.drawRect(MARGIN, startY, MARGIN + 4f, startY + 90f, paint)

        // Section title
        paint.color = DARK
        paint.textSize = 13f
        paint.isFakeBoldText = true
        canvas.drawText("Financial Summary", MARGIN + 14f, startY + 22f, paint)

        // Three columns: Income | Expenses | Saved
        val colW = CONTENT_WIDTH / 3f
        drawSummaryColumn(canvas, MARGIN + 14f,            startY + 40f, "Total Income",   "R ${"%.2f".format(income)},  ", Color.parseColor("#2EC4B6"))
        drawSummaryColumn(canvas, MARGIN + 14f + colW,     startY + 40f, "Total Expenses", "R ${"%.2f".format(expenses)}", Color.parseColor("#F87171"))
        drawSummaryColumn(canvas, MARGIN + 14f + colW * 2, startY + 40f, "Saved",          "R ${"%.2f".format(saved)}",   ORANGE)

        return startY + 110f
    }

    private fun drawSummaryColumn(canvas: Canvas, x: Float, y: Float, label: String, value: String, valueColor: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.parseColor("#555555")
        paint.textSize = 10f
        paint.isFakeBoldText = false
        canvas.drawText(label, x, y, paint)

        paint.color = valueColor
        paint.textSize = 13f
        paint.isFakeBoldText = true
        canvas.drawText(value, x, y + 18f, paint)
    }

    // -------------------------------------------------------------------------
    // Category breakdown
    // -------------------------------------------------------------------------

    private fun drawCategorySection(
        canvas: Canvas,
        startY: Float,
        categories: List<Category>,
        totals: Map<String, Double>
    ): Float {
        var y = startY
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        y = drawSectionTitle(canvas, y, "Category Breakdown", paint)
        y = drawTableHeader(canvas, y, "Category", "Type", "Amount", paint)

        val expenseCategories = categories.filter { it.type != "INCOME" }
            .sortedByDescending { totals[it.id] ?: 0.0 }

        expenseCategories.forEachIndexed { i, cat ->
            val amount = totals[cat.id] ?: 0.0
            val rowBg = if (i % 2 == 0) Color.parseColor("#F9FFFE") else Color.WHITE
            y = drawTableRow(canvas, y, cat.name, cat.type, "R ${"%.2f".format(amount)}", rowBg, paint)
        }

        return y + 16f
    }

    // -------------------------------------------------------------------------
    // Account spending
    // -------------------------------------------------------------------------

    private fun drawAccountSection(
        canvas: Canvas,
        startY: Float,
        accounts: List<BankAccount>,
        accountSpending: List<AccountSpending>
    ): Float {
        var y = startY
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        y = drawSectionTitle(canvas, y, "Spending by Account", paint)
        y = drawTableHeader(canvas, y, "Account", "Bank", "Amount Spent", paint)

        accountSpending.forEachIndexed { i, spend ->
            val account = accounts.find { it.id == spend.bankAccountId }
            val name  = account?.nickname ?: "Unknown Account"
            val bank  = account?.bankName    ?: ""
            val rowBg = if (i % 2 == 0) Color.parseColor("#F9FFFE") else Color.WHITE
            y = drawTableRow(canvas, y, name, bank, "R ${"%.2f".format(spend.total)}", rowBg, paint)
        }

        return y + 16f
    }

    // -------------------------------------------------------------------------
    // Shared drawing helpers
    // -------------------------------------------------------------------------

    private fun drawSectionTitle(canvas: Canvas, y: Float, title: String, paint: Paint): Float {
        // Teal underline bar
        paint.color = TEAL
        canvas.drawRect(MARGIN, y, MARGIN + CONTENT_WIDTH, y + 2f, paint)

        paint.color = DARK
        paint.textSize = 13f
        paint.isFakeBoldText = true
        canvas.drawText(title, MARGIN, y + 18f, paint)
        return y + 28f
    }

    private fun drawTableHeader(canvas: Canvas, y: Float, col1: String, col2: String, col3: String, paint: Paint): Float {
        paint.color = Color.parseColor("#E8F9F7")
        canvas.drawRect(MARGIN, y, MARGIN + CONTENT_WIDTH, y + 20f, paint)

        paint.color = Color.parseColor("#1A5C55")
        paint.textSize = 10f
        paint.isFakeBoldText = true
        canvas.drawText(col1, MARGIN + 6f, y + 14f, paint)
        canvas.drawText(col2, MARGIN + CONTENT_WIDTH * 0.55f, y + 14f, paint)
        canvas.drawText(col3, MARGIN + CONTENT_WIDTH * 0.78f, y + 14f, paint)
        return y + 22f
    }

    private fun drawTableRow(
        canvas: Canvas,
        y: Float,
        col1: String,
        col2: String,
        col3: String,
        bgColor: Int,
        paint: Paint
    ): Float {
        paint.color = bgColor
        canvas.drawRect(MARGIN, y, MARGIN + CONTENT_WIDTH, y + 18f, paint)

        // Divider line
        paint.color = DIVIDER
        canvas.drawLine(MARGIN, y + 18f, MARGIN + CONTENT_WIDTH, y + 18f, paint)

        paint.color = DARK
        paint.textSize = 10f
        paint.isFakeBoldText = false
        canvas.drawText(col1.take(32), MARGIN + 6f, y + 13f, paint)
        canvas.drawText(col2.take(20), MARGIN + CONTENT_WIDTH * 0.55f, y + 13f, paint)

        paint.isFakeBoldText = true
        canvas.drawText(col3, MARGIN + CONTENT_WIDTH * 0.78f, y + 13f, paint)
        return y + 18f
    }

    private fun drawFooter(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = DIVIDER
        canvas.drawLine(MARGIN, PAGE_HEIGHT - 30f, PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 30f, paint)

        paint.color = Color.parseColor("#888888")
        paint.textSize = 9f
        canvas.drawText("Generated by Pulser  •  For personal financial reference only", MARGIN, PAGE_HEIGHT - 15f, paint)
    }
}
