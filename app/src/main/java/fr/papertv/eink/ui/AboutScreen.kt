package fr.papertv.eink.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.papertv.eink.BuildConfig

/** Page de la dernière version publiée, pour vérifier qu'on est à jour. */
const val RELEASES_URL = "https://github.com/mdeberly/tv_france_eink/releases/latest"
const val AUTHOR_URL = "https://www.deberly.fr"

/** Menu : mise à jour du programme et « À propos ». */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AboutScreen(state: UiState, onRefresh: () -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Ink.White)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EinkButton("‹ Retour", Modifier.width(110.dp), onClick = onClose)
            Text(
                "Paper TV",
                Modifier.padding(start = 12.dp),
                style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Black, color = Ink.Black),
            )
        }
        Rule(3.dp)
        CompositionLocalProvider(LocalOverscrollConfiguration provides if (COLOR) LocalOverscrollConfiguration.current else null) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Section("Programme")
                Text(
                    when {
                        state.loading -> "Mise à jour en cours…"
                        state.updatedAt != null -> "Dernière mise à jour : " + formatDateTime(state.updatedAt) +
                            (state.error?.let { "\nÉchec de la dernière tentative ($it)." } ?: "")
                        else -> "Pas encore de données."
                    },
                    style = body,
                )
                Spacer(Modifier.height(10.dp))
                EinkButton(
                    if (state.loading) "Mise à jour…" else "↻ Actualiser le programme",
                    Modifier.fillMaxWidth(),
                    enabled = !state.loading,
                    filled = true,
                    onClick = onRefresh,
                )

                Spacer(Modifier.height(24.dp))
                Section("À propos")
                Text(
                    "Application créée par Maximilien DEBERLY, pharmacien, avec l'aide de l'IA Claude pour le code.",
                    style = body,
                )
                Spacer(Modifier.height(8.dp))
                Link("www.deberly.fr", AUTHOR_URL)
                Spacer(Modifier.height(14.dp))
                Text(
                    "Version ${BuildConfig.VERSION_NAME} (" + (if (COLOR) "couleur" else "E Ink") + ")",
                    style = body,
                )
                Text("Dernière version disponible :", style = body)
                Link(RELEASES_URL.removePrefix("https://"), RELEASES_URL)

                Spacer(Modifier.height(24.dp))
                Section("Sources")
                Text(
                    "Programmes TV : XML TV Fr (xmltvfr.fr), d'après les guides des chaînes. " +
                        "Images : © chaînes et diffuseurs.\n\nNetflix : " + TMDB_ATTRIBUTION,
                    style = small,
                )
            }
        }
    }
}

private val body = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, color = Ink.Black)
private val small = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = Ink.Dark)

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(),
        Modifier.padding(bottom = 6.dp),
        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = Ink.Accent),
    )
}

@Composable
private fun Link(label: String, url: String) {
    val style = TextLinkStyles(
        SpanStyle(
            color = if (COLOR) Ink.Accent else Ink.Black,
            fontWeight = FontWeight.Bold,
            textDecoration = TextDecoration.Underline,
        )
    )
    Text(
        buildAnnotatedString { withLink(LinkAnnotation.Url(url, style)) { append(label) } },
        style = body,
    )
}
