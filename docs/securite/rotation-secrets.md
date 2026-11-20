# Rotation des secrets — lot 0 sécurité

Jusqu'au correctif C3, plusieurs secrets étaient écrits en clair dans des fichiers suivis par git (`docker-compose.yml`, `application*.yml`, `DataLoader.java`, `utils/BcryptGen.java`). Le lot 0 les retire du code, mais **ils restent lisibles dans l'historique git** et dans tout clone existant. Ils doivent donc être considérés comme **compromis** et régénérés : retirer un secret du dépôt ne suffit pas.

Ce document ne contient aucune valeur.

## 1. Secrets à régénérer

| Variable | Rôle | Où la régénérer |
|---|---|---|
| `JWT_SECRET` | Signature des jetons de connexion (HS256) | Sur le serveur : `openssl rand -base64 48`. Aucun fournisseur externe |
| `MAIL_PASSWORD` | Mot de passe SMTP du compte d'envoi | Fournisseur SMTP (Gmail : compte Google → Sécurité → Mots de passe d'application). Révoquer l'ancien mot de passe d'application **puis** en créer un nouveau |
| `GOOGLE_MAPS_API_KEY` | Géocodage des adresses | Console Google Cloud → API et services → Identifiants. Créer une nouvelle clé, la **restreindre** (API Geocoding seulement, restriction par adresse IP du VPS), puis supprimer l'ancienne |
| `SUPERADMIN_INITIAL_PASSWORD` | Mot de passe initial du compte super-admin (`DataLoader`) | Choisir une valeur forte. Elle ne sert que si le compte n'existe pas encore |
| Mot de passe actuel du compte super-admin | Le mot de passe initial codé en dur était connu de tous les lecteurs du dépôt | Si le compte a gardé ce mot de passe initial, se connecter et en changer **immédiatement** |
| `MAIL_USERNAME` | Pas un secret, mais retiré de `docker-compose.yml` avec les autres | — |

Mots de passe des comptes applicatifs : le hash BCrypt de **tous** les comptes était renvoyé sans authentification par `GET /api/login` jusqu'à C1. Il faut forcer un changement de mot de passe pour les comptes sensibles (SUPERADMIN, RH, CONTROLEUR_STOCK) et, idéalement, pour tous.

Effet de la rotation de `JWT_SECRET` : **tous les jetons en cours deviennent invalides**. Chaque utilisateur devra se reconnecter, ce qui est voulu.

## 2. Où les poser

Sur le VPS, dans le dossier du `docker-compose.yml` du backend :

```bash
cp .env.example .env
chmod 600 .env          # lisible par le seul propriétaire
nano .env               # renseigner chaque variable
```

`.env` n'est pas suivi par git (`.gitignore`) et n'est pas copié dans l'image (`.dockerignore`). En profil `prod`, l'application **refuse de démarrer** si `JWT_SECRET`, `MAIL_USERNAME`, `MAIL_PASSWORD` ou `GOOGLE_MAPS_API_KEY` manque. Le message d'erreur donne les noms des variables manquantes, jamais leur valeur.

En développement, le même fichier `.env` à la racine du projet est lu automatiquement (`spring.config.import: optional:file:.env[.properties]`).

⚠ Le `docker-compose.yml` du dossier parent `Projets en cours/` est hors dépôt et n'a pas été modifié. Vérifiez s'il contient lui aussi des secrets en clair. Si c'est le cas, appliquez-lui le même traitement : `env_file`, puis rotation.

## 3. Ordre de déploiement

1. **Préparer** les nouvelles valeurs : clé JWT générée, nouveau mot de passe d'application SMTP, nouvelle clé Google restreinte. Ne pas encore révoquer les anciennes.
2. **Écrire `.env`** sur le VPS avec les nouvelles valeurs, plus `SUPERADMIN_INITIAL_PASSWORD` si le compte super-admin doit être recréé.
3. **Déployer** la branche `fix/securite-lot-0` (back puis front) : `docker compose up -d --build`. Vérifier les journaux de démarrage : pas de « Démarrage refusé ».
4. **Tester** : connexion, envoi d'un mail (mot de passe oublié), géocodage d'un site.
5. **Révoquer** les anciennes valeurs : ancien mot de passe d'application SMTP, ancienne clé Google.
6. **Changer** le mot de passe du super-admin s'il était encore l'ancien mot de passe initial, puis forcer le changement pour les comptes sensibles.
7. **Purger l'historique git** (section 5), seulement après les étapes 1 à 6. La purge ne protège pas des copies déjà faites : c'est la rotation qui protège.

## 4. MongoDB : à traiter (non appliqué dans ce lot)

Constat : l'URI Mongo (`mongodb://mongo:27017/...`) n'a **pas d'identifiants**, et `docker-compose.yml` publie le port `27018:27017` **sur toutes les interfaces de l'hôte**. Si le pare-feu du VPS ne bloque pas ce port, la base est lisible et modifiable depuis Internet sans mot de passe.

Procédure proposée (à valider avant application) :

1. **Fermer le port tout de suite** : remplacer `"27018:27017"` par `"127.0.0.1:27018:27017"` (accès local uniquement, par exemple pour un tunnel SSH), ou supprimer le bloc `ports` du service `mongo`. Le backend joint Mongo par le réseau interne docker (`mongo:27017`) et n'a pas besoin du port publié. Vérifier ensuite avec `ss -tlnp | grep 27018` sur le VPS.
2. **Créer un utilisateur applicatif** :
   ```bash
   docker exec -it mongo mongosh
   use admin
   db.createUser({ user: "pointic_app", pwd: passwordPrompt(), roles: [ { role: "readWrite", db: "pointage_cleanic" } ] })
   ```
   Puis activer l'authentification : variables `MONGO_INITDB_ROOT_USERNAME` / `MONGO_INITDB_ROOT_PASSWORD` dans `.env`, ou `command: ["--auth"]` sur le service `mongo`.
3. **Passer l'URI en variable** : `spring.data.mongodb.uri: ${MONGODB_URI}` dans `application-prod.yml`, avec `MONGODB_URI=mongodb://pointic_app:<mot de passe>@mongo:27017/pointage_cleanic?authSource=admin` dans `.env`. Ajouter `MONGODB_URI` à `SecretsRequisVerificateur.SECRETS_REQUIS_PROD` et à `.env.example`.
4. **Faire une sauvegarde avant** (`mongodump`) et tester la reconnexion du backend.

## 5. Purge de l'historique git — à appliquer par vous seul

⚠ **Réécrit l'historique.** Ne la lancez qu'une fois la rotation faite (section 3). Ce n'est pas Claude qui l'exécute.

### Risques

- **Tous les hash de commit changent** à partir du premier commit qui contenait un secret. Tout autre clone (autres postes, CI, VPS, worktrees) devient incompatible : il faut **le supprimer et recloner**. Un `git pull` sur un ancien clone réintroduirait l'historique non purgé.
- Les **pull requests ouvertes** et les branches non fusionnées partent de l'ancien historique : elles seront à rebaser ou à recréer. C'est le cas ici de `feature/reclamation-qr-site` et `feature/pointage-qr-agence` (back et front). **Fusionnez ou sauvegardez-les avant** (`git bundle create sauvegarde.bundle --all`).
- GitHub garde les anciens objets accessibles par leur hash pendant un temps, ainsi que dans les forks et dans les PR fermées. Pour une purge complète, il faut contacter le support GitHub (« remove cached views / sensitive data »).
- Les copies déjà faites (clones, sauvegardes, caches de CI) ne sont **pas** purgées. C'est pour cela que la rotation passe en premier.

### Procédure

```bash
# 0. Prérequis : pip install git-filter-repo ; prévenir tous les contributeurs ; geler les pushs.
# 1. Clone miroir neuf (ne pas travailler dans un clone existant)
git clone --mirror git@github.com:<organisation>/Pointage-Cleanic-Backend.git purge.git
cd purge.git
git bundle create ../avant-purge.bundle --all        # sauvegarde intégrale

# 2. Fichier de remplacements, HORS du dépôt et jamais commité : une ligne par secret,
#    au format  <ancienne valeur>==>***SECRET-RETIRE***
#    (lister les anciennes valeurs depuis votre gestionnaire de mots de passe, pas depuis ce document)
nano ../remplacements.txt

# 3. Réécriture : remplace les valeurs dans tout l'historique et supprime l'utilitaire BcryptGen
git filter-repo --replace-text ../remplacements.txt --invert-paths --path src/main/java/com/example/Pointage_Cleanic/utils/BcryptGen.java

# 4. Vérification : aucune ancienne valeur ne doit subsister
git log --all -p | grep -F -f <(sed 's/==>.*//' ../remplacements.txt) && echo "ÉCHEC : valeur encore présente" || echo "OK"

# 5. Publication (réécriture forcée, assumée ici)
git push --force --mirror

# 6. Nettoyage
shred -u ../remplacements.txt
# puis, sur CHAQUE poste / serveur : supprimer l'ancien clone et recloner.
```

Même procédure pour le dépôt front si l'audit y relève un secret (rien n'a été relevé dans le front par ce lot).
