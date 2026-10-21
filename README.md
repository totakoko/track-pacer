# Track Pacer — métronome d'allure pour Pixel Watch 2

Application Wear OS autonome qui t'indique, par bips, vibrations et signaux visuels,
**à quel instant tu devrais passer chaque repère** (50/100/200/300/400 m) d'une piste
à une allure cible donnée. Elle ne lit pas le GPS : c'est un chronomètre qui connaît
l'allure et la géométrie de la piste. À toi de regarder les marques au sol.

## Fonctionnalités

- Allure cible en min:s / km (de 2:00 à 12:59), repères configurables (50 à 400 m).
- Signaux configurables par profils : repère seul, 3-2-1, bip grave 10 s avant,
  10 s + 3-2-1 (défaut), 15 s + 5 s. Le repère est un double bip, le tour (400 m) un
  triple bip. Vibration en miroir, chacun activable.
- Écran de course : anneau de progression vers le prochain repère, compte à rebours,
  grand chrono au dixième, flash d'écran au repère.
- Options : fréquence cardiaque (Health Services), distance **théorique** (où tu devrais
  être à cette allure), écran maintenu allumé.
- Mode always-on : rendu ambiant simplifié (noir, blanc, 1 Hz) tout en gardant les bips
  précis grâce à un service de premier plan et un wake lock partiel.
- Blocs : **Pause** / **Reprendre**, **Stop** fige le chrono et enregistre le bloc ;
  **Nouveau bloc** repart à zéro ; **Terminer** ouvre le récapitulatif (durée, distance théorique).
- Gestes sur l'écran de course : appui n'importe où = pause / reprise, appui long = fin du
  bloc (retour haptique). Le bouton latéral de la Pixel Watch 2 est une touche système
  (récents, double appui = Wallet, appui long = Assistant) jamais transmise aux applications.
- Sons synthétisés façon marimba (fondamentale + harmoniques, décroissance exponentielle),
  volume réglable sur quatre niveaux et bouton « Test sonore » dans les réglages.
- Indicateur d'activité en cours sur le cadran (chrono visible hors de l'app).

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
- `service/` : `PacerService` (service de premier plan `health`, planificateur de cues sans dérive),
  `Beeper` (sinusoïdes AudioTrack, routées vers les écouteurs Bluetooth), `Haptics`,
  `HeartRateMonitor` (MeasureClient), `Permissions` (BODY_SENSORS ≤ API 35, READ_HEART_RATE ≥ 36).
- `ui/` : Compose Wear M3, navigation swipe-to-dismiss (désactivée sur l'écran de course),
  `AmbientLifecycleObserver` exposé via `LocalAmbientMode`.

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
