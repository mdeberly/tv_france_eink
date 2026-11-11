package fr.papertv.eink.data

/** Une chaîne affichée dans l'application, dans l'ordre de la numérotation TNT. */
data class Channel(
    val id: String,
    val name: String,
)

/** Un programme issu du flux XMLTV. Les dates sont en millisecondes epoch. */
data class Programme(
    val channelId: String,
    val start: Long,
    val stop: Long,
    val title: String,
    val subTitle: String? = null,
    val description: String? = null,
    val categories: List<String> = emptyList(),
    val iconUrl: String? = null,
    val episode: String? = null,
    val year: String? = null,
    val country: String? = null,
    val directors: List<String> = emptyList(),
    val actors: List<String> = emptyList(),
    val presenters: List<String> = emptyList(),
    val rating: String? = null,
    val starRating: String? = null,
) {
    val durationMinutes: Long get() = (stop - start) / 60_000
}

/** Une ligne de l'écran d'accueil : la chaîne, son programme de prime time et le suivant. */
data class PrimeTimeEntry(
    val channel: Channel,
    val primeTime: Programme?,
    val next: Programme?,
)

object Channels {
    /** Fichier XMLTV des chaînes de la TNT (TF1 … franceinfo, plus Canal+). */
    const val TNT_URL = "https://xmltvfr.fr/xmltv/xmltv_tnt.zip"

    /** Fichier XMLTV « France » : nécessaire pour TV5MONDE, absente du fichier TNT. */
    const val FRANCE_URL = "https://xmltvfr.fr/xmltv/xmltv_fr.zip"

    /** Chaînes gratuites de la TNT + Canal+ + TV5MONDE, dans l'ordre d'affichage. */
    val ALL: List<Channel> = listOf(
        Channel("TF1.fr", "TF1"),
        Channel("France2.fr", "France 2"),
        Channel("France3.fr", "France 3"),
        Channel("CanalPlus.fr", "Canal+"),
        Channel("France5.fr", "France 5"),
        Channel("M6.fr", "M6"),
        Channel("Arte.fr", "Arte"),
        Channel("W9.fr", "W9"),
        Channel("TMC.fr", "TMC"),
        Channel("NT1.fr", "TFX"),
        Channel("LaChaineParlementaire.fr", "LCP"),
        Channel("France4.fr", "France 4"),
        Channel("BFMTV.fr", "BFM TV"),
        Channel("CNews.fr", "CNews"),
        Channel("CStar.fr", "CStar"),
        Channel("Gulli.fr", "Gulli"),
        Channel("T18.fr", "T18"),
        Channel("NOVO19.fr", "Novo19"),
        Channel("TF1SeriesFilms.fr", "TF1 Séries Films"),
        Channel("LEquipe21.fr", "L'Équipe"),
        Channel("6ter.fr", "6ter"),
        Channel("Numero23.fr", "RMC Story"),
        Channel("RMCDecouverte.fr", "RMC Découverte"),
        Channel("Cherie25.fr", "RMC Life"),
        Channel("LCI.fr", "LCI"),
        Channel("FranceInfo.fr", "franceinfo"),
        Channel("TV5Monde.fr", "TV5MONDE"),
    )

    val TNT_IDS: Set<String> = ALL.map { it.id }.toSet() - "TV5Monde.fr"
    val FRANCE_ONLY_IDS: Set<String> = setOf("TV5Monde.fr")
}
