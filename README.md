# Carte de l'aventurier

Mod Fabric pour Minecraft 1.21.1 : une carte au trésor qui suit **ta** progression dans le modpack du
serveur.

- Clic droit avec la carte en main : la carte du monde s'ouvre, avec sept régions (Terres du Milieu,
  Mers, Profondeurs, Aether, Nether, End, Forêt du Crépuscule) et 169 objectifs.
- À droite, la liste « Que faire ? » : les récompenses à réclamer, puis tout ce que tu peux faire
  maintenant, région par région. Un clic t'emmène sur l'objectif.
- Double-clic sur une région : ses routes d'objectifs apparaissent (molette pour faire défiler les
  grandes régions). Chaque ✕ est un objectif : boss, donjon, structure, mais aussi guildes de magie,
  cuisine, pêche, primes, photos, animaux. Sa fiche dit où chercher. Réussi, il devient un sceau doré.
- Les étapes suivantes restent dans le brouillard tant que tu n'as pas réussi celle d'avant.
- Chaque objectif donne une récompense à réclamer depuis la carte : des ressources, ou un objet
  unique pour les boss et les épreuves finales.
- Chacun a sa progression. Ce que tu as déjà fait avant l'arrivée du mod (boss tués, progrès) est
  pris en compte. Un boss battu à plusieurs compte pour tous ceux qui étaient à côté.
- Tu reçois la carte à ta première connexion. Perdue ? Tape `/carte`.

Aucune touche ajoutée.

## Pour l'admin du serveur
- Mod requis côté serveur **et** client.
- Objectifs et récompenses : `src/main/resources/adventuremap/default_map.json`. Pour les changer
  sans nouvelle version, copier ce fichier dans `config/adventuremap/map.json` du serveur et le
  modifier : les joueurs reçoivent la carte du serveur.
- Au démarrage, le log liste les identifiants inconnus (mod absent, faute de frappe).

## Compiler
```
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew build
```
Le jar est dans `build/libs/`.

Licence GPL-3.0.
