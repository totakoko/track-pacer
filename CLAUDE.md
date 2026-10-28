# Track Pacer — consignes pour l'IA

Application Wear OS (Pixel Watch 2, écran rond 192 dp) : métronome d'allure sans GPS.
Le besoin et la spec sont dans `README.md` ; cette page dit **comment travailler** dessus.

## Construire et tester

```bash
export JAVA_HOME=/home/maxime/.jdks/jdk-21.0.12.1+1   # pas de java sur le PATH
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease --offline --console=plain --quiet
```

La montre est joignable en Wi-Fi : `adb mdns services` la découvre, `adb devices` la liste,
`adb install -r app/build/outputs/apk/release/app-release.apk` installe.

## Vérifier chaque écran sur la montre, à chaque changement d'UI

L'écran est **rond** : tout ce qui touche le haut ou le bas est rogné sur les côtés. Les
retours « bouton tronqué », « libellé coupé », « pas de marge en bas » ont été faits plusieurs
fois. Avant de rendre la main :

1. Capturer **chaque écran modifié**, en haut et en bas de son contenu, et regarder les captures.
   ```bash
   adb shell "input keyevent KEYCODE_WAKEUP; sleep 0.4"      # la montre dort : réveiller avant tout tap
   adb exec-out screencap -p > shot.png                       # puis convertir en JPEG (PIL) pour la lire
   ```
2. Contrôler : un bouton ou une pastille en bas doit avoir ses coins **entièrement** dans le
   cercle (`ActionPill` + `ACTION_PILL_MARGIN`, ou `listPadding()` pour une liste) ; le premier
   élément d'une colonne doit être sous l'heure et dégagé du bord (marge en haut) ; aucun texte
   en deux lignes ni en « … » (`maxLines = 1`, raccourcir le libellé plutôt que le laisser couper).
3. Une seule taille de bouton par écran. Unités collées aux nombres (`400m`, `21s`, `3:53`).
4. Pendant un pilotage adb, **balayer horizontalement à y = 100** (sous l'heure ; à y = 60 le
   point x = 40 est hors du cercle et le geste est ignoré), jamais au milieu : un balayage qui
   part d'un interrupteur le bascule. Après toute session de pilotage, recapturer la page des
   réglages et comparer avec l'état initial. Un tap « à l'aveugle » qui rate sa cible peut
   atterrir sur le cadran ou dans les réglages système : capturer avant de taper, et vérifier
   `dumpsys window | grep mCurrentFocus` après chaque chaîne.
5. Si le délai d'extinction a été allongé (`settings put system screen_off_timeout 600000`),
   le remettre à `30000`.

## Comportements à ne pas casser

- Aucun geste « n'importe où » sur l'écran de course : seules les pastilles et les pages
  (contrôles à gauche, réglages à droite) agissent. Le thème désactive
  `android:windowSwipeToDismiss` : sans ça, un balayage vers la droite depuis la page de
  contrôles fermait l'application. Le seul balayage qui quitte est celui de l'accueil
  (`SwipeToDismissBox` → `finishAndRemoveTask()`).
- Les transitions rép → récup → rép sont manuelles ; les options « Récup auto » et « Départ
  auto » restent facultatives.
- Énergie : le service ne tient le CPU qu'autour des bips (alarme exacte sinon) ; l'activité
  tient le CPU seulement quand elle est visible en ambiant pendant une séance.
- L'anneau de progression lit l'horloge **dans une lambda d'état** (`clock()`), sinon il ne se
  redessine pas.

## Git

Commits sans trailer `Co-Authored-By` ni mention de session (consigne globale de Maxime).
