# Branches et releases

## Modèle

| Branche | Rôle | CI |
|---|---|---|
| `develop` (défaut) | tout le travail ; les PR y arrivent | tests JVM + APK debug (mobile) |
| `main` | uniquement ce qui est prêt à sortir ; protégée, mise à jour par PR depuis `develop` | tests JVM + APK debug |
| tag `vX.Y.Z` sur `main` | déclenche `Release` | APK release signés (mobile + automotive) et GitHub Release |

`main` n'accepte pas de push direct : une PR depuis `develop` (au moins une revue ou une CI verte
selon la protection du dépôt), puis merge. Une release se fait **depuis un commit de `main`** :
le workflow refuse un tag dont le commit n'est pas un ancêtre de `origin/main`.

## Secrets du dépôt (Settings → Secrets and variables → Actions)

| Secret | Contenu |
|---|---|
| `MYSPOTI_KEYSTORE_BASE64` | `keystore/release.jks` encodé en base64 (`base64 -w0 keystore/release.jks`) |
| `MYSPOTI_KEYSTORE_PASSWORD` | mot de passe du keystore |
| `MYSPOTI_KEY_ALIAS` | alias de la clé |
| `MYSPOTI_KEY_PASSWORD` | mot de passe de la clé |

Sans `MYSPOTI_KEYSTORE_BASE64`, le workflow `Release` se contente d'un avertissement et ne
construit rien (il ne peut pas signer un build stable sans clé).

## Couper une release

```bash
git checkout develop && git pull
# … travail, tests, commits …
git checkout main && git pull
git merge --no-ff develop
git push origin main

git tag v1.2.0          # sur main
git push origin v1.2.0  # déclenche Release
```

Le workflow :

1. vérifie que le tag est bien sur `main` ;
2. calcule `versionName = 1.2.0` et `versionCode = 10200` (majeur × 10000 + mineur × 100 + correctif) ;
3. signe avec le keystore des secrets ;
4. publie **`app-mobile-release.apk`** dans la GitHub Release, avec les notes générées depuis les
   commits.

### Android Auto et Android Automotive OS, deux choses différentes

- **Android Auto** : l'app tourne **sur le téléphone** et l'écran de la voiture l'affiche par
  projection. Rien ne s'installe dans la voiture : c'est l'APK du téléphone (`app-mobile-release.apk`)
  qui suffit.
- **Android Automotive OS** : la voiture fait tourner Android elle-même ; les apps s'installent
  dans la voiture via la piste « automotive » du Play Store (ou un sideload pour le développement).
  C'est un autre produit, rare, et un APK séparé (`flavor automotive`). Le workflow
  **`Automotive APK`** le construit à la demande (`workflow_dispatch`) ; il n'est pas publié avec
  les releases normales.

On peut aussi lancer `Release` à la main (`workflow_dispatch`) avec un numéro de version : la
release est alors publiée sous le tag correspondant.

## Build local

```bash
# avec keystore/keystore.properties (ou les variables MYSPOTI_*)
./gradlew :app:assembleRelease -PappVersionName=1.2.0 -PappVersionCode=120
# APK : app/build/outputs/apk/mobile/release/app-mobile-release.apk

# pour l'émulateur voiture (x86_64), non distribué
./gradlew :app:assembleAutomotiveRelease -Pabis=x86_64
```

Une release « stable » (version sans `-beta`) refuse de se construire sans clé de release :
c'est voulu (`app/build.gradle.kts`).
