package fr.papertv.eink.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Film ou série disponible sur Netflix en France, d'après TMDB (disponibilités JustWatch). */
data class StreamTitle(
    val id: Long,
    val isMovie: Boolean,
    val title: String,
    val originalTitle: String?,
    val year: String?,
    val overview: String?,
    val posterPath: String?,
    val backdropPath: String?,
    val voteAverage: Double,
    val voteCount: Int,
    val genres: List<String>,
) {
    val posterUrl: String? get() = posterPath?.let { "https://image.tmdb.org/t/p/w342$it" }
    val backdropUrl: String? get() = backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" }
}

/** Compléments chargés à l'ouverture de la fiche. */
data class StreamDetails(
    val tagline: String?,
    val runtimeMinutes: Int?,
    val seasons: Int?,
    val episodes: Int?,
    val creators: List<String>,
    val cast: List<String>,
)

enum class StreamKind(val label: String, val path: String) {
    MOVIES("Films", "movie"),
    SERIES("Séries", "tv"),
}

enum class StreamSort(val label: String) {
    POPULAR("Populaires"),
    TOP_RATED("Mieux notés"),
}

class TmdbException(message: String) : IOException(message)

/**
 * Accès à l'API TMDB. La clé est soit une « API Key » v3 (32 caractères, passée en paramètre),
 * soit un « API Read Access Token » v4 (passé en en-tête Authorization).
 */
class TmdbRepository(private val cacheDir: File, private val token: () -> String?) {

    suspend fun list(kind: StreamKind, sort: StreamSort, force: Boolean = false): Pair<List<StreamTitle>, Long> =
        withContext(Dispatchers.IO) {
            val file = File(cacheDir, "netflix_${kind.path}_${sort.name.lowercase()}.json")
            val now = System.currentTimeMillis()
            if (!force) readCache(file)?.let { (at, list) -> if (now - at < MAX_AGE) return@withContext list to at }
            try {
                val genres = genres(kind)
                val list = (1..PAGES).flatMap { page -> discover(kind, sort, page, genres) }.distinctBy { it.id }
                writeCache(file, now, list)
                list to now
            } catch (e: IOException) {
                // Hors connexion : on garde le cache, même ancien.
                readCache(file)?.let { (at, list) -> list to at } ?: throw e
            }
        }

    suspend fun details(t: StreamTitle): StreamDetails? = withContext(Dispatchers.IO) {
        runCatching {
            val kind = if (t.isMovie) "movie" else "tv"
            val o = get("/$kind/${t.id}", mapOf("language" to "fr-FR", "append_to_response" to "credits"))
            val credits = o.optJSONObject("credits")
            val crew = credits?.optJSONArray("crew")
            val directors = (0 until (crew?.length() ?: 0)).map { crew!!.getJSONObject(it) }
                .filter { it.optString("job") == "Director" }.map { it.getString("name") }
            val creators = o.optJSONArray("created_by")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("name") } }
                ?: emptyList()
            val cast = credits?.optJSONArray("cast")?.let { a ->
                (0 until minOf(a.length(), 6)).map { a.getJSONObject(it).getString("name") }
            } ?: emptyList()
            StreamDetails(
                tagline = o.optString("tagline").takeIf { it.isNotBlank() },
                runtimeMinutes = o.optInt("runtime").takeIf { it > 0 },
                seasons = o.optInt("number_of_seasons").takeIf { it > 0 },
                episodes = o.optInt("number_of_episodes").takeIf { it > 0 },
                creators = if (t.isMovie) directors else creators,
                cast = cast,
            )
        }.getOrNull()
    }

    private fun discover(kind: StreamKind, sort: StreamSort, page: Int, genres: Map<Int, String>): List<StreamTitle> {
        val params = mutableMapOf(
            "with_watch_providers" to NETFLIX.toString(),
            "watch_region" to "FR",
            "with_watch_monetization_types" to "flatrate",
            "language" to "fr-FR",
            "include_adult" to "false",
            "page" to page.toString(),
        )
        when (sort) {
            StreamSort.POPULAR -> {
                params["sort_by"] = "popularity.desc"
                params["vote_count.gte"] = "20"
            }
            StreamSort.TOP_RATED -> {
                params["sort_by"] = "vote_average.desc"
                // Un minimum de votants, pour que la moyenne ait un sens.
                params["vote_count.gte"] = if (kind == StreamKind.MOVIES) "1000" else "500"
            }
        }
        val results = get("/discover/${kind.path}", params).getJSONArray("results")
        return (0 until results.length()).map { parseTitle(results.getJSONObject(it), kind == StreamKind.MOVIES, genres) }
    }

    private val genreCache = HashMap<StreamKind, Map<Int, String>>()

    private fun genres(kind: StreamKind): Map<Int, String> = genreCache.getOrPut(kind) {
        val a = get("/genre/${kind.path}/list", mapOf("language" to "fr")).getJSONArray("genres")
        (0 until a.length()).associate { a.getJSONObject(it).let { g -> g.getInt("id") to g.getString("name") } }
    }

    private fun get(path: String, params: Map<String, String>): JSONObject {
        val key = token()?.trim().orEmpty()
        if (key.isEmpty()) throw TmdbException("clé TMDB manquante")
        val isV3Key = key.length == 32 && key.all { it.isLetterOrDigit() }
        val query = (if (isV3Key) params + ("api_key" to key) else params)
            .entries.joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, "UTF-8") }
        val conn = (URL("$BASE$path?$query").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            if (!isV3Key) setRequestProperty("Authorization", "Bearer $key")
        }
        try {
            val code = conn.responseCode
            if (code == 401) throw TmdbException("clé TMDB refusée")
            if (code != 200) throw IOException("HTTP $code")
            return JSONObject(conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) })
        } finally {
            conn.disconnect()
        }
    }

    private fun readCache(f: File): Pair<Long, List<StreamTitle>>? = try {
        if (!f.exists()) null else JSONObject(f.readText()).let { it.getLong("at") to fromJson(it.getJSONArray("items")) }
    } catch (_: Exception) {
        null
    }

    private fun writeCache(f: File, at: Long, list: List<StreamTitle>) {
        val tmp = File(f.path + ".tmp")
        tmp.writeText(JSONObject().put("at", at).put("items", toJson(list)).toString())
        tmp.renameTo(f)
    }

    companion object {
        private const val BASE = "https://api.themoviedb.org/3"
        /** Identifiant du fournisseur « Netflix » dans TMDB. */
        const val NETFLIX = 8
        private const val PAGES = 2
        private const val MAX_AGE = 24 * 3_600_000L

        fun parseTitle(o: JSONObject, isMovie: Boolean, genres: Map<Int, String>): StreamTitle {
            fun s(k: String) = o.optString(k).takeIf { it.isNotBlank() && it != "null" }
            val title = s(if (isMovie) "title" else "name") ?: s(if (isMovie) "original_title" else "original_name") ?: "?"
            val original = s(if (isMovie) "original_title" else "original_name")?.takeIf { it != title }
            val ids = o.optJSONArray("genre_ids")
            return StreamTitle(
                id = o.getLong("id"),
                isMovie = isMovie,
                title = title,
                originalTitle = original,
                year = s(if (isMovie) "release_date" else "first_air_date")?.take(4),
                overview = s("overview"),
                posterPath = s("poster_path"),
                backdropPath = s("backdrop_path"),
                voteAverage = o.optDouble("vote_average", 0.0),
                voteCount = o.optInt("vote_count"),
                genres = (0 until (ids?.length() ?: 0)).mapNotNull { genres[ids!!.getInt(it)] },
            )
        }

        fun toJson(list: List<StreamTitle>) = JSONArray().apply {
            list.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id); put("m", t.isMovie); put("t", t.title); putOpt("ot", t.originalTitle)
                    putOpt("y", t.year); putOpt("o", t.overview); putOpt("p", t.posterPath); putOpt("b", t.backdropPath)
                    put("va", t.voteAverage); put("vc", t.voteCount); put("g", JSONArray(t.genres))
                })
            }
        }

        fun fromJson(a: JSONArray) = (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            fun s(k: String) = if (o.has(k) && !o.isNull(k)) o.getString(k) else null
            StreamTitle(
                id = o.getLong("id"), isMovie = o.getBoolean("m"), title = o.getString("t"), originalTitle = s("ot"),
                year = s("y"), overview = s("o"), posterPath = s("p"), backdropPath = s("b"),
                voteAverage = o.getDouble("va"), voteCount = o.getInt("vc"),
                genres = o.getJSONArray("g").let { g -> (0 until g.length()).map { g.getString(it) } },
            )
        }
    }
}
