package com.muslimpath.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Mosque
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muslimpath.app.data.Lesson
import com.muslimpath.app.data.ReviewStatus
import com.muslimpath.app.data.TicketStatus

/** Maps icon keys used in content.json to Material icons. */
fun iconFor(key: String): ImageVector = when (key) {
    "faith" -> Icons.Outlined.Favorite
    "worship" -> Icons.Outlined.SelfImprovement
    "wudu" -> Icons.Outlined.WaterDrop
    "prayer" -> Icons.Outlined.Mosque
    "quran" -> Icons.AutoMirrored.Outlined.MenuBook
    "dua" -> Icons.Outlined.Spa
    "ethics" -> Icons.Outlined.Star
    "sira" -> Icons.Outlined.WbSunny
    "faq" -> Icons.AutoMirrored.Outlined.HelpOutline
    "route" -> Icons.Outlined.Route
    else -> Icons.Outlined.AutoStories
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, container: Color = MaterialTheme.colorScheme.primaryContainer, tint: Color = MaterialTheme.colorScheme.primary) {
    Box(modifier.size(44.dp).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = colors, border = border) { content() }
    } else {
        Card(modifier = modifier.fillMaxWidth(), colors = colors, border = border) { content() }
    }
}

enum class LessonState { DONE, NEXT, AVAILABLE }

@Composable
fun LessonRow(
    lesson: Lesson,
    state: LessonState,
    onClick: () -> Unit,
    index: Int? = null,
    saved: Boolean = false,
) {
    AppCard(Modifier.padding(vertical = 4.dp), onClick = onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            val (icon, tint) = when (state) {
                LessonState.DONE -> Icons.Outlined.CheckCircle to MaterialTheme.colorScheme.primary
                LessonState.NEXT -> Icons.Outlined.Route to MaterialTheme.colorScheme.secondary
                LessonState.AVAILABLE -> Icons.Outlined.RadioButtonUnchecked to MaterialTheme.colorScheme.outline
            }
            Icon(icon, contentDescription = when (state) {
                LessonState.DONE -> "Пройден"; LessonState.NEXT -> "Следующий"; else -> "Доступен"
            }, tint = tint)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    (index?.let { "$it. " } ?: "") + lesson.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    lesson.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("${lesson.minutes} мин", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (saved) Icon(Icons.Outlined.Bookmark, contentDescription = "Сохранено", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun ReviewBadge(status: ReviewStatus) {
    val verified = status == ReviewStatus.VERIFIED
    Pill(
        text = status.label,
        icon = if (verified) Icons.Outlined.VerifiedUser else Icons.Outlined.HourglassEmpty,
        container = if (verified) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        content = if (verified) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
    )
}

@Composable
fun StatusPill(status: TicketStatus) {
    val good = status == TicketStatus.ANSWERED || status == TicketStatus.ASSIGNED
    Pill(
        text = status.label,
        container = if (good) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        content = if (good) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun Pill(text: String, icon: ImageVector? = null, container: Color, content: Color) {
    Surface(color = container, contentColor = content, shape = CircleShape) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
    )
}

@Composable
fun ClickableRow(title: String, subtitle: String? = null, icon: ImageVector, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

@Composable
fun VSpace(h: Int) = Spacer(Modifier.height(h.dp))

fun formatDate(ms: Long): String =
    java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.forLanguageTag("ru")).format(java.util.Date(ms))

/** "2026-10-18" -> "18 октября 2026". */
fun formatIsoDate(iso: String?): String? = iso?.let {
    runCatching {
        val d = java.time.LocalDate.parse(it)
        d.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale.forLanguageTag("ru")))
    }.getOrDefault(it)
}
