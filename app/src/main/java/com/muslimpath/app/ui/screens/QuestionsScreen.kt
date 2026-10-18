package com.muslimpath.app.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.muslimpath.app.data.TicketStatus
import com.muslimpath.app.data.UserQuestion
import com.muslimpath.app.data.UserState
import com.muslimpath.app.ui.Nav
import com.muslimpath.app.ui.components.AppCard
import com.muslimpath.app.ui.components.EmptyHint
import com.muslimpath.app.ui.components.FormScreen
import com.muslimpath.app.ui.components.ListScreen
import com.muslimpath.app.ui.components.ReviewBadge
import com.muslimpath.app.ui.components.SectionTitle
import com.muslimpath.app.ui.components.StatusPill
import com.muslimpath.app.ui.components.VSpace
import com.muslimpath.app.ui.components.formatDate

@Composable
fun QuestionsScreen(vm: AppViewModel, state: UserState, nav: Nav) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query) { vm.search(query) }

    ListScreen(
        title = "Вопросы и ответы",
        onBack = { nav.back() },
        actions = { if (vm.serverAvailable) IconButton(onClick = vm::refreshFromServer) { Icon(Icons.Outlined.Refresh, "Обновить статусы") } },
    ) {
        item { SearchField(query, { query = it }, "Найти ответ") }

        if (query.trim().length >= 2) {
            searchResults(results.first, results.second, state, vm.nextLesson(state), nav)
            return@ListScreen
        }

        item {
            VSpace(12)
            Button(onClick = { nav.ask() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("Задать вопрос команде")
            }
        }

        if (state.questions.isNotEmpty()) {
            item { SectionTitle("Мои вопросы") }
            items(state.questions, key = { it.id }) { q -> MyQuestionCard(q, vm) }
        }

        item { SectionTitle("Популярные вопросы") }
        val popular = vm.content.faqs.filter { it.popular }.ifEmpty { vm.content.faqs }
        items(popular, key = { it.id }) { f -> FaqCard(f, initiallyOpen = false, onAsk = { nav.ask() }) }
    }
}

@Composable
fun FaqCard(faq: Faq, initiallyOpen: Boolean, onAsk: () -> Unit) {
    var open by rememberSaveable(faq.id) { mutableStateOf(initiallyOpen) }
    AppCard(Modifier.padding(vertical = 4.dp).animateContentSize(), onClick = { open = !open }) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(faq.question, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (open) "Свернуть" else "Развернуть")
            }
            if (open) {
                VSpace(8)
                Text(faq.answer, style = MaterialTheme.typography.bodyMedium)
                if (faq.sources.isNotEmpty()) {
                    VSpace(8)
                    Text(
                        "Источники: " + faq.sources.joinToString("; ") { listOf(it.title, it.reference).filter(String::isNotBlank).joinToString(", ") },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                VSpace(8)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ReviewBadge(faq.review.status)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onAsk) { Text("Уточнить") }
                }
            }
        }
    }
}

@Composable
private fun MyQuestionCard(q: UserQuestion, vm: AppViewModel) {
    AppCard(Modifier.padding(vertical = 4.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDate(q.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                StatusPill(q.status)
            }
            VSpace(6)
            Text(q.text, style = MaterialTheme.typography.bodyMedium)
            if (q.status == TicketStatus.QUEUED && !vm.serverAvailable) {
                VSpace(6)
                Text(
                    "Вопрос сохранён на устройстве и будет отправлен, когда подключится сервер.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            q.answer?.let { answer ->
                VSpace(10)
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Ответ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(answer, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                VSpace(8)
                Text("Насколько понятен ответ?", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1 to "Непонятно", 3 to "Частично", 5 to "Понятно").forEach { (v, label) ->
                        FilterChip(selected = q.rating == v, onClick = { vm.rateAnswer(q.id, v) }, label = { Text(label) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskQuestionScreen(vm: AppViewModel, nav: Nav, lessonId: String?) {
    val lesson = lessonId?.let(vm.content::lesson)
    var text by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf(lesson?.categoryId) }
    var expanded by remember { mutableStateOf(false) }
    var sent by rememberSaveable { mutableStateOf(false) }

    FormScreen(title = "Задать вопрос", onBack = { nav.back() }) {
        if (sent) {
            VSpace(24)
            Text("Спасибо, вопрос принят", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            VSpace(8)
            Text(
                "Его рассмотрит команда приложения или проверенный специалист. Статус и ответ появятся в разделе «Вопросы → Мои вопросы».",
                style = MaterialTheme.typography.bodyLarge,
            )
            VSpace(20)
            Button(onClick = { nav.back() }) { Text("Готово") }
            return@FormScreen
        }

        lesson?.let {
            Text("Вопрос к уроку «${it.title}»", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            VSpace(12)
        }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = vm.content.category(categoryId)?.title ?: "Не выбрана",
                onValueChange = {},
                readOnly = true,
                label = { Text("Тема вопроса") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                vm.content.categories.forEach { c ->
                    DropdownMenuItem(text = { Text(c.title) }, onClick = { categoryId = c.id; expanded = false })
                }
            }
        }
        VSpace(12)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(2000) },
            label = { Text("Ваш вопрос") },
            supportingText = { Text("${text.length} / 2000") },
            minLines = 5,
            modifier = Modifier.fillMaxWidth(),
        )
        VSpace(8)
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
            Text(
                "На вопросы отвечают люди — команда приложения и проверенные специалисты, а не искусственный интеллект. " +
                    "Вопросы, которые требуют индивидуальной фетвы, передаются квалифицированному специалисту. " +
                    "Пожалуйста, не указывайте в тексте лишние личные данные.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(12.dp),
            )
        }
        VSpace(16)
        Button(
            onClick = { vm.askQuestion(text, categoryId, lessonId); sent = true },
            enabled = text.trim().length >= 10,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Отправить вопрос") }
        if (text.isNotEmpty() && text.trim().length < 10) {
            Text("Опишите вопрос чуть подробнее", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
