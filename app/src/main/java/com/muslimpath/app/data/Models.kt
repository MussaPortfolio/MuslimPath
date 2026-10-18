package com.muslimpath.app.data

/** A learning route (траектория) — an ordered sequence of lessons. */
data class LearningRoute(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val lessonIds: List<String>,
)

data class Category(val id: String, val title: String, val icon: String)

data class Source(val title: String, val reference: String)

/** Religious review metadata (ТЗ п. 3.3, 8). */
data class Review(
    val status: ReviewStatus,
    val reviewer: String?,
    val reviewedAt: String?,
    val editor: String?,
)

enum class ReviewStatus(val label: String) {
    DRAFT("Черновик"),
    PENDING("Ожидает проверки специалистом"),
    VERIFIED("Проверено специалистом");

    companion object {
        fun parse(s: String?) = entries.firstOrNull { it.name.equals(s, ignoreCase = true) } ?: DRAFT
    }
}

sealed interface LessonBlock {
    data class Text(val text: String) : LessonBlock
    data class Heading(val text: String) : LessonBlock
    data class Steps(val items: List<String>) : LessonBlock
    data class Bullets(val items: List<String>) : LessonBlock
    data class Note(val text: String) : LessonBlock
    data class Arabic(val arabic: String, val transliteration: String?, val translation: String?) : LessonBlock
}

data class QuizQuestion(val question: String, val options: List<String>, val answer: Int, val explanation: String?)

data class Lesson(
    val id: String,
    val categoryId: String,
    val title: String,
    val summary: String,
    val minutes: Int,
    val blocks: List<LessonBlock>,
    val quiz: List<QuizQuestion>,
    val sources: List<Source>,
    val review: Review,
    val updatedAt: String,
    val related: List<String>,
)

data class Faq(
    val id: String,
    val question: String,
    val answer: String,
    val categoryId: String,
    val popular: Boolean,
    val sources: List<Source>,
    val review: Review,
)

data class Content(
    val routes: List<LearningRoute>,
    val categories: List<Category>,
    val lessons: List<Lesson>,
    val faqs: List<Faq>,
) {
    private val lessonById = lessons.associateBy { it.id }
    fun lesson(id: String) = lessonById[id]
    fun route(id: String?) = routes.firstOrNull { it.id == id }
    fun category(id: String?) = categories.firstOrNull { it.id == id }
}

// ---- User-side records (stored locally; synced to the server when an account exists) ----

enum class TicketStatus(val label: String) {
    QUEUED("Ожидает отправки"),
    SENT("Отправлено"),
    IN_REVIEW("Рассматривается"),
    ASSIGNED("Передан специалисту"),
    ANSWERED("Получен ответ"),
    CLOSED("Закрыто");

    companion object {
        fun parse(s: String?) = entries.firstOrNull { it.name.equals(s, ignoreCase = true) } ?: QUEUED
    }
}

data class UserQuestion(
    val id: String,
    val text: String,
    val categoryId: String?,
    val lessonId: String?,
    val createdAt: Long,
    val status: TicketStatus,
    val answer: String?,
    val rating: Int?, // 1..5 — «понятность ответа»
)


enum class ReminderFrequency(val label: String, val days: Int) {
    OFF("Не напоминать", 0),
    DAILY("Каждый день", 1),
    EVERY_3_DAYS("Раз в 3 дня", 3),
    WEEKLY("Раз в неделю", 7),
}

data class NotificationSettings(
    val reminders: ReminderFrequency = ReminderFrequency.OFF,
    val newMaterials: Boolean = true,
    val answers: Boolean = true,
    val routeUpdates: Boolean = true,
)

data class HistoryEntry(val lessonId: String, val at: Long)

data class UserState(
    val onboardingDone: Boolean = false,
    val name: String = "",
    val routeId: String? = null,
    val completed: Map<String, Long> = emptyMap(),
    val quizScores: Map<String, Int> = emptyMap(),
    val saved: Set<String> = emptySet(),
    val history: List<HistoryEntry> = emptyList(),
    val questions: List<UserQuestion> = emptyList(),
    val notifications: NotificationSettings = NotificationSettings(),
    val textScale: Float = 1f,
    val quran: QuranPrefs = QuranPrefs(),
    val prayer: PrayerSettings = PrayerSettings(),
)

/** Prayer-time settings. Coordinates stay on the device and are never sent to the server. */
enum class PrayerSource { CALCULATED, KBR }

data class PrayerSettings(
    val source: PrayerSource = PrayerSource.CALCULATED,
    val placeName: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val zone: String = java.time.ZoneId.systemDefault().id,
    val method: CalcMethod = CalcMethod.MWL,
    val school: AsrSchool = AsrSchool.HANAFI,
    val notify: Set<Prayer> = emptySet(),
    val notifyBeforeMin: Int = 0,
    val adjustments: Map<Prayer, Int> = emptyMap(), // manual corrections in minutes, to match the local mosque
) {
    val hasLocation get() = lat != null && lon != null
}

enum class QuranMode(val label: String) { BOTH("Текст и перевод"), ARABIC("Только арабский"), TRANSLATION("Только перевод") }

/** Где пользователь остановился в Коране и его закладки ("сура:аят"). */
data class QuranPrefs(
    val lastSura: Int? = null,
    val lastAyah: Int = 1,
    val bookmarks: List<String> = emptyList(),
    val mode: QuranMode = QuranMode.BOTH,
    val translationId: String = "ru.krachkovsky",
    val reciterId: String = "alafasy",
)
