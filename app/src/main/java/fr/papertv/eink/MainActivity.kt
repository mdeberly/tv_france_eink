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
import fr.papertv.eink.data.StreamKind
import fr.papertv.eink.data.StreamSort
import fr.papertv.eink.data.StreamTitle
import fr.papertv.eink.ui.AboutScreen
import fr.papertv.eink.ui.NetflixScreen
import fr.papertv.eink.ui.NetflixState
import fr.papertv.eink.ui.NetflixViewModel
import fr.papertv.eink.ui.StreamDetailScreen
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
    private val netflixViewModel: NetflixViewModel by viewModels()

    /** Action des touches physiques (volume / page) : +1 page suivante, -1 page précédente. */
    private var onPageKey: ((Int) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ImageLoader.init(cacheDir)
        setContent {
            EinkTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val netflix by netflixViewModel.state.collectAsStateWithLifecycle()
                val actions = remember {
                    NetflixActions(
                        open = netflixViewModel::open,
                        select = { k, s -> netflixViewModel.select(k, s) },
                        refresh = netflixViewModel::refresh,
                        saveToken = netflixViewModel::saveToken,
                        loadDetails = netflixViewModel::loadDetails,
                    )
                }
                Box(Modifier.fillMaxSize().background(Ink.White).safeDrawingPadding()) {
                    App(
                        state = state,
                        onSelectDate = viewModel::select,
                        onRefresh = { viewModel.refresh(force = true) },
                        registerPageKeys = { onPageKey = it },
                        netflix = netflix,
                        netflixActions = actions,
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

/** Actions de la rubrique Netflix (reliées au NetflixViewModel). */
data class NetflixActions(
    val open: () -> Unit = {},
    val select: (StreamKind, StreamSort) -> Unit = { _, _ -> },
    val refresh: () -> Unit = {},
    val saveToken: (String) -> Unit = {},
    val loadDetails: (StreamTitle) -> Unit = {},
)

@Composable
fun App(
    state: UiState,
    onSelectDate: (LocalDate) -> Unit,
    onRefresh: () -> Unit,
    registerPageKeys: (((Int) -> Unit)?) -> Unit = {},
    initialChannel: String? = null,
    initialPage: Int = 0,
    netflix: NetflixState = NetflixState(),
    netflixActions: NetflixActions = NetflixActions(),
) {
    var page by rememberSaveable { mutableIntStateOf(initialPage) }
    var pageCount by remember { mutableIntStateOf(1) }
    var openChannel by rememberSaveable { mutableStateOf(initialChannel) }
    var datePicker by rememberSaveable { mutableStateOf(false) }
    var menu by rememberSaveable { mutableStateOf(false) }
    // Rubrique Netflix : ouverte ou non, page (E Ink), position (couleur), titre consulté.
    var netflixOpen by rememberSaveable { mutableStateOf(false) }
    var netflixPage by rememberSaveable { mutableIntStateOf(0) }
    var netflixPageCount by remember { mutableIntStateOf(1) }
    var netflixTitle by rememberSaveable { mutableStateOf<Long?>(null) }
    val netflixList = rememberLazyListState()
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
    val title = netflixTitle?.let { id -> netflix.items.firstOrNull { it.id == id } }
    when {
        netflixOpen && title != null -> {
            BackHandler { netflixTitle = null }
            val scroll = rememberScrollState()
            SideEffect { registerPageKeys { d -> scope.launch { scroll.pageBy(d) } } }
            StreamDetailScreen(
                title, netflix.details[title.id], scroll,
                onLoadDetails = { netflixActions.loadDetails(title) },
                onBack = { netflixTitle = null },
            )
        }
        netflixOpen -> {
            BackHandler { netflixOpen = false }
            SideEffect { registerPageKeys { d -> netflixPage = (netflixPage + d).coerceIn(0, netflixPageCount - 1) } }
            NetflixScreen(
                state = netflix,
                page = netflixPage,
                onPageChange = { netflixPage = it },
                onPageCount = { netflixPageCount = it },
                listState = netflixList,
                onSelect = { k, s ->
                    netflixActions.select(k, s)
                    netflixPage = 0
                    scope.launch { netflixList.scrollToItem(0) }
                },
                onOpen = { netflixTitle = it.id },
                onRefresh = netflixActions.refresh,
                onSaveToken = netflixActions.saveToken,
                onBack = { netflixOpen = false },
            )
        }
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
                onOpenNetflix = {
                    netflixOpen = true
                    netflixActions.open()
                },
            )
        }
    }
}
