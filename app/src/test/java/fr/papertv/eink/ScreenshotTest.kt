package fr.papertv.eink

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import fr.papertv.eink.data.Channels
import fr.papertv.eink.data.ImageLoader
import fr.papertv.eink.data.Programme
import fr.papertv.eink.data.XmltvParser
import fr.papertv.eink.ui.AboutScreen
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
    private val out get() = "build/screenshots/" + if (COLOR) "color" else "eink"

    @Before
    fun setUp() {
        // Version couleur : capture au format d'un téléphone récent (Pixel, 1080×2400).
        if (COLOR) RuntimeEnvironment.setQualifiers("w411dp-h914dp-port-xxhdpi")
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
                e.primeTime?.iconUrl?.let { ImageLoader.load(it, 300) }
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
