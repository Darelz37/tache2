# Proclama

Proclame est un Portail de publication et de consultation de résultats d'examens, conçu pour absorber des pics de charge.
**Angular 18** · **Spring Boot 3.5 (Java 21)** · **PostgreSQL** · stockage objet **S3 (SeaweedFS)**.

## 1. Prérequis (à installer une fois)

| Outil | Version | Vérification |
|---|---|---|
| JDK | 21 | `java -version` |
| Maven | 3.9+ | `mvn -v` |
| Node.js (avec npm) | 22 LTS | `node -v` |
| PostgreSQL | 16 ou 17 (service démarré, port 5432) | `psql --version` |
| SeaweedFS (`weed`) | 3.80, un seul exécutable | voir étape 3 |

## 2. Base de données (une seule fois)

```bash
psql -U postgres -f scripts/init-db.sql
```

Cela crée l'utilisateur et la base `proclama`. Les tables sont créées par l'API au premier démarrage.

## 3. Stockage S3 (SeaweedFS)

Télécharger `windows_amd64.zip` (ou `linux_amd64.tar.gz`) depuis
<https://github.com/seaweedfs/seaweedfs/releases/tag/3.80>, puis placer `weed.exe` (ou `weed`) dans un dossier `tools/` à la racine du projet.

```powershell
.\scripts\start-s3.ps1        # Windows (PowerShell)
./scripts/start-s3.sh         # Linux / macOS / Git Bash
```

Laisser ce terminal ouvert.

## 4. API et worker (Spring Boot)

Dans deux nouveaux terminaux :

```powershell
.\scripts\start-api.ps1             # API sur http://localhost:8080
.\scripts\start-api.ps1 -Worker     # worker d'import CSV
```
(`./scripts/start-api.sh` et `./scripts/start-api.sh worker` sous Linux / macOS.)

Le premier lancement compile le projet (`mvn package`). Vérification : <http://localhost:8080/api/ready> doit répondre `READY`.

## 5. Application web (Angular)

```bash
cd web
npm ci
npm start
```

Ouvrir <http://localhost:4200> (les appels `/api` sont redirigés vers `localhost:8080`).

Les credentials de Connexion du personnel sont : `admin@proclama.local` / `admin123`.

Pour tester un import, générer par exemple un CSV : `sh scripts/gen-csv.sh 50000 > resultats.csv`.

## Configuration : profils Spring

| Fichier | Rôle |
|---|---|
| `application.yml` | Paramètres communs. Choisit le profil via `SPRING_PROFILES_ACTIVE` (par défaut : `dev`). |
| `application-dev.yml` | **Vraies valeurs** pour le développement local. |
| `application-prod.yml` | **Uniquement des placeholders** `${...}`, fournis à l'exécution avec `SPRING_PROFILES_ACTIVE=prod`. |

Variables attendues en `prod` : `DATABASE_URL`, `S3_ENDPOINT`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `JWT_SECRET` (obligatoires) ;
`S3_BUCKET`, `DB_POOL_SIZE`, `ENABLE_DEBUG`, `RATE_LIMIT_PER_MIN`, `WORKER_INTERVAL_SECONDS`, `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD`, `LOG_LEVEL` (avec valeur par défaut).
Format de `DATABASE_URL` : `postgres://utilisateur:motdepasse@hote:5432/base`.

## Arborescence

| Dossier            | Contenu                                                                                                                                                                                                                                     |
|--------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `api/` et `worker` | API et worker Spring Boot (il s'agit du même jar, le worker se lance avec `--app.mode=worker`)                                                                                                                                              |
| `web/`             | Application Angular                                                                                                                                                                                                                         |
| `scripts/`         | `init-db.sql` pour creer l'utilisateur et la base de donnée proclame, `s3-dev.json` pour la configuration de seaweedfs, `start-s3.*` pour demarer le seaweedfs, `start-api.*` pour demarer l'api, `gen-csv.sh` pour generer  un fichier csv |
 
## Ports utilisés

`4200` web · `8080` API · `5432` PostgreSQL · `8333` S3 · `8081`, `9333`, `8888` SeaweedFS (internes).
