package fr.papertv.eink.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Chargeur d'images minimal (cache mémoire + disque). Les vignettes sont réduites à la
 * taille utile : l'écran e-ink n'affiche de toute façon que des niveaux de gris.
 */
object ImageLoader {
    // 1/8 de la mémoire de l'application (au moins 16 Mo) : de quoi garder toutes les vignettes d'une soirée.
    private val memory = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).coerceIn(16L shl 20, 256L shl 20).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val limiter = Semaphore(4)
    @Volatile private var diskDir: File? = null

    fun init(cacheDir: File) {
        diskDir = File(cacheDir, "img").apply { mkdirs() }
        // Purge simple : on supprime les vignettes de plus de 7 jours.
        val limit = System.currentTimeMillis() - 7 * 24 * 3_600_000L
        diskDir?.listFiles()?.forEach { if (it.lastModified() < limit) it.delete() }
    }

    fun cached(url: String): Bitmap? = memory.get(url)

    /** Pour les tests : injecte une image déjà décodée. */
    fun put(url: String, bitmap: Bitmap) {
        memory.put(url, bitmap)
    }

    suspend fun load(url: String, maxPx: Int): Bitmap? {
        memory.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            limiter.withPermit {
                memory.get(url) ?: runCatching {
                    val bytes = readDisk(url) ?: fetch(url)?.also { writeDisk(url, it) }
                    bytes?.let { decode(it, maxPx) }
                }.getOrNull()?.also { memory.put(url, it) }
            }
        }
    }

    /** Décode en réduisant l'image pour que son plus grand côté ne dépasse pas [maxPx]. */
    fun decode(bytes: ByteArray, maxPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        val longest = maxOf(decoded.width, decoded.height)
        if (longest <= maxPx) return decoded
        val scale = maxPx.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            decoded, (decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1), true,
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    private fun fetch(url: String): ByteArray? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
        }
        return try {
            if (conn.responseCode == 200) conn.inputStream.use { it.readBytes() } else null
        } finally {
            conn.disconnect()
        }
    }

    private fun key(url: String): String =
        MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun readDisk(url: String): ByteArray? =
        diskDir?.let { File(it, key(url)) }?.takeIf { it.exists() }?.readBytes()

    private fun writeDisk(url: String, bytes: ByteArray) {
        diskDir?.let { File(it, key(url)).writeBytes(bytes) }
    }
}
