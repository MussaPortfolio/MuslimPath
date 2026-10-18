package com.muslimpath.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.muslimpath.app.R
import com.muslimpath.app.data.Content
import com.muslimpath.app.ui.components.IconBadge
import com.muslimpath.app.ui.components.VSpace
import com.muslimpath.app.ui.components.iconFor

@Composable
fun OnboardingScreen(content: Content, onFinish: (routeId: String?, name: String) -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    var route by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().imePadding()
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).padding(start = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { i ->
                    Box(
                        Modifier.height(6.dp).width(if (i == page) 22.dp else 6.dp).clip(CircleShape)
                            .background(if (i <= page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                    )
                }
            }
            if (page < 2) TextButton(onClick = { onFinish(null, name) }) { Text("Пропустить") }
        }

        AnimatedContent(page, Modifier.weight(1f), label = "onboarding") { p ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                when (p) {
                    0 -> Welcome()
                    1 -> Features()
                    else -> ChooseRoute(content, route, { route = it }, name, { name = it })
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (page > 0) TextButton(onClick = { page-- }) { Text("Назад") }
            Spacer(Modifier.weight(1f))
            Button(onClick = { if (page < 2) page++ else onFinish(route, name) }) {
                Text(
                    when {
                        page < 2 -> "Далее"
                        route == null -> "Начать без выбора"
                        else -> "Начать обучение"
                    }
                )
            }
        }
    }
}

@Composable
private fun Welcome() {
    VSpace(16)
    Image(
        painterResource(R.drawable.logo_full),
        contentDescription = "Логотип «Путь»",
        modifier = Modifier.fillMaxWidth().height(260.dp),
    )
    VSpace(24)
    Text(
        "Ассаляму алейкум!",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    VSpace(12)
    Text(
        "«Путь» помогает спокойно и последовательно изучать основы Ислама: короткие уроки, проверенные материалы и люди, которые готовы помочь.",
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    VSpace(12)
    Text(
        "Регистрация не нужна — можно начать прямо сейчас.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Features() {
    VSpace(24)
    Text("Что есть в приложении", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
    VSpace(20)
    Feature(Icons.Outlined.AutoStories, "Маршрут обучения", "Приложение подскажет следующий шаг — не нужно разбираться в сотнях материалов самому.")
    Feature(Icons.Outlined.VerifiedUser, "Проверенные материалы", "У каждого урока указаны источники и статус проверки специалистом.")
    Feature(Icons.Outlined.Forum, "Вопросы и ответы", "Найдите ответ в базе или задайте вопрос команде. Ответы дают люди, а не ИИ.")
    Feature(Icons.Outlined.Schedule, "Коран и время намаза", "Чтение с переводом и аудио, расписание намазов с оповещениями.")
    Feature(Icons.Outlined.Lock, "Только для вас", "Прогресс личный: никаких рейтингов и сравнений с другими.")
}

@Composable
private fun Feature(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String) {
    Row(Modifier.padding(vertical = 10.dp)) {
        IconBadge(icon)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChooseRoute(content: Content, selected: String?, onSelect: (String) -> Unit, name: String, onName: (String) -> Unit) {
    VSpace(24)
    Text("С чего хотите начать?", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
    VSpace(6)
    Text(
        "Маршрут можно поменять в любой момент.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    VSpace(16)
    content.routes.forEach { r ->
        val isSel = r.id == selected
        Card(
            onClick = { onSelect(r.id) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
            ),
            border = BorderStroke(if (isSel) 2.dp else 1.dp, if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(iconFor(r.icon), Modifier.size(44.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(r.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    VSpace(20)
    OutlinedTextField(
        value = name,
        onValueChange = { onName(it.take(40)) },
        label = { Text("Как к вам обращаться? (необязательно)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    VSpace(8)
}
