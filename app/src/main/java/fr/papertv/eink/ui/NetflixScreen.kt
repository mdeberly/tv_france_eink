package fr.papertv.eink.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.papertv.eink.data.StreamDetails
import fr.papertv.eink.data.StreamKind
import fr.papertv.eink.data.StreamSort
import fr.papertv.eink.data.StreamTitle
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

const val TMDB_KEY_URL = "https://www.themoviedb.org/settings/api"
const val TMDB_ATTRIBUTION = "Données et images : TMDB · disponibilités : JustWatch. " +
    "Ce produit utilise l'API TMDB mais n'est ni approuvé ni certifié par TMDB."

private val FR = Locale.FRANCE

/** « 7,8/10 » */
fun formatRating(v: Double): String = String.format(FR, "%.1f/10", v)

/** « 12 345 votes » */
fun formatVotes(n: Int): String = NumberFormat.getIntegerInstance(FR).format(n).replace(' ', ' ') +
    if (n > 1) " votes" else " vote"

@Composable
fun NetflixScreen(
    state: NetflixState,
    page: Int,
    onPageChange: (Int) -> Unit,
    onPageCount: (Int) -> Unit,
    listState: LazyListState,
    onSelect: (StreamKind, StreamSort) -> Unit,
    onOpen: (StreamTitle) -> Unit,
    onRefresh: () -> Unit,
    onSaveToken: (String) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Ink.White)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EinkButton("‹ TV", Modifier.width(76.dp), onClick = onBack)
            Text(
                "Netflix",
                Modifier.weight(1f).padding(start = 12.dp),
                style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Black, color = Ink.Accent),
            )
            if (state.hasToken) {
                EinkButton(if (state.loading) "…" else "↻", Modifier.width(44.dp), enabled = !state.loading, onClick = onRefresh)
            }
        }
        Rule(3.dp)
        if (!state.hasToken) {
            TokenForm(state.error, onSaveToken)
            return@Column
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = if (COLOR) 6.dp else 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StreamKind.entries.forEach { k ->
                Chip(k.label, selected = state.kind == k, Modifier.weight(1f)) { onSelect(k, state.sort) }
            }
            Spacer(Modifier.width(4.dp))
            StreamSort.entries.forEach { s ->
                Chip(s.label, selected = state.sort == s, Modifier.weight(1.45f)) { onSelect(state.kind, s) }
            }
        }
        Rule()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.items.isEmpty() && state.loading -> Centered("Chargement…")
                state.items.isEmpty() -> Centered(
                    "Liste indisponible" + (state.error?.let { " ($it)" } ?: "") + ".\n\nVérifiez la connexion puis touchez « ↻ »."
                )
                COLOR -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.items, key = { it.id }) { t ->
                        Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Ink.Card).einkClickable { onOpen(t) }.padding(12.dp)) {
                            StreamRow(t, summaryLines = 4)
                        }
                    }
                }
                else -> PagedTitles(state.items, page, onPageChange, onPageCount, onOpen)
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = if (COLOR) RoundedCornerShape(10.dp) else androidx.compose.ui.graphics.RectangleShape
    Box(
        modifier
            .height(if (COLOR) 36.dp else 32.dp)
            .clip(shape)
            .background(if (selected) Ink.Accent else if (COLOR) Ink.Card else Ink.White)
            .border(if (COLOR) 1.dp else 2.dp, if (selected || !COLOR) Ink.Accent else Ink.Line, shape)
            .einkClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = TextStyle(
                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = if (selected) androidx.compose.ui.graphics.Color.White else Ink.Black,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun Centered(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, color = Ink.Black))
    }
}

/** Version E Ink : 3 titres par écran, on tourne les pages. */
@Composable
private fun PagedTitles(
    items: List<StreamTitle>,
    page: Int,
    onPageChange: (Int) -> Unit,
    onPageCount: (Int) -> Unit,
    onOpen: (StreamTitle) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pagerHeight = 59.dp
        val listHeight = maxHeight - pagerHeight
        val perPage = PER_PAGE
        val pages = ((items.size + perPage - 1) / perPage).coerceAtLeast(1)
        val current = page.coerceIn(0, pages - 1)
        SideEffect { onPageCount(pages) }
        val swipe = with(LocalDensity.current) { 50.dp.toPx() }
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.height(listHeight).fillMaxWidth().pointerInput(current, pages) {
                    var dx = 0f
                    var dy = 0f
                    detectDragGestures(
                        onDragStart = { dx = 0f; dy = 0f },
                        onDragEnd = {
                            val d = if (abs(dx) > abs(dy)) dx else dy
                            if (abs(d) > swipe) {
                                val target = if (d < 0) current + 1 else current - 1
                                if (target in 0 until pages) onPageChange(target)
                            }
                        },
                    ) { change, amount ->
                        change.consume(); dx += amount.x; dy += amount.y
                    }
                },
            ) {
                val rowHeight = listHeight / perPage
                items.drop(current * perPage).take(perPage).forEach { t ->
                    Column(Modifier.height(rowHeight).fillMaxWidth().einkClickable { onOpen(t) }) {
                        // Hauteur restante pour le résumé : ligne moins marges, titre, année/genres et note.
                        val titleLines = if (t.title.length > 24) 2 else 1
                        val lines = ((rowHeight.value - 17f - 20f * titleLines - 17f - 19f) / 20f).toInt()
                        Box(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp)) {
                            StreamRow(t, summaryLines = lines.coerceAtLeast(1))
                        }
                        Rule()
                    }
                }
            }
            Rule(3.dp)
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EinkButton("‹ Préc.", Modifier.width(100.dp), enabled = current > 0) { onPageChange(current - 1) }
                Text(
                    "${current + 1} / $pages", Modifier.weight(1f),
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = Ink.Black),
                )
                EinkButton("Suiv. ›", Modifier.width(100.dp), enabled = current < pages - 1) { onPageChange(current + 1) }
            }
        }
    }
}

/** Ligne de liste : affiche, titre, année et genres, note moyenne et votants, résumé. */
@Composable
private fun StreamRow(t: StreamTitle, summaryLines: Int) {
    Row {
        NetImage(t.posterUrl, Modifier.size(width = 66.dp, height = 99.dp), maxPx = 140.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                t.title,
                style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp, color = Ink.Black),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(t.year, t.genres.take(2).joinToString(", ").takeIf { it.isNotEmpty() }).joinToString(" · "),
                style = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, color = Ink.Dark),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            RatingLine(t, big = false)
            t.overview?.let {
                Text(
                    it,
                    style = TextStyle(fontSize = if (COLOR) 14.sp else 15.sp, lineHeight = if (COLOR) 19.sp else 20.sp, color = Ink.Black),
                    maxLines = summaryLines, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RatingLine(t: StreamTitle, big: Boolean) {
    val text = if (t.voteCount == 0) "Pas encore de note" else
        "★ ${formatRating(t.voteAverage)} · ${formatVotes(t.voteCount)}"
    Text(
        text,
        style = TextStyle(
            fontSize = if (big) 17.sp else 13.sp,
            lineHeight = if (big) 24.sp else 19.sp,
            fontWeight = FontWeight.Bold,
            color = if (COLOR) Ink.Accent else Ink.Black,
        ),
        maxLines = 1,
    )
}

/** Saisie de la clé TMDB (gratuite) au premier usage. */
@Composable
private fun TokenForm(error: String?, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(
            "La rubrique Netflix utilise TMDB (The Movie Database) pour la liste des films et séries " +
                "disponibles sur Netflix en France, les résumés, les affiches et les notes des spectateurs.",
            style = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, color = Ink.Black),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Il faut une clé gratuite : créez un compte TMDB, puis copiez votre « clé d'API » ou votre " +
                "« jeton d'accès en lecture » depuis :",
            style = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, color = Ink.Black),
        )
        Text(
            buildAnnotatedString {
                withLink(LinkAnnotation.Url(TMDB_KEY_URL, TextLinkStyles(SpanStyle(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline, color = Ink.Accent)))) {
                    append("themoviedb.org/settings/api")
                }
            },
            style = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
        )
        Spacer(Modifier.height(14.dp))
        BasicTextField(
            value = value,
            onValueChange = { value = it.trim() },
            singleLine = true,
            textStyle = TextStyle(fontSize = 15.sp, color = Ink.Black),
            cursorBrush = SolidColor(Ink.Black),
            modifier = Modifier.fillMaxWidth().border(2.dp, Ink.Accent, if (COLOR) RoundedCornerShape(10.dp) else androidx.compose.ui.graphics.RectangleShape)
                .padding(12.dp),
            decorationBox = { inner ->
                if (value.isEmpty()) Text("Coller la clé ici", style = TextStyle(fontSize = 15.sp, color = Ink.Mid))
                inner()
            },
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text("Erreur : $it", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink.Accent))
        }
        Spacer(Modifier.height(12.dp))
        EinkButton("Enregistrer", Modifier.fillMaxWidth(), enabled = value.length >= 32, filled = true) { onSave(value) }
        Spacer(Modifier.height(16.dp))
        Text(TMDB_ATTRIBUTION, style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = Ink.Dark))
    }
}

/** Fiche d'un film ou d'une série. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StreamDetailScreen(
    t: StreamTitle,
    details: StreamDetails?,
    scroll: ScrollState = rememberScrollState(),
    onLoadDetails: () -> Unit = {},
    onBack: () -> Unit,
) {
    LaunchedEffect(t.id) { onLoadDetails() }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(Ink.White)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EinkButton("‹ Retour", Modifier.width(110.dp), onClick = onBack)
            Text(
                if (t.isMovie) "Film Netflix" else "Série Netflix",
                Modifier.weight(1f).padding(start = 12.dp),
                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Black, color = Ink.Accent),
                maxLines = 1,
            )
        }
        Rule(3.dp)
        CompositionLocalProvider(LocalOverscrollConfiguration provides if (COLOR) LocalOverscrollConfiguration.current else null) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(14.dp)) {
                val image = t.backdropUrl ?: t.posterUrl
                if (image != null) {
                    NetImage(image, Modifier.fillMaxWidth().aspectRatio(16f / 9f), maxPx = 400.dp)
                    Spacer(Modifier.height(12.dp))
                }
                Text(t.title, style = TextStyle(fontSize = 23.sp, lineHeight = 27.sp, fontWeight = FontWeight.Black, color = Ink.Black))
                t.originalTitle?.let {
                    Text(it, style = TextStyle(fontSize = 14.sp, fontStyle = FontStyle.Italic, color = Ink.Dark))
                }
                val facts = listOfNotNull(
                    if (t.isMovie) "Film" else "Série",
                    t.year,
                    details?.runtimeMinutes?.let { formatDuration(it.toLong()) },
                    details?.seasons?.let { if (it > 1) "$it saisons" else "1 saison" },
                    details?.episodes?.let { "$it épisodes" },
                ).joinToString(" · ")
                Text(facts, style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = Ink.Dark))
                if (t.genres.isNotEmpty()) {
                    Text(t.genres.joinToString(", "), style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = Ink.Dark))
                }
                Spacer(Modifier.height(6.dp))
                RatingLine(t, big = true)
                Text(
                    "Moyenne des notes des spectateurs TMDB",
                    style = TextStyle(fontSize = 12.sp, color = Ink.Dark),
                )
                details?.tagline?.let {
                    Spacer(Modifier.height(10.dp))
                    Text("« $it »", style = TextStyle(fontSize = 16.sp, fontStyle = FontStyle.Italic, lineHeight = 22.sp, color = Ink.Black))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    t.overview ?: "Pas de résumé en français.",
                    style = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, color = Ink.Black),
                )
                if (details != null) {
                    val credits = listOfNotNull(
                        details.creators.takeIf { it.isNotEmpty() }?.let { (if (t.isMovie) "Réalisation : " else "Création : ") + it.joinToString(", ") },
                        details.cast.takeIf { it.isNotEmpty() }?.let { "Avec : " + it.joinToString(", ") },
                    )
                    if (credits.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        credits.forEach { Text(it, style = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, color = Ink.Black)) }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Rule()
                Spacer(Modifier.height(6.dp))
                Text("Disponible sur Netflix (France), en abonnement.", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink.Black))
                Text(TMDB_ATTRIBUTION, style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, color = Ink.Dark))
            }
        }
        if (!COLOR && scroll.maxValue > 0) {
            Rule(3.dp)
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EinkButton("▲ Haut", Modifier.width(100.dp), enabled = scroll.value > 0) { scope.launch { scroll.pageBy(-1) } }
                val step = (scroll.viewportSize * 0.9f).toInt().coerceAtLeast(1)
                Text(
                    "${1 + (scroll.value + step - 1) / step} / ${1 + (scroll.maxValue + step - 1) / step}",
                    Modifier.weight(1f),
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = Ink.Black),
                )
                EinkButton("Bas ▼", Modifier.width(100.dp), enabled = scroll.value < scroll.maxValue) { scope.launch { scroll.pageBy(1) } }
            }
        }
    }
}
