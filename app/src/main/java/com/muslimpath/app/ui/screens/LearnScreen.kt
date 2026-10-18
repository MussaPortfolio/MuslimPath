package com.muslimpath.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muslimpath.app.AppViewModel
import com.muslimpath.app.data.Faq
import com.muslimpath.app.data.Lesson
import com.muslimpath.app.data.UserState
import com.muslimpath.app.ui.Nav
import com.muslimpath.app.ui.components.AppCard
import com.muslimpath.app.ui.components.EmptyHint
import com.muslimpath.app.ui.components.IconBadge
import com.muslimpath.app.ui.components.LessonRow
import com.muslimpath.app.ui.components.LessonState
import com.muslimpath.app.ui.components.ListScreen
import com.muslimpath.app.ui.components.Pill
import com.muslimpath.app.ui.components.SectionTitle
import com.muslimpath.app.ui.components.VSpace
import com.muslimpath.app.ui.components.iconFor

fun lessonState(lesson: Lesson, state: UserState, next: Lesson?) = when {
    lesson.id in state.completed -> LessonState.DONE
    lesson.id == next?.id -> LessonState.NEXT
    else -> LessonState.AVAILABLE
}

@Composable
fun SearchField(query: String, onQuery: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Outlined.Close, "Очистить") } },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun LearnScreen(vm: AppViewModel, state: UserState, nav: Nav) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    val content = vm.content
    val next = vm.nextLesson(state)
    val results = remember(query) { vm.search(query) }

    ListScreen(title = "Обучение") {
        item {
            SearchField(query, { query = it }, "Поиск по урокам и ответам")
            VSpace(8)
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Маршруты") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Библиотека") })
            }
            VSpace(8)
        }

        if (query.trim().length >= 2) {
            searchResults(results.first, results.second, state, next, nav)
            return@ListScreen
        }

        if (tab == 0) {
            items(content.routes, key = { it.id }) { r ->
                val done = r.lessonIds.count { it in state.completed }
                val current = r.id == state.routeId
                AppCard(Modifier.padding(vertical = 5.dp), onClick = { nav.route(r.id) }) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(iconFor(r.icon))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("${r.lessonIds.size} уроков", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (current) Pill("Текущий", container = MaterialTheme.colorScheme.secondaryContainer, content = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        VSpace(8)
                        Text(r.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (done > 0) {
                            VSpace(10)
                            LinearProgressIndicator(
                                progress = { done / r.lessonIds.size.toFloat() },
                                modifier = Modifier.fillMaxWidth().height(5.dp),
                                drawStopIndicator = {},
                            )
                            VSpace(4)
                            Text("Пройдено $done из ${r.lessonIds.size}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        } else {
            items(content.categories, key = { it.id }) { c ->
                val lessons = content.lessons.count { it.categoryId == c.id }
                val faqs = content.faqs.count { it.categoryId == c.id }
                AppCard(Modifier.padding(vertical = 4.dp), onClick = { nav.category(c.id) }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(iconFor(c.icon))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOfNotNull(
                                    lessons.takeIf { it > 0 }?.let { "$it ${plural(it, "урок", "урока", "уроков")}" },
                                    faqs.takeIf { it > 0 }?.let { "$it ${plural(it, "ответ", "ответа", "ответов")}" },
                                ).joinToString(" · ").ifEmpty { "Материалы готовятся" },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

fun androidx.compose.foundation.lazy.LazyListScope.searchResults(
    lessons: List<Lesson>,
    faqs: List<Faq>,
    state: UserState,
    next: Lesson?,
    nav: Nav,
    onFaq: ((Faq) -> Unit)? = null,
) {
    if (lessons.isEmpty() && faqs.isEmpty()) {
        item {
            EmptyHint("Ничего не найдено. Попробуйте другие слова или задайте вопрос команде.")
            Button(onClick = { nav.ask() }) { Text("Задать вопрос") }
        }
        return
    }
    if (lessons.isNotEmpty()) {
        item { SectionTitle("Уроки") }
        items(lessons, key = { "l" + it.id }) { l ->
            LessonRow(l, lessonState(l, state, next), onClick = { nav.lesson(l.id) }, saved = l.id in state.saved)
        }
    }
    if (faqs.isNotEmpty()) {
        item { SectionTitle("Ответы на вопросы") }
        items(faqs, key = { "f" + it.id }) { f -> FaqCard(f, initiallyOpen = false, onAsk = { nav.ask() }) }
    }
}

fun plural(n: Int, one: String, few: String, many: String): String {
    val m10 = n % 10; val m100 = n % 100
    return when {
        m10 == 1 && m100 != 11 -> one
        m10 in 2..4 && m100 !in 12..14 -> few
        else -> many
    }
}

@Composable
fun RouteScreen(vm: AppViewModel, state: UserState, nav: Nav, routeId: String) {
    val route = vm.content.route(routeId) ?: return
    val lessons = route.lessonIds.mapNotNull(vm.content::lesson)
    val isCurrent = state.routeId == route.id
    val next = if (isCurrent) vm.nextLesson(state) else lessons.firstOrNull { it.id !in state.completed }

    ListScreen(title = route.title, onBack = { nav.back() }) {
        item {
            Text(route.description, style = MaterialTheme.typography.bodyLarge)
            VSpace(12)
            if (isCurrent) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text("Это ваш текущий маршрут", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            } else {
                Button(onClick = { vm.selectRoute(route.id) }) { Text("Выбрать этот маршрут") }
            }
            VSpace(8)
            Text(
                "Уроки можно открывать в любом порядке — порядок ниже лишь рекомендация.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            VSpace(8)
        }
        itemsIndexed(lessons, key = { _, l -> l.id }) { i, l ->
            LessonRow(l, lessonState(l, state, next), onClick = { nav.lesson(l.id) }, index = i + 1, saved = l.id in state.saved)
        }
    }
}

@Composable
fun CategoryScreen(vm: AppViewModel, state: UserState, nav: Nav, categoryId: String) {
    val c = vm.content.category(categoryId) ?: return
    val lessons = vm.content.lessons.filter { it.categoryId == c.id }
    val faqs = vm.content.faqs.filter { it.categoryId == c.id }
    val next = vm.nextLesson(state)
    ListScreen(title = c.title, onBack = { nav.back() }) {
        if (lessons.isEmpty() && faqs.isEmpty()) item { EmptyHint("Материалы этого раздела готовятся и проходят проверку.") }
        items(lessons, key = { it.id }) { l ->
            LessonRow(l, lessonState(l, state, next), onClick = { nav.lesson(l.id) }, saved = l.id in state.saved)
        }
        if (faqs.isNotEmpty()) {
            item { SectionTitle("Частые вопросы") }
            items(faqs, key = { it.id }) { f -> FaqCard(f, initiallyOpen = false, onAsk = { nav.ask() }) }
        }
        item {
            VSpace(16)
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                androidx.compose.material3.TextButton(onClick = { nav.ask() }) { Text("Не нашли ответ? Задайте вопрос") }
            }
        }
    }
}
