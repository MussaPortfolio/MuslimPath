package com.muslimpath.app.ui.screens

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface as DisplaySurface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.muslimpath.app.data.Qibla
import com.muslimpath.app.data.UserState
import com.muslimpath.app.ui.Nav
import com.muslimpath.app.ui.components.EmptyHint
import com.muslimpath.app.ui.components.ListScreen
import com.muslimpath.app.ui.components.VSpace
import com.muslimpath.app.ui.theme.Green600
import com.muslimpath.app.ui.theme.Green800
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private const val ON_TARGET_DEGREES = 3f
private val Gold = Color(0xFFE3C27A)

@Composable
fun QiblaScreen(state: UserState, nav: Nav) {
    val s = state.prayer
    val lat = s.lat
    val lon = s.lon

    ListScreen(title = "Компас", onBack = { nav.back() }) {
        if (lat == null || lon == null) {
            item {
                EmptyHint("Чтобы определить направление на Каабу, укажите город. Координаты остаются только на вашем телефоне.")
                Button(onClick = nav::prayerCity) { Text("Указать город") }
            }
            return@ListScreen
        }
        item { QiblaCompass(lat, lon, s.placeName, nav::prayerCity) }
    }
}

@Composable
private fun QiblaCompass(lat: Double, lon: Double, placeName: String?, onChangeCity: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val bearing = remember(lat, lon) { Qibla.bearing(lat, lon).toFloat() }
    val declination = remember(lat, lon) {
        GeomagneticField(lat.toFloat(), lon.toFloat(), 0f, System.currentTimeMillis()).declination
    }
    val sensors = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val sensor = remember { sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) }

    // Unwrapped so the dial turns the short way across north instead of spinning back around.
    var heading by remember { mutableFloatStateOf(0f) }
    var hasHeading by remember { mutableStateOf(false) }
    var accuracy by remember { mutableIntStateOf(SensorManager.SENSOR_STATUS_ACCURACY_HIGH) }

    if (sensor != null) LifecycleResumeEffect(sensor, declination) {
        val listener = object : SensorEventListener {
            private val matrix = FloatArray(9)
            private val remapped = FloatArray(9)
            private val angles = FloatArray(3)

            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                // Keep "up" meaning the top of the screen in any display orientation.
                when (view.display?.rotation) {
                    DisplaySurface.ROTATION_90 -> SensorManager.remapCoordinateSystem(matrix, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, remapped)
                    DisplaySurface.ROTATION_180 -> SensorManager.remapCoordinateSystem(matrix, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, remapped)
                    DisplaySurface.ROTATION_270 -> SensorManager.remapCoordinateSystem(matrix, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, remapped)
                    else -> matrix.copyInto(remapped)
                }
                SensorManager.getOrientation(remapped, angles)
                // The sensor is referenced to magnetic north; the bearing to true north.
                val trueHeading = Math.toDegrees(angles[0].toDouble()).toFloat() + declination
                heading = if (hasHeading) heading + Qibla.turn(heading, trueHeading) else trueHeading
                hasHeading = true
                accuracy = event.accuracy
            }

            override fun onAccuracyChanged(sensor: Sensor, value: Int) {
                accuracy = value
            }
        }
        sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onPauseOrDispose { sensors.unregisterListener(listener) }
    }

    val dialRotation by animateFloatAsState(-heading, label = "qiblaDial")
    val turn = Qibla.turn(heading, bearing)
    val onTarget = hasHeading && abs(turn) <= ON_TARGET_DEGREES

    // A buzz on reaching the qibla, so the screen need not be watched; stay awake while aligning.
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(onTarget) { if (onTarget) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // The instruction comes first: it is what the person acts on.
        if (sensor != null) {
            Text(
                when {
                    !hasHeading -> "Определяем направление…"
                    onTarget -> "Вы смотрите на киблу"
                    turn > 0 -> "Поверните вправо на ${turn.roundToInt()}°"
                    else -> "Поверните влево на ${(-turn).roundToInt()}°"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = if (onTarget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            VSpace(8)
        }
        QiblaDial(
            dialRotation = { dialRotation },
            bearing = bearing,
            onTarget = onTarget,
        )
        VSpace(8)
        Text(
            "Кибла: ${bearing.roundToInt() % 360}° от севера",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        placeName?.let { name ->
            TextButton(onClick = onChangeCity) { Text("${name.substringBefore(" · ")} · Изменить") }
        }
        VSpace(8)

        if (sensor == null) {
            Notice("На этом телефоне нет датчика ориентации, поэтому стрелка не следует за поворотом. Север на рисунке вверху: сверьтесь с обычным компасом.")
        } else {
            Text(
                "Положите телефон горизонтально, подальше от металла и магнитов.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (hasHeading && accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
                VSpace(12)
                Notice("Компас неточен. Откалибруйте его: несколько раз плавно проведите телефоном в воздухе восьмёркой.")
            }
        }
    }
}

@Composable
private fun Notice(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
    }
}

/** Dial turns so north stays north; the arrow is fixed to it at the qibla bearing. */
@Composable
private fun QiblaDial(dialRotation: () -> Float, bearing: Float, onTarget: Boolean) {
    val measurer = rememberTextMeasurer()
    val pointer = MaterialTheme.colorScheme.outline

    Canvas(Modifier.size(300.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = min(size.width, size.height) / 2 - 16.dp.toPx()

        drawCircle(
            Brush.radialGradient(listOf(Green600, Green800), center, radius),
            radius,
            center,
        )
        drawCircle(if (onTarget) Gold else Color.White.copy(alpha = 0.25f), radius, center, style = Stroke((if (onTarget) 3 else 1).dp.toPx()))

        // Fixed marker: the direction the top of the phone is pointing.
        val marker = Path().apply {
            moveTo(center.x, center.y - radius - 3.dp.toPx())
            lineTo(center.x - 6.dp.toPx(), center.y - radius - 13.dp.toPx())
            lineTo(center.x + 6.dp.toPx(), center.y - radius - 13.dp.toPx())
            close()
        }
        drawPath(marker, if (onTarget) Gold else pointer)

        rotate(dialRotation(), center) {
            for (deg in 0 until 360 step 10) rotate(deg.toFloat(), center) {
                val major = deg % 30 == 0
                drawLine(
                    Color.White.copy(alpha = if (major) 0.8f else 0.3f),
                    Offset(center.x, center.y - radius + 8.dp.toPx()),
                    Offset(center.x, center.y - radius + (if (major) 18 else 13).dp.toPx()),
                    strokeWidth = (if (major) 1.5f else 1f).dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            listOf("С" to 0f, "В" to 90f, "Ю" to 180f, "З" to 270f).forEach { (name, deg) ->
                rotate(deg, center) {
                    val text = measurer.measure(
                        name,
                        TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (deg == 0f) Gold else Color.White.copy(alpha = 0.85f),
                        ),
                    )
                    drawText(text, topLeft = Offset(center.x - text.size.width / 2, center.y - radius + 22.dp.toPx()))
                }
            }

            rotate(bearing, center) {
                val w = 7.dp.toPx()
                val tip = center.y - radius + 62.dp.toPx()
                drawPath(
                    Path().apply {
                        moveTo(center.x, tip)
                        lineTo(center.x + w, center.y)
                        lineTo(center.x - w, center.y)
                        close()
                    },
                    Gold,
                )
                drawPath(
                    Path().apply {
                        moveTo(center.x, center.y + radius * 0.3f)
                        lineTo(center.x + w, center.y)
                        lineTo(center.x - w, center.y)
                        close()
                    },
                    Color.White.copy(alpha = 0.3f),
                )
                // The Kaaba, sitting just past the tip of the needle.
                val cube = 15.dp.toPx()
                drawRoundRect(
                    Color(0xFF1C1B1A),
                    topLeft = Offset(center.x - cube / 2, tip - cube - 5.dp.toPx()),
                    size = Size(cube, cube),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
                drawRect(
                    Gold,
                    topLeft = Offset(center.x - cube / 2, tip - cube - 1.dp.toPx()),
                    size = Size(cube, 2.dp.toPx()),
                )
            }
        }
        drawCircle(Gold, 8.dp.toPx(), center)
        drawCircle(Green800, 3.dp.toPx(), center)
    }
}
