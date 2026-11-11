package fr.papertv.eink.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Choisit, pour une soirée donnée, le programme de « prime time » d'une chaîne et celui
 * qui le suit. Les grilles contiennent beaucoup de courts programmes (météo, pubs,
 * mini-séries de 3 min) et des émissions d'access (20h40) qu'il faut écarter.
 */
object PrimeTimeSelector {
    val PARIS: ZoneId = ZoneId.of("Europe/Paris")

    /** Durée minimale d'un programme pour être retenu comme prime time. */
    private const val MIN_PRIME_MINUTES = 30L

    /** Durée minimale du programme « suivant » (on saute les interludes). */
    private const val MIN_NEXT_MINUTES = 15L

    /** La soirée à afficher : celle du jour, ou celle de la veille avant 3 h du matin. */
    fun eveningFor(now: ZonedDateTime): LocalDate =
        if (now.hour < 3) now.toLocalDate().minusDays(1) else now.toLocalDate()

    fun select(programmes: List<Programme>, evening: LocalDate, zone: ZoneId = PARIS): Pair<Programme?, Programme?> {
        val sorted = programmes.sortedBy { it.start }
        fun at(h: Int, m: Int) = evening.atTime(LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()

        val t2030 = at(20, 30)
        val t2035 = at(20, 35)
        val t2050 = at(20, 50)
        val t2100 = at(21, 0)
        val t2110 = at(21, 10)
        val t2130 = at(21, 30)
        val t2200 = at(22, 0)

        val prime =
            // 1. Un long programme (film, match…) commencé tôt et toujours à l'antenne à 21 h.
            sorted.firstOrNull { it.start in t2030..t2100 && it.stop > t2100 && it.durationMinutes >= 75 }
                // 2. Le premier programme consistant qui démarre entre 20h50 et 21h30.
                ?: sorted.firstOrNull { it.start in t2050..t2130 && it.durationMinutes >= MIN_PRIME_MINUTES }
                // 3. Fenêtre élargie 20h35 – 22h.
                ?: sorted.firstOrNull { it.start in t2035 until t2200 && it.durationMinutes >= MIN_PRIME_MINUTES }
                // 4. À défaut (chaînes d'info en continu…) : ce qui est diffusé à 21h10.
                ?: sorted.firstOrNull { it.start <= t2110 && it.stop > t2110 }

        val next = prime?.let { p ->
            sorted.firstOrNull { it.start >= p.stop && it.durationMinutes >= MIN_NEXT_MINUTES }
        }
        return prime to next
    }
}
