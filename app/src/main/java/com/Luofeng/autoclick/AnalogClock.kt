package com.Luofeng.autoclick

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

data class AnalogClockHands(
    val hourDegrees: Double,
    val minuteDegrees: Double,
    val secondDegrees: Double
) {
    companion object {
        fun from(hour: Int, minute: Int, second: Int): AnalogClockHands {
            val h = Math.floorMod(hour, 12)
            val secondDegrees = second * 6.0
            val minuteDegrees = minute * 6.0 + second * 0.1
            val hourDegrees = h * 30.0 + minute * 0.5 + second * (0.5 / 60.0)
            return AnalogClockHands(hourDegrees, minuteDegrees, secondDegrees)
        }
    }
}

@Composable
fun AnalogClockFace(
    nowTick: Long,
    modifier: Modifier = Modifier,
    faceFill: Color = LocalAppColors.current.CardBackground,
    accentColor: Color = LocalAppColors.current.PrimaryBlue,
    lightColor: Color = LocalAppColors.current.LightBlueTintDeep,
    secondHandColor: Color = AppColorsDark.Danger
) {
    val hands = remember(nowTick) {
        val cal = Calendar.getInstance().apply { timeInMillis = nowTick }
        AnalogClockHands.from(
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            cal.get(Calendar.SECOND)
        )
    }
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerStroke = (radius * 0.031f).coerceIn(2.8f, 5.5f)
        val innerStroke = (radius * 0.093f).coerceIn(6.2f, 13.5f)
        val outerRadius = radius - outerStroke / 2f
        val innerRadius = radius * 0.90f
        val tickOuter = innerRadius - innerStroke * 0.55f

        drawCircle(color = faceFill, radius = innerRadius, center = center)
        drawCircle(
            color = lightColor,
            radius = outerRadius,
            center = center,
            style = Stroke(width = 1.2f * outerStroke)
        )
        drawCircle(
            color = accentColor,
            radius = innerRadius,
            center = center,
            style = Stroke(width = innerStroke)
        )
        for (i in 0 until 60) {
            val degrees = i * 6.0
            if (i % 5 == 0) {
                drawLine(
                    color = accentColor,
                    start = analogHandTip(center, tickOuter * 0.85f, degrees),
                    end = analogHandTip(center, tickOuter, degrees),
                    strokeWidth = innerStroke * 0.85f,
                    cap = StrokeCap.Round
                )
            } else {
                drawLine(
                    color = lightColor,
                    start = analogHandTip(center, tickOuter * 0.93f, degrees),
                    end = analogHandTip(center, tickOuter, degrees),
                    strokeWidth = innerStroke * 0.35f,
                    cap = StrokeCap.Round
                )
            }
        }
        drawLine(
            color = accentColor,
            start = center,
            end = analogHandTip(center, innerRadius * 0.42f, hands.hourDegrees),
            strokeWidth = innerStroke * 1.20f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = accentColor,
            start = center,
            end = analogHandTip(center, innerRadius * 0.62f, hands.minuteDegrees),
            strokeWidth = innerStroke * 0.75f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = secondHandColor,
            start = center,
            end = analogHandTip(center, innerRadius * 0.82f, hands.secondDegrees),
            strokeWidth = outerStroke * 1.1f,
            cap = StrokeCap.Round
        )
        drawCircle(color = accentColor, radius = innerStroke * 0.85f, center = center)
        drawCircle(color = secondHandColor, radius = innerStroke * 0.32f, center = center)
    }
}

internal fun analogHandTip(center: Offset, length: Float, degrees: Double): Offset {
    val rad = Math.toRadians(degrees - 90.0)
    return Offset(
        center.x + (length * cos(rad)).toFloat(),
        center.y + (length * sin(rad)).toFloat()
    )
}
