package dev.nothingisland

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import android.os.SystemClock
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlin.math.sign
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import android.media.session.PlaybackState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal val CALC_OPS = setOf('+', '-', '×', '÷')

/** Rechnet Ausdrücke wie "12+3×4" mit Punkt vor Strich; ein Prozentzeichen teilt durch 100. Null bei Fehler. */
internal fun calcEval(src: String): Double? {
    var i = 0
    fun peek(): Char? = src.getOrNull(i)
    fun number(): Double? {
        var neg = false
        while (peek() == '-') { neg = !neg; i++ }
        val st = i
        while (peek()?.let { it.isDigit() || it == '.' } == true) i++
        var v = src.substring(st, i).toDoubleOrNull() ?: return null
        while (peek() == '%') { v /= 100.0; i++ }
        return if (neg) -v else v
    }
    fun term(): Double? {
        var v = number() ?: return null
        while (peek() == '×' || peek() == '÷') {
            val op = peek(); i++
            val r = number() ?: return null
            if (op == '×') v *= r else { if (r == 0.0) return null; v /= r }
        }
        return v
    }
    fun expr(): Double? {
        var v = term() ?: return null
        while (peek() == '+' || peek() == '-') {
            val op = peek(); i++
            val r = term() ?: return null
            v = if (op == '+') v + r else v - r
        }
        return v
    }
    val r = expr()
    return if (i == src.length) r?.takeIf { it.isFinite() } else null
}

internal fun calcFormat(v: Double): String {
    if (v == Math.rint(v) && kotlin.math.abs(v) < 1e15) return v.toLong().toString()
    val s = java.math.BigDecimal(v).round(java.math.MathContext(10)).stripTrailingZeros().toPlainString()
    return s
}

@Composable
internal fun CalcPanel(sc: Float, modifier: Modifier, onUsed: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val hap = IslandBus.settings.collectAsState().value.haptics
    var expr by remember { mutableStateOf("") }
    var done by remember { mutableStateOf(false) } // Ergebnis steht da: nächste Ziffer beginnt neu
    val live = if (expr.isEmpty() || done) null else if (expr.any { it in CALC_OPS || it == '%' } || expr.indexOf('-', 1) > 0) calcEval(expr) else null

    fun press(k: String) {
        onUsed()
        if (hap) QuickActions.haptic(ctx, 1)
        when (k) {
            "C" -> { expr = ""; done = false }
            "⌫" -> { expr = expr.dropLast(1); done = false }
            "=" -> calcEval(expr)?.let { expr = calcFormat(it); done = true }
            "+", "-", "×", "÷" -> {
                val c = k[0]
                if (expr.isEmpty()) { if (c == '-') expr = "-" }
                else {
                    done = false
                    val last = expr.last()
                    expr = if (last in CALC_OPS) {
                        if (c == '-' && (last == '×' || last == '÷')) expr + c else expr.dropLast(1) + c
                    } else expr + c
                    if (expr == "-") expr = "-"
                }
            }
            "%" -> if (expr.isNotEmpty() && (expr.last().isDigit() || expr.last() == '%')) { expr += "%"; done = false }
            "," -> {
                if (done) { expr = ""; done = false }
                val cur = expr.takeLastWhile { it.isDigit() || it == '.' }
                if (!cur.contains('.')) expr += if (cur.isEmpty()) "0." else "."
            }
            else -> {
                if (done) { expr = ""; done = false }
                if (expr.length < 40) expr += k
            }
        }
    }

    val shownExpr = expr.replace('.', ',').ifEmpty { "0" }
    val resultText = when {
        done -> null
        live != null -> "= " + calcFormat(live).replace('.', ',')
        else -> null
    }
    val rows = listOf(
        listOf("C", "%", "⌫", "÷"),
        listOf("7", "8", "9", "×"),
        listOf("4", "5", "6", "-"),
        listOf("1", "2", "3", "+"),
        listOf("0", ",", "="),
    )
    Column(
        modifier = modifier.padding(horizontal = (14f * sc).dp, vertical = (12f * sc).dp),
        verticalArrangement = Arrangement.spacedBy((6f * sc).dp),
    ) {
        Column(Modifier.weight(1.25f).fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
            Text(
                resultText ?: " ", color = TextGray, fontFamily = Mono, fontSize = (12f * sc).sp,
                maxLines = 1,
            )
            Text(
                shownExpr.replace('-', '−'),
                color = Fg, fontFamily = Mono, fontWeight = FontWeight.Medium,
                fontSize = (if (shownExpr.length > 14) 18f else 28f).times(sc).sp,
                maxLines = 1, overflow = TextOverflow.Clip,
            )
        }
        rows.forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((6f * sc).dp)) {
                row.forEach { k ->
                    val src = remember { MutableInteractionSource() }
                    val down by src.collectIsPressedAsState()
                    val isOp = k in setOf("÷", "×", "-", "+", "%")
                    val isEq = k == "="
                    val bg by androidx.compose.animation.animateColorAsState(
                        if (down) Color.White else if (isEq) NothingRed else ButtonBg,
                        tween(if (down) 40 else 200), label = "calcbg",
                    )
                    Box(
                        modifier = Modifier
                            .weight(if (k == "0") 2f else 1f)
                            .fillMaxHeight()
                            .bounceClick({ press(k) }, src)
                            .clip(RoundedCornerShape((14f * sc).dp))
                            .drawBehind { drawRect(bg) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (k == "-") "−" else k,
                            color = if (down) Color.Black else if (isEq) OnAccent else if (isOp || k == "C" || k == "⌫") NothingRed else Fg,
                            fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = (16f * sc).sp,
                        )
                    }
                }
            }
        }
    }
}
