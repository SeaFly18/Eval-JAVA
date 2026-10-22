# InfosAdresse

API REST qui agrège, à partir d'une simple adresse, des informations publiques :
géocodage, risques naturels et technologiques (Géorisques), monuments
historiques à proximité (base Mérimée), parcelles cadastrales (API Carto IGN)
et ventes immobilières récentes (DVF).

## Principe

```
adresse ──▶ Géocodage (coordonnées) ──┬──▶ Géorisques (risques)            ──┐
                                       ├──▶ Monuments (base Mérimée)        ──┤
                                       ├──▶ Cadastre (parcelles, API Carto) ──┼──▶ réponse agrégée
                                       └──▶ Foncier (ventes DVF)            ──┘
```

Les quatre services métier s'exécutent **en parallèle** (threads virtuels),
tout comme la pagination de la base Mérimée et le téléchargement des fichiers
DVF annuels côté Foncier.

## Prérequis

- **JDK 25**
- **Maven 3.9+** (ou le wrapper `./mvnw`)

> ⚠️ Le projet cible Java 25 (`pom.xml`). Vérifiez que `JAVA_HOME` pointe vers un JDK 25 :
> ```bash
> export JAVA_HOME=$(/usr/libexec/java_home -v 25)   # macOS
> ```

## Lancer l'application

```bash
mvn spring-boot:run
```

L'API démarre sur **http://localhost:8080**.

Pour changer le port :

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

## Tests

```bash
mvn test
```

## Documentation interactive (Swagger)

Une fois l'application démarrée :

- **Swagger UI** : http://localhost:8080/swagger-ui.html
- **OpenAPI JSON** : http://localhost:8080/v3/api-docs

## Endpoints

### `GET /infos/{adresse}`

Endpoint principal (et unique) : géocode l'adresse puis agrège, en parallèle,
risques + monuments + cadastre + ventes foncières.

| Paramètre      | Type   | Défaut | Description                              |
|----------------|--------|--------|------------------------------------------|
| `adresse`      | path   | —      | Adresse en texte libre                   |
| `nbMonuments`  | query  | `5`    | Nombre de monuments les plus proches     |
| `nbVentes`     | query  | `5`    | Nombre de ventes immobilières proches    |

Exemple :

```bash
curl "http://localhost:8080/infos/88%20rue%20abel%20gance?nbMonuments=5&nbVentes=5"
```

Réponse (extrait) :

```json
{
  "adresseRecherchee": "88 rue abel gance",
  "latitude": 49.048586,
  "longitude": 2.113939,
  "risques": {
    "adresse": "88 Rue Abel Gance, 95310 Saint-Ouen-l'Aumône",
    "commune": "Saint-Ouen-l'Aumône",
    "risquesPrincipaux": [
      {
        "categorie": "naturel",
        "libelle": "Inondation",
        "libelleStatutCommune": "Risque Existant",
        "libelleStatutAdresse": "Risque non Connu"
      }
    ]
  },
  "monuments": {
    "adresse": "88 Rue Abel Gance 95310 Saint-Ouen-l'Aumône",
    "commune": "Saint-Ouen-l'Aumône",
    "departement": "95",
    "monuments": [
      {
        "titre": "Ancienne abbaye de Maubuisson",
        "commune": "Saint-Ouen-l'Aumône",
        "nature": "arrêté",
        "reference": "PA00080199",
        "distanceMetres": 287.49,
        "urlNotice": "https://www.pop.culture.gouv.fr/notice/merimee/PA00080199"
      }
    ]
  },
  "cadastre": {
    "parcelles": [
      {
        "idu": "95572000AI0123",
        "section": "AI",
        "numero": "0123",
        "commune": "Saint-Ouen-l'Aumône",
        "contenance": 512
      }
    ]
  },
  "ventes": {
    "adresse": "88 Rue Abel Gance 95310 Saint-Ouen-l'Aumône",
    "commune": "Saint-Ouen-l'Aumône",
    "codeInsee": "95572",
    "ventes": [
      {
        "date": "2024-05-14",
        "nature": "Vente",
        "valeur": 285000,
        "types": "Maison",
        "surfaceBati": 95,
        "prixM2": 3000,
        "distanceMetres": 142.8
      }
    ]
  }
}
```

## Sources de données

Les services métier sont tous agrégés par `GET /infos/{adresse}` :

| Service                | API externe                                                        |
|------------------------|--------------------------------------------------------------------|
| Géocodage              | `https://data.geopf.fr/geocodage` (`/search`, `/reverse`)          |
| Géorisques             | `https://georisques.gouv.fr/api/v1/resultats_rapport_risque`       |
| Monuments (Mérimée)    | `https://tabular-api.data.gouv.fr` (jeu de données POP Culture)    |
| Cadastre               | `https://apicarto.ign.fr/api/cadastre`                             |
| Foncier (DVF)          | `https://files.data.gouv.fr` (fichiers DVF annuels par commune)    |

## Robustesse réseau

L'API Géorisques renvoie fréquemment des resets de connexion (throttling).
Pour y faire face :

- le client HTTP est forcé en **HTTP/1.1** (`RestClientConfig`) ;
- l'appel Géorisques est **réessayé** (jusqu'à 8 tentatives, backoff) avec un
  en-tête `Accept-Encoding: identity` pour éviter les erreurs de décompression ;
- la pagination Mérimée réessaie chaque page (timeouts transitoires de l'API tabular) ;
- côté Foncier, les fichiers DVF des dernières années sont téléchargés **en
  parallèle** (threads virtuels) pour limiter le temps de réponse.

## Structure

```
src/main/java/fr/vinci/petitcrudfilms/
├── CrudApplication.java          # point d'entrée Spring Boot
├── RestClientConfig.java         # RestClient.Builder (HTTP/1.1)
├── controller/
│   └── AppController.java        # GET /infos/{adresse} (agrégation)
├── entity/                       # modèles de géocodage
└── service/
    ├── GeocodageService.java
    ├── GeorisquesService.java
    ├── MonumentsService.java
    ├── CadastreService.java
    └── FoncierService.java
```
