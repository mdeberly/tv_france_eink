package fr.papertv.eink.data

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Parseur XMLTV en flux (XmlPull) : ne garde que les programmes des chaînes demandées
 * et dont la fin est postérieure à [notBefore]. Le fichier « France » pèse ~70 Mo une fois
 * décompressé, d'où le parcours en flux et l'arrêt anticipé.
 */
class XmltvParser(
    private val wantedChannels: Set<String>,
    private val notBefore: Long = Long.MIN_VALUE,
) {

    fun parse(input: InputStream): List<Programme> {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, "UTF-8")

        val out = ArrayList<Programme>()
        val seenAll = HashSet<String>()
        var lastChannel: String? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "programme") {
                val channel = parser.getAttributeValue(null, "channel")
                if (channel != lastChannel) {
                    // Les programmes sont regroupés par chaîne : quand on quitte une chaîne
                    // voulue, on la considère terminée. Arrêt dès que toutes sont lues.
                    if (lastChannel != null && lastChannel in wantedChannels) seenAll += lastChannel
                    if (seenAll.size == wantedChannels.size) break
                    lastChannel = channel
                }
                if (channel in wantedChannels) {
                    readProgramme(parser, channel)?.let { if (it.stop > notBefore) out += it }
                } else {
                    skip(parser)
                }
            }
            event = parser.next()
        }
        return out
    }

    private fun readProgramme(p: XmlPullParser, channel: String): Programme? {
        val start = parseDate(p.getAttributeValue(null, "start")) ?: return skipNull(p)
        val stop = parseDate(p.getAttributeValue(null, "stop")) ?: return skipNull(p)
        var title: String? = null
        var subTitle: String? = null
        var desc: String? = null
        val categories = ArrayList<String>()
        var icon: String? = null
        var episode: String? = null
        var year: String? = null
        var country: String? = null
        val directors = ArrayList<String>()
        val actors = ArrayList<String>()
        val presenters = ArrayList<String>()
        var rating: String? = null
        var star: String? = null

        val depth = p.depth
        while (true) {
            val e = p.next()
            if (e == XmlPullParser.END_TAG && p.depth == depth) break
            if (e == XmlPullParser.END_DOCUMENT) break
            if (e != XmlPullParser.START_TAG) continue
            when (p.name) {
                "title" -> title = title ?: readText(p)
                "sub-title" -> subTitle = subTitle ?: readText(p)
                "desc" -> desc = desc ?: readText(p)
                "category" -> readText(p)?.let { categories += it }
                "icon" -> {
                    if (icon == null) icon = p.getAttributeValue(null, "src")
                    skip(p)
                }
                "episode-num" -> {
                    val system = p.getAttributeValue(null, "system")
                    val text = readText(p)
                    if (system == "onscreen" || episode == null) episode = text?.let { formatEpisode(system, it) }
                }
                "date" -> year = readText(p)
                "country" -> country = country ?: readText(p)
                "credits" -> readCredits(p, directors, actors, presenters)
                "rating" -> rating = rating ?: readValue(p)
                "star-rating" -> star = star ?: readValue(p)
                else -> skip(p)
            }
        }
        val t = title?.trim().orEmpty()
        if (t.isEmpty()) return null
        return Programme(
            channelId = channel,
            start = start,
            stop = stop,
            title = t,
            subTitle = subTitle?.trim()?.takeIf { it.isNotEmpty() && it != t },
            description = desc?.trim()?.takeIf { it.isNotEmpty() },
            categories = categories.distinct(),
            iconUrl = icon,
            episode = episode,
            year = year?.take(4),
            country = country,
            directors = directors,
            actors = actors.take(8),
            presenters = presenters,
            rating = rating,
            starRating = star,
        )
    }

    private fun readCredits(
        p: XmlPullParser,
        directors: MutableList<String>,
        actors: MutableList<String>,
        presenters: MutableList<String>,
    ) {
        val depth = p.depth
        while (true) {
            val e = p.next()
            if (e == XmlPullParser.END_TAG && p.depth == depth) return
            if (e == XmlPullParser.END_DOCUMENT) return
            if (e != XmlPullParser.START_TAG) continue
            val target = when (p.name) {
                "director" -> directors
                "actor" -> actors
                "presenter" -> presenters
                else -> null
            }
            val text = readText(p)
            if (target != null && !text.isNullOrBlank()) target += text.trim()
        }
    }

    /** Lit le contenu de `<value>` d'un élément `<rating>` / `<star-rating>`. */
    private fun readValue(p: XmlPullParser): String? {
        var value: String? = null
        val depth = p.depth
        while (true) {
            val e = p.next()
            if (e == XmlPullParser.END_TAG && p.depth == depth) return value
            if (e == XmlPullParser.END_DOCUMENT) return value
            if (e == XmlPullParser.START_TAG && p.name == "value") value = readText(p)
        }
    }

    /** Lit le texte d'un élément simple et se positionne sur sa balise fermante. */
    private fun readText(p: XmlPullParser): String? {
        val sb = StringBuilder()
        val depth = p.depth
        while (true) {
            val e = p.next()
            when {
                e == XmlPullParser.TEXT -> sb.append(p.text)
                e == XmlPullParser.END_TAG && p.depth == depth -> break
                e == XmlPullParser.END_DOCUMENT -> break
            }
        }
        return sb.toString().trim().takeIf { it.isNotEmpty() }
    }

    private fun skip(p: XmlPullParser) {
        if (p.eventType != XmlPullParser.START_TAG) return
        var level = 1
        while (level > 0) {
            when (p.next()) {
                XmlPullParser.START_TAG -> level++
                XmlPullParser.END_TAG -> level--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    private fun skipNull(p: XmlPullParser): Programme? {
        skip(p)
        return null
    }

    companion object {
        private val FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z")

        fun parseDate(raw: String?): Long? = try {
            raw?.let { OffsetDateTime.parse(it.trim(), FORMAT).toInstant().toEpochMilli() }
        } catch (_: Exception) {
            null
        }

        /** Rend lisible « xmltv_ns » (0-based « s.e.p ») ; les autres systèmes sont gardés tels quels. */
        fun formatEpisode(system: String?, raw: String): String? {
            if (system != "xmltv_ns") return raw.trim().takeIf { it.isNotEmpty() }
            val parts = raw.split('.')
            fun num(i: Int) = parts.getOrNull(i)?.substringBefore('/')?.trim()?.toIntOrNull()?.plus(1)
            val s = num(0)
            val e = num(1)
            return when {
                s != null && e != null -> "Saison $s, épisode $e"
                e != null -> "Épisode $e"
                s != null -> "Saison $s"
                else -> null
            }
        }
    }
}
