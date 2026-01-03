# Bot Discord Java - État actuel du projet

## 1. Présentation du projet

Ce projet est un bot Discord développé en Java, utilisant l’API Discord via la bibliothèque Discord4J.  
Le bot communique avec un backend HTTP et un service de génération de texte de type LLM.

### Objectif réel du bot

D’après le code, le bot a pour objectifs concrets :

- Répondre aux messages où il est mentionné en s’appuyant sur un service de génération de texte (`OllamaClient`).
- Exposer des commandes slash permettant :
    - de demander une explication de concept technique (`/teach`) ;
    - de traduire un texte en français (`/translate`) ;
    - de résumer un texte en français (`/summarize`) ;
    - de poser une question sur des documents envoyés dans un salon (`/qa`) ;
    - de créer des rôles Discord via un backend (`/role create`) ;
    - de synchroniser l’état d’une guilde Discord vers un backend (`/refresh`).

### Cas d’usage effectivement implémentés

Fonctionnalités présentes dans le code :

- Réactions aux messages texte classiques lorsque le bot est mentionné.
- Indexation des pièces jointes texte envoyées dans un salon, pour un usage ultérieur par `/qa`.
- Commandes slash globales :
    - `/teach concept:<string>` : explication technique.
    - `/translate text:<string>` : traduction vers le français.
    - `/summarize text:<string>` : résumé en français.
    - `/qa question:<string>` : question basée sur des documents déjà envoyés dans le salon.
    - `/role create name:<string> permissions:<string>` : création d’un rôle via appel HTTP à un backend.
    - `/refresh` : envoi d’un instantané de la guilde au backend.

### Rôle du bot dans le système global

- Interface Discord entre les utilisateurs et :
    - un service HTTP de type LLM pour la génération de texte (`OllamaClient`) ;
    - un backend applicatif pour la gestion de rôles et la synchronisation de guildes (`/role`, `/refresh`).
- Point central de collecte de données Discord (rôles, membres, documents de salon) pour les transmettre au backend.

---

## 2. Organisation actuelle du code

### Structure réelle des packages

Le code montré est contenu dans le package principal :

- `fr.univtln.pegliasco.tp`

Les classes visibles :

- `MyBot`
- `ApiConfig`
- `MessageUtils`
- `OllamaClient`

### Responsabilités des principales classes

- `MyBot`
    - Classe principale contenant la méthode `main`.
    - Initialise le client Discord et configure les intents.
    - Instancie la configuration API et le client `OllamaClient`.
    - Enregistre les commandes slash globales.
    - Définit et branche tous les handlers d’événements (messages, interactions slash).
    - Gère la logique métier directement à l’intérieur des handlers.

- `ApiConfig`
    - Lit la configuration depuis l’environnement (`Dotenv`).
    - Construit l’URL de base du backend, avec une valeur par défaut `http://localhost:8080` si la variable d’environnement `BACKEND_BASE_URL` est absente ou vide.
    - Fournit des méthodes d’assemblage d’URL pour :
        - le service LLM (`ollamaUrl`) ;
        - l’API de création de rôles (`roleCreateUrl`) ;
        - l’API de rafraîchissement de guilde (`refreshGuildUrl`).

- `MessageUtils`
    - Découpe une chaîne longue en segments respectant la limite de 2000 caractères de Discord.
    - Recherche des points de coupure "propres" (sauts de ligne, espaces) et évite de couper au milieu de paires de surrogates (emojis).
    - Fournit une méthode d’échappement minimal de certains caractères Markdown pour limiter les effets de mise en forme dans Discord.

- `OllamaClient`
    - Client HTTP pour un service de génération de texte externe.
    - Construit et envoie des requêtes JSON à une URL fournie (mode générique et modes spécialisés).
    - Propose des méthodes dédiées :
        - `generate` (message libre) ;
        - `generateTeaching` (prompt adapté à `/teach`) ;
        - `generateTranslation` (prompt adapté à `/translate`) ;
        - `generateSummary` (prompt adapté à `/summarize`) ;
        - `generateQA` (prompt adapté à `/qa`).
    - Parse la réponse JSON pour extraire un champ textuel (`response`, `message` ou `content`), avec une logique de repli sur le corps brut.

### Couplage actuel entre commandes Discord et logique métier

- Les handlers de commandes slash sont définis directement dans `MyBot` et appellent immédiatement les méthodes de `OllamaClient` ou du client HTTP bas niveau.
- La construction des prompts, l’échappement Markdown et la découpe des messages sont effectués dans `MyBot` en combinant directement `OllamaClient` et `MessageUtils`.
- Les appels au backend (rôles, refresh guilde, téléchargement de pièces jointes) sont réalisés directement dans `MyBot` via `HttpClient` sans couche de service intermédiaire.

---

## 3. Gestion des commandes Discord

### Librairie Discord réellement utilisée

- Bibliothèque principale : `discord4j`  
  Utilisation des éléments suivants :
    - `DiscordClient`
    - `GatewayDiscordClient`
    - Événements :
        - `ReadyEvent`
        - `MessageCreateEvent`
        - `ChatInputInteractionEvent`
    - Modèles Discord : `Guild`, `Member`, `Message`, `User`, `Snowflake`
    - Types de commandes : `ApplicationCommandRequest`, `ApplicationCommandOptionData`

### Mode de gestion des événements et commandes

- Connexion via `DiscordClient.create(token)` puis `client.gateway().withGateway(...)`.
- Enregistrement des listeners d’événements sur le `GatewayDiscordClient` via `gateway.on(EventType.class, handler)`.
- Enregistrement des commandes slash globales à chaque démarrage via l’API REST Discord4J (`getApplicationService().createGlobalApplicationCommand`), après vérification de leur existence.

### Flux actuel de traitement d’une commande

Exemple de flux générique pour une commande slash (`/teach`, `/translate`, `/summarize`, `/qa`, `/role`, `/refresh`) :

1. Réception d’un `ChatInputInteractionEvent`.
2. Vérification du nom de la commande (`evt.getCommandName()`).
3. Extraction des options via `evt.getOption(...)` et `getValue().asString()`.
4. Vérifications simples (présence de texte, guilde valide, permissions passées en paramètre, etc.).
5. `evt.deferReply()` pour indiquer au client Discord que la réponse est en cours de préparation.
6. Appel à :
    - `OllamaClient` pour les commandes `/teach`, `/translate`, `/summarize`, `/qa`, ou
    - `HttpClient.create().post()` / `HttpClient.create().get()` pour les appels backend et téléchargements de fichiers.
7. Post‑traitement de la réponse :
    - Échappement Markdown via `MessageUtils.escapeDiscordMarkdown` pour les contenus générés.
    - Découpage en segments de ≤ 2000 caractères via `MessageUtils.splitForDiscord`.
8. Envoi des réponses sous forme de followups (`evt.createFollowup().withContent(part)`).

Pour les messages classiques :

1. Réception d’un `MessageCreateEvent`.
2. Indexation éventuelle des pièces jointes texte du message.
3. Ignorance des messages du bot lui‑même et des messages dont le contenu ressemble à du JSON brut (commençant par `{`).
4. Vérification que le bot est mentionné dans le message.
5. Nettoyage du message (suppression de la mention du bot).
6. Appel à `ollama.generate(...)`, échappement, découpe, puis réponse dans le même salon.

---

## 4. Communication avec le service backend

### Type de communication réellement implémentée

- Communication HTTP de type REST, avec des requêtes `POST` et `GET` sur des URLs construites à partir d’`ApiConfig`.
- Corps JSON pour certains appels (`/refresh`, service LLM).

### Client HTTP utilisé

- `reactor.netty.http.client.HttpClient` est utilisé pour :
    - Les appels au backend pour `/role create` et `/refresh`.
    - Le téléchargement de pièces jointes depuis les URLs fournies par Discord.
- `OllamaClient` encapsule également un `HttpClient` configuré spécifiquement pour le service de génération de texte.

### Gestion actuelle des erreurs et du temps de réponse

- Dans `OllamaClient` :
    - Timeout de connexion paramétré à 5 secondes.
    - Timeout de réponse paramétré à 120 secondes.
    - Si le code HTTP n’est pas 2xx, une erreur est propagée avec un message `HTTP <code>` et le corps tronqué dans les logs.
    - Si la réponse JSON ne contient pas les champs attendus, le corps brut est renvoyé si non vide, sinon une erreur `Réponse vide` est produite.

- Dans `MyBot` :
    - De nombreux appels HTTP effectuent un `onErrorResume` qui logue l’erreur et renvoie un message générique vers Discord (par exemple : *"Erreur interne lors de l'appel au modèle."*, *"Erreur lors de l'explication du concept."*, etc.).
    - Pour `/role` et `/refresh`, si le code HTTP n’est pas 2xx, une exception est créée avec le code et un extrait du corps (`shortBody`), puis remontée jusqu’au handler, qui renvoie un message d’erreur textuel au client Discord.
    - Les erreurs dans les handlers d’événements sont loguées et généralement absorbées, pour éviter l’arrêt du flux.

---

## 5. Stack technique effective

### Version de Java

- La version exacte de Java est déterminée dans le `pom.xml` (non affiché ici), mais le code utilise des fonctionnalités compatibles avec Java 11+ (API HTTP Netty, `var` absent, `record` absent, `switch` classique).
- Le code montré est écrit de manière compatible avec une version Java standard moderne, sans utilisation d’APIs spécifiques à une version très récente.

### Dépendances Maven réellement présentes (à partir du code observé)

Les dépendances utilisées dans le code (présumées présentes dans le `pom.xml`) sont :

- `discord4j-core` (Discord4J)
- `discord4j-discordjson` (types `ApplicationCommandRequest`, `ApplicationCommandOptionData`)
- `reactor-core` (types `Mono`, `Flux`)
- `reactor-netty` (types `HttpClient`, `ByteBufFlux`, `ChannelOption`)
- `jackson-databind` (types `ObjectMapper`, `JsonNode`, `ObjectNode`, `ArrayNode`)
- `dotenv-java` (`io.github.cdimascio.dotenv.Dotenv`)
- `slf4j-api` et une implémentation de logging compatible (utilisation de `LoggerFactory`)

### Frameworks ou bibliothèques effectivement utilisés

- Discord4J pour l’intégration avec Discord.
- Jackson pour la sérialisation/désérialisation JSON.
- Reactor Netty pour le client HTTP.
- Dotenv pour la configuration par variables d’environnement.
- SLF4J pour les logs.

---

## 6. Fonctionnalités existantes

### Liste précise des commandes et fonctionnalités implémentées

- Commandes slash globales :

    - `/teach`
        - Option obligatoire : `concept` (string).
        - Envoie un prompt de type explication technique au service LLM.
        - Retourne la réponse du modèle, échappée et découpée.

    - `/translate`
        - Option obligatoire : `text` (string).
        - Envoie un prompt de traduction en français au service LLM.
        - Retourne la traduction, échappée et découpée.

    - `/summarize`
        - Option obligatoire : `text` (string).
        - Envoie un prompt de résumé en français au service LLM.
        - Retourne le résumé, échappé et découpé.

    - `/qa`
        - Option obligatoire : `question` (string).
        - Construit un contexte à partir des documents texte indexés dans le salon courant (pièces jointes texte précédemment envoyées).
        - Envoie un prompt de question‑réponse au service LLM.
        - Retourne la réponse, échappée et découpée.
        - Si aucun document n’est indexé, renvoie un message éphémère expliquant la situation.

    - `/role create`
        - Sous‑commande `create` avec options obligatoires :
            - `name` (string)
            - `permissions` (string, liste CSV de permissions, interprétée côté backend).
        - Récupère l’ID Discord de l’utilisateur appelant et l’ID de la guilde.
        - Appelle le backend via HTTP `POST` sur l’URL configurée, en passant les paramètres en query string.
        - Retourne la réponse brute de l’API, ou un message générique en cas d’erreur.

    - `/refresh`
        - Sans option.
        - Récupère la guilde depuis le contexte de l’interaction, puis construit un instantané JSON :
            - rôles (id, nom, couleur, position, permissions, mentionnable) ;
            - membres (id, username, discriminator, displayName, rôle IDs, date d’arrivée) ;
            - éventuellement `ownerId` si récupérable.
        - Envoie ce JSON au backend via HTTP `POST`.
        - Retourne la réponse textuelle de l’API.

- Gestion des messages :

    - Indexation des pièces jointes texte :
        - Pour chaque `MessageCreateEvent`, le bot télécharge les pièces jointes dont le `contentType` commence par `text/`.
        - Le contenu est stocké en mémoire dans une `Map<Long, List<String>>` indexée par ID de salon.
    - Réponse automatique aux mentions :
        - Si le message mentionne le bot, et n’est pas un JSON brut, le contenu nettoyé est transmis à `ollama.generate`.
        - La réponse du service est renvoyée dans le salon, après échappement Markdown et découpe.

### Ce qui n’est PAS encore implémenté (d’après le code visible)

- Pas de persistance des documents indexés au‑delà de la mémoire process : tout est stocké dans une `ConcurrentHashMap` en mémoire.
- Pas de gestion explicite des permissions Discord pour l’exécution des commandes (la logique repose sur l’API backend pour `/role`).
- Pas de mécanisme interne de rate limiting ou de file d’attente pour les appels au service LLM ou au backend.
- Pas de configuration avancée des modèles ou des prompts côté bot au‑delà des chaînes codées en dur dans `OllamaClient`.

---

## 7. Lancement du bot

### Prérequis nécessaires

- JDK installé (version compatible avec le `pom.xml` du projet).
- Maven installé.
- Un bot Discord configuré, avec un token valide.
- Un backend HTTP accessible si l’on veut utiliser :
    - les commandes `/role` et `/refresh` ;
    - le service LLM accessible via l’URL fournie.

### Configuration requise

Variables d’environnement gérées par `Dotenv` :

- `DISCORD_TOKEN`  
  Token du bot Discord. Obligatoire pour démarrer le bot.
- `BACKEND_BASE_URL`  
  URL de base du backend.  
  Si non définie ou vide, la valeur par défaut utilisée est `http://localhost:8080`.

À partir de `BACKEND_BASE_URL`, les URLs suivantes sont dérivées :

- `BACKEND_BASE_URL` \+ `/ollama` pour le service LLM.
- `BACKEND_BASE_URL` \+ `/guilds/discord/{guildDiscordId}/roles` pour `/role`.
- `BACKEND_BASE_URL` \+ `/guilds/discord/{guildDiscordId}/refresh` pour `/refresh`.

### Commandes Maven réellement fonctionnelles pour démarrer le bot

Sous réserve d’un `pom.xml` Maven standard avec un plugin `exec` ou `spring-boot-maven-plugin` (non affiché ici), les commandes typiques sont :

- Compilation :

```bash
mvn compile
```

- Exécution via la classe `main` (avec `exec-maven-plugin` correctement configuré) :

```bash
mvn exec:java -Dexec.mainClass=fr.univtln.pegliasco.tp.MyBot
```

Si un autre mécanisme d’exécution est défini dans le `pom.xml`, il doit être utilisé tel qu’il est configuré.

---

## 8. Tests existants

D’après les fichiers fournis :

- Aucun test (unitaire ou d’intégration) n’est visible.
- Aucun framework de test (par exemple JUnit) n’est observé dans les extraits de code.
- Aucun code de test n’est présent dans les packages montrés.

---

## 9. Limitations actuelles

### Contraintes techniques visibles dans le code

- Stockage en mémoire des documents indexés (`CHANNEL_DOCUMENTS`) sans mécanisme de nettoyage ni persistance.
- Les prompts de génération (`teach`, `translate`, `summarize`, `qa`) sont codés en dur dans `OllamaClient`.
- Dépendance forte à la disponibilité du backend et du service LLM : en cas d’indisponibilité prolongée, les commandes associées ne peuvent pas répondre autrement que par un message d’erreur générique.
- Le bot repose sur les limites de Discord (notamment 2000 caractères) gérées par une découpe manuelle via `MessageUtils`.

### Manques actuels

- Absence de tests dans le code observé.
- Absence de configuration avancée ou externe des prompts et des modèles.
- Gestion des erreurs principalement basée sur des logs et des messages génériques retournés à l’utilisateur.