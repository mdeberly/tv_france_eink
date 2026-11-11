package fr.papertv.eink

import fr.papertv.eink.data.PrimeTimeSelector
import fr.papertv.eink.data.XmltvParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class XmltvTest {
    private val programmes = javaClass.getResourceAsStream("/sample.xml")!!.use {
        XmltvParser(setOf("M6.fr", "TF1.fr")).parse(it)
    }
    private val evening = LocalDate.of(2026, 11, 11)

    @Test
    fun parsesOnlyWantedChannels() {
        assertEquals(6, programmes.size)
        val p = programmes.first { it.title.startsWith("La France a un incroyable talent") }
        assertEquals("Les auditions", p.subTitle)
        assertEquals("Saison 2, épisode 5", p.episode)
        assertEquals(listOf("Karine Le Marchand"), p.presenters)
        assertEquals("3/5", p.starRating)
        assertEquals("http://static-cdn.tv.sfr.net/img.jpg", p.iconUrl)
        assertEquals(60, p.durationMinutes)
    }

    @Test
    fun skipsAccessPrimeAndShortFillers() {
        val (prime, next) = PrimeTimeSelector.select(programmes.filter { it.channelId == "M6.fr" }, evening)
        assertEquals("La France a un incroyable talent", prime?.title)
        assertEquals("La France a un incroyable talent, ça continue", next?.title)
    }

    @Test
    fun keepsLongProgrammeStartedEarly() {
        val (prime, next) = PrimeTimeSelector.select(programmes.filter { it.channelId == "TF1.fr" }, evening)
        assertEquals("Football", prime?.title)
        assertEquals("Après match", next?.title)
    }

    @Test
    fun noDataForOtherEvening() {
        val (prime, _) = PrimeTimeSelector.select(programmes, evening.plusDays(3))
        assertNull(prime)
    }
}
