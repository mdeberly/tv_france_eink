package fr.papertv.eink.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.papertv.eink.data.PrimeTimeEntry
import fr.papertv.eink.data.Programme
import kotlinx.coroutines.launch

/** Fait défiler d'un écran (moins une ligne de recouvrement), sans animation. */
suspend fun ScrollState.pageBy(direction: Int) {
    val step = (viewportSize * 0.9f).toInt().coerceAtLeast(1)
    scrollTo((value + direction * step).coerceIn(0, maxValue))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailScreen(
    entry: PrimeTimeEntry,
    scroll: ScrollState = rememberScrollState(),
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val p = entry.primeTime ?: return
    Column(Modifier.fillMaxSize().background(Ink.White)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EinkButton("‹ Retour", Modifier.width(110.dp), onClick = onBack)
            Text(
                entry.channel.name,
                Modifier.weight(1f).padding(start = 12.dp),
                style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Black, color = Ink.Accent),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Rule(3.dp)
        // Pas d'effet de rebond : chaque frame d'animation coûte un rafraîchissement e-ink.
        CompositionLocalProvider(LocalOverscrollConfiguration provides if (COLOR) LocalOverscrollConfiguration.current else null) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(14.dp),
            ) {
                if (p.iconUrl != null) {
                    NetImage(p.iconUrl, Modifier.fillMaxWidth().aspectRatio(16f / 9f), maxPx = 400.dp)
                    Spacer(Modifier.height(12.dp))
                }
                ProgrammeBlock(p, big = true)

                entry.next?.let { next ->
                    Spacer(Modifier.height(20.dp))
                    Rule(3.dp)
                    Text(
                        "ENSUITE",
                        Modifier.padding(top = 6.dp, bottom = 6.dp),
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = Ink.Accent),
                    )
                    ProgrammeBlock(next, big = false)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        // E Ink : on tourne les pages avec des boutons ; en couleur, défilement fluide au doigt.
        if (!COLOR && scroll.maxValue > 0) {
            Rule(3.dp)
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EinkButton("▲ Haut", Modifier.width(100.dp), enabled = scroll.value > 0) {
                    scope.launch { scroll.pageBy(-1) }
                }
                val step = (scroll.viewportSize * 0.9f).toInt().coerceAtLeast(1)
                val pages = 1 + (scroll.maxValue + step - 1) / step
                val current = 1 + (scroll.value + step - 1) / step
                Text(
                    "${current.coerceAtMost(pages)} / $pages",
                    Modifier.weight(1f),
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                )
                EinkButton("Bas ▼", Modifier.width(100.dp), enabled = scroll.value < scroll.maxValue) {
                    scope.launch { scroll.pageBy(1) }
                }
            }
        }
    }
}

@Composable
private fun ProgrammeBlock(p: Programme, big: Boolean) {
    Text(
        "${formatTime(p.start)} – ${formatTime(p.stop)} · ${formatDuration(p.durationMinutes)}",
        style = TextStyle(fontSize = 13.sp, color = Ink.Dark),
    )
    Text(
        p.title,
        style = TextStyle(
            fontSize = if (big) 23.sp else 19.sp,
            lineHeight = if (big) 27.sp else 23.sp,
            fontWeight = FontWeight.Black,
        ),
    )
    val sub = listOfNotNull(p.subTitle, p.episode).joinToString(" · ")
    if (sub.isNotEmpty()) {
        Text(sub, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp))
    }
    val info = listOfNotNull(
        p.categories.firstOrNull(),
        p.country,
        p.year,
        p.rating?.takeUnless { it.equals("Tout public", ignoreCase = true) },
        p.starRating?.let { "★ $it" },
    ).joinToString(" · ")
    if (info.isNotEmpty()) {
        Spacer(Modifier.height(2.dp))
        Text(info, style = TextStyle(fontSize = 13.sp, color = Ink.Dark, lineHeight = 18.sp))
    }
    p.description?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, style = TextStyle(fontSize = if (big) 16.sp else 15.sp, lineHeight = if (big) 23.sp else 21.sp))
    }
    val credits = listOfNotNull(
        p.directors.takeIf { it.isNotEmpty() }?.let { "Réalisation : " + it.joinToString(", ") },
        p.presenters.takeIf { it.isNotEmpty() }?.let { "Présentation : " + it.joinToString(", ") },
        p.actors.takeIf { it.isNotEmpty() }?.let { "Avec : " + it.joinToString(", ") },
    )
    if (credits.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        credits.forEach { Text(it, style = TextStyle(fontSize = 14.sp, lineHeight = 19.sp)) }
    }
}
