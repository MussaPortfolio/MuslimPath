package com.muslimpath.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.muslimpath.app.AppViewModel
import com.muslimpath.app.BuildConfig
import com.muslimpath.app.data.ReminderFrequency
import com.muslimpath.app.data.UserState
import com.muslimpath.app.ui.Nav
import com.muslimpath.app.ui.Tab
import com.muslimpath.app.ui.components.AppCard
import com.muslimpath.app.ui.components.ClickableRow
import com.muslimpath.app.ui.components.EmptyHint
import com.muslimpath.app.ui.components.FormScreen
import com.muslimpath.app.ui.components.LessonRow
import com.muslimpath.app.ui.components.LessonState
import com.muslimpath.app.ui.components.ListScreen
import com.muslimpath.app.ui.components.SectionTitle
import com.muslimpath.app.ui.components.VSpace
import com.muslimpath.app.ui.components.formatDate

@Composable
fun ProfileScreen(vm: AppViewModel, state: UserState, nav: Nav) {
    val email by vm.accountEmail.collectAsStateWithLifecycle()
    val route = vm.content.route(state.routeId)
    val next = vm.nextLesson(state)
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    ListScreen(title = "Профиль") {
        item {
            Text(state.name.ifBlank { "Гость" }, style = MaterialTheme.typography.headlineSmall)
            Text(
                email ?: "Гостевой режим — прогресс хранится на этом устройстве",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            VSpace(16)
            // Personal progress only — no ratings or comparisons (ТЗ 3.7).
            AppCard {
                Row(Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Stat(state.completed.size.toString(), plural(state.completed.size, "урок пройден", "урока пройдено", "уроков пройдено"), Modifier.weight(1f))
                    Stat(state.saved.size.toString(), "сохранено", Modifier.weight(1f))
                    Stat(route?.let { "${it.lessonIds.count { id -> id in state.completed }}/${it.lessonIds.size}" } ?: "—", "этап маршрута", Modifier.weight(1f))
                }
            }
            VSpace(8)
            Text(
                "Текущий маршрут: ${route?.title ?: "не выбран"}" + (next?.let { "\nСледующий урок: ${it.title}" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        item {
            SectionTitle("Обучение")
            ClickableRow("Сменить маршрут", route?.title, Icons.Outlined.Route, onClick = { nav.tab(Tab.Learn) }, trailing = { Arrow() })
            ClickableRow("Сохранённые уроки", "${state.saved.size}", Icons.Outlined.Bookmark, onClick = nav::saved, trailing = { Arrow() })
            ClickableRow("История обучения", null, Icons.Outlined.History, onClick = nav::history, trailing = { Arrow() })
            ClickableRow("Вопросы и ответы", if (state.questions.isEmpty()) "Частые вопросы и ваши обращения" else "Ваших вопросов: ${state.questions.size}", Icons.Outlined.Forum, onClick = nav::questions, trailing = { Arrow() })

            SectionTitle("Настройки")
            ClickableRow("Время намаза", state.prayer.placeName ?: "Город не выбран", Icons.Outlined.Schedule, onClick = nav::prayer, trailing = { Arrow() })
            ClickableRow("Чтец Корана", vm.reciter().name, Icons.Outlined.RecordVoiceOver, onClick = nav::reciters, trailing = { Arrow() })
            ClickableRow("Уведомления", state.notifications.reminders.label, Icons.Outlined.Notifications, onClick = nav::notifications, trailing = { Arrow() })
            ClickableRow(
                if (email == null) "Создать аккаунт или войти" else "Аккаунт",
                if (email == null) "Чтобы сохранить прогресс между устройствами" else email,
                Icons.Outlined.AccountCircle,
                onClick = nav::account,
                trailing = { Arrow() },
            )
            Text(
                "Язык интерфейса: русский",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 42.dp, top = 4.dp),
            )

            SectionTitle("О приложении")
            ClickableRow("Политика конфиденциальности", null, Icons.Outlined.PrivacyTip, onClick = { nav.doc("privacy") }, trailing = { Arrow() })
            ClickableRow("Пользовательское соглашение", null, Icons.Outlined.Description, onClick = { nav.doc("terms") }, trailing = { Arrow() })
            ClickableRow("О проекте и проверке материалов", "Версия ${BuildConfig.VERSION_NAME}", Icons.Outlined.Info, onClick = { nav.doc("about") }, trailing = { Arrow() })
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            ClickableRow("Удалить мои данные", "Прогресс, вопросы, обращения и аккаунт", Icons.Outlined.DeleteForever, onClick = { confirmDelete = true })
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Удалить все данные?") },
        text = {
            Text(
                "Будут удалены прогресс, сохранённые уроки, история, ваши вопросы и обращения" +
                    (if (email != null) ", а также аккаунт на сервере" else "") + ". Это действие нельзя отменить."
            )
        },
        confirmButton = {
            TextButton(onClick = { confirmDelete = false; vm.deleteAllData() }) {
                Text("Удалить", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
    )
}

@Composable
private fun Arrow() = Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = MaterialTheme.colorScheme.outline)

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SavedScreen(vm: AppViewModel, state: UserState, nav: Nav) {
    val lessons = state.saved.mapNotNull(vm.content::lesson)
    ListScreen(title = "Сохранённые уроки", onBack = { nav.back() }) {
        if (lessons.isEmpty()) item { EmptyHint("Здесь появятся уроки, которые вы сохраните кнопкой-закладкой.") }
        items(lessons, key = { it.id }) { l ->
            LessonRow(l, if (l.id in state.completed) LessonState.DONE else LessonState.AVAILABLE, onClick = { nav.lesson(l.id) }, saved = true)
        }
    }
}

@Composable
fun HistoryScreen(vm: AppViewModel, state: UserState, nav: Nav) {
    ListScreen(title = "История обучения", onBack = { nav.back() }) {
        if (state.history.isEmpty()) item { EmptyHint("Пока пусто. Откройте любой урок — он появится здесь.") }
        items(state.history, key = { it.lessonId }) { h ->
            val l = vm.content.lesson(h.lessonId) ?: return@items
            Column {
                Text(
                    formatDate(h.at) + (state.completed[l.id]?.let { " · пройден" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                LessonRow(l, if (l.id in state.completed) LessonState.DONE else LessonState.AVAILABLE, onClick = { nav.lesson(l.id) }, saved = l.id in state.saved)
            }
        }
    }
}

@Composable
fun NotificationSettingsScreen(vm: AppViewModel, state: UserState, nav: Nav) {
    val n = state.notifications
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun setReminders(f: ReminderFrequency) {
        if (f != ReminderFrequency.OFF && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        vm.updateNotifications(n.copy(reminders = f))
    }

    ListScreen(title = "Уведомления", onBack = { nav.back() }) {
        item {
            Text(
                "Мы присылаем только мягкие напоминания и никогда не используем чувство вины или давление.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SectionTitle("Напоминать продолжить обучение")
            ReminderFrequency.entries.forEach { f ->
                Row(
                    Modifier.fillMaxWidth().selectable(selected = n.reminders == f, onClick = { setReminders(f) }, role = Role.RadioButton)
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = n.reminders == f, onClick = null)
                    Text(f.label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
            SectionTitle("Другие уведомления")
            Toggle("Ответы на мои вопросы и обращения", n.answers) { vm.updateNotifications(n.copy(answers = it)) }
            Toggle("Новые материалы", n.newMaterials) { vm.updateNotifications(n.copy(newMaterials = it)) }
            Toggle("Обновления учебного маршрута", n.routeUpdates) { vm.updateNotifications(n.copy(routeUpdates = it)) }
        }
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = value, onClick = { onChange(!value) }, role = Role.Switch).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = value, onCheckedChange = null)
    }
}

@Composable
fun AccountScreen(vm: AppViewModel, nav: Nav) {
    val email by vm.accountEmail.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableStateOf("register") }
    var login by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    FormScreen(title = "Аккаунт", onBack = { nav.back() }) {
        if (!vm.serverAvailable) {
            VSpace(12)
            Text(
                "Синхронизация между устройствами станет доступна после подключения сервера. Сейчас все данные надёжно хранятся на этом устройстве, и приложением можно полноценно пользоваться без регистрации.",
                style = MaterialTheme.typography.bodyLarge,
            )
            return@FormScreen
        }
        if (email != null) {
            VSpace(12)
            Text("Вы вошли как", style = MaterialTheme.typography.labelLarge)
            Text(email!!, style = MaterialTheme.typography.titleLarge)
            VSpace(8)
            Text("Прогресс и сохранённые уроки синхронизируются между устройствами.", style = MaterialTheme.typography.bodyMedium)
            VSpace(16)
            OutlinedButton(onClick = { vm.signOut() }) { Text("Выйти") }
            return@FormScreen
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.FilterChip(mode == "register", { mode = "register" }, { Text("Создать аккаунт") })
            androidx.compose.material3.FilterChip(mode == "login", { mode = "login" }, { Text("Войти") })
        }
        VSpace(12)
        Text(
            if (mode == "register") "Аккаунт нужен только для сохранения прогресса между устройствами. Мы попросим лишь e-mail и пароль."
            else "Войдите, чтобы восстановить прогресс с другого устройства.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        VSpace(12)
        OutlinedTextField(
            login, { login = it.trim() }, label = { Text("E-mail") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(),
        )
        VSpace(8)
        OutlinedTextField(
            password, { password = it }, label = { Text("Пароль") }, singleLine = true,
            supportingText = { if (mode == "register") Text("Не менее 8 символов") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(),
        )
        VSpace(16)
        Button(
            onClick = { if (mode == "register") vm.register(login, password) else vm.signIn(login, password) },
            enabled = "@" in login && password.length >= 8,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (mode == "register") "Создать аккаунт" else "Войти") }
        VSpace(8)
        TextButton(onClick = { nav.doc("privacy") }) { Text("Как мы обрабатываем данные") }
    }
}
