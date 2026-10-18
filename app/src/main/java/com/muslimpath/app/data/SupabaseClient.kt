package com.muslimpath.app.data

import android.content.Context
import com.muslimpath.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal Supabase (GoTrue + PostgREST) client over HTTPS.
 *
 * Guest mode: an anonymous Supabase user is created on first network action, so questions
 * are owned by `auth.uid()` and protected by RLS. Registration upgrades the same
 * anonymous user with email + password, keeping all data (ТЗ 3.1).
 *
 * When `supabase.url` / `supabase.anonKey` are absent from local.properties the client is disabled
 * and the app runs fully offline.
 */
class SupabaseClient(context: Context) {

    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    val isConfigured: Boolean get() = baseUrl.startsWith("https://") && anonKey.isNotBlank()

    val email: String? get() = prefs.getString("email", null)
    val isRegistered: Boolean get() = email != null

    // ---------- Auth ----------

    private suspend fun accessToken(): String {
        val token = prefs.getString("access", null)
        val expires = prefs.getLong("expires", 0)
        if (token != null && System.currentTimeMillis() < expires - 60_000) return token
        val refresh = prefs.getString("refresh", null)
        val session = if (refresh != null) {
            runCatching { call("POST", "/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", refresh), auth = false) }
                .getOrNull()
        } else null
        val result = session ?: call("POST", "/auth/v1/signup", JSONObject().put("data", JSONObject()), auth = false)
        saveSession(result as JSONObject)
        return prefs.getString("access", null)!!
    }

    private fun saveSession(o: JSONObject) {
        prefs.edit()
            .putString("access", o.getString("access_token"))
            .putString("refresh", o.getString("refresh_token"))
            .putLong("expires", System.currentTimeMillis() + o.optLong("expires_in", 3600) * 1000)
            .apply()
        o.optJSONObject("user")?.optString("email")?.takeIf { it.isNotBlank() }?.let { prefs.edit().putString("email", it).apply() }
    }

    /** Converts the anonymous guest into a full account; progress stays attached to the same user id. */
    suspend fun register(email: String, password: String) {
        call("PUT", "/auth/v1/user", JSONObject().put("email", email).put("password", password))
        prefs.edit().putString("email", email).apply()
    }

    suspend fun signIn(email: String, password: String) {
        val o = call("POST", "/auth/v1/token?grant_type=password", JSONObject().put("email", email).put("password", password), auth = false)
        saveSession(o as JSONObject)
        prefs.edit().putString("email", email).apply()
    }

    fun signOut() = prefs.edit().clear().apply()

    /** Server-side deletion of the account and all linked personal data (RPC defined in migrations). */
    suspend fun deleteAccount() {
        if (prefs.getString("access", null) != null) call("POST", "/rest/v1/rpc/delete_my_account", JSONObject())
        signOut()
    }

    // ---------- Data ----------

    suspend fun submitQuestion(q: UserQuestion) {
        call("POST", "/rest/v1/questions", JSONObject()
            .put("client_id", q.id).put("body", q.text).put("category_id", q.categoryId).put("lesson_id", q.lessonId))
    }

    suspend fun rateAnswer(clientId: String, rating: Int) {
        call("POST", "/rest/v1/rpc/rate_answer", JSONObject().put("p_client_id", clientId).put("p_rating", rating))
    }

    /** Pulls statuses/answers for the user's own tickets. Keys are client ids. */
    suspend fun fetchQuestionUpdates(): Map<String, Pair<TicketStatus, String?>> =
        (call("GET", "/rest/v1/questions?select=client_id,status,answer") as JSONArray).objects()
            .associate { it.getString("client_id") to (TicketStatus.parse(it.optString("status")) to it.optStringOrNull("answer")) }

    suspend fun pushProgress(completed: Map<String, Long>, saved: Set<String>) {
        if (completed.isNotEmpty()) {
            val rows = JSONArray(completed.map { (id, at) -> JSONObject().put("lesson_id", id).put("completed_at", isoUtc(at)) })
            call("POST", "/rest/v1/progress?on_conflict=user_id,lesson_id", rows, prefer = "resolution=merge-duplicates")
        }
        call("POST", "/rest/v1/rpc/set_saved_lessons", JSONObject().put("lesson_ids", JSONArray(saved.toList())))
    }

    suspend fun pullProgress(): Pair<Map<String, Long>, Set<String>> {
        val done = (call("GET", "/rest/v1/progress?select=lesson_id,completed_at") as JSONArray).objects()
            .associate { it.getString("lesson_id") to parseIso(it.getString("completed_at")) }
        val saved = (call("GET", "/rest/v1/saved_lessons?select=lesson_id") as JSONArray).objects()
            .map { it.getString("lesson_id") }.toSet()
        return done to saved
    }

    // ---------- HTTP ----------

    private suspend fun call(method: String, path: String, body: Any? = null, auth: Boolean = true, prefer: String? = null): Any? =
        withContext(Dispatchers.IO) {
            check(isConfigured) { "Сервер не настроен" }
            val token = if (auth) accessToken() else null
            val conn = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("apikey", anonKey)
                setRequestProperty("Authorization", "Bearer ${token ?: anonKey}")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                prefer?.let { setRequestProperty("Prefer", it) }
                if (body != null && method != "GET") {
                    doOutput = true
                    outputStream.use { it.write(body.toString().toByteArray()) }
                }
            }
            try {
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw IOException("HTTP $code: ${text.take(200)}")
                when {
                    text.isBlank() -> null
                    text.trimStart().startsWith("[") -> JSONArray(text)
                    else -> JSONObject(text)
                }
            } finally {
                conn.disconnect()
            }
        }

    private fun isoUtc(ms: Long) = java.time.Instant.ofEpochMilli(ms).toString()
    private fun parseIso(s: String) = runCatching { java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli() }
        .getOrDefault(System.currentTimeMillis())
}
