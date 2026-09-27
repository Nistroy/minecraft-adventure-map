# minecraft-adventure-map — instructions agents

Mod Fabric 1.21.1 (client + serveur, `required` des deux côtés) : « Carte de l'aventurier », un objet
qui ouvre une carte au trésor de la progression **de chaque joueur**. Public, GPL-3.0. Pour le
serveur `minecraft-server` (identifiants de mods tirés de son modpack). Docs `.md` = notes denses
pour agents, sauf `README.md` (humains).

## Carte du code
- `src/main/java/io/github/nistroy/adventuremap/`
  - `map/` (pur, testé) — `MapDefinition` lit/valide le JSON · `Region`, `MapNode`, `Reward` ·
    `Requirement` (une façon de réussir, les alternatives d'un objectif sont en OU) · `PlayerFacts`
    (ce que le serveur sait d'un joueur) · `Progress` (réussis, visibles, disponibles, réclamables,
    état de région, liste « À faire » `todo()`, `unclaimed()` ; même code serveur et client).
  - `geometry/` (pur, testé) — `Blob` contour de région, `Polygon` remplissage par lignes + clic,
    `Curve` pointillés, `DoubleClick`.
  - `server/` — `PlayerRecord` données par joueur (attachment Fabric persistant, `copyOnDeath`,
    testé) · `ServerFacts` stats + progrès vanilla du joueur · `MapService` état, réclamations, don de
    la carte, annonces · `Tracker` structures/dimensions (1×/s), boss vus tomber (64 blocs) ·
    `MapCommand` `/carte`.
  - `network/` — `MapStatePayload` (S→C : JSON de la carte + réussis + réclamés + ouvrir ?) ·
    `ClaimPayload` (C→S).
  - `AdventureMap` entrée + registres · `AdventureMapItem` · `Rewards` récompense → `ItemStack` ·
    `MapCheck` identifiants inconnus listés au démarrage.
- `src/client/java/.../client/` — `AdventureMapScreen` écran (monde → double-clic → région, fiche à
  droite = liste « À faire » cliquable ; molette = défilement région/fiche) · `MapPainter` dessin (le
  GUI ne fait que rectangles + textures) · `Viewport` repère 1000 × 600 ↔ pixels GUI, `scrollY` · `AdventureMapClient` réception de l'état.
- `src/main/resources/adventuremap/default_map.json` — **la carte** : 7 régions, 169 objectifs
  (v0.2.0), récompenses. Par objectif : `hint` (où chercher, requis par les tests). Par région :
  `height` (> 600 → défile). Colonnes x ≈ 80/250/420/590/760/920, lignes y = 70 + 108·n.
  Conditions : `advancement`, `killed`, `visited` (+`count`), `dimension`, `picked_up`, `used`,
  `crafted`, `stat`, `nodes`. Surchargée par `config/adventuremap/map.json` du serveur si présent (pas besoin de
  nouvelle version pour régler objectifs/récompenses ; le client reçoit le JSON du serveur).
- `tools/generate_textures.py` — parchemin, brouillard, sceau, sprite **provisoire** de l'objet.

## Décisions (nistroy 2026-09-24)
- Progression par joueur, même en jouant ensemble ; jamais « le groupe a fini ».
- Rattrapage du passé : chaque objectif accepte aussi les stats vanilla (`killed`, `picked_up`,
  `used`, `stat`) et les progrès vanilla/mods déjà gagnés. Structures/dimensions : pas de trace
  vanilla → suivies seulement depuis l'installation, sauf progrès équivalent listé.
- Boss : `player_killed_entity` ne crédite que le coup final → `Tracker.onDeath` note aussi tous
  les joueurs à ≤ 64 blocs.
- 1re évaluation d'un joueur muette (`PlayerRecord.initialized`) : pas d'avalanche d'annonces.
- `/carte` redonne une carte si aucune dans l'inventaire ; la carte ne porte aucune donnée.
- Carte donnée à la 1re connexion (`map_given`).
- Récompenses uniques v0.1 = objets vanilla nommés + histoire + enchantements, sans pouvoir.

## Décisions v0.2 (nistroy 2026-09-27)
- Enrichir les 6 régions ; seule nouvelle région = nouvelle dimension : Forêt du Crépuscule.
- Anti « je sais pas quoi faire » : liste « À faire » + indice par objectif. Pas de compteurs ni paliers.
- Contenu hors aventure accepté : guildes (RPG Series), vie quotidienne (Farmer's Delight, Tide,
  Bountiful, sac), collection (Exposure, Lootr, Naturalist).
- Aether plus `hiddenUntilStarted` : la liste doit montrer le portail.
- Une seule entrée par région (test) ; `after` jamais vers une autre région.
- Finales Mers/Profondeurs/End plus exigeantes qu'en v0.1 : sceau perdu tant que non refait,
  réclamation jamais doublée (`claimed` gardé).
- Annihilation : seul le progrès vanilla (tueur) compte ; l'entité est une vache-hitbox taguée.

## Identifiants (relevés dans les jars de `minecraft-server/server/mods`, 2026-09-24)
- YUNG's renomme : forteresse `betterfortresses:fortress`, monument
  `betteroceanmonuments:ocean_monument`, donjons `betterdungeons:*` (on garde aussi l'id vanilla).
- Dungeons and Taverns = namespace `nova_structures`. Tidal Towns = `joshie:village_ocean`.
- Boss : `bosses_of_mass_destruction:{lich,gauntlet,void_blossom,obsidilith}`,
  `aquamirae:{captain_cornelia,maze_mother}`, `aether:{slider,valkyrie_queen,sun_spirit}`,
  `soulsweapons:{accursed_lord_boss,night_shade,returning_knight,moonknight,chaos_monarch,day_stalker,night_prowler}`,
  `friendsandfoes:{wildfire,iceologer,illusioner}`,
  `twilightforest:{naga,lich,minoshroom,hydra,knight_phantom,ur_ghast,alpha_yeti,snow_queen}`.
- Mini-boss D&T et boss Incendium = mobs vanilla tagués → progrès seulement.
- Dimensions : `aether:the_aether`, `deeperdarker:otherside`, `twilightforest:twilight_forest`.
- 2026-09-27 : tous les ids de la carte comparés aux jars du live + TF `4.8.629` → 0 inconnu. TF
  absent du live tant que `minecraft-server#52` n'est pas déployé.
- `runServer` sans les mods : ~100 identifiants inconnus attendus (tous modés), 0 vanilla.

## Tests — TDD obligatoire
- Red-Green-Refactor ; bug → test de régression d'abord ; jamais affaiblir un test.
- `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew build`
- Fini = build vert + `./gradlew runServer` propre (`Done (`, ligne `Carte de l'aventurier` sans
  identifiant vanilla inconnu). Écran → `./gradlew runClient`.
- CI `.github/workflows/ci.yml` sur chaque PR ; PR rouge jamais mergée.
- `No key layers in MapLike[{}]` au démarrage dev : bruit vanilla (aussi dans les logs de la barque).

## Règles
- Langue : identifiants EN ; docs, commentaires, textes joueurs FR ; commits/branches Conventional
  Commits EN.
- Git : jamais commit sur `main` ; branche `<type>/<sujet>` ; PR + merge via `gh`.
- Anti-invention : API vérifiée au `javap` sur les jars Loom (`.gradle/loom-cache/`), identifiants de
  mods lus dans leurs jars. Modrinth/GitHub → `curl`.
- Release : tag `vX.Y.Z` (= `version` de `gradle.properties`) → workflow release → jar attaché.
- Ajout au serveur = mod `required` des deux côtés → pack packwiz + accord nistroy (`CLAUDE.md` de
  `minecraft-server`).

## Non vérifiable sans joueur humain
Rendu de l'écran (étiquettes qui se chevauchent, défilement, liste « À faire »), double-clic, réclamation, don de la carte à la connexion, annonces, `/carte`
(exige un joueur), repérage des structures.
