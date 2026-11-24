package dev.nothingisland

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

private fun tempText(v: Double, target: Double): String {
    if (v.isNaN()) return "–"
    val now = "${v.toInt()}°"
    return if (target > 0) "$now/${target.toInt()}°" else now
}

private fun leftText(min: Int) = when {
    min < 0 -> "–"
    min >= 60 -> "${min / 60}:${"%02d".format(min % 60)} h"
    else -> "$min min"
}

private fun etaText(min: Int): String {
    if (min < 0) return "–"
    val c = Calendar.getInstance().apply { add(Calendar.MINUTE, min) }
    return "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
}

/** Gezeichneter 3D-Drucker: das Objekt wächst mit dem Fortschritt, die Düse fährt hin und her und glüht beim Heizen. */
@Composable
private fun PrinterArt(p: PrinterView, progress: Float, sizeDp: Float, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "prt")
    val sweep by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sw")
    val pulse by inf.animateFloat(0.35f, 1f, infiniteRepeatable(tween(750, easing = LinearEasing), RepeatMode.Reverse), label = "pl")
    val moving = !p.paused && !p.heating
    val frame = Fg
    val accent = NothingRed
    Canvas(modifier.size(sizeDp.dp)) {
        val k = size.width / 100f
        val st = Stroke(width = 5f * k, cap = StrokeCap.Round)
        // Rahmen
        drawRoundRect(frame.copy(alpha = 0.85f), Offset(12f * k, 8f * k), Size(76f * k, 80f * k), CornerRadius(8f * k), style = st)
        // Druckbett
        drawLine(frame, Offset(24f * k, 76f * k), Offset(76f * k, 76f * k), strokeWidth = 5f * k, cap = StrokeCap.Round)
        // Objekt wächst von unten
        val h = 42f * k * progress.coerceIn(0f, 1f)
        if (h > 0.5f) drawRoundRect(accent, Offset(38f * k, 73f * k - h), Size(24f * k, h), CornerRadius(2f * k))
        // Portal und Düse stehen knapp über dem Objekt
        val gy = (66f * k - h - 4f * k).coerceAtLeast(20f * k)
        drawLine(frame, Offset(12f * k, gy), Offset(88f * k, gy), strokeWidth = 4f * k, cap = StrokeCap.Round)
        val cx = if (moving) (38f + 24f * sweep) * k else 50f * k
        drawRoundRect(frame, Offset(cx - 7f * k, gy - 5f * k), Size(14f * k, 9f * k), CornerRadius(2f * k))
        val tip = Path().apply {
            moveTo(cx - 4f * k, gy + 4f * k); lineTo(cx + 4f * k, gy + 4f * k); lineTo(cx, gy + 11f * k); close()
        }
        val glow = if (p.heating) lerp(frame, accent, pulse) else if (p.paused) frame.copy(alpha = 0.5f) else accent
        drawPath(tip, glow)
    }
}

@Composable
private fun Stat(label: String, value: String, sc: Float, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = TextGray, fontFamily = Mono, fontSize = (9f * sc).sp, letterSpacing = 1.2.sp, maxLines = 1)
        Text(value, color = Fg, fontFamily = Mono, fontSize = (12f * sc).sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Große Pille für einen laufenden 3D-Druck. */
@Composable
internal fun PrinterExpanded(
    e: IslandEvent,
    p: PrinterView,
    count: Int,
    index: Int,
    sc: Float,
    onCycle: () -> Unit,
    modifier: Modifier,
    tall: Boolean,
) {
    val prog by animateFloatAsState(p.percent / 100f, tween(700, easing = FastOutSlowInEasing), label = "pp")
    val file = p.file.removeSuffix(".3mf").removeSuffix(".gcode")
    val sub = buildString {
        if (file.isNotBlank()) append(file)
        if (p.total > 0) { if (isNotEmpty()) append("  ·  "); append("Schicht ${p.layer}/${p.total}") }
    }
    val headline = if (p.heating) {
        val cur = if (p.phase.startsWith("Bett")) p.bed else p.nozzle
        val tgt = if (p.phase.startsWith("Bett")) p.bedTarget else p.nozzleTarget
        if (!cur.isNaN() && tgt > 0) "${p.phase}  ${cur.toInt()}° → ${tgt.toInt()}°" else p.phase
    } else if (p.paused) p.phase else "${p.phase}  ${p.percent}%"
    Box(modifier) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = (16f * sc).dp, vertical = (10f * sc).dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PrinterArt(p, prog, 58f * sc)
                Spacer(Modifier.width((12f * sc).dp))
                Column(Modifier.weight(1f)) {
                    AppLabel(e.title.ifBlank { p.brand }, sc)
                    Text(
                        headline, color = Fg, fontSize = (14f * sc).sp, fontWeight = FontWeight.Medium,
                        maxLines = if (tall) 2 else 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (sub.isNotBlank()) Text(
                        sub, color = TextGray, fontSize = (11f * sc).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (count > 1) Text(
                    "${index + 1}/$count", color = AccentText, fontFamily = Mono, fontSize = (11f * sc).sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onCycle)
                        .padding(horizontal = (8f * sc).dp, vertical = (6f * sc).dp),
                )
            }
            if (tall) {
                Column(verticalArrangement = Arrangement.spacedBy((6f * sc).dp)) {
                    Row { Stat("REST", leftText(p.leftMin), sc, Modifier.weight(1f)); Stat("FERTIG", etaText(p.leftMin), sc, Modifier.weight(1f)) }
                    Row { Stat("DÜSE", tempText(p.nozzle, p.nozzleTarget), sc, Modifier.weight(1f)); Stat("BETT", tempText(p.bed, p.bedTarget), sc, Modifier.weight(1f)) }
                }
            } else {
                Row(Modifier.fillMaxWidth()) {
                    Stat("REST", leftText(p.leftMin), sc, Modifier.weight(1f))
                    Stat("FERTIG", etaText(p.leftMin), sc, Modifier.weight(1f))
                    Stat("DÜSE", tempText(p.nozzle, p.nozzleTarget), sc, Modifier.weight(1.2f))
                    Stat("BETT", tempText(p.bed, p.bedTarget), sc, Modifier.weight(1.2f))
                }
            }
        }
        Box(
            Modifier.align(Alignment.BottomCenter).padding(horizontal = (28f * sc).dp).padding(bottom = (5f * sc).dp)
                .fillMaxWidth().height(2.dp).background(DotGray),
        ) {
            Box(Modifier.fillMaxWidth(prog.coerceIn(0f, 1f)).fillMaxHeight().background(NothingRed))
        }
    }
}
