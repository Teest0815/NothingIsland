package dev.nothingisland

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/** Links und Bild-Vorschau für Meldungen. */

/** Öffnet eine Adresse im Browser (aus dem Dienst heraus, daher als neue Aufgabe). */
internal fun openUrl(ctx: Context, url: String) {
    val u = if (url.startsWith("http://", true) || url.startsWith("https://", true)) url else "https://$url"
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private val urlRegex = Regex("""(?i)\b((?:https?://|www\.)[^\s<>"']+[^\s<>"'.,;:!?)\]])""")

internal fun hasLink(text: String): Boolean = urlRegex.containsMatchIn(text)

/** Text mit anklickbaren Adressen. Alles außerhalb der Adresse bleibt normal (Antippen geht an die Karte). */
@Composable
internal fun LinkText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign? = null,
) {
    val ctx = LocalContext.current
    val annotated = remember(text) {
        val matches = urlRegex.findAll(text).toList()
        if (matches.isEmpty()) AnnotatedString(text)
        else buildAnnotatedString(text, matches, ctx)
    }
    Text(
        text = annotated,
        color = color,
        fontSize = fontSize,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        modifier = modifier,
    )
}

private fun buildAnnotatedString(text: String, matches: List<MatchResult>, ctx: Context): AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        var pos = 0
        for (m in matches) {
            if (m.range.first > pos) append(text.substring(pos, m.range.first))
            val url = m.value
            withLink(
                LinkAnnotation.Clickable(
                    tag = url,
                    styles = TextLinkStyles(SpanStyle(color = Color(0xFF8AB4FF), textDecoration = TextDecoration.Underline)),
                ) { openUrl(ctx, url) },
            ) { append(url) }
            pos = m.range.last + 1
        }
        if (pos < text.length) append(text.substring(pos))
    }

/** Übergabe des Bildes an die Vorschau (eine Activity bekommt kein Bitmap per Intent). */
internal object PicturePreview {
    @Volatile var bitmap: ImageBitmap? = null

    /** Lädt das große Bild im Hintergrund und öffnet dann die Vollbild-Vorschau. */
    fun show(ctx: Context, small: ImageBitmap, full: (() -> ImageBitmap?)?) {
        val app = ctx.applicationContext
        Thread {
            bitmap = runCatching { full?.invoke() }.getOrNull() ?: small
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                runCatching {
                    app.startActivity(
                        Intent(app, PicturePreviewActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
                    )
                }
            }
        }.start()
    }
}

/** Vollbild-Vorschau: zwei Finger zoomen, Doppeltippen setzt zurück, Antippen schließt. */
class PicturePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bmp = PicturePreview.bitmap
        if (bmp == null) { finish(); return }
        setContent {
            var scale by remember { mutableFloatStateOf(1f) }
            var ox by remember { mutableFloatStateOf(0f) }
            var oy by remember { mutableFloatStateOf(0f) }
            Box(
                Modifier.fillMaxSize().background(Color.Black).clipToBounds()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            if (scale <= 1.01f) { ox = 0f; oy = 0f } else { ox += pan.x; oy += pan.y }
                        }
                    }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = scale; scaleY = scale
                        translationX = ox; translationY = oy
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        PicturePreview.bitmap = null
        super.onDestroy()
    }
}
