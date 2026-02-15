# Bot-Discord (Java)

Bot Discord en Java (Discord4J) qui :

- répond quand on le mentionne (via un service LLM exposé sur une route backend `/ollama`) ;
- propose des commandes slash d’assistance (`/teach`, `/translate`, `/summarize`, `/qa`) ;
- propose des commandes slash “backend” (`/role create`, `/refresh`) pour piloter/synchroniser une guilde via HTTP.

Le projet est construit avec Maven et cible **Java 21**.

---

## Sommaire

- [Fonctionnalités](#fonctionnalités)
- [Stack & dépendances](#stack--dépendances)
- [Architecture du code](#architecture-du-code)
- [Prérequis](#prérequis)
- [Configuration](#configuration)
- [Lancer le bot](#lancer-le-bot)
- [Commandes / utilisation](#commandes--utilisation)
- [Backend attendu (contrat HTTP)](#backend-attendu-contrat-http)
- [Limitations & comportements](#limitations--comportements)
- [Développement](#développement)
- [Dépannage](#dépannage)

---

## Fonctionnalités

### 1) Réponse aux mentions

- Lorsqu’un message contient une mention du bot (`<@id>`), le bot enlève la mention et envoie le texte au service LLM.
- La réponse est **échappée** (anti-markdown) et **découpée** en morceaux de 2000 caractères (limite Discord) avant envoi.

### 2) Indexation de fichiers texte (pour `/qa`)

- Quand un message contient des pièces jointes de type `text/*`, le bot télécharge leur contenu et l’indexe en mémoire par salon.
- La commande `/qa` s’appuie sur ces documents indexés comme contexte.

### 3) Commandes slash “LLM”

- `/teach concept:<string>` : explication pédagogique d’un concept
- `/translate text:<string>` : traduction en français
- `/summarize text:<string>` : résumé en français
- `/qa question:<string>` : Q/R basée sur les documents indexés dans le salon

### 4) Commandes slash “backend”

- `/role create name:<string> permissions:<csv>` : appelle le backend pour créer un rôle
- `/refresh` : envoie au backend un “snapshot” de la guilde (rôles, membres, ownerId…)

---

## Stack & dépendances

Déclarées dans `pom.xml` :

- **Java 21** (source/target 21)
- **Discord4J** `com.discord4j:discord4j-core:3.2.9`
- **Reactor Netty** `io.projectreactor.netty:reactor-netty-http:1.3.0-RC1` (client HTTP)
- **Jackson** `com.fasterxml.jackson.core:jackson-databind:2.18.2` (JSON)
- **dotenv** `io.github.cdimascio:java-dotenv:5.2.2` (variables `.env`)
- **slf4j** `slf4j-api` + `slf4j-simple` (logs)
- **JUnit 5** (tests)

Assemblage : `maven-assembly-plugin` produit un `jar-with-dependencies` dont la classe main est `fr.univtln.pegliasco.tp.MyBot`.

---

## Architecture du code

Package principal : `fr.univtln.pegliasco.tp`

- `MyBot` : point d’entrée (`main`).
  - initialise la gateway Discord + intents
  - enregistre les commandes slash globales au démarrage
  - gère les handlers d’événements (messages et interactions)
  - appelle le backend (`/refresh`, `/role`) et le LLM (via `OllamaClient`)
- `ApiConfig` : lit la config et construit les URLs backend.
- `OllamaClient` : client HTTP vers l’endpoint LLM (`/ollama`).
- `MessageUtils` : utilitaires (split 2000 chars, échappement markdown, troncature “safe”).

---

## Prérequis

- **JDK 21** installé
- **Maven** (ou wrapper Maven si vous en ajoutez un)
- Un **bot Discord** créé sur le portail développeur Discord, avec son **token**
- Un **backend HTTP** accessible (local ou distant) exposant les routes attendues (voir [Backend attendu](#backend-attendu-contrat-http))

### Intents Discord

Le bot active :

- `GUILDS`
- `GUILD_MEMBERS`
- `GUILD_MESSAGES`
- `MESSAGE_CONTENT`

Sur le portail Discord Developer, pensez à activer les intents “privileged” si nécessaire (notamment **Message Content Intent** et **Server Members Intent**) selon votre configuration.

---

## Configuration

La config se fait via un fichier `.env` (chargé par `Dotenv.load()` dans `MyBot`).

### Variables d’environnement

- `DISCORD_TOKEN` (**obligatoire**) : token du bot Discord
- `BACKEND_BASE_URL` (optionnel) : base URL du backend, défaut `http://localhost:8080`

`ApiConfig` utilise `BACKEND_BASE_URL` et construit :

- LLM : `{BACKEND_BASE_URL}/ollama`
- Rôles : `{BACKEND_BASE_URL}/guilds/discord/{guildId}/roles`
- Refresh : `{BACKEND_BASE_URL}/guilds/discord/{guildId}/refresh`

### Exemple de `.env`

```dotenv
DISCORD_TOKEN=xxxxxxxxxxxxxxxxxxxxxxxxxxxx
BACKEND_BASE_URL=http://localhost:8080
```

---

## Lancer le bot

### Option A — Exécuter via Maven

```powershell
mvn -q test
mvn -q package
mvn -q exec:java -Dexec.mainClass="fr.univtln.pegliasco.tp.MyBot"
```

> Remarque : le projet ne déclare pas le plugin `exec-maven-plugin` dans le `pom.xml`. Sur certaines configs Maven, la dernière commande peut nécessiter d’ajouter le plugin (sinon utilisez l’option B).

### Option B — Exécuter le jar “fat”

Après compilation, Maven produit un jar autonome (avec dépendances) :

- `target/Bot-Discord-1.0-SNAPSHOT-jar-with-dependencies.jar`

Exécution :

```powershell
java -jar .\target\Bot-Discord-1.0-SNAPSHOT-jar-with-dependencies.jar
```

---

## Commandes / utilisation

### Mention du bot

Dans un salon où le bot a accès, mentionnez-le puis écrivez votre message :

- `@MonBot Peux-tu m’expliquer les streams en Java ?`

Le bot envoie le contenu au LLM et répond dans le salon.

### `/teach`

- Paramètre : `concept` (obligatoire)
- Réponse : explication structurée (définition, utilité, exemple, pièges)

### `/translate`

- Paramètre : `text` (obligatoire)
- Réponse : traduction en français

### `/summarize`

- Paramètre : `text` (obligatoire)
- Réponse : résumé en français

### `/qa`

- Paramètre : `question` (obligatoire)
- Pré-requis : avoir envoyé au moins **un fichier texte** dans ce salon (pièce jointe)
- Réponse : le bot construit un contexte avec les documents indexés et interroge le LLM.

### `/role create`

- Paramètres :
  - `name` (obligatoire)
  - `permissions` (obligatoire) : chaîne CSV (ex: `READ_MESSAGES,SEND_MESSAGES`)

Le bot POST un JSON au backend pour créer un rôle.

### `/refresh`

Envoie un snapshot JSON de la guilde au backend (rôles + membres + ownerId si dispo).

---

## Backend attendu (contrat HTTP)

Le bot ne parle **pas** directement à Ollama : il appelle un **backend** unique à `BACKEND_BASE_URL`, qui expose plusieurs routes.

### 1) POST `/ollama`

Utilisé par `OllamaClient`.

- Requête JSON :
  - `message` (string) : texte utilisateur **tronqué à 2000 chars** côté bot
  - `mode` (optionnel) : `teach | translate | summarize | qa`

- Réponse : le bot tente d’extraire le texte depuis l’un de ces champs :
  - `response` (prioritaire)
  - sinon `message`
  - sinon `content`
  - sinon il renvoie le corps brut

### 2) POST `/guilds/discord/{guildId}/roles`

Utilisé par `/role create`.

Payload envoyé :

```json
{
  "userDiscordId": 123456789,
  "roleName": "MonRole",
  "position": 0,
  "permissions": ["READ_MESSAGES", "SEND_MESSAGES"]
}
```

Le bot considère `2xx` comme succès, sinon affiche `HTTP <code> — <extrait du corps>`.

### 3) POST `/guilds/discord/{guildId}/refresh`

Utilisé par `/refresh`.

Payload envoyé (schéma indicatif) :

```json
{
  "id": 123,
  "name": "MaGuilde",
  "ownerId": 456,
  "roles": [
    {
      "id": 1,
      "name": "Admin",
      "color": 16711680,
      "position": 10,
      "permissions": 123456,
      "mentionable": true
    }
  ],
  "members": [
    {
      "id": 42,
      "username": "user",
      "discriminator": "0001",
      "displayName": "User",
      "roleIds": [1, 2],
      "joinedAt": "2026-02-15T..."
    }
  ]
}
```

---

## Limitations & comportements

- **Indexation des documents** :
  - uniquement `text/*` (si `Content-Type` est absent, le bot tente quand même)
  - stockée **en mémoire** (`CHANNEL_DOCUMENTS`) → perdu au redémarrage
- **/qa** :
  - chaque document est tronqué à 2000 caractères lors de la construction du contexte
  - le contexte complet peut devenir très gros si beaucoup de fichiers sont envoyés (risque d’être tronqué côté `OllamaClient` à 2000 au moment de l’envoi)
- **Découpage Discord** : réponses découpées en morceaux de 2000 caractères maximum
- **Troncature backend** : `OllamaClient` tronque `message` à 2000 chars pour éviter un rejet (observé) côté backend

---

## Développement

### Tests

Un test JUnit couvre `MessageUtils` : `src/test/java/.../MessageUtilsTest.java`.

Lancer les tests :

```powershell
mvn test
```

### Build

```powershell
mvn package
```

---

## Dépannage

### Le bot ne démarre pas / token null

- Vérifiez que `.env` est présent à la racine du projet (répertoire de lancement)
- Vérifiez `DISCORD_TOKEN`

### Les commandes slash n’apparaissent pas

- Les commandes sont enregistrées comme **globales** : la propagation peut prendre quelques minutes.
- Vérifiez les logs au démarrage : le bot log “déjà enregistrée” ou “enregistrement…”.

### `/qa` dit “Aucun document indexé”

- Envoyez une pièce jointe texte dans **le même salon**, attendez la log “Texte indexé…”, puis relancez `/qa`.

### Le backend renvoie HTTP 4xx/5xx

- Vérifiez `BACKEND_BASE_URL`
- Vérifiez que les routes existent et acceptent le JSON attendu
- Le bot tronque certains corps d’erreur (pour éviter d’afficher du HTML trop long)

### Messages coupés / formatage bizarre

- Le bot échappe une partie du markdown (`*`, `_`, `` ` ``, `~`, `\`) via `MessageUtils.escapeDiscordMarkdown`.
- Les réponses sont découpées à 2000 caractères.

---

## Licence

Projet pédagogique / étudiant (aucune licence spécifiée dans le dépôt à ce stade).
