package com.muslimpath.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muslimpath.app.AppViewModel
import com.muslimpath.app.R
import com.muslimpath.app.data.TicketStatus
import com.muslimpath.app.data.UserState
import com.muslimpath.app.ui.Nav
import com.muslimpath.app.ui.Tab
import com.muslimpath.app.ui.components.AppCard
import com.muslimpath.app.ui.components.IconBadge
import com.muslimpath.app.ui.components.ListScreen
import com.muslimpath.app.ui.components.SectionTitle
import com.muslimpath.app.ui.components.VSpace
import com.muslimpath.app.ui.theme.Green600
import com.muslimpath.app.ui.theme.Green800

@Composable
fun HomeScreen(vm: AppViewModel, state: UserState, nav: Nav) {
    val content = vm.content
    val route = content.route(state.routeId)
    val next = vm.nextLesson(state)
    val routeDone = route?.lessonIds?.count { it in state.completed } ?: 0
    val answered = state.questions.count { it.status == TicketStatus.ANSWERED && it.rating == null }

    ListScreen(title = "Путь") {
        item {
            Text(
                if (state.name.isBlank()) "Ассаляму алейкум!" else "Ассаляму алейкум, ${state.name}!",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                "Шаг за шагом, в удобном для вас темпе.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            VSpace(16)
        }

        // Next step — the main purpose of the home screen (ТЗ 4.1).
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Green800),
            ) {
                Column(
                    Modifier.background(Brush.linearGradient(listOf(Green800, Green600))).padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                route?.title ?: "Маршрут не выбран",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            )
                            VSpace(6)
                            Text(
                                when {
                                    next != null -> next.title
                                    route != null -> "Маршрут пройден"
                                    else -> "Выберите маршрут"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                color = androidx.compose.ui.graphics.Color.White,
                            )
                            if (next != null) Text(
                                "${next.minutes} мин · ${next.summary}",
                                style = MaterialTheme.typography.bodySmall,
                                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                                maxLines = 2,
                            )
                        }
                        Image(painterResource(R.drawable.logo_emblem), null, Modifier.size(64.dp))
                    }
                    if (route != null) {
                        VSpace(14)
                        LinearProgressIndicator(
                            progress = { routeDone / route.lessonIds.size.coerceAtLeast(1).toFloat() },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.25f),
                            drawStopIndicator = {},
                        )
                        VSpace(6)
                        Text(
                            "Пройдено $routeDone из ${route.lessonIds.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                        )
                    }
                    VSpace(14)
                    Button(
                        onClick = { if (next != null) nav.lesson(next.id) else nav.tab(Tab.Learn) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    ) {
                        Text(
                            when {
                                next != null && state.completed.isEmpty() -> "Начать"
                                next != null -> "Продолжить"
                                else -> "Выбрать маршрут"
                            }
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(18.dp))
                    }
                }
            }
        }

        if (route != null && next == null) item {
            VSpace(12)
            AppCard {
                Column(Modifier.padding(16.dp)) {
                    Text("Ма шаа Аллах! Вы прошли все уроки маршрута.", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Можно повторить материал или выбрать следующий маршрут.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { nav.tab(Tab.Learn) }) { Text("Другие маршруты") }
                }
            }
        }

        // Order: route, prayer, Quran, help.
        item {
            VSpace(12)
            NextPrayerCard(state, nav)
        }

        item {
            VSpace(12)
            val last = state.quran.lastSura?.let { vm.quranRepo.suras[it - 1] }
            // Opens the sura where the reader stopped, or the sura list if nothing was read yet.
            AppCard(onClick = { if (last != null) nav.sura(last.number, state.quran.lastAyah) else nav.quran() }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.AutoMirrored.Outlined.MenuBook)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Коран", style = MaterialTheme.typography.titleSmall)
                        Text(
                            last?.name ?: "Арабский текст и перевод смыслов",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            SectionTitle("Нужна помощь?")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { nav.ask() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Forum, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Задать вопрос")
                }
                OutlinedButton(onClick = nav::questions, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Outlined.HelpOutline, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Ответы")
                }
            }
        }

        if (answered > 0) item {
            VSpace(12)
            AppCard(onClick = nav::questions) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Outlined.Forum)
                    Spacer(Modifier.width(12.dp))
                    Text("На ваш вопрос пришёл ответ", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null)
                }
            }
        }
    }
}
