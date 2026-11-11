# Paper TV

Application Android qui affiche le programme TV de **prime time** des chaînes gratuites de la TNT,
de **Canal+** et de **TV5MONDE**. Elle est pensée pour les écrans e-ink, en particulier le
**Mudita Kompakt** (4,3", 800×480).

## Fonctionnement

- **Accueil** : la soirée du jour, chaîne par chaîne (version E Ink : 3 chaînes par écran, résumé en gros caractères).
  Chaque ligne affiche une vignette, le titre, le résumé, puis l'heure de début et de fin en petit.
- **Défilement page par page**, sans animation : boutons « ‹ Préc. » / « Suiv. › », balayage
  (gauche ou haut = page suivante) ou touches de volume.
- **Sélecteur de date** : flèches ‹ › autour du titre, ou toucher le titre pour choisir dans la liste.
  On peut remonter à la veille et avancer jusqu'à la dernière soirée disponible dans les données
  (en général 7 à 9 jours).
- **Fiche détaillée** (toucher une chaîne) : description complète, saison/épisode, genre, pays,
  année, note, réalisateur et distribution, puis le programme **suivant** (« Ensuite »).
- Interface noir et blanc, gros contrastes, images en niveaux de gris, pas d'effets d'onde ni de
  rebond : on limite au maximum les rafraîchissements de l'écran e-ink.

## Données

Les programmes viennent des fichiers XMLTV publics de [XML TV Fr](https://xmltvfr.fr) :

| Fichier | Taille | Utilisation | Rafraîchi |
|---|---|---|---|
| `xmltv_tnt.zip` | ~0,8 Mo | chaînes TNT + Canal+ | toutes les 12 h |
| `xmltv_fr.zip` | ~9 Mo | TV5MONDE seulement | tous les 3 jours |

Seules les chaînes utiles sont conservées, dans un cache local : l'application fonctionne hors
connexion avec les dernières données téléchargées. Le bouton ↻ force la mise à jour.

Le « prime time » est choisi ainsi : un long programme déjà à l'antenne à 21 h (film, match),
sinon le premier programme d'au moins 30 min qui démarre entre 20h50 et 21h30, sinon entre 20h35
et 22h, sinon ce qui passe à 21h10 (chaînes d'info). Le programme suivant ignore les interludes de
moins de 15 min.

## Installation

Télécharger l'APK de la dernière [release](../../releases) sur le téléphone et l'ouvrir
(autoriser l'installation d'applications de sources inconnues). Android 8.0 minimum.

## Compilation

```bash
./gradlew :app:assembleRelease
```

Signature : la clé n'est pas versionnée. En local, placer un keystore dans `keystore/release.jks`
(alias et mots de passe `papertv` par défaut, surchargeables par `PAPERTV_KEY_ALIAS`,
`PAPERTV_STORE_PASSWORD`, `PAPERTV_KEY_PASSWORD`). Sur GitHub Actions, ajouter le secret
`PAPERTV_KEYSTORE_B64` (keystore encodé en base64) et, si besoin, les mots de passe en secrets.
Sans clé, l'APK est signé avec une clé de debug, qui change d'une build CI à l'autre : il faut alors
désinstaller l'ancienne version avant d'installer la nouvelle.

Captures d'écran (Robolectric + Roborazzi, au format du Kompakt) :

```bash
./gradlew :app:testDebugUnitTest --tests 'fr.papertv.eink.ScreenshotTest' -Ppapertv.tnt=/chemin/xmltv_tnt.xml -Ppapertv.fr=/chemin/xmltv_fr.xml
```

Le workflow GitHub Actions `Android` compile l'APK à chaque push et peut, sur déclenchement
manuel, régénérer `docs/screenshots` et publier une release.
