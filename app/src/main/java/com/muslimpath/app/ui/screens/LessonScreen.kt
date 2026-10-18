package com.muslimpath.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.TextDecrease
import androidx.compose.material.icons.outlined.TextIncrease
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.muslimpath.app.AppViewModel
import com.muslimpath.app.data.Lesson
import com.muslimpath.app.data.LessonBlock
import com.muslimpath.app.data.QuizQuestion
import com.muslimpath.app.data.UserState
import com.muslimpath.app.ui.Nav
import com.muslimpath.app.ui.components.LessonRow
import com.muslimpath.app.ui.components.ListScreen
import com.muslimpath.app.ui.components.Pill
import com.muslimpath.app.ui.components.ReviewBadge
import com.muslimpath.app.ui.components.SectionTitle
import com.muslimpath.app.ui.components.VSpace
import com.muslimpath.app.ui.components.formatDate
import com.muslimpath.app.ui.components.formatIsoDate
import com.muslimpath.app.ui.theme.ArabicStyle

@Composable
fun LessonScreen(vm: AppViewModel, state: UserState, nav: Nav, lessonId: String) {
    val lesson = vm.content.lesson(lessonId) ?: return
    LaunchedEffect(lessonId) { vm.openLesson(lessonId) }
    val saved = lessonId in state.saved
    val done = state.completed[lessonId]
    val category = vm.content.category(lesson.categoryId)
    val route = vm.content.route(state.routeId)
    val nextInRoute = route?.lessonIds?.let { ids ->
        ids.indexOf(lessonId).takeIf { it >= 0 }?.let { ids.getOrNull(it + 1) }?.let(vm.content::lesson)
    }
    var quizScore by rememberSaveable(lessonId) { mutableStateOf<Int?>(null) }

    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * state.textScale)) {
        ListScreen(
            title = category?.title ?: "Урок",
            onBack = { nav.back() },
            actions = {
                IconButton(onClick = { vm.setTextScale(state.textScale - 0.1f) }) { Icon(Icons.Outlined.TextDecrease, "Уменьшить текст") }
                IconButton(onClick = { vm.setTextScale(state.textScale + 0.1f) }) { Icon(Icons.Outlined.TextIncrease, "Увеличить текст") }
                IconButton(onClick = { vm.toggleSaved(lessonId) }) {
                    Icon(
                        if (saved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                        if (saved) "Убрать из сохранённых" else "Сохранить урок",
                        tint = if (saved) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
        ) {
            item {
                Text(lesson.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                VSpace(6)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pill("${lesson.minutes} мин", container = MaterialTheme.colorScheme.surfaceVariant, content = MaterialTheme.colorScheme.onSurfaceVariant)
                    ReviewBadge(lesson.review.status)
                }
                if (done != null) {
                    VSpace(6)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Пройден ${formatDate(done)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                VSpace(12)
                Text(lesson.summary, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                VSpace(8)
            }

            items(lesson.blocks.size) { i -> Block(lesson.blocks[i]) }

            if (lesson.quiz.isNotEmpty()) item {
                SectionTitle("Проверьте себя")
                Quiz(lesson.quiz) { quizScore = it }
            }

            item {
                VSpace(20)
                if (done == null) {
                    Button(
                        onClick = { vm.completeLesson(lessonId, quizScore) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Отметить урок пройденным") }
                } else if (nextInRoute != null) {
                    Button(onClick = { nav.back(); nav.lesson(nextInRoute.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Следующий урок: ${nextInRoute.title}", maxLines = 1)
                    }
                }
                VSpace(8)
                OutlinedButton(onClick = { nav.ask(lessonId) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Forum, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Задать вопрос по уроку")
                }
            }

            item { SourcesAndReview(lesson) }

            val related = lesson.related.mapNotNull(vm.content::lesson)
            if (related.isNotEmpty()) {
                item { SectionTitle("Дополнительные материалы") }
                items(related, key = { "r" + it.id }) { l ->
                    LessonRow(l, lessonState(l, state, null), onClick = { nav.lesson(l.id) }, saved = l.id in state.saved)
                }
            }
        }
    }
}

@Composable
private fun Block(block: LessonBlock) {
    when (block) {
        is LessonBlock.Heading -> Text(
            block.text,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 18.dp, bottom = 6.dp).semantics { heading() },
        )
        is LessonBlock.Text -> Text(block.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 6.dp))
        is LessonBlock.Bullets -> Column(Modifier.padding(vertical = 6.dp)) {
            block.items.forEach {
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text("•", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.width(10.dp))
                    Text(it, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        is LessonBlock.Steps -> Column(Modifier.padding(vertical = 6.dp)) {
            block.items.forEachIndexed { i, s ->
                Row(Modifier.padding(vertical = 5.dp)) {
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) { Text("${i + 1}", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge) }
                    Spacer(Modifier.width(12.dp))
                    Text(s, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
        is LessonBlock.Note -> Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            Row(Modifier.padding(14.dp)) {
                Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(10.dp))
                Text(block.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        is LessonBlock.Arabic -> OutlinedCard(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
        ) {
            Column(Modifier.padding(16.dp)) {
                // Arabic is always laid out right-to-left regardless of UI direction.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Text(
                        block.arabic,
                        style = ArabicStyle.copy(textDirection = TextDirection.Rtl, color = MaterialTheme.colorScheme.onSurface),
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                block.transliteration?.let {
                    VSpace(8)
                    Text(it, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.primary)
                }
                block.translation?.let {
                    VSpace(6)
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun Quiz(questions: List<QuizQuestion>, onScore: (Int) -> Unit) {
    val answers = remember(questions) { mutableStateMapOf<Int, Int>() }
    var checked by remember(questions) { mutableStateOf(false) }

    questions.forEachIndexed { qi, q ->
        Text("${qi + 1}. ${q.question}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
        q.options.forEachIndexed { oi, opt ->
            val selected = answers[qi] == oi
            val correct = checked && oi == q.answer
            val wrong = checked && selected && oi != q.answer
            OutlinedCard(
                onClick = { if (!checked) answers[qi] = oi },
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                border = BorderStroke(
                    if (selected || correct) 2.dp else 1.dp,
                    when {
                        correct -> MaterialTheme.colorScheme.primary
                        wrong -> MaterialTheme.colorScheme.error
                        selected -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.outlineVariant
                    },
                ),
            ) {
                Text(opt, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (checked) q.explanation?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
    VSpace(10)
    if (!checked) {
        OutlinedButton(
            onClick = {
                checked = true
                onScore(questions.indices.count { answers[it] == questions[it].answer })
            },
            enabled = answers.size == questions.size,
        ) { Text("Проверить") }
    } else {
        val score = questions.indices.count { answers[it] == questions[it].answer }
        Text(
            if (score == questions.size) "Всё верно, ма шаа Аллах!" else "Верно $score из ${questions.size}. Можно перечитать урок и попробовать снова.",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        androidx.compose.material3.TextButton(onClick = { answers.clear(); checked = false }) { Text("Пройти ещё раз") }
    }
}

@Composable
private fun SourcesAndReview(lesson: Lesson) {
    SectionTitle("Источники")
    if (lesson.sources.isEmpty()) {
        Text("Источники будут добавлены редактором.", style = MaterialTheme.typography.bodySmall)
    }
    lesson.sources.forEach {
        Text(
            buildString { append(it.title); if (it.reference.isNotBlank()) append(" — ").append(it.reference) },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 3.dp),
        )
    }
    VSpace(12)
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    VSpace(12)
    Text("О проверке материала", style = MaterialTheme.typography.titleSmall)
    VSpace(4)
    val r = lesson.review
    val lines = listOfNotNull(
        "Статус: ${r.status.label}",
        r.reviewer?.let { "Проверил: $it" },
        formatIsoDate(r.reviewedAt)?.let { "Дата проверки: $it" },
        r.editor?.let { "Ответственный редактор: $it" },
        formatIsoDate(lesson.updatedAt)?.let { "Последнее обновление: $it" },
    )
    lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    VSpace(6)
    Text(
        "Материал носит образовательный характер и не заменяет консультацию учёного или имама. Заметили неточность — напишите нам через «Задать вопрос».",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
