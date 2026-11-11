package fr.papertv.eink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FULL = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE)

/**
 * Choix de la soirée : toutes les dates disponibles tiennent sur un seul écran
 * (pas de défilement, chaque ligne se partage la hauteur).
 */
@Composable
fun DatePickerScreen(state: UiState, onPick: (LocalDate) -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Ink.White)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EinkButton("‹ Retour", Modifier.width(110.dp), onClick = onClose)
            Text(
                "Choisir la soirée",
                Modifier.padding(start = 12.dp),
                style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Black),
            )
        }
        Rule(3.dp)
        Column(Modifier.weight(1f).fillMaxWidth()) {
            state.availableDates.forEach { date ->
                val selected = date == state.selected
                Column(
                    Modifier.weight(1f).heightIn(min = 34.dp).fillMaxWidth()
                        .background(if (selected) Ink.Highlight else Ink.White)
                        .einkClickable { onPick(date) },
                ) {
                    Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                        val label = eveningLabel(date, state.today)
                        val full = FULL.format(date).replaceFirstChar { it.uppercase() }
                        Text(
                            if (label.startsWith(full.substringBefore(' '))) full else "$label · $full",
                            style = TextStyle(
                                fontSize = 17.sp,
                                fontWeight = if (date == state.today || selected) FontWeight.Black else FontWeight.Normal,
                                color = if (selected) Ink.OnHighlight else Ink.Black,
                            ),
                        )
                    }
                    Rule()
                }
            }
        }
    }
}
