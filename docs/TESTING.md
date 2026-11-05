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
   ```powershell
   adb forward tcp:5277 tcp:5277
   & "$env:ANDROID_HOME\extras\google\auto\desktop-head-unit.exe"
   ```
3. L'app expose déjà son arbre de navigation (`AutoBrowser` → `MediaBrowserService`) :
   elle apparaît dans « Média » du DHU.

Le DHU sait simuler l'écran tactile, la molette, le mode jour/nuit, les
restrictions « en roulant » (`restrict all`), la micro et les capteurs.

L'arbre Android Auto se teste aussi **sans** l'app Android Auto : un test instrumenté
se lie au service et parcourt les dossiers (avec Navidrome sur `10.0.2.2:4533`) :

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.myspoti.auto.AutoBrowserTest
```

Il vérifie que `PlaybackService` répond à `android.media.browse.MediaBrowserService`, que la
racine donne Home / Library / Downloads, que Library liste les collections, qu'un album rend
un « Start radio » et des morceaux jouables, et que la recherche répond.
Une app *Android Automotive* (écran embarqué) se teste à part, dans l'AVD
`car` — ce n'est pas le même modèle.

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
| Accueil / Recherche / Bibliothèque | ✅ | à faire (deux panneaux) | — | — |
| Album / Artiste / Playlists | ✅ | à faire | — | — |
| Lecture + notification | ✅ | ✅ | ⚠️ | ✅ (média) |
| Téléchargements / hors ligne | ✅ | ✅ | — | — |
| Serveur HTTP / HTTPS, Jellyfin / Navidrome | ✅ | ✅ | — | — |
