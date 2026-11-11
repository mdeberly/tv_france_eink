package fr.papertv.eink.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.papertv.eink.data.Settings
import fr.papertv.eink.data.StreamDetails
import fr.papertv.eink.data.StreamKind
import fr.papertv.eink.data.StreamSort
import fr.papertv.eink.data.StreamTitle
import fr.papertv.eink.data.TmdbException
import fr.papertv.eink.data.TmdbRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class NetflixState(
    val kind: StreamKind = StreamKind.MOVIES,
    val sort: StreamSort = StreamSort.POPULAR,
    val items: List<StreamTitle> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val updatedAt: Long? = null,
    val hasToken: Boolean = false,
    val details: Map<Long, StreamDetails> = emptyMap(),
)

class NetflixViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = Settings(app)
    private val repository = TmdbRepository(app.cacheDir) { settings.tmdbToken }
    private val _state = MutableStateFlow(NetflixState(hasToken = settings.tmdbToken != null))
    val state: StateFlow<NetflixState> = _state
    private var job: Job? = null

    /** À l'ouverture de la rubrique : charge la liste si elle est vide. */
    fun open() {
        if (_state.value.items.isEmpty() && _state.value.hasToken) load(force = false)
    }

    fun select(kind: StreamKind = _state.value.kind, sort: StreamSort = _state.value.sort) {
        if (kind == _state.value.kind && sort == _state.value.sort) return
        _state.value = _state.value.copy(kind = kind, sort = sort, items = emptyList())
        load(force = false)
    }

    fun refresh() = load(force = true)

    fun saveToken(token: String) {
        settings.tmdbToken = token
        _state.value = _state.value.copy(hasToken = settings.tmdbToken != null, error = null)
        load(force = true)
    }

    fun loadDetails(t: StreamTitle) {
        if (t.id in _state.value.details) return
        viewModelScope.launch {
            repository.details(t)?.let { d -> _state.value = _state.value.copy(details = _state.value.details + (t.id to d)) }
        }
    }

    private fun load(force: Boolean) {
        job?.cancel()
        val kind = _state.value.kind
        val sort = _state.value.sort
        _state.value = _state.value.copy(loading = true, error = null)
        job = viewModelScope.launch {
            _state.value = try {
                val (items, at) = repository.list(kind, sort, force)
                _state.value.copy(loading = false, items = items, updatedAt = at)
            } catch (e: CancellationException) {
                throw e
            } catch (e: TmdbException) {
                _state.value.copy(loading = false, error = e.message)
            } catch (e: Exception) {
                _state.value.copy(loading = false, error = "connexion impossible")
            }
        }
    }
}
