package fr.papertv.eink.ui

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.asComposeColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.papertv.eink.data.ImageLoader
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Variante couleur (téléphones classiques) ou E Ink (noir et blanc pur). */
val COLOR: Boolean = fr.papertv.eink.BuildConfig.COLOR

/**
 * Palette. E Ink : noir, blanc et un gris foncé seulement (les gris clairs « bavent »).
 * Couleur : tons chauds de Paper Meteo (papier crème, encre brun-noir, accent terre cuite).
 */
object Ink {
    val Black = Color(if (COLOR) 0xFF2E2622 else 0xFF000000)
    val White = Color(if (COLOR) 0xFFF7E8D5 else 0xFFFFFFFF)
    val Dark = Color(if (COLOR) 0xFF7B6F67 else 0xFF333333)
    val Mid = Color(if (COLOR) 0xFFB5A496 else 0xFF666666)
    val Line = Color(if (COLOR) 0xFFE2C9AF else 0xFF000000)
    val Accent = Color(if (COLOR) 0xFFB84E24 else 0xFF000000)
    val Card = Color(if (COLOR) 0xFFFCF4EA else 0xFFFFFFFF)
    val Highlight = Color(if (COLOR) 0xFFEFCBAA else 0xFF000000)
    val OnHighlight = Color(if (COLOR) 0xFF2E2622 else 0xFFFFFFFF)
}

@Composable
fun EinkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Ink.Accent, onPrimary = Color.White,
            background = Ink.White, onBackground = Ink.Black,
            surface = Ink.White, onSurface = Ink.Black,
        ),
    ) {
        CompositionLocalProvider(LocalContentColor provides Ink.Black, content = content)
    }
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.FRANCE)

fun formatTime(epochMs: Long): String =
    TIME.format(Instant.ofEpochMilli(epochMs).atZone(fr.papertv.eink.data.PrimeTimeSelector.PARIS))

private val DATE_TIME = DateTimeFormatter.ofPattern("EEEE d MMMM 'à' HH:mm", Locale.FRANCE)

fun formatDateTime(epochMs: Long): String =
    DATE_TIME.format(Instant.ofEpochMilli(epochMs).atZone(fr.papertv.eink.data.PrimeTimeSelector.PARIS))

fun formatDuration(minutes: Long): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0L -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${"%02d".format(minutes % 60)}"
}

/** Bouton à bordure pleine, sans effet d'onde en E Ink (inutile et lent). */
@Composable
fun EinkButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    val border = if (enabled) Ink.Accent else Ink.Mid
    val fg = when {
        !enabled -> Ink.Mid
        filled -> Color.White
        else -> if (COLOR) Ink.Accent else Ink.Black
    }
    val shape = if (COLOR) RoundedCornerShape(12.dp) else RectangleShape
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .border(if (COLOR) 1.5.dp else 2.dp, border, shape)
            .background(if (filled && enabled) Ink.Accent else if (COLOR) Ink.Card else Ink.White)
            .einkClickable(enabled, onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
    }
}

/** Clic sans effet visuel en E Ink, avec l'effet d'onde habituel en couleur. */
@Composable
fun Modifier.einkClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    if (COLOR) clickable(enabled = enabled, onClick = onClick)
    else clickable(enabled = enabled, interactionSource = null, indication = null, onClick = onClick)

/** Trait horizontal plein (noir en E Ink, filet chaud en couleur). */
@Composable
fun Rule(thickness: Dp = 1.dp, modifier: Modifier = Modifier) {
    val t = if (COLOR) (thickness / 2).coerceAtLeast(1.dp) else thickness
    Box(modifier.fillMaxWidth().height(t).background(Ink.Line))
}

private val grayscale = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }).asComposeColorFilter()

/** Image distante en niveaux de gris, avec un cadre vide pendant le chargement. */
@Composable
fun NetImage(url: String?, modifier: Modifier, maxPx: Dp = 160.dp) {
    val shape = if (COLOR) RoundedCornerShape(10.dp) else RectangleShape
    val px = with(LocalDensity.current) { maxPx.roundToPx() }
    var bitmap by remember(url) { mutableStateOf(url?.let { ImageLoader.cached(it) }) }
    if (url != null && bitmap == null) {
        LaunchedEffect(url) { bitmap = ImageLoader.load(url, px) }
    }
    Box(
        if (COLOR) modifier.clip(shape).background(Ink.Highlight)
        else modifier.border(1.dp, Ink.Black)
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = if (COLOR) null else grayscale,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
