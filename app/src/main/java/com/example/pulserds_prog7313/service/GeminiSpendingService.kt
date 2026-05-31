package com.example.pulserds_prog7313.service

import com.example.pulserds_prog7313.BuildConfig
import com.example.pulserds_prog7313.data.Category
import com.example.pulserds_prog7313.data.ExpenseEntry
import com.example.pulserds_prog7313.data.SavingsGoal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class SpendingInsight(
    val summary: String,
    val overspendingWarnings: List<String>,
    val budgetSuggestions: List<String>,
    val savingsAdvice: String,
    val topRisk: String
)

class GeminiSpendingService {

    // Use the v1 REST endpoint directly — the Google AI SDK 0.9.0 hardcodes v1beta
    // which no longer supports gemini-2.0-flash. OkHttp is already a project dependency.
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val apiKey = BuildConfig.GEMINI_API_KEY.trim()  // trim in case local.properties has accidental whitespace
    private val model  = "gemini-2.0-flash-lite"
    private val url    = "https://generativelanguage.googleapis.com/v1/models/$model:generateContent?key=$apiKey"

    /**
     * Builds a structured prompt from the user's real data and returns parsed insights.
     * Call this from a coroutine (viewModelScope.launch).
     */
    suspend fun analyzeSpending(
        entries: List<ExpenseEntry>,
        categories: List<Category>,
        savingsGoals: List<SavingsGoal>,
        periodLabel: String,
        currency: String = "R"
    ): SpendingInsight = withContext(Dispatchers.IO) {

        val categoryMap = categories.associateBy { it.id }

        val spendByCategory = entries
            .filter { it.amount < 0 }
            .groupBy { categoryMap[it.categoryId]?.name ?: "Unknown" }
            .mapValues { (_, list) -> list.sumOf { kotlin.math.abs(it.amount) } }
            .entries
            .sortedByDescending { it.value }
            .joinToString("\n") { (cat, total) ->
                "  - $cat: $currency ${"%.2f".format(total)}"
            }

        val income     = entries.filter { it.amount > 0 }.sumOf { it.amount }
        val totalSpend = entries.filter { it.amount < 0 }.sumOf { kotlin.math.abs(it.amount) }
        val saved      = (income - totalSpend).coerceAtLeast(0.0)

        val goalsText = if (savingsGoals.isEmpty()) "No active savings goals." else
            savingsGoals.joinToString("\n") { goal ->
                val progress = if (goal.targetAmount > 0)
                    (goal.savedAmount / goal.targetAmount * 100).toInt() else 0
                "  - ${goal.name}: ${currency} ${"%.2f".format(goal.savedAmount)} " +
                        "of ${currency} ${"%.2f".format(goal.targetAmount)} ($progress%) — due ${goal.targetDate}"
            }

        val prompt = """
            You are a personal finance AI for a South African budgeting app called Pulser.
            Analyse the user's spending data for the period: $periodLabel.
            
            FINANCIAL SUMMARY:
            - Total Income:  $currency ${"%.2f".format(income)}
            - Total Spent:   $currency ${"%.2f".format(totalSpend)}
            - Net Saved:     $currency ${"%.2f".format(saved)}
            - Savings Rate:  ${if (income > 0) "%.1f".format(saved / income * 100) else "0"}%
            
            SPENDING BY CATEGORY:
            $spendByCategory
            
            SAVINGS GOALS:
            $goalsText
            
            Respond ONLY with a single JSON object, no markdown fences, no extra text:
            {
              "summary": "2-sentence overall financial health summary",
              "overspendingWarnings": ["warning 1", "warning 2"],
              "budgetSuggestions": ["suggestion 1", "suggestion 2", "suggestion 3"],
              "savingsAdvice": "one paragraph on savings goal progress",
              "topRisk": "the single biggest financial risk in one sentence"
            }
        """.trimIndent()

        // Build the Gemini REST request body
        val requestBody = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(
                    JSONObject().put("text", prompt)
                ))
            ))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.4)
                put("maxOutputTokens", 800)
            })
        }.toString()

        return@withContext try {
            val okRequest = Request.Builder()
                .url(url)
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            var response = client.newCall(okRequest).execute()
            var responseBody = response.body?.string() ?: "{}"
            var httpCode = response.code

            // Retry up to 3 times with exponential backoff on rate-limit (429)
            var attempt = 0
            while (httpCode == 429 && attempt < 3) {
                attempt++
                val backoffMs = (1000L * (1 shl attempt)) // 2s, 4s, 8s
                android.util.Log.w("GeminiService", "429 rate limit — retrying in ${backoffMs}ms (attempt $attempt)")
                delay(backoffMs)
                response = client.newCall(okRequest).execute()
                responseBody = response.body?.string() ?: "{}"
                httpCode = response.code
            }

            android.util.Log.d("GeminiService", "HTTP $httpCode")
            android.util.Log.d("GeminiService", "Response: $responseBody")

            if (!response.isSuccessful) {
                android.util.Log.e("GeminiService", "API error $httpCode: $responseBody")
                val (summary, topRisk) = when (httpCode) {
                    429  -> "AI insights unavailable — rate limit reached." to
                            "Free tier quota exceeded. Please wait a minute and try again."
                    401, 403 -> "AI insights unavailable — authentication failed." to
                            "Invalid or missing API key (HTTP $httpCode)."
                    else -> "Could not load AI insights. Check your connection." to
                            "API error $httpCode — see logcat for details."
                }
                return@withContext SpendingInsight(
                    summary              = summary,
                    overspendingWarnings = emptyList(),
                    budgetSuggestions    = emptyList(),
                    savingsAdvice        = "",
                    topRisk              = topRisk
                )
            }

            // Gemini REST response shape:
            // {"candidates":[{"content":{"parts":[{"text":"{ ... JSON ... }"}]}}]}
            val text = JSONObject(responseBody)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trimJson()

            android.util.Log.d("GeminiService", "Parsed text: $text")
            parseInsight(text)
        } catch (e: Exception) {
            android.util.Log.e("GeminiService", "Exception: ${e.javaClass.simpleName}: ${e.message}", e)
            SpendingInsight(
                summary              = "Could not load AI insights. Check your connection.",
                overspendingWarnings = emptyList(),
                budgetSuggestions    = emptyList(),
                savingsAdvice        = "",
                topRisk              = "Error: ${e.javaClass.simpleName} — ${e.message?.take(80)}"
            )
        }
    }

    /**
     * Strips optional markdown code fences (```json ... ```) that Gemini
     * sometimes wraps around its JSON output despite being told not to.
     */
    private fun String.trimJson(): String = this
        .trim()
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()

    /**
     * Parses the JSON response using Android's built-in org.json library.
     * This correctly handles commas and special characters inside string values,
     * unlike the previous hand-rolled regex approach which split on every comma.
     */
    private fun parseInsight(json: String): SpendingInsight {
        val obj = JSONObject(json)

        fun jsonArrayToList(key: String): List<String> {
            val arr = obj.optJSONArray(key) ?: return emptyList()
            return (0 until arr.length()).map { arr.getString(it) }
        }

        return SpendingInsight(
            summary              = obj.optString("summary", ""),
            overspendingWarnings = jsonArrayToList("overspendingWarnings"),
            budgetSuggestions    = jsonArrayToList("budgetSuggestions"),
            savingsAdvice        = obj.optString("savingsAdvice", ""),
            topRisk              = obj.optString("topRisk", "")
        )
    }
}