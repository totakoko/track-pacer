# Track Pacer — métronome d'allure pour Pixel Watch 2

Application Wear OS autonome qui t'indique, par bips, vibrations et signaux visuels,
**à quel instant tu devrais passer chaque repère** (50/100/200/300/400 m) d'une piste
à une allure cible donnée. Elle ne lit pas le GPS : c'est un chronomètre qui connaît
l'allure et la géométrie de la piste. À toi de regarder les marques au sol.

## Fonctionnalités

Le besoin : travailler des allures sans GPS, sur piste ou sur tout parcours dont on connaît les
distances. La montre est un chronomètre qui sait **quand** tu devrais passer chaque repère ;
tu compares le bip avec la marque au sol.

- Accueil en une phrase, chaque nombre est un bouton qui ouvre son sélecteur :
  `[400 m] à [4:45/km]` puis `⟷ [100 m]  ⌛ [1:00]` (rappels, récup).
  - **Répétition** : distance de 100 m à 99,9 km, ou « Libre » (fin sur appui).
  - **Allure** : min:s / km, de 2:00 à 12:59.
  - **Rappels** : tous les 50 m à 1 km, par pas de 50 m. Double bip au repère, triple bip au
    tour (400 m sur piste, 5 km à partir de rappels de 500 m).
  - **Récup** : mm:ss, 0:00 = chrono libre. Toujours un 3-2-1 avant la fin, puis double bip
    (ou triple bip si la rép suivante part toute seule).
- Déroulé : Démarrer → rép avec rappels → au temps cible de la rép, triple bip et le chrono
  continue en rouge (« Fin de rép ») → **Récup** sur ton appui : le temps réel de la rép est
  enregistré, l'écart avec la cible affiché (« 1:35.1 · +1.9 s ») → récup (compte à rebours ou
  chrono) → **Go** sur ton appui → … → **Terminer**, depuis la page de contrôles (glisser vers
  la droite : Pause / Reprendre, Terminer). Rien d'autre sur l'écran de course ne réagit au
  toucher. Le bouton latéral de la Pixel Watch 2 est une touche système, jamais transmise.
- Quitter : balayer vers la droite depuis le bord gauche de l'accueil ferme l'application.
  Une séance en cours continue dans son service (indicateur sur le cadran) jusqu'à Terminer.
- Options : **Départ auto** (Go tout seul à la fin d'une récup chronométrée) et **Récup auto**
  (bascule au temps cible de la rép, désactivée par défaut : seul ton appui connaît le vrai
  temps). **3-2-1** avant chaque rappel, désactivé par défaut.
- Écran de course : échéance suivante en tête (« 300 m dans 8 s »), gros chrono (ou compte à
  rebours de récup), distance théorique « 212 / 400 m » dessous, anneau de progression, flash
  au repère. Mode ambiant noir et blanc à 1 Hz, secondes fluides tant que l'app est devant.
- Récapitulatif : chaque rép avec son temps réel, sa cible et l'écart ; les récups entre.
- Sons synthétisés façon marimba (soft-clipping pour le niveau), volume sur quatre niveaux,
  option « Volume max » (volume média de la montre au maximum pendant la séance, haut-parleur
  uniquement, jamais en Bluetooth), vibration pleine amplitude, bouton « Test sonore ».
- Options d'affichage : distance théorique, fréquence cardiaque (Health Services), écran
  maintenu allumé. Indicateur d'activité en cours sur le cadran.

## Énergie

Par ordre de coût décroissant sur une Pixel Watch 2, et ce que fait l'app :

1. **Écran** : « Écran allumé » est désactivé par défaut ; le mode ambiant (1 Hz, noir)
   prend le relais. L'UI interactive se rafraîchit à 4 Hz, pas à chaque image, et le chrono
   n'affiche pas les dixièmes en direct (ils restent dans le récapitulatif).
2. **CPU éveillé** : le wake lock partiel du service n'est tenu qu'autour des bips. Dès que le
   prochain signal est à plus de 20 s, une alarme exacte (`USE_EXACT_ALARM`, `WakeReceiver`)
   réveille le SoC 3 s avant et le verrou est relâché entre-temps. Récupération libre et pause
   ne tiennent aucun verrou. Exception voulue : quand l'activité est visible en mode ambiant
   pendant une séance, elle tient son propre verrou pour que les secondes défilent sans à-coups
   (l'écran coûte déjà bien plus). Sans la permission d'alarme exacte, retour au verrou continu.
   Mesuré sur Pixel Watch 2 en veille : repère 400 m sonné avec 5 ms de retard, fin de récup
   30 s avec 3 ms, aucun verrou tenu entre les deux.
3. **Capteur cardio** : lecture optique continue, désactivée par défaut.
4. **Audio** : trois `AudioTrack` statiques pré-calculés, coût négligeable. En Bluetooth
   c'est la liaison radio qui coûte, pas l'app.

## Pile technique (octobre 2026)

| Brique | Version |
|---|---|
| Android Gradle Plugin (Kotlin intégré) | 9.4.1 |
| Gradle / Kotlin | 9.6.0 / 2.4.21 |
| Wear Compose Material 3 / Foundation / Navigation | 1.7.1 |
| Health Services | 1.1.0 |
| androidx.wear (ambient) / wear-ongoing | 1.4.0 / 1.1.0 |
| compileSdk / targetSdk / minSdk | 37.2 / 36 / 34 |

Architecture en un module, trois couches :

- `domain/` : arithmétique d'allure pure (`PaceMath`), état de session, réglages. Testé unitairement.
- `data/` : `SettingsRepository` (Preferences DataStore) et `RunStateHolder` (StateFlow partagé).
- `service/` : `PacerService` (service de premier plan `health`, planificateur de cues sans dérive,
  réveil par alarme exacte via `WakeReceiver`), `Beeper` (AudioTrack, routé vers les écouteurs
  Bluetooth), `MediaVolumeBoost`, `Haptics`, `HeartRateMonitor` (MeasureClient),
  `Permissions` (BODY_SENSORS ≤ API 35, READ_HEART_RATE ≥ 36).
- `ui/` : Compose Wear M3, navigation swipe-to-dismiss (désactivée sur l'écran de course, qui
  est un `HorizontalPager` à deux pages, contrôles à gauche), sélecteurs à roues pour allure,
  distances et durées, `AmbientLifecycleObserver` exposé via `LocalAmbientMode`.

L'UI ne reçoit aucun « tick » : elle dérive le chrono de `SystemClock.elapsedRealtime()` et
de l'instant de départ publié par le service.

## Construire

Prérequis : JDK 17+ et un SDK Android avec la plateforme 37.2 (`sdkmanager "platforms;android-37.2" "build-tools;36.0.0"`).
Renseigner `sdk.dir` dans `local.properties`.

```bash
./gradlew :app:testDebugUnitTest          # tests du domaine
./gradlew :app:assembleRelease            # APK minifié, signé avec la clé debug (≈ 4 Mo)
./gradlew :app:lintDebug
```

## Installer sur la montre

1. Sur la montre : Paramètres → Système → À propos → appuyer 7 fois sur « Numéro de build »,
   puis Paramètres → Options pour les développeurs → activer « Débogage ADB » et « Débogage sans fil ».
2. Dans « Débogage sans fil » → « Associer un nouvel appareil » puis, sur le PC :
   ```bash
   adb pair <ip>:<port-d-association>
   adb connect <ip>:<port-de-debogage>
   adb install -r app/build/outputs/apk/release/app-release.apk
   ```
3. Au premier démarrage d'une séance, l'app demande : activité physique (service de premier
   plan), notifications (indicateur sur le cadran) et fréquence cardiaque (optionnel).

## Limites connues et choix assumés

- La distance affichée est **théorique** (temps ÷ allure). Le GPS de la montre n'est pas utilisé.
- Si la permission « activité physique » est refusée, la séance tourne quand même mais le
  service ne peut pas passer en premier plan : les bips peuvent dériver en mode ambiant.
  La promotion est retentée à chaque bloc une fois la permission accordée.
- Non publié sur le Play Store : la clé de signature est celle de debug.
