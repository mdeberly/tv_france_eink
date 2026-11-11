package fr.papertv.eink

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.papertv.eink.data.ImageLoader
import fr.papertv.eink.ui.AboutScreen
import fr.papertv.eink.ui.DatePickerScreen
import fr.papertv.eink.ui.DetailScreen
import fr.papertv.eink.ui.EinkTheme
import fr.papertv.eink.ui.Ink
import fr.papertv.eink.ui.ListScreen
import fr.papertv.eink.ui.TvViewModel
import fr.papertv.eink.ui.UiState
import fr.papertv.eink.ui.pageBy
import kotlinx.coroutines.launch
import java.time.LocalDate
import androidx.compose.foundation.background

class MainActivity : ComponentActivity() {
    private val viewModel: TvViewModel by viewModels()

    /** Action des touches physiques (volume / page) : +1 page suivante, -1 page précédente. */
    private var onPageKey: ((Int) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ImageLoader.init(cacheDir)
        setContent {
            EinkTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                Box(Modifier.fillMaxSize().background(Ink.White).safeDrawingPadding()) {
                    App(
                        state = state,
                        onSelectDate = viewModel::select,
                        onRefresh = { viewModel.refresh(force = true) },
                        registerPageKeys = { onPageKey = it },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val direction = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_DOWN -> 1
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_UP -> -1
            else -> 0
        }
        val handler = onPageKey
        // Version couleur : les touches de volume gardent leur rôle habituel.
        if (!BuildConfig.COLOR && direction != 0 && handler != null) {
            if (event.action == KeyEvent.ACTION_DOWN) handler(direction)
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

@Composable
fun App(
    state: UiState,
    onSelectDate: (LocalDate) -> Unit,
    onRefresh: () -> Unit,
    registerPageKeys: (((Int) -> Unit)?) -> Unit = {},
    initialChannel: String? = null,
    initialPage: Int = 0,
) {
    var page by rememberSaveable { mutableIntStateOf(initialPage) }
    var pageCount by remember { mutableIntStateOf(1) }
    var openChannel by rememberSaveable { mutableStateOf(initialChannel) }
    var datePicker by rememberSaveable { mutableStateOf(false) }
    var menu by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Position dans la liste (version couleur), gardée pendant la consultation d'une fiche.
    val listState = rememberLazyListState()
    // Nouvelle soirée : on revient au début de la liste.
    val selectDate: (LocalDate) -> Unit = {
        onSelectDate(it)
        page = 0
        scope.launch { listState.scrollToItem(0) }
    }

    val entry = openChannel?.let { id -> state.entries.firstOrNull { it.channel.id == id } }
    when {
        menu -> {
            BackHandler { menu = false }
            SideEffect { registerPageKeys(null) }
            AboutScreen(state, onRefresh = onRefresh, onClose = { menu = false })
        }
        datePicker -> {
            BackHandler { datePicker = false }
            SideEffect { registerPageKeys(null) }
            DatePickerScreen(state, onPick = { selectDate(it); datePicker = false }, onClose = { datePicker = false })
        }
        entry != null -> {
            BackHandler { openChannel = null }
            val scroll = rememberScrollState()
            SideEffect { registerPageKeys { d -> scope.launch { scroll.pageBy(d) } } }
            DetailScreen(entry, scroll, onBack = { openChannel = null })
        }
        else -> {
            SideEffect {
                registerPageKeys { d -> page = (page + d).coerceIn(0, pageCount - 1) }
            }
            ListScreen(
                state = state,
                page = page,
                onPageChange = { page = it },
                onPageCount = { pageCount = it },
                onOpen = { openChannel = it.channel.id },
                onSelectDate = selectDate,
                onOpenDatePicker = { datePicker = true },
                onOpenMenu = { menu = true },
                listState = listState,
            )
        }
    }
}
