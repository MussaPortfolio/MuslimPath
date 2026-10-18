package com.muslimpath.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local, offline-first storage of everything the user does (ТЗ 6.4: сбой сети не должен
 * приводить к потере прогресса). Every change is written synchronously with `commit()`.
 * Nothing here leaves the device unless the user submits a question/request or signs in.
 */
class UserStore(context: Context) {

    private val prefs = context.getSharedPreferences("user_store", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<UserState> = _state.asStateFlow()

    fun edit(transform: (UserState) -> UserState) {
        _state.update { old ->
            transform(old).also { prefs.edit().putString(KEY, serialize(it).toString()).commit() }
        }
    }

    /** Right to erase (ТЗ 6.3): wipes all locally stored personal data. */
    fun clear() {
        prefs.edit().clear().commit()
        _state.value = UserState()
    }

    private fun read(): UserState {
        val raw = prefs.getString(KEY, null) ?: return UserState()
        return runCatching { deserialize(JSONObject(raw)) }.getOrElse { UserState() }
    }

    private companion object {
        const val KEY = "state_v1"

        fun serialize(s: UserState) = JSONObject().apply {
            put("onboardingDone", s.onboardingDone)
            put("name", s.name)
            put("routeId", s.routeId)
            put("completed", JSONObject(s.completed as Map<*, *>))
            put("quizScores", JSONObject(s.quizScores as Map<*, *>))
            put("saved", JSONArray(s.saved.toList()))
            put("history", JSONArray(s.history.map { JSONObject().put("l", it.lessonId).put("t", it.at) }))
            put("questions", JSONArray(s.questions.map {
                JSONObject().put("id", it.id).put("text", it.text).put("cat", it.categoryId)
                    .put("lesson", it.lessonId).put("at", it.createdAt).put("status", it.status.name)
                    .put("answer", it.answer).put("rating", it.rating)
            }))
            put("notif", JSONObject().put("rem", s.notifications.reminders.name)
                .put("new", s.notifications.newMaterials).put("ans", s.notifications.answers)
                .put("route", s.notifications.routeUpdates))
            put("textScale", s.textScale.toDouble())
            put("prayer", JSONObject().put("source", s.prayer.source.name).put("place", s.prayer.placeName).put("lat", s.prayer.lat).put("lon", s.prayer.lon)
                .put("zone", s.prayer.zone).put("method", s.prayer.method.name).put("school", s.prayer.school.name)
                .put("notify", JSONArray(s.prayer.notify.map { it.name })).put("before", s.prayer.notifyBeforeMin)
                .put("adhan", s.prayer.adhan)
                .put("adj", JSONObject(s.prayer.adjustments.mapKeys { it.key.name } as Map<*, *>)))
            put("quran", JSONObject().put("sura", s.quran.lastSura).put("ayah", s.quran.lastAyah)
                .put("bookmarks", JSONArray(s.quran.bookmarks)).put("mode", s.quran.mode.name).put("tr", s.quran.translationId).put("reciter", s.quran.reciterId))
        }

        fun deserialize(o: JSONObject): UserState {
            fun longMap(key: String) = o.optJSONObject(key)?.let { m -> m.keys().asSequence().associateWith { m.getLong(it) } } ?: emptyMap()
            val notif = o.optJSONObject("notif")
            return UserState(
                onboardingDone = o.optBoolean("onboardingDone"),
                name = o.optString("name"),
                routeId = o.optStringOrNull("routeId"),
                completed = longMap("completed"),
                quizScores = longMap("quizScores").mapValues { it.value.toInt() },
                saved = o.optJSONArray("saved")?.strings()?.toSet() ?: emptySet(),
                history = o.optJSONArray("history")?.objects()?.map { HistoryEntry(it.getString("l"), it.getLong("t")) } ?: emptyList(),
                questions = o.optJSONArray("questions")?.objects()?.map {
                    UserQuestion(
                        id = it.getString("id"),
                        text = it.getString("text"),
                        categoryId = it.optStringOrNull("cat"),
                        lessonId = it.optStringOrNull("lesson"),
                        createdAt = it.getLong("at"),
                        status = TicketStatus.parse(it.optString("status")),
                        answer = it.optStringOrNull("answer"),
                        rating = if (it.has("rating") && !it.isNull("rating")) it.getInt("rating") else null,
                    )
                } ?: emptyList(),
                notifications = NotificationSettings(
                    reminders = runCatching { ReminderFrequency.valueOf(notif?.optString("rem") ?: "") }.getOrDefault(ReminderFrequency.OFF),
                    newMaterials = notif?.optBoolean("new", true) ?: true,
                    answers = notif?.optBoolean("ans", true) ?: true,
                    routeUpdates = notif?.optBoolean("route", true) ?: true,
                ),
                textScale = o.optDouble("textScale", 1.0).toFloat(),
                prayer = o.optJSONObject("prayer")?.let { p ->
                    PrayerSettings(
                        source = runCatching { PrayerSource.valueOf(p.optString("source")) }.getOrDefault(PrayerSource.CALCULATED),
                        placeName = p.optStringOrNull("place"),
                        lat = if (p.has("lat") && !p.isNull("lat")) p.getDouble("lat") else null,
                        lon = if (p.has("lon") && !p.isNull("lon")) p.getDouble("lon") else null,
                        zone = p.optString("zone", java.time.ZoneId.systemDefault().id),
                        method = runCatching { CalcMethod.valueOf(p.optString("method")) }.getOrDefault(CalcMethod.MWL),
                        school = runCatching { AsrSchool.valueOf(p.optString("school")) }.getOrDefault(AsrSchool.HANAFI),
                        notify = p.optJSONArray("notify")?.strings()?.mapNotNull { n -> runCatching { Prayer.valueOf(n) }.getOrNull() }?.toSet() ?: emptySet(),
                        notifyBeforeMin = p.optInt("before", 0),
                        adhan = p.optBoolean("adhan", false),
                        adjustments = p.optJSONObject("adj")?.let { a ->
                            a.keys().asSequence().mapNotNull { k -> runCatching { Prayer.valueOf(k) to a.getInt(k) }.getOrNull() }.toMap()
                        } ?: emptyMap(),
                    )
                }?.let { p ->
                    if (p.source == PrayerSource.CALCULATED && p.placeName == "Нальчик") p.withCity(CITIES.first { it.name == "Нальчик" }) else p
                } ?: PrayerSettings(),
                quran = o.optJSONObject("quran")?.let { q ->
                    QuranPrefs(
                        lastSura = if (q.has("sura") && !q.isNull("sura")) q.getInt("sura") else null,
                        lastAyah = q.optInt("ayah", 1),
                        bookmarks = q.optJSONArray("bookmarks")?.strings() ?: emptyList(),
                        mode = runCatching { QuranMode.valueOf(q.optString("mode")) }.getOrDefault(QuranMode.BOTH),
                        translationId = q.optString("tr", "ru.krachkovsky"),
                        reciterId = q.optString("reciter", "alafasy"),
                    )
                } ?: QuranPrefs(),
            )
        }
    }
}
