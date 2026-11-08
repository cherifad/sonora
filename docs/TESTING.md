# Tester sur téléphone, tablette, Wear OS et Android Auto

Ce dépôt cible Android 9+ (minSdk 29). Cette page liste les outils **légers** pour
couvrir chaque format, en priorité depuis **Windows**, sans Android Studio complet.

## Ce qui marche, par cible

| Cible | Outil | Poids / remarques |
|---|---|---|
| **Téléphone** | Émulateur (`pixel_7`) | ~1,5 Go d'image, x86_64 |
| **Tablette** | Émulateur Android (`medium_tablet` ou `pixel_tablet`) | ~1,5 Go d'image, x86_64 |
| **Tablette / pliable, sans émulateur** | `adb shell wm size` + `wm density` sur le téléphone | 0 Mo, réversible (voir plus bas) |
| **Wear OS** | Émulateur Android (`wearos_small_round`, Wear OS 5.1) | ~1,5 Go ; appairage avec un émulateur téléphone |
| **Android Auto (projection)** | **Desktop Head Unit (DHU 2.0)** + ton téléphone | ~10 Mo, aucun émulateur |
| **Android Automotive (OS embarqué)** | Émulateur `android-automotive-playstore` | ~3 Go, pour une app *head-unit*, pas pour Auto |

Wear OS et Automotive ne sont couverts que par l'émulateur officiel : c'est la
seule famille d'outils complète. « Léger » veut donc dire : **cmdline-tools +
emulator, sans Android Studio**, une image x86_64, un AVD à la fois, snapshots.

Les tests de ce dépôt tournent sur l'émulateur ; le téléphone physique n'est plus
utilisé. Sur cette machine (WSL2) tout est scripté :

```bash
tools/fetch-emulator-libs.sh   # une fois : bibliothèques hôtes manquantes
tools/create-avds.sh           # une fois : jellyfin (téléphone) + jellyfin_tablet
tools/emulator.sh              # fenêtre sur le bureau Windows (WSLg)
tools/emulator.sh jellyfin_tablet
```

Navidrome de test : `http://10.0.2.2:4533` depuis l'émulateur (pas d'`adb reverse`).

## Installation minimale sur Windows (PowerShell)

```powershell
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_HOME = $sdk

# Outils (pas d'Android Studio)
sdkmanager --install "platform-tools" "emulator" "extras;google;auto"
sdkmanager --licenses

# Une image par cible (x86_64 = accélérée)
sdkmanager --install "system-images;android-36-ext19;google_apis;x86_64"
sdkmanager --install "system-images;android-35-ext15;android-wear;x86_64"
sdkmanager --install "system-images;android-35-ext15;android-automotive-playstore;x86_64"

# Appareils virtuels
avdmanager create avd -n phone   -k "system-images;android-36-ext19;google_apis;x86_64" -d pixel_7
avdmanager create avd -n tablet  -k "system-images;android-36-ext19;google_apis;x86_64" -d medium_tablet
avdmanager create avd -n wear    -k "system-images;android-35-ext15;android-wear;x86_64" -d wearos_small_round
avdmanager create avd -n car     -k "system-images;android-35-ext15;android-automotive-playstore;x86_64" -d automotive_1024p_landscape
```

Lancement (l'émulateur seul, sans Studio) :

```powershell
emulator -avd tablet -no-snapshot-save -no-boot-anim
emulator -avd wear   -no-snapshot-save
& "$env:ANDROID_HOME\extras\google\auto\desktop-head-unit.exe"
```

Accélération : **WHPX** (Hyper-V + « Windows Hypervisor Platform ») ou **AEHD**
(Android Emulator Hypervisor Driver, plus léger si Hyper-V est désactivé).
HAXM est abandonné. Vérifier avec `emulator-check accel`.

## Interagir dans une vraie fenêtre (sans Android Studio)

L'émulateur **ouvre une fenêtre par défaut** : c'est `-no-window` (ou
`-qt-hide-window`, réservé à Android Studio) qui la supprime. Depuis PowerShell :

```powershell
& "$env:ANDROID_HOME\emulator\emulator.exe" -avd tablet -gpu host
# le terminal garde les logs, la fenêtre est interactive
```

Dans cette fenêtre :

- la souris est le doigt (clic, glisser, appui long, Ctrl + glisser = pincer) ;
  le clavier de l'hôte tape directement dans les champs ;
- le **panneau de droite** : alim, volume, rotation, capture d'écran, zoom,
  retour / accueil / récents, plier/déplier (pliable), boutons Wear, et « … » ;
- « … » = **Extended controls** : position GPS, batterie, réseau, orientation,
  capteurs, caméra, micro, empreinte, snapshots ;
- **F1** ouvre la liste complète des raccourcis ;
- glisser-déposer un APK sur la fenêtre l'installe.

Si aucune fenêtre n'apparaît : vérifier qu'aucun `-no-window` n'est passé,
puis `-gpu swiftshader_indirect`, `-no-snapshot-load`, `-verbose`, et
`emulator-check accel` (WHPX/AEHD).

### Encore plus léger : scrcpy

Une seule petite fenêtre, identique pour un émulateur ou un téléphone :

```powershell
scrcpy -s emulator-5554        # ou -s <ip>:<port> pour le téléphone
```

Souris/clavier mappés, presse-papiers partagé, glisser-déposer d'APK, audio,
capture — souvent plus agréable que la fenêtre de l'émulateur pour tester une UI.

### Piloter depuis le terminal

Tout ce que fait la fenêtre est scriptable :

```bash
adb shell input tap 540 1200
adb shell input swipe 540 1800 540 600 300
adb shell input text "mira"
adb exec-out screencap -p > shot.png
adb emu rotate                 # commandes de la console de l'émulateur
adb emu geo fix 2.35 48.85
adb emu avd snapshot save etat1
```

Le nouveau CLI `android` (remplaçant à terme de `emulator`) gère aussi les AVD :
`android emulator list`, `android emulator start tablet`, `android emulator stop <serial>`.

## Android Auto : le DHU (le plus léger)

Aucun émulateur : le téléphone projette vers une « autoradio » logicielle.

1. Sur le téléphone : installer **Android Auto** (Play Store), activer le mode
   développeur (10 taps sur la version), puis **Démarrer le serveur d'unité
   centrale** dans son menu.
2. Sur le PC, téléphone en USB (ou ADB sans fil) :
   ```bash
   tools/dhu.sh            # WSL/Linux ; ou desktop-head-unit.exe sous Windows
   ```
   (le script fait l'`adb forward tcp:5277` et pose `LD_LIBRARY_PATH`).
3. L'app expose déjà son arbre de navigation (`AutoBrowser` → `MediaBrowserService`) :
   elle apparaît dans « Média » du DHU.

Le DHU sait simuler l'écran tactile, la molette, le mode jour/nuit, les
restrictions « en roulant » (`restrict all`), la micro et les capteurs.

L'arbre Android Auto se teste aussi **sans** l'app Android Auto : un test instrumenté
se lie au service et parcourt les dossiers (avec Navidrome sur `10.0.2.2:4533`) :

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.adlen.sonora.test.auto.AutoBrowserTest
```

Il vérifie que `PlaybackService` répond à `android.media.browse.MediaBrowserService`, que la
racine donne Home / Library / Downloads, que Library liste les collections, qu'un album rend
un « Start radio » et des morceaux jouables, et que la recherche répond.

Les écrans du téléphone ont leurs tests Compose :

- `MainFlowTest` : Home, Library, **Search** (une requête sur la bibliothèque de test) et un
  album de la bibliothèque ouvert et lancé ;
- `SetupFlowTest` : le formulaire serveur de l'onboarding se connecte, puis l'étape permissions
  et Home. L'étape du look n'est pas automatisée : son aperçu anime en boucle, ce sur quoi un
  test Compose ne peut pas « idler » ; le test la marque vue et couvre le reste.

```bash
./gradlew :app:connectedMobileDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.package=com.adlen.sonora.test.ui
```

```bash
./gradlew :app:connectedMobileDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.adlen.sonora.test.ui.MainFlowTest
```

À savoir : AGP **désinstalle l'application** à la fin d'un `connected…AndroidTest` (les données
d'onboarding partent avec). Pour la garder après le run :
`-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`.

Le parcours « album + lecture » a besoin que l'app soit **déjà onboardée au lancement du
processus** (les pages lisent le serveur dès leur première composition ; un test ne peut pas
reconfigurer une session déjà démarrée). Onboarde une fois (émulateur), puis lance :

```bash
./gradlew :app:connectedMobileDebugAndroidTest \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.adlen.sonora.test.ui.MainFlowTest
```

Sans onboarding préalable, Home et Library sont testés et le parcours album est sauté.

Le DHU lui-même est installé (`extras;google;auto`) et se lance avec `tools/dhu.sh` ; il
demande l'app Android Auto **sur l'appareil**, absente de l'image `google_apis` de
l'émulateur — d'où le test ci-dessus, qui couvre l'arbre sans elle.
Une app *Android Automotive* (écran embarqué) se teste à part, dans l'AVD
`car` — ce n'est pas le même modèle.

## Android Automotive OS (voiture) : build `automotive`

En plus du téléphone, le dépôt produit un APK **Android Automotive OS** : même service de
navigation média, sans activité de lancement (la voiture dessine l'UI). C'est un *flavor*
Gradle, pas un module séparé :

```bash
# image + AVD (une fois)
sdkmanager --install "system-images;android-35-ext15;android-automotive-playstore;x86_64"
avdmanager create avd -n jellyfin_car \
  -k "system-images;android-35-ext15;android-automotive-playstore;x86_64" \
  -d automotive_1024p_landscape

# APK pour l'émulateur (x86_64), puis la voiture
./gradlew :app:assembleAutomotiveDebug -Pabis=x86_64
tools/emulator.sh jellyfin_car
adb -s emulator-5554 install -r app/build/outputs/apk/automotive/debug/app-automotive-debug.apk
tools/car-open.sh
```

`tools/car-open.sh` démarre l'app média de la voiture (`com.android.car.media`) sur notre
`PlaybackService` : Home / Library / Downloads, puis lecture — le tout dessiné par le système.

Deux pièges connus :

- une app sans activité de lancement reste à l'état « stopped » et la voiture l'ignore :
  `tools/car-open.sh` démarre son service une fois pour lever l'état ;
- `run-as` écrit dans les données de l'utilisateur 0 alors que la voiture tourne en
  utilisateur 10 : pour configurer le serveur, le test instrumenté (`AutoBrowserTest`) le fait
  dans le bon contexte — `adb shell am instrument -w --user 10 -e class
  com.adlen.sonora.test.auto.AutoBrowserTest com.adlen.sonora.car.dev.test/androidx.test.runner.AndroidJUnitRunner`.

## Tablette sans émulateur (sur le téléphone)

```bash
adb shell wm size 1600x2560      # ratio tablette
adb shell wm density 240
# … tester, puis :
adb shell wm size reset
adb shell wm density reset
```

C'est instantané et suffisant pour valider les points de rupture de largeur et
les deux panneaux. L'émulateur `medium_tablet` reste utile pour les vraies
densités et le multi-fenêtre.

## Wear OS : à savoir pour cette app

L'APK est une app **téléphone** : il s'installera sur un émulateur Wear, mais
l'interface n'est pas adaptée (pas de module `wearApp`, pas de rotation
d'écran, pas de tuiles). Pour un vrai support il faudra un module Compose for
Wear OS ; en attendant, un test Wear ne valide que « s'installe / se lance ».

Appairage : dans le Device Manager, « Wear OS emulator pairing » (ou les trois
points de l'AVD) relie une montre virtuelle à un téléphone virtuel ou réel.

## Alternatives cloud (si le PC est faible)

- **Firebase Test Lab** : Robo/Instrumentation sur des téléphones et tablettes
  réels, sans machine puissante.
- **Samsung Remote Test Lab** : Galaxy réels, gratuit, comptes Samsung.
- **BrowserStack App Live** : téléphones et tablettes réels.
- **Genymotion** (desktop ou SaaS) : téléphone/tablette très rapides, et une
  image **Android Automotive** ; pas de Wear OS ni de projection Auto.

## À éviter

- **WSA (Windows Subsystem for Android)** : arrêté par Microsoft en mars 2025.
- **BlueStacks / LDPlayer / MuMu / Nox** : orientés jeu, pas de flux ADB de
  développement fiable, ni Wear OS ni Android Auto, publicités.
- **Waydroid** : Linux uniquement.

## Matrice de test de ce dépôt

| | Téléphone | Tablette | Wear | Auto |
|---|---|---|---|---|
| Installation + démarrage | ✅ | ✅ | ⚠️ (UI non adaptée) | — |
| Accueil / Recherche / Bibliothèque | ✅ | ✅ (deux panneaux) | — | — |
| Album / Artiste / Playlists | ✅ | ✅ | — | — |
| Genres / notes / radio / « More like this » | ✅ | ✅ | — | — |
| Lecture + notification | ✅ | ✅ | ⚠️ | ✅ (média) |
| ReplayGain / égaliseur / skip-silence | ✅ | ✅ | — | — |
| Téléchargements / hors ligne / reprise | ✅ | ✅ | — | — |
| Tuile Réglages rapides / widget | ✅ | ✅ | — | — |
| Identifiants chiffrés (Keystore) | ✅ | ✅ | — | — |
| Serveur HTTP / HTTPS, Jellyfin / Navidrome | ✅ | ✅ | — | — |

## Portes de CI

`.github/workflows/ci.yml` exécute à chaque push/PR :

1. `:api-subsonic:testDebugUnitTest` et `:app:testMobileDebugUnitTest` (75 tests backend au
   moment de l'écriture, plus les tests unitaires de l'app) ;
2. `:app:assembleMobileDebug` et `:app:assembleAutomotiveDebug` ;
3. `:app:assembleMobileRelease` — R8, réduction des ressources et lint-vital.

Le workflow `Release` signe les APK stables depuis un tag `vX.Y.Z` sur `main`.

## Contrat contre un vrai serveur

`tools/server_contract.py` interroge les endpoints que Sonora utilise et sort en erreur si un
endpoint requis ne répond pas `status: ok` :

```bash
tools/server_contract.py --url http://10.0.2.2:4533 --user admin --password '…'
tools/server_contract.py --url https://music.example --user me --api-key '…' --json
```

Il affiche le type/version du serveur, les extensions OpenSubsonic et OK/FAIL/WARN par endpoint.
`--insecure` accepte le certificat auto-signé d'un serveur de test. À lancer contre Navidrome,
Jellyfin+Subfin, Gonic et Airsonic avant une release.

## Vérifications manuelles des surfaces plateforme

- **Tuile Réglages rapides** : ajouter la tuile Sonora, lecture/pause, l'état suit le lecteur.
- **Widget** : ajouter le widget 4x1, vérifier pochette/titre/artiste, précédent/lecture/suivant
  et le bouton shuffle.
- **Identifiants chiffrés** : après configuration,
  `adb shell run-as com.adlen.sonora.dev ls shared_prefs` doit montrer `subsonic_secure.xml`
  et aucun `subsonic.xml` en clair.
- **Mise à jour** : installer la release précédente, configurer le serveur et télécharger un
  album, puis installer le nouvel APK par-dessus ; réglages, file et téléchargements doivent
  survivre.
