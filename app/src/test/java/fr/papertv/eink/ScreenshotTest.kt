package fr.papertv.eink

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import fr.papertv.eink.data.Channels
import fr.papertv.eink.data.ImageLoader
import fr.papertv.eink.data.Programme
import fr.papertv.eink.data.XmltvParser
import androidx.compose.foundation.lazy.LazyListState
import fr.papertv.eink.data.StreamKind
import fr.papertv.eink.data.StreamSort
import fr.papertv.eink.data.TmdbRepository
import fr.papertv.eink.ui.AboutScreen
import fr.papertv.eink.ui.NetflixScreen
import fr.papertv.eink.ui.NetflixState
import fr.papertv.eink.ui.StreamDetailScreen
import fr.papertv.eink.ui.DatePickerScreen
import fr.papertv.eink.ui.COLOR
import fr.papertv.eink.ui.DetailScreen
import fr.papertv.eink.ui.EinkTheme
import fr.papertv.eink.ui.ListScreen
import fr.papertv.eink.ui.UiState
import fr.papertv.eink.ui.availableDates
import fr.papertv.eink.ui.buildEntries
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * Captures d'écran au format du Mudita Kompakt (800×480 px, 4,3", densité hdpi :
 * 320×533 dp, moins la barre d'état). Nécessite les fichiers XMLTV réels :
 *   ./gradlew recordRoborazziDebug -Ppapertv.tnt=/chemin/xmltv_tnt.xml -Ppapertv.fr=/chemin/xmltv_fr.xml
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w320dp-h509dp-port-hdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var state: UiState
    private val out get() = "build/screenshots/" + (if (COLOR) "color" else "eink") +
        (System.getProperty("papertv.device")?.takeIf { it in DEVICES }?.let { "-$it" } ?: "")

    companion object {
        /**
         * Formats d'écran supplémentaires. Bigme HiBreak S (noir et blanc) : 5,84", 1440×720 px,
         * 276 ppi, densité xhdpi supposée → 360×720 dp, moins la barre d'état.
         */
        val DEVICES = mapOf("hibreak-s" to "w360dp-h696dp-port-xhdpi")
    }

    @Before
    fun setUp() {
        // Autre appareil (papertv.device), sinon : Kompakt en E Ink, téléphone récent (Pixel) en couleur.
        val device = DEVICES[System.getProperty("papertv.device")]
        when {
            device != null -> RuntimeEnvironment.setQualifiers(device)
            COLOR -> RuntimeEnvironment.setQualifiers("w411dp-h914dp-port-xxhdpi")
        }
        val tnt = System.getProperty("papertv.tnt")?.let(::File)
        assumeTrue("Fichier XMLTV absent", tnt != null && tnt.exists())
        val today = LocalDate.parse(System.getProperty("papertv.today") ?: LocalDate.now().toString())
        val programmes = ArrayList<Programme>()
        programmes += tnt!!.inputStream().use { XmltvParser(Channels.TNT_IDS).parse(it) }
        System.getProperty("papertv.fr")?.let(::File)?.takeIf { it.exists() }?.let { f ->
            programmes += f.inputStream().use { XmltvParser(Channels.FRANCE_ONLY_IDS).parse(it) }
        }
        val dates = availableDates(programmes, today)
        state = UiState(
            loading = false, today = today, selected = today, availableDates = dates,
            entries = buildEntries(programmes, today), updatedAt = System.currentTimeMillis(),
        )
        // Précharge les vignettes pour que la capture soit complète.
        runBlocking {
            state.entries.forEach { e ->
                e.primeTime?.iconUrl?.let { url ->
                    // Hors réseau, une image de remplacement (papertv.placeholder) permet de vérifier la mise en page.
                    if (ImageLoader.load(url, 600) == null) {
                        System.getProperty("papertv.placeholder")?.let { ImageLoader.load(it, 600) }
                            ?.let { ImageLoader.put(url, it) }
                    }
                }
            }
        }
    }

    private fun list(page: Int, name: String) {
        compose.setContent {
            EinkTheme {
                ListScreen(state, page, {}, {}, {}, {}, {}, {})
            }
        }
        compose.onRoot().captureRoboImage("$out/$name.png")
    }

    @Test fun list_page1() = list(0, "1_accueil_page1")
    @Test fun list_page2() = list(1, "2_accueil_page2")
    @Test fun list_last() = list(99, "3_accueil_derniere_page")

    @Test
    fun detail() {
        val entry = state.entries.first { it.channel.id == "France2.fr" }
        compose.setContent { EinkTheme { DetailScreen(entry, onBack = {}) } }
        compose.onRoot().captureRoboImage("$out/4_detail_haut.png")
    }

    @Test
    fun detailScrolled() {
        val entry = state.entries.first { it.channel.id == "Arte.fr" }
        val scroll = ScrollState(0)
        compose.setContent { EinkTheme { DetailScreen(entry, scroll, onBack = {}) } }
        compose.waitForIdle()
        runBlocking { scroll.scrollTo(scroll.maxValue) }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("$out/5_detail_ensuite.png")
    }

    /** Rubrique Netflix : données TMDB réelles si une clé est fournie (papertv.tmdb ou TMDB_TOKEN). */
    private fun netflix(kind: StreamKind, sort: StreamSort): NetflixState {
        val token = System.getProperty("papertv.tmdb") ?: System.getenv("TMDB_TOKEN")
        assumeTrue("Clé TMDB absente", !token.isNullOrBlank())
        val repo = TmdbRepository(File(System.getProperty("java.io.tmpdir"), "tmdb-" + System.nanoTime()).apply { mkdirs() }) { token }
        val items = runBlocking { repo.list(kind, sort, force = true).first }
        runBlocking {
            items.take(4).forEach { t ->
                listOfNotNull(t.posterUrl, t.backdropUrl).forEach { url ->
                    if (ImageLoader.load(url, 600) == null) {
                        System.getProperty("papertv.placeholder")?.let { ImageLoader.load(it, 600) }?.let { ImageLoader.put(url, it) }
                    }
                }
            }
        }
        val details = runBlocking { items.take(1).associate { it.id to repo.details(it) } }
            .filterValues { it != null }.mapValues { it.value!! }
        return NetflixState(kind = kind, sort = sort, items = items, hasToken = true, details = details)
    }

    @Test
    fun netflixMovies() {
        val n = netflix(StreamKind.MOVIES, StreamSort.POPULAR)
        compose.setContent {
            EinkTheme { NetflixScreen(n, 0, {}, {}, LazyListState(), { _, _ -> }, {}, {}, {}, {}) }
        }
        compose.onRoot().captureRoboImage("$out/8_netflix_films.png")
    }

    @Test
    fun netflixSeriesTop() {
        val n = netflix(StreamKind.SERIES, StreamSort.TOP_RATED)
        compose.setContent {
            EinkTheme { NetflixScreen(n, 0, {}, {}, LazyListState(), { _, _ -> }, {}, {}, {}, {}) }
        }
        compose.onRoot().captureRoboImage("$out/9_netflix_series_mieux_notees.png")
    }

    @Test
    fun netflixDetail() {
        val n = netflix(StreamKind.MOVIES, StreamSort.POPULAR)
        val t = n.items.first()
        compose.setContent { EinkTheme { StreamDetailScreen(t, n.details[t.id], onBack = {}) } }
        compose.onRoot().captureRoboImage("$out/10_netflix_fiche.png")
    }

    @Test
    fun netflixToken() {
        compose.setContent {
            EinkTheme { NetflixScreen(NetflixState(hasToken = false), 0, {}, {}, LazyListState(), { _, _ -> }, {}, {}, {}, {}) }
        }
        compose.onRoot().captureRoboImage("$out/11_netflix_cle.png")
    }

    @Test
    fun about() {
        compose.setContent { EinkTheme { AboutScreen(state, {}, {}) } }
        compose.onRoot().captureRoboImage("$out/7_a_propos.png")
    }

    @Test
    fun datePicker() {
        compose.setContent { EinkTheme { DatePickerScreen(state, {}, {}) } }
        compose.onRoot().captureRoboImage("$out/6_choix_date.png")
    }
}
