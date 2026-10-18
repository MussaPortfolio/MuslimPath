package com.muslimpath.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.muslimpath.app.data.Ayah
import com.muslimpath.app.data.QuranAudio
import com.muslimpath.app.data.QuranMode
import com.muslimpath.app.data.QuranRepository
import com.muslimpath.app.data.Content
import com.muslimpath.app.data.ContentRepository
import com.muslimpath.app.data.HistoryEntry
import com.muslimpath.app.data.Lesson
import com.muslimpath.app.data.NotificationSettings
import com.muslimpath.app.data.SupabaseClient
import com.muslimpath.app.data.TicketStatus
import com.muslimpath.app.data.UserQuestion
import com.muslimpath.app.data.UserState
import com.muslimpath.app.data.UserStore
import com.muslimpath.app.notifications.Reminders
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val content: Content = ContentRepository(app).load()
    private val store = UserStore(app)
    private val remote = SupabaseClient(app)

    val state: StateFlow<UserState> = store.state

    val quranRepo = QuranRepository(app)

    /** Quran text, loaded lazily the first time the Quran section is opened. */
    private val _quran = MutableStateFlow<Map<Int, List<Ayah>>?>(null)
    val quran: StateFlow<Map<Int, List<Ayah>>?> = _quran.asStateFlow()
    private var quranLoading = false

    fun loadQuran() {
        if (_quran.value != null || quranLoading) return
        quranLoading = true
        viewModelScope.launch {
            runCatching { quranRepo.load() }
                .onSuccess { _quran.value = it }
                .onFailure { _message.value = "Не удалось открыть текст Корана" }
            quranLoading = false
        }
    }

    fun setQuranPosition(sura: Int, ayah: Int) {
        val q = state.value.quran
        if (q.lastSura == sura && q.lastAyah == ayah) return
        store.edit { it.copy(quran = it.quran.copy(lastSura = sura, lastAyah = ayah)) }
    }

    fun toggleAyahBookmark(sura: Int, ayah: Int) = store.edit {
        val key = "$sura:$ayah"
        val list = it.quran.bookmarks
        it.copy(quran = it.quran.copy(bookmarks = if (key in list) list - key else listOf(key) + list))
    }

    val audio = QuranAudio.get(app)

    fun reciter() = audio.reciters.firstOrNull { it.id == state.value.quran.reciterId } ?: audio.reciters.first()

    fun setReciter(id: String) {
        store.edit { it.copy(quran = it.quran.copy(reciterId = id)) }
        // Restart the current recitation with the new voice from the same ayah.
        val s = audio.state.value
        if (s.sura != null && s.sura > 0 && s.playing) playSura(s.sura, (s.ayah ?: 1).coerceAtLeast(1))
    }

    fun playSura(sura: Int, fromAyah: Int = 1) {
        val info = quranRepo.suras[sura - 1]
        audio.play(reciter(), sura, info.ayahCount, fromAyah)
    }

    override fun onCleared() {
        audio.stop()
    }

    // ---------- Prayer times ----------

    fun updatePrayer(transform: (com.muslimpath.app.data.PrayerSettings) -> com.muslimpath.app.data.PrayerSettings) {
        store.edit { it.copy(prayer = transform(it.prayer)) }
        com.muslimpath.app.notifications.PrayerAlarms.reschedule(getApplication())
        refreshOfficialSchedule()
    }

    /** Pulls the latest daily post from kbrdum.ru when the bundled timetable doesn't cover today/tomorrow. */
    fun refreshOfficialSchedule() {
        if (state.value.prayer.source != com.muslimpath.app.data.PrayerSource.KBR) return
        viewModelScope.launch {
            if (com.muslimpath.app.data.OfficialSchedules.refreshKbr(getApplication())) {
                com.muslimpath.app.notifications.PrayerAlarms.reschedule(getApplication())
            }
        }
    }

    fun setQuranMode(mode: QuranMode) = store.edit { it.copy(quran = it.quran.copy(mode = mode)) }

    /** Search in the translation text and sura names. */
    fun searchQuran(query: String): List<Ayah> {
        val q = query.trim().lowercase()
        val text = _quran.value ?: return emptyList()
        if (q.length < 3) return emptyList()
        return text.values.asSequence().flatten().filter { it.translation.lowercase().contains(q) }.take(100).toList()
    }

    /** One-shot user-visible messages (snackbar). */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun messageShown() { _message.value = null }

    private val _account = MutableStateFlow(remote.email)
    val accountEmail: StateFlow<String?> = _account.asStateFlow()
    val serverAvailable get() = remote.isConfigured

    init {
        com.muslimpath.app.data.OfficialSchedules.init(app)
        refreshOfficialSchedule()
        refreshFromServer()
    }

    // ---------- Onboarding & route ----------

    fun finishOnboarding(routeId: String?, name: String) = store.edit {
        it.copy(onboardingDone = true, routeId = routeId ?: it.routeId, name = name.trim())
    }

    fun selectRoute(routeId: String) = store.edit { it.copy(routeId = routeId) }

    /** First not-yet-completed lesson of the current route — «следующий шаг». */
    fun nextLesson(s: UserState): Lesson? {
        val route = content.route(s.routeId) ?: return content.lessons.firstOrNull { it.id !in s.completed }
        return route.lessonIds.firstOrNull { it !in s.completed }?.let(content::lesson)
    }

    // ---------- Lessons ----------

    fun openLesson(id: String) = store.edit {
        it.copy(history = (listOf(HistoryEntry(id, now())) + it.history.filter { h -> h.lessonId != id }).take(100))
    }

    fun completeLesson(id: String, quizScore: Int?) {
        store.edit {
            it.copy(
                completed = if (id in it.completed) it.completed else it.completed + (id to now()),
                quizScores = if (quizScore != null) it.quizScores + (id to quizScore) else it.quizScores,
            )
        }
        syncProgress()
    }

    fun toggleSaved(id: String) {
        store.edit { it.copy(saved = if (id in it.saved) it.saved - id else it.saved + id) }
        syncProgress()
    }

    fun setTextScale(scale: Float) = store.edit { it.copy(textScale = scale.coerceIn(0.85f, 1.6f)) }

    fun search(query: String): Pair<List<Lesson>, List<com.muslimpath.app.data.Faq>> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList<Lesson>() to emptyList()
        val lessons = content.lessons.filter { l ->
            l.title.lowercase().contains(q) || l.summary.lowercase().contains(q) ||
                l.blocks.any { it.toString().lowercase().contains(q) }
        }
        val faqs = content.faqs.filter { it.question.lowercase().contains(q) || it.answer.lowercase().contains(q) }
        return lessons to faqs
    }

    // ---------- Questions ----------

    fun askQuestion(text: String, categoryId: String?, lessonId: String?) {
        val q = UserQuestion(UUID.randomUUID().toString(), text.trim(), categoryId, lessonId, now(), TicketStatus.QUEUED, null, null)
        store.edit { it.copy(questions = listOf(q) + it.questions) }
        sendPending()
    }

    fun rateAnswer(id: String, rating: Int) {
        store.edit { s -> s.copy(questions = s.questions.map { if (it.id == id) it.copy(rating = rating) else it }) }
        if (remote.isConfigured) viewModelScope.launch { runCatching { remote.rateAnswer(id, rating) } }
    }

    // ---------- Settings ----------

    fun updateNotifications(n: NotificationSettings) {
        store.edit { it.copy(notifications = n) }
        Reminders.schedule(getApplication(), n.reminders)
    }

    fun register(email: String, password: String) = viewModelScope.launch {
        runCatching { remote.register(email.trim(), password) }
            .onSuccess { _account.value = remote.email; _message.value = "Аккаунт создан. Прогресс будет синхронизироваться."; syncProgress() }
            .onFailure { _message.value = "Не удалось создать аккаунт: ${it.message}" }
    }

    fun signIn(email: String, password: String) = viewModelScope.launch {
        runCatching { remote.signIn(email.trim(), password); remote.pullProgress() }
            .onSuccess { (done, saved) ->
                store.edit { it.copy(completed = done + it.completed, saved = it.saved + saved, onboardingDone = true) }
                _account.value = remote.email
                _message.value = "Вход выполнен, прогресс восстановлен"
                syncProgress()
            }
            .onFailure { _message.value = "Не удалось войти: ${it.message}" }
    }

    fun signOut() {
        remote.signOut()
        _account.value = null
    }

    /** Deletes local data and, if an account exists, requests server-side deletion. */
    fun deleteAllData() = viewModelScope.launch {
        if (remote.isConfigured) runCatching { remote.deleteAccount() }
            .onFailure { _message.value = "Локальные данные удалены. Сервер недоступен — повторите удаление аккаунта позже." }
        remote.signOut()
        store.clear()
        _account.value = null
        Reminders.schedule(getApplication(), com.muslimpath.app.data.ReminderFrequency.OFF)
        com.muslimpath.app.notifications.PrayerAlarms.reschedule(getApplication())
    }

    // ---------- Sync ----------

    private fun sendPending() {
        if (!remote.isConfigured) return
        viewModelScope.launch {
            val s = state.value
            s.questions.filter { it.status == TicketStatus.QUEUED }.forEach { q ->
                runCatching { remote.submitQuestion(q) }.onSuccess { setQuestionStatus(q.id, TicketStatus.SENT) }
            }
        }
    }

    private fun setQuestionStatus(id: String, status: TicketStatus) =
        store.edit { s -> s.copy(questions = s.questions.map { if (it.id == id) it.copy(status = status) else it }) }

    fun refreshFromServer() {
        if (!remote.isConfigured) return
        sendPending()
        viewModelScope.launch {
            if (state.value.questions.any { it.status != TicketStatus.QUEUED }) {
                runCatching { remote.fetchQuestionUpdates() }.onSuccess { updates ->
                    store.edit { s ->
                        s.copy(questions = s.questions.map { q ->
                            updates[q.id]?.let { (st, ans) -> q.copy(status = st, answer = ans ?: q.answer) } ?: q
                        })
                    }
                }
            }
        }
    }

    private fun syncProgress() {
        if (!remote.isConfigured || !remote.isRegistered) return
        viewModelScope.launch { runCatching { remote.pushProgress(state.value.completed, state.value.saved) } }
    }

    private fun now() = System.currentTimeMillis()
}
