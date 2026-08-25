package com.example.myjarvice.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun IconMenu(
    tint: Color = Color.White,
    size: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val strokeWidth = 2.dp.toPx()
        val w = this.size.width
        val h = this.size.height
        // 3 sleek rounded horizontal lines
        drawLine(tint, Offset(w * 0.15f, h * 0.28f), Offset(w * 0.85f, h * 0.28f), strokeWidth, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.15f, h * 0.50f), Offset(w * 0.65f, h * 0.50f), strokeWidth, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.15f, h * 0.72f), Offset(w * 0.85f, h * 0.72f), strokeWidth, StrokeCap.Round)
    }
}

@Composable
fun IconNewChat(
    tint: Color = Color.White,
    size: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = this.size.width
        val h = this.size.height

        // Square container with top-right open
        val path = Path().apply {
            moveTo(w * 0.45f, h * 0.2f)
            lineTo(w * 0.25f, h * 0.2f)
            quadraticTo(w * 0.15f, h * 0.2f, w * 0.15f, h * 0.3f)
            lineTo(w * 0.15f, h * 0.75f)
            quadraticTo(w * 0.15f, h * 0.85f, w * 0.25f, h * 0.85f)
            lineTo(w * 0.75f, h * 0.85f)
            quadraticTo(w * 0.85f, h * 0.85f, w * 0.85f, h * 0.75f)
            lineTo(w * 0.85f, h * 0.55f)
        }
        drawPath(path, color = tint, style = stroke)

        // Pen line in top right
        drawLine(tint, Offset(w * 0.50f, h * 0.50f), Offset(w * 0.82f, h * 0.18f), 1.8.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
fun IconVoiceWaveform(
    tint: Color = Color.White,
    size: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val strokeWidth = 2.dp.toPx()
        val w = this.size.width
        val h = this.size.height
        val midY = h * 0.5f

        // Sound wave frequency bars (ChatGPT style)
        drawLine(tint, Offset(w * 0.18f, midY - h * 0.16f), Offset(w * 0.18f, midY + h * 0.16f), strokeWidth, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.34f, midY - h * 0.32f), Offset(w * 0.34f, midY + h * 0.32f), strokeWidth, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.50f, midY - h * 0.42f), Offset(w * 0.50f, midY + h * 0.42f), strokeWidth, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.66f, midY - h * 0.28f), Offset(w * 0.66f, midY + h * 0.28f), strokeWidth, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.82f, midY - h * 0.14f), Offset(w * 0.82f, midY + h * 0.14f), strokeWidth, StrokeCap.Round)
    }
}

@Composable
fun IconMicrophone(
    tint: Color = Color.White,
    size: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = this.size.width
        val h = this.size.height

        // Mic body capsule
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.36f, h * 0.15f),
            size = Size(w * 0.28f, h * 0.46f),
            cornerRadius = CornerRadius(w * 0.14f, w * 0.14f),
            style = stroke
        )

        // Mic holder arc
        val arcPath = Path().apply {
            moveTo(w * 0.22f, h * 0.44f)
            quadraticTo(w * 0.22f, h * 0.72f, w * 0.5f, h * 0.72f)
            quadraticTo(w * 0.78f, h * 0.72f, w * 0.78f, h * 0.44f)
        }
        drawPath(arcPath, color = tint, style = stroke)

        // Mic base stem
        drawLine(tint, Offset(w * 0.5f, h * 0.72f), Offset(w * 0.5f, h * 0.86f), 1.8.dp.toPx(), StrokeCap.Round)
        drawLine(tint, Offset(w * 0.32f, h * 0.86f), Offset(w * 0.68f, h * 0.86f), 1.8.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
fun IconSend(
    tint: Color = Color.Black,
    size: Dp = 18.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val strokeWidth = 2.dp.toPx()
        val w = this.size.width
        val h = this.size.height

        // Vertical arrow stem
        drawLine(tint, Offset(w * 0.5f, h * 0.78f), Offset(w * 0.5f, h * 0.22f), strokeWidth, StrokeCap.Round)

        // Arrow head
        val arrowHead = Path().apply {
            moveTo(w * 0.25f, h * 0.45f)
            lineTo(w * 0.5f, h * 0.20f)
            lineTo(w * 0.75f, h * 0.45f)
        }
        drawPath(arrowHead, color = tint, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun IconPlus(
    tint: Color = Color.White,
    size: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val strokeWidth = 1.8.dp.toPx()
        val w = this.size.width
        val h = this.size.height
        drawLine(tint, Offset(w * 0.2f, h * 0.5f), Offset(w * 0.8f, h * 0.5f), strokeWidth, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.5f, h * 0.2f), Offset(w * 0.5f, h * 0.8f), strokeWidth, StrokeCap.Round)
    }
}

@Composable
fun IconCopy(
    tint: Color = Color.White,
    size: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = this.size.width
        val h = this.size.height

        // Front rounded rect
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.3f, h * 0.3f),
            size = Size(w * 0.58f, h * 0.58f),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = stroke
        )

        // Back overlapping rect
        val backPath = Path().apply {
            moveTo(w * 0.2f, h * 0.7f)
            lineTo(w * 0.15f, h * 0.7f)
            quadraticTo(w * 0.12f, h * 0.7f, w * 0.12f, h * 0.67f)
            lineTo(w * 0.12f, h * 0.15f)
            quadraticTo(w * 0.12f, h * 0.12f, w * 0.15f, h * 0.12f)
            lineTo(w * 0.67f, h * 0.12f)
            quadraticTo(w * 0.7f, h * 0.12f, w * 0.7f, h * 0.15f)
            lineTo(w * 0.7f, h * 0.2f)
        }
        drawPath(backPath, color = tint, style = stroke)
    }
}

@Composable
fun IconSpeaker(
    tint: Color = Color.White,
    size: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = this.size.width
        val h = this.size.height

        // Speaker cone
        val cone = Path().apply {
            moveTo(w * 0.15f, h * 0.38f)
            lineTo(w * 0.32f, h * 0.38f)
            lineTo(w * 0.52f, h * 0.2f)
            lineTo(w * 0.52f, h * 0.8f)
            lineTo(w * 0.32f, h * 0.62f)
            lineTo(w * 0.15f, h * 0.62f)
            close()
        }
        drawPath(cone, color = tint, style = stroke)

        // Sound waves
        val wave1 = Path().apply {
            moveTo(w * 0.66f, h * 0.35f)
            quadraticTo(w * 0.74f, h * 0.5f, w * 0.66f, h * 0.65f)
        }
        drawPath(wave1, color = tint, style = stroke)

        val wave2 = Path().apply {
            moveTo(w * 0.78f, h * 0.25f)
            quadraticTo(w * 0.90f, h * 0.5f, w * 0.78f, h * 0.75f)
        }
        drawPath(wave2, color = tint, style = stroke)
    }
}

@Composable
fun IconSettings(
    tint: Color = Color.White,
    size: Dp = 18.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = this.size.width
        val h = this.size.height
        val c = Offset(w * 0.5f, h * 0.5f)

        // Outer gear teeth circle
        drawCircle(color = tint, radius = w * 0.34f, center = c, style = stroke)
        // Center hole
        drawCircle(color = tint, radius = w * 0.14f, center = c, style = stroke)

        // 4 gear pins
        drawLine(tint, Offset(w * 0.5f, h * 0.08f), Offset(w * 0.5f, h * 0.18f), 1.8.dp.toPx(), StrokeCap.Round)
        drawLine(tint, Offset(w * 0.5f, h * 0.82f), Offset(w * 0.5f, h * 0.92f), 1.8.dp.toPx(), StrokeCap.Round)
        drawLine(tint, Offset(w * 0.08f, h * 0.5f), Offset(w * 0.18f, h * 0.5f), 1.8.dp.toPx(), StrokeCap.Round)
        drawLine(tint, Offset(w * 0.82f, h * 0.5f), Offset(w * 0.92f, h * 0.5f), 1.8.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
fun IconTrash(
    tint: Color = Color.White,
    size: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = this.size.width
        val h = this.size.height

        // Lid
        drawLine(tint, Offset(w * 0.2f, h * 0.28f), Offset(w * 0.8f, h * 0.28f), 1.4.dp.toPx(), StrokeCap.Round)
        drawLine(tint, Offset(w * 0.4f, h * 0.2f), Offset(w * 0.6f, h * 0.2f), 1.4.dp.toPx(), StrokeCap.Round)

        // Bin body
        val bin = Path().apply {
            moveTo(w * 0.28f, h * 0.28f)
            lineTo(w * 0.32f, h * 0.82f)
            quadraticTo(w * 0.33f, h * 0.88f, w * 0.40f, h * 0.88f)
            lineTo(w * 0.60f, h * 0.88f)
            quadraticTo(w * 0.67f, h * 0.88f, w * 0.68f, h * 0.82f)
            lineTo(w * 0.72f, h * 0.28f)
        }
        drawPath(bin, color = tint, style = stroke)

        // Inner vertical slot
        drawLine(tint, Offset(w * 0.5f, h * 0.42f), Offset(w * 0.5f, h * 0.74f), 1.2.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
fun IconSparkles(
    tint: Color = Color.White,
    size: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // 4-point star (Gemini style)
        val star = Path().apply {
            moveTo(w * 0.5f, h * 0.12f)
            quadraticTo(w * 0.5f, h * 0.5f, w * 0.88f, h * 0.5f)
            quadraticTo(w * 0.5f, h * 0.5f, w * 0.5f, h * 0.88f)
            quadraticTo(w * 0.5f, h * 0.5f, w * 0.12f, h * 0.5f)
            quadraticTo(w * 0.5f, h * 0.5f, w * 0.5f, h * 0.12f)
        }
        drawPath(star, color = tint, style = Fill)
    }
}

@Composable
fun IconDocument(
    tint: Color = Color.White,
    size: Dp = 18.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = this.size.width
        val h = this.size.height

        // Page container with folded top corner
        val doc = Path().apply {
            moveTo(w * 0.2f, h * 0.85f)
            lineTo(w * 0.2f, h * 0.15f)
            lineTo(w * 0.6f, h * 0.15f)
            lineTo(w * 0.8f, h * 0.35f)
            lineTo(w * 0.8f, h * 0.85f)
            close()
        }
        drawPath(doc, color = tint, style = stroke)

        // Text lines
        drawLine(tint, Offset(w * 0.35f, h * 0.45f), Offset(w * 0.65f, h * 0.45f), 1.2.dp.toPx(), StrokeCap.Round)
        drawLine(tint, Offset(w * 0.35f, h * 0.62f), Offset(w * 0.55f, h * 0.62f), 1.2.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
fun IconActivity(
    tint: Color = Color.White,
    size: Dp = 18.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = this.size.width
        val h = this.size.height

        // Heartbeat pulse waveform line
        val pulse = Path().apply {
            moveTo(w * 0.12f, h * 0.52f)
            lineTo(w * 0.32f, h * 0.52f)
            lineTo(w * 0.42f, h * 0.22f)
            lineTo(w * 0.58f, h * 0.78f)
            lineTo(w * 0.68f, h * 0.52f)
            lineTo(w * 0.88f, h * 0.52f)
        }
        drawPath(pulse, color = tint, style = stroke)
    }
}

@Composable
fun IconMessage(
    tint: Color = Color.White,
    size: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = this.size.width
        val h = this.size.height

        val bubble = Path().apply {
            moveTo(w * 0.2f, h * 0.22f)
            lineTo(w * 0.8f, h * 0.22f)
            quadraticTo(w * 0.88f, h * 0.22f, w * 0.88f, h * 0.30f)
            lineTo(w * 0.88f, h * 0.65f)
            quadraticTo(w * 0.88f, h * 0.73f, w * 0.8f, h * 0.73f)
            lineTo(w * 0.42f, h * 0.73f)
            lineTo(w * 0.24f, h * 0.88f)
            lineTo(w * 0.24f, h * 0.73f)
            lineTo(w * 0.2f, h * 0.73f)
            quadraticTo(w * 0.12f, h * 0.73f, w * 0.12f, h * 0.65f)
            lineTo(w * 0.12f, h * 0.30f)
            quadraticTo(w * 0.12f, h * 0.22f, w * 0.2f, h * 0.22f)
            close()
        }
        drawPath(bubble, color = tint, style = stroke)
    }
}

@Composable
fun IconMail(
    tint: Color = Color.White,
    size: Dp = 18.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = this.size.width
        val h = this.size.height

        // Envelope rectangle
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.15f, h * 0.22f),
            size = Size(w * 0.70f, h * 0.56f),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = stroke
        )

        // Envelope flap V
        val flap = Path().apply {
            moveTo(w * 0.18f, h * 0.26f)
            lineTo(w * 0.50f, h * 0.52f)
            lineTo(w * 0.82f, h * 0.26f)
        }
        drawPath(flap, color = tint, style = stroke)
    }
}

