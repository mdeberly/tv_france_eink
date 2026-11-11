package fr.papertv.eink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.papertv.eink.data.PrimeTimeEntry
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val DAY = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE)
private val DAY_SHORT = DateTimeFormatter.ofPattern("EEEE d", Locale.FRANCE)

/** Version E Ink : 3 chaînes par écran, pour un résumé en gros caractères. */
const val PER_PAGE = 3

/** Libellé court de la soirée : « Ce soir », « Hier soir », « Demain soir » ou le jour. */
fun eveningLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Ce soir"
    today.minusDays(1) -> "Hier soir"
    today.plusDays(1) -> "Demain soir"
    else -> DAY_SHORT.format(date).replaceFirstChar { it.uppercase() }
}

@Composable
fun ListScreen(
    state: UiState,
    page: Int,
    onPageChange: (Int) -> Unit,
    onPageCount: (Int) -> Unit,
    onOpen: (PrimeTimeEntry) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onOpenDatePicker: () -> Unit,
    onOpenMenu: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    Column(Modifier.fillMaxSize().background(Ink.White)) {
        Header(state, onSelectDate, onOpenDatePicker, onOpenMenu)
        Rule(3.dp)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.entries.isEmpty() && state.loading ->
                    Message("Chargement du programme…\n\nLe premier téléchargement peut prendre une minute.")
                state.entries.isEmpty() -> Message(
                    "Programme indisponible" + (state.error?.let { " ($it)" } ?: "") +
                        ".\n\nVérifiez la connexion puis touchez « ⋮ » › Actualiser."
                )
                COLOR -> SmoothList(state, listState, onOpen)
                else -> PagedList(state, page, onPageChange, onPageCount, onOpen)
            }
        }
    }
}

@Composable
private fun Header(
    state: UiState,
    onSelectDate: (LocalDate) -> Unit,
    onOpenDatePicker: () -> Unit,
    onOpenMenu: () -> Unit,
) {
    val dates = state.availableDates
    val index = dates.indexOf(state.selected)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EinkButton("‹", Modifier.width(44.dp), enabled = index > 0) { onSelectDate(dates[index - 1]) }
        Column(
            Modifier.weight(1f).einkClickable(onClick = onOpenDatePicker).padding(horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                eveningLabel(state.selected, state.today) + " ▾",
                style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Black, color = Ink.Black),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val sub = DAY.format(state.selected).replaceFirstChar { it.uppercase() } +
                when {
                    state.loading -> " · mise à jour…"
                    state.error != null -> " · hors ligne"
                    else -> ""
                }
            Text(sub, style = TextStyle(fontSize = 12.sp, color = Ink.Dark), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        EinkButton("›", Modifier.width(44.dp), enabled = index in 0 until dates.size - 1) { onSelectDate(dates[index + 1]) }
        Spacer(Modifier.width(6.dp))
        EinkButton("⋮", Modifier.width(44.dp), onClick = onOpenMenu)
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, color = Ink.Black))
    }
}

/** Variante E Ink : un nombre fixe de chaînes par écran, on tourne les pages. */
@Composable
private fun PagedList(
    state: UiState,
    page: Int,
    onPageChange: (Int) -> Unit,
    onPageCount: (Int) -> Unit,
    onOpen: (PrimeTimeEntry) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pagerHeight = 59.dp
        val listHeight = maxHeight - pagerHeight
        val perPage = PER_PAGE
        val pages = ((state.entries.size + perPage - 1) / perPage).coerceAtLeast(1)
        val current = page.coerceIn(0, pages - 1)
        SideEffect { onPageCount(pages) }
        val swipe = with(LocalDensity.current) { 50.dp.toPx() }

        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .height(listHeight)
                    .fillMaxWidth()
                    .pointerInput(current, pages) {
                        var dx = 0f
                        var dy = 0f
                        detectDragGestures(
                            onDragStart = { dx = 0f; dy = 0f },
                            onDragEnd = {
                                // Glisser vers la gauche ou vers le haut : page suivante.
                                val d = if (abs(dx) > abs(dy)) dx else dy
                                if (abs(d) > swipe) {
                                    val target = if (d < 0) current + 1 else current - 1
                                    if (target in 0 until pages) onPageChange(target)
                                }
                            },
                        ) { change, amount ->
                            change.consume()
                            dx += amount.x
                            dy += amount.y
                        }
                    },
            ) {
                val rowHeight = listHeight / perPage
                state.entries.drop(current * perPage).take(perPage).forEach { entry ->
                    EinkRow(entry, Modifier.height(rowHeight).fillMaxWidth().einkClickable { onOpen(entry) })
                }
            }
            Rule(3.dp)
            Pager(current, pages, onPageChange)
        }
    }
}

@Composable
private fun EinkRow(entry: PrimeTimeEntry, modifier: Modifier) {
    val p = entry.primeTime ?: return
    Column(modifier) {
        Row(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp)) {
            Column(Modifier.width(84.dp)) {
                Text(
                    entry.channel.name,
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Black, lineHeight = 16.sp, color = Ink.Black),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                NetImage(p.iconUrl, Modifier.size(width = 84.dp, height = 63.dp))
            }
            Spacer(Modifier.width(10.dp))
            BoxWithConstraints(Modifier.weight(1f).fillMaxSize()) {
                // Le résumé occupe toute la hauteur restante, coupé proprement à la ligne.
                val titleLines = if (p.title.length > 28) 2 else 1
                val used = 20f * titleLines + 18f + 4f
                val summaryLines = ((maxHeight.value - used) / SUMMARY_LINE).toInt().coerceAtLeast(1)
                ProgrammeSummary(entry, summaryLines)
            }
        }
        Rule()
    }
}

/** Hauteur de ligne du résumé (dp/sp), en gros caractères pour l'E Ink. */
private const val SUMMARY_LINE = 21f

@Composable
private fun ProgrammeSummary(entry: PrimeTimeEntry, summaryLines: Int) {
    val p = entry.primeTime ?: return
    Column(verticalArrangement = Arrangement.Top) {
        Text(
            p.title,
            style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp, color = Ink.Black),
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${formatTime(p.start)} – ${formatTime(p.stop)}" + (p.categories.firstOrNull()?.let { " · $it" } ?: ""),
            style = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, color = Ink.Dark),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        val summary = p.description ?: p.subTitle ?: p.episode
        if (summary != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                summary,
                style = TextStyle(fontSize = 16.sp, lineHeight = SUMMARY_LINE.sp, color = Ink.Black),
                maxLines = summaryLines, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Pager(current: Int, pages: Int, onPageChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EinkButton("‹ Préc.", Modifier.width(100.dp), enabled = current > 0) { onPageChange(current - 1) }
        Text(
            "${current + 1} / $pages",
            Modifier.weight(1f),
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = Ink.Black),
        )
        EinkButton("Suiv. ›", Modifier.width(100.dp), enabled = current < pages - 1) { onPageChange(current + 1) }
    }
}

/** Variante couleur : liste en cartes, défilement fluide. */
@Composable
private fun SmoothList(state: UiState, listState: LazyListState, onOpen: (PrimeTimeEntry) -> Unit) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(state.entries, key = { it.channel.id }) { entry ->
            ColorCard(entry) { onOpen(entry) }
        }
    }
}

@Composable
private fun ColorCard(entry: PrimeTimeEntry, onClick: () -> Unit) {
    val p = entry.primeTime ?: return
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Ink.Card).einkClickable(onClick = onClick).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                entry.channel.name.uppercase(),
                Modifier.weight(1f),
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = Ink.Accent),
                maxLines = 1,
            )
            Text(
                "${formatTime(p.start)} – ${formatTime(p.stop)}",
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink.Dark),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row {
            NetImage(p.iconUrl, Modifier.size(width = 112.dp, height = 84.dp), maxPx = 200.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    p.title,
                    style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 21.sp, color = Ink.Black),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                p.categories.firstOrNull()?.let {
                    Text(it, style = TextStyle(fontSize = 12.sp, color = Ink.Dark), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val summary = p.description ?: p.subTitle ?: p.episode
                if (summary != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        summary,
                        style = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, color = Ink.Black),
                        maxLines = 3, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
