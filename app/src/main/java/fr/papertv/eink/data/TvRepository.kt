package fr.papertv.eink.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.zip.ZipInputStream

/**
 * Télécharge les flux XMLTV de xmltvfr.fr, n'en garde que les chaînes utiles et met
 * le résultat en cache (JSON) pour fonctionner hors connexion et éviter de retélécharger.
 */
class TvRepository(private val cacheDir: File) {

    /** Une source XMLTV et sa politique de rafraîchissement. */
    private enum class Source(val url: String, val channels: Set<String>, val maxAgeMs: Long, val file: String) {
        TNT(Channels.TNT_URL, Channels.TNT_IDS, 12 * HOUR, "tnt.json"),
        // Le fichier « France » est lourd (~9 Mo) : il couvre ~5 jours, on le garde 3 jours.
        FRANCE(Channels.FRANCE_URL, Channels.FRANCE_ONLY_IDS, 72 * HOUR, "france.json"),
    }

    data class Result(
        val programmes: List<Programme>,
        val updatedAt: Long?,
        val error: String?,
    )

    /**
     * Retourne les programmes en cache, et les télécharge si le cache est absent, trop
     * ancien, ne couvre pas la soirée [evening] ou si [force] est vrai.
     */
    suspend fun load(
        evening: LocalDate,
        force: Boolean,
        onPartial: suspend (Result) -> Unit = {},
    ): Result = withContext(Dispatchers.IO) {
        val all = ArrayList<Programme>()
        var oldest: Long? = null
        val errors = ArrayList<String>()
        val now = System.currentTimeMillis()
        val eveningEnd = evening.plusDays(1).atTime(1, 0).atZone(PrimeTimeSelector.PARIS).toInstant().toEpochMilli()
        // On garde la veille pour pouvoir la consulter avec le sélecteur de date.
        val keepFrom = evening.minusDays(1).atStartOfDay(PrimeTimeSelector.PARIS).toInstant().toEpochMilli()

        for (source in Source.entries) {
            val cached = readCache(source)
            val covers = cached != null && cached.second.any { it.stop >= eveningEnd }
            val fresh = cached != null && now - cached.first < source.maxAgeMs
            var result = cached
            if (force || !covers || !fresh) {
                try {
                    val programmes = download(source, keepFrom)
                    if (programmes.isNotEmpty()) {
                        writeCache(source, now, programmes)
                        result = now to programmes
                    }
                } catch (e: Exception) {
                    errors += if (e is IOException) "connexion impossible" else (e.message ?: e.javaClass.simpleName)
                }
            }
            if (result != null) {
                all += result.second
                oldest = minOf(oldest ?: result.first, result.first)
            }
            // La TNT (petit fichier) s'affiche sans attendre le fichier « France ».
            if (source != Source.entries.last()) onPartial(Result(all.toList(), oldest, errors.firstOrNull()))
        }
        Result(all, oldest, errors.firstOrNull())
    }

    private fun download(source: Source, keepFrom: Long): List<Programme> {
        val conn = (URL(source.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", "PaperTV/1.0 (Android)")
        }
        try {
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
            ZipInputStream(BufferedInputStream(conn.inputStream, 64 * 1024)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name.endsWith(".xml")) {
                        return XmltvParser(source.channels, keepFrom).parse(zip)
                    }
                }
            }
            return emptyList()
        } finally {
            conn.disconnect()
        }
    }

    private fun readCache(source: Source): Pair<Long, List<Programme>>? = try {
        val f = File(cacheDir, source.file)
        if (!f.exists()) null else {
            val json = JSONObject(f.readText())
            json.getLong("fetchedAt") to fromJson(json.getJSONArray("programmes"))
        }
    } catch (_: Exception) {
        null
    }

    private fun writeCache(source: Source, fetchedAt: Long, programmes: List<Programme>) {
        val json = JSONObject().put("fetchedAt", fetchedAt).put("programmes", toJson(programmes))
        val tmp = File(cacheDir, source.file + ".tmp")
        tmp.writeText(json.toString())
        tmp.renameTo(File(cacheDir, source.file))
    }

    companion object {
        private const val HOUR = 3_600_000L

        fun toJson(list: List<Programme>): JSONArray = JSONArray().apply {
            list.forEach { p ->
                put(JSONObject().apply {
                    put("c", p.channelId); put("s", p.start); put("e", p.stop); put("t", p.title)
                    putOpt("st", p.subTitle); putOpt("d", p.description); putOpt("i", p.iconUrl)
                    putOpt("ep", p.episode); putOpt("y", p.year); putOpt("co", p.country)
                    putOpt("r", p.rating); putOpt("sr", p.starRating)
                    put("cat", JSONArray(p.categories)); put("dir", JSONArray(p.directors))
                    put("act", JSONArray(p.actors)); put("pre", JSONArray(p.presenters))
                })
            }
        }

        fun fromJson(arr: JSONArray): List<Programme> = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            fun s(k: String) = if (o.has(k) && !o.isNull(k)) o.getString(k) else null
            fun l(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
            Programme(
                channelId = o.getString("c"), start = o.getLong("s"), stop = o.getLong("e"), title = o.getString("t"),
                subTitle = s("st"), description = s("d"), iconUrl = s("i"), episode = s("ep"), year = s("y"),
                country = s("co"), rating = s("r"), starRating = s("sr"), categories = l("cat"),
                directors = l("dir"), actors = l("act"), presenters = l("pre"),
            )
        }
    }
}
