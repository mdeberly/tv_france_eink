package fr.papertv.eink.data

import android.content.Context
import fr.papertv.eink.BuildConfig

/** Clé TMDB : celle saisie dans l'application, sinon celle intégrée à la compilation (secret TMDB_TOKEN). */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("papertv", Context.MODE_PRIVATE)

    var tmdbToken: String?
        get() = prefs.getString(KEY, null)?.takeIf { it.isNotBlank() } ?: BuildConfig.TMDB_TOKEN.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().putString(KEY, value?.trim()).apply()
        }

    companion object {
        private const val KEY = "tmdb_token"
    }
}
