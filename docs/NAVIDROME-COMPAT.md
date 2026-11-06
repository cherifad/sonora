# Compatibilité Navidrome (API Subsonic)

Revue du client contre la documentation développeur de Navidrome
([`subsonic-api.md`](https://github.com/navidrome/website/blob/master/content/en/docs/developers/subsonic-api.md),
[`adding-apps.md`](https://github.com/navidrome/website/blob/master/content/en/docs/developers/adding-apps.md)),
testée contre Navidrome **0.64.2** (sans ffmpeg) sur `10.0.2.2:4533`.

## Points de l'API utilisés

| Endpoint | Usage | État |
|---|---|---|
| `ping` | test de connexion (onboarding) | ✅ |
| `getAlbumList2` | accueil (`newest`, `frequent`, `recent`), bibliothèque (`alphabeticalByName`) | ✅ types supportés |
| `getArtists` | index des artistes (index → artistes) | ✅ |
| `getArtist`, `getAlbum`, `getSong` | pages | ✅ |
| `getTopSongs`, `getArtistInfo2` | bio et titres populaires | ✅ (dégradé : sans Last.fm, réponse vide) |
| `search3` | recherche | ✅ requêtes simples, sans Lucene |
| `getPlaylist`, `createPlaylist`, `updatePlaylist`, `deletePlaylist` | playlists | ✅ |
| `getStarred2`, `star`, `unstar` | favoris | ✅ |
| `getGenres` | genres | ✅ |
| `getAlbumList2?type=byGenre`, `getSongsByGenre` | pages genre (albums + titres) | ✅ |
| `getRandomSongs`, `getSimilarSongs2` | « For you », radio et « More like this » | ✅ |
| `setRating` | notes 0..5 (`userRating` en lecture) | ✅ |
| `getOpenSubsonicExtensions` | sonde de capacités (une fois par session) | ✅ |
| `getBookmarks`, `createBookmark`, `deleteBookmark` | signets | ✅ |
| `getShares`, `createShare`, `deleteShare` | partages (lien copié depuis la fiche du titre) | ✅ |
| `getScanStatus`, `startScan` | état du scan et « Scan now » dans Settings | ✅ |
| `getNowPlaying`, `getAvatar` | now playing du serveur, avatar du compte | ✅ |
| champ `replayGain` | normalisation (piste ou album, plafonnée par le peak) | ✅ |
| `getLyricsBySongId`, `getLyrics` | paroles | ✅ OpenSubsonic puis repli |
| `stream` (+ `format`, `maxBitRate`) | lecture et téléchargements | ✅ |
| `getCoverArt` | pochettes | ✅ |
| `scrobble` | « en cours » et écoute | ✅ `submission=false` puis `true` |
| `savePlayQueue`, `getPlayQueue` | file persistée au serveur | ✅ |

## Spécificités Navidrome prises en compte

- **Les IDs sont des chaînes** (MD5/UUID) : elles ne sont jamais converties en entier ; l'app
  calcule un `Int` de ligne à partir du hash de la chaîne (`stableId`), pas de l'ID serveur.
- **`scrobble`** : Navidrome ne compte une écoute que sur `submission=true`. L'app envoie
  `submission=false` au démarrage, puis `true` à `min(durée/2, 4 min)`.
- **`getPlayQueue.current`** est une chaîne, pas un entier : lu avec `optString`.
- **`search3`** : l'app n'envoie que des requêtes simples (pas de syntaxe Lucene).
- **`getIndexes` / `getMusicDirectory`** : non utilisés (Navidrome ne fait que simuler une
  arborescence).
- **`getTopSongs` / `getArtistInfo2`** : nécessitent Last.fm ; les appels passent par
  `callOptional` (un endpoint refusé est mémorisé) et retombent sur les albums de l'artiste /
  une bio vide, donc sans erreur visible.
- **Téléchargements** : `stream` avec `format`/`maxBitRate` selon la qualité ; si le serveur ne
  sait pas transcoder (ffmpeg absent), l'app **retombe sur le fichier original** — vérifié sur
  le Navidrome de test.
- **Multi-bibliothèques** : tout ce que l'utilisateur peut voir est listé ; pas de sélecteur de
  « music folder ».

## Écarts connus (non bloquants)

- **Authentification par API key** : implémentée quand le serveur annonce
  `apiKeyAuthentication` (champ optionnel dans l'écran serveur) ; le token `u`/`t`/`s` reste le
  défaut.
- **`getOpenSubsonicExtensions`** : sonde de capacités au démarrage, mise en cache et
  réinitialisée quand le serveur change.
- **`getMusicFolders`** : non utilisé (tout est fusionné) ; un sélecteur de bibliothèque
  reste à faire pour les serveurs multi-dossiers.
- **Multi-bibliothèques** : pas de sélecteur de « music folder ».

## Catalogue des clients Navidrome

L'app pourra être proposée au
[catalogue](https://www.navidrome.org/apps/) quand le dépôt sera public : il faut soit un dépôt
open source avec **≥ 15 étoiles**, soit une fiche sur un store ; les captures doivent être en
WebP (≤ 1200 px). À faire au moment de la publication (M6/M7).
