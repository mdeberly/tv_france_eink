package fr.papertv.eink.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.papertv.eink.data.Channels
import fr.papertv.eink.data.PrimeTimeEntry
import fr.papertv.eink.data.PrimeTimeSelector
import fr.papertv.eink.data.Programme
import fr.papertv.eink.data.TvRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZonedDateTime

data class UiState(
    val loading: Boolean = true,
    /** La soirée « du jour » (avant 3 h du matin, celle de la veille). */
    val today: LocalDate = currentEvening(),
    /** La soirée choisie avec le sélecteur de date. */
    val selected: LocalDate = today,
    /** Soirées consultables : de la veille jusqu'à la dernière couverte par les données. */
    val availableDates: List<LocalDate> = listOf(today),
    val entries: List<PrimeTimeEntry> = emptyList(),
    val updatedAt: Long? = null,
    val error: String? = null,
)

fun currentEvening(): LocalDate = PrimeTimeSelector.eveningFor(ZonedDateTime.now(PrimeTimeSelector.PARIS))

fun buildEntries(programmes: List<Programme>, evening: LocalDate): List<PrimeTimeEntry> {
    val byChannel = programmes.groupBy { it.channelId }
    return Channels.ALL.mapNotNull { channel ->
        val list = byChannel[channel.id] ?: return@mapNotNull null
        val (prime, next) = PrimeTimeSelector.select(list, evening)
        if (prime == null) null else PrimeTimeEntry(channel, prime, next)
    }
}

/**
 * Soirées disponibles : de la veille de [today] jusqu'à la dernière soirée pour laquelle
 * la majorité des chaînes TNT ont un programme couvrant 22 h.
 */
fun availableDates(programmes: List<Programme>, today: LocalDate): List<LocalDate> {
    val lastStopByChannel = programmes.groupBy { it.channelId }.mapValues { (_, l) -> l.maxOf { it.stop } }
    if (lastStopByChannel.isEmpty()) return listOf(today)
    // Médiane des fins de grille : une chaîne mal renseignée ne réduit pas la plage.
    val median = lastStopByChannel.values.sorted()[lastStopByChannel.size / 2]
    val dates = ArrayList<LocalDate>()
    var d = today.minusDays(1)
    while (true) {
        val t22 = d.atTime(22, 0).atZone(PrimeTimeSelector.PARIS).toInstant().toEpochMilli()
        if (d > today && median < t22) break
        dates += d
        d = d.plusDays(1)
        if (dates.size > 30) break
    }
    return dates
}

class TvViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = TvRepository(app.cacheDir)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state
    private var programmes: List<Programme> = emptyList()
    private var job: Job? = null

    init {
        refresh(force = false)
    }

    /** Recharge si la journée a changé depuis le dernier affichage (application restée ouverte). */
    fun onResume() {
        if (currentEvening() != _state.value.today) refresh(force = false, resetDate = true)
    }

    fun select(date: LocalDate) {
        val s = _state.value
        if (date == s.selected || date !in s.availableDates) return
        _state.value = s.copy(selected = date, entries = buildEntries(programmes, date))
    }

    fun refresh(force: Boolean, resetDate: Boolean = false) {
        if (job?.isActive == true) return
        val today = currentEvening()
        val previous = _state.value
        val selected = if (resetDate || previous.today != today) today else previous.selected
        _state.value = previous.copy(loading = true, error = null, today = today, selected = selected)
        job = viewModelScope.launch {
            val result = repository.load(today, force) { partial ->
                if (partial.programmes.isNotEmpty()) publish(partial.programmes, partial.updatedAt, null, loading = true)
            }
            publish(result.programmes, result.updatedAt, result.error, loading = false)
        }
    }

    private fun publish(list: List<Programme>, updatedAt: Long?, error: String?, loading: Boolean) {
        programmes = list
        val s = _state.value
        val dates = availableDates(list, s.today)
        val selected = if (s.selected in dates) s.selected else s.today
        _state.value = s.copy(
            loading = loading,
            selected = selected,
            availableDates = dates,
            entries = buildEntries(list, selected),
            updatedAt = updatedAt,
            error = error,
        )
    }
}
