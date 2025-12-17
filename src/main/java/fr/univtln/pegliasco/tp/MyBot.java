package fr.univtln.pegliasco.tp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import discord4j.core.DiscordClient;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.common.util.Snowflake;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;
import io.github.cdimascio.dotenv.Dotenv;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyBot {

    private static final Logger log = LoggerFactory.getLogger(MyBot.class);
    private static final Map<Long, List<String>> CHANNEL_DOCUMENTS = new ConcurrentHashMap<>();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.load();
        ApiConfig api = new ApiConfig(dotenv);

        String token = dotenv.get("DISCORD_TOKEN");
        DiscordClient client = DiscordClient.create(token);

        OllamaClient ollama = new OllamaClient(api.ollamaUrl());



        client.gateway()
                .setEnabledIntents(IntentSet.of(
                        Intent.GUILDS,
                        Intent.GUILD_MEMBERS,
                        Intent.GUILD_MESSAGES,
                        Intent.MESSAGE_CONTENT
                ))
                .withGateway(gateway -> {
            Snowflake selfId = gateway.getSelfId();

            HttpClient httpClient = HttpClient.create();

            Mono<Long> applicationIdMono = gateway.getRestClient().getApplicationId();

            Mono<Void> printOnLogin = gateway.on(ReadyEvent.class, evt ->
                    Mono.fromRunnable(() ->
                            log.info("Connecté en tant que {}#{}",
                                    evt.getSelf().getUsername(),
                                    evt.getSelf().getDiscriminator()))
            ).then();

            /*----------  /refresh ---------- */
            ApplicationCommandRequest refreshCmd = ApplicationCommandRequest.builder()
                    .name("refresh")
                    .description("Envoie au backend toutes les infos de la guilde (rôles, membres...).")
                    .build();

            Mono<Void> registerRefresh = applicationIdMono
                    .flatMap(appId ->
                            gateway.getRestClient().getApplicationService()
                                    .getGlobalApplicationCommands(appId)
                                    .collectList()
                                    .flatMap(existing -> {
                                        boolean hasRefresh = existing.stream()
                                                .anyMatch(cmd -> "refresh".equalsIgnoreCase(cmd.name()));
                                        if (hasRefresh) {
                                            log.info("Commande globale /refresh déjà enregistrée.");
                                            return Mono.empty();
                                        }
                                        log.info("Enregistrement de la commande globale /refresh...");
                                        return gateway.getRestClient().getApplicationService()
                                                .createGlobalApplicationCommand(appId, refreshCmd)
                                                .doOnSuccess(cmd ->
                                                        log.info("Commande globale /refresh enregistrée (id={})", cmd.id()))
                                                .then();
                                    })
                    )
                    .onErrorResume(e -> {
                        log.error("Erreur lors de l'enregistrement de /refresh : {}", e.getMessage(), e);
                        return Mono.empty();
                    });


            /* ---------- /teach ---------- */

            ApplicationCommandRequest teachCmd = ApplicationCommandRequest.builder()
                    .name("teach")
                    .description("Expliquer un concept technique.")
                    .addOption(ApplicationCommandOptionData.builder()
                            .name("concept")
                            .description("Le concept technique à expliquer.")
                            .type(3)
                            .required(true)
                            .build())
                    .build();

            Mono<Void> registerTeach = applicationIdMono
                    .flatMap(appId ->
                            gateway.getRestClient().getApplicationService()
                                    .getGlobalApplicationCommands(appId)
                                    .collectList()
                                    .flatMap(existing -> {
                                        boolean hasTeach = existing.stream()
                                                .anyMatch(cmd -> "teach".equalsIgnoreCase(cmd.name()));
                                        if (hasTeach) {
                                            log.info("Commande globale /teach déjà enregistrée.");
                                            return Mono.empty();
                                        }
                                        log.info("Enregistrement de la commande globale /teach...");
                                        return gateway.getRestClient().getApplicationService()
                                                .createGlobalApplicationCommand(appId, teachCmd)
                                                .doOnSuccess(cmd ->
                                                        log.info("Commande globale /teach enregistrée (id={})", cmd.id()))
                                                .then();
                                    })
                    )
                    .onErrorResume(e -> {
                        log.error("Erreur lors de l'enregistrement de /teach : {}", e.getMessage(), e);
                        return Mono.empty();
                    });

            /* ---------- /translate ---------- */

            ApplicationCommandRequest translateCmd = ApplicationCommandRequest.builder()
                    .name("translate")
                    .description("Traduire un texte en français.")
                    .addOption(ApplicationCommandOptionData.builder()
                            .name("text")
                            .description("Le texte à traduire.")
                            .type(3)
                            .required(true)
                            .build())
                    .build();

            Mono<Void> registerTranslate = applicationIdMono
                    .flatMap(appId ->
                            gateway.getRestClient().getApplicationService()
                                    .getGlobalApplicationCommands(appId)
                                    .collectList()
                                    .flatMap(existing -> {
                                        boolean hasTranslate = existing.stream()
                                                .anyMatch(cmd -> "translate".equalsIgnoreCase(cmd.name()));
                                        if (hasTranslate) {
                                            log.info("Commande globale /translate déjà enregistrée.");
                                            return Mono.empty();
                                        }
                                        log.info("Enregistrement de la commande globale /translate...");
                                        return gateway.getRestClient().getApplicationService()
                                                .createGlobalApplicationCommand(appId, translateCmd)
                                                .doOnSuccess(cmd ->
                                                        log.info("Commande globale /translate enregistrée (id={})", cmd.id()))
                                                .then();
                                    })
                    )
                    .onErrorResume(e -> {
                        log.error("Erreur lors de l'enregistrement de /translate : {}", e.getMessage(), e);
                        return Mono.empty();
                    });

            /* ---------- /summarize ---------- */

            ApplicationCommandRequest summarizeCmd = ApplicationCommandRequest.builder()
                    .name("summarize")
                    .description("Résumer un texte en français.")
                    .addOption(ApplicationCommandOptionData.builder()
                            .name("text")
                            .description("Le texte à résumer.")
                            .type(3)
                            .required(true)
                            .build())
                    .build();

            Mono<Void> registerSummarize = applicationIdMono
                    .flatMap(appId ->
                            gateway.getRestClient().getApplicationService()
                                    .getGlobalApplicationCommands(appId)
                                    .collectList()
                                    .flatMap(existing -> {
                                        boolean hasSummarize = existing.stream()
                                                .anyMatch(cmd -> "summarize".equalsIgnoreCase(cmd.name()));
                                        if (hasSummarize) {
                                            log.info("Commande globale /summarize déjà enregistrée.");
                                            return Mono.empty();
                                        }
                                        log.info("Enregistrement de la commande globale /summarize...");
                                        return gateway.getRestClient().getApplicationService()
                                                .createGlobalApplicationCommand(appId, summarizeCmd)
                                                .doOnSuccess(cmd ->
                                                        log.info("Commande globale /summarize enregistrée (id={})", cmd.id()))
                                                .then();
                                    })
                    )
                    .onErrorResume(e -> {
                        log.error("Erreur lors de l'enregistrement de /summarize : {}", e.getMessage(), e);
                        return Mono.empty();
                    });

            /* ---------- /qa ---------- */

            ApplicationCommandRequest qaCmd = ApplicationCommandRequest.builder()
                    .name("qa")
                    .description("Question-réponse basée sur les documents envoyés dans ce salon.")
                    .addOption(ApplicationCommandOptionData.builder()
                            .name("question")
                            .description("La question à poser sur les documents du salon.")
                            .type(3)
                            .required(true)
                            .build())
                    .build();

            Mono<Void> registerQa = applicationIdMono
                    .flatMap(appId ->
                            gateway.getRestClient().getApplicationService()
                                    .getGlobalApplicationCommands(appId)
                                    .collectList()
                                    .flatMap(existing -> {
                                        boolean hasQa = existing.stream()
                                                .anyMatch(cmd -> "qa".equalsIgnoreCase(cmd.name()));
                                        if (hasQa) {
                                            log.info("Commande globale /qa déjà enregistrée.");
                                            return Mono.empty();
                                        }
                                        log.info("Enregistrement de la commande globale /qa...");
                                        return gateway.getRestClient().getApplicationService()
                                                .createGlobalApplicationCommand(appId, qaCmd)
                                                .doOnSuccess(cmd ->
                                                        log.info("Commande globale /qa enregistrée (id={})", cmd.id()))
                                                .then();
                                    })
                    )
                    .onErrorResume(e -> {
                        log.error("Erreur lors de l'enregistrement de /qa : {}", e.getMessage(), e);
                        return Mono.empty();
                    });

            /*---------- role ---------- */
            ApplicationCommandRequest roleCmd = ApplicationCommandRequest.builder()
                    .name("role")
                    .description("Gestion des rôles via API")
                    .addOption(ApplicationCommandOptionData.builder()
                            .name("create")
                            .description("Créer un rôle")
                            .type(1) // SUB_COMMAND
                            .addOption(ApplicationCommandOptionData.builder()
                                    .name("name")
                                    .description("Nom du rôle")
                                    .type(3) // STRING
                                    .required(true)
                                    .build())
                            .addOption(ApplicationCommandOptionData.builder()
                                    .name("permissions")
                                    .description("Permissions (séparées par des virgules)")
                                    .type(3) // STRING
                                    .required(true)
                                    .build())
                            .build())
                    .build();

            Mono<Void> registerRole = applicationIdMono
                    .flatMap(appId ->
                            gateway.getRestClient().getApplicationService()
                                    .getGlobalApplicationCommands(appId)
                                    .collectList()
                                    .flatMap(existing -> {
                                        boolean hasRole = existing.stream()
                                                .anyMatch(cmd -> "role".equalsIgnoreCase(cmd.name()));
                                        if (hasRole) {
                                            log.info("Commande globale /role déjà enregistrée.");
                                            return Mono.empty();
                                        }
                                        log.info("Enregistrement de la commande globale /role...");
                                        return gateway.getRestClient().getApplicationService()
                                                .createGlobalApplicationCommand(appId, roleCmd)
                                                .doOnSuccess(cmd ->
                                                        log.info("Commande globale /role enregistrée (id={})", cmd.id()))
                                                .then();
                                    })
                    )
                    .onErrorResume(e -> {
                        log.error("Erreur lors de l'enregistrement de /role : {}", e.getMessage(), e);
                        return Mono.empty();
                    });

            /* ---------- Messages + index des fichiers ---------- */

            Mono<Void> messages = gateway.on(MessageCreateEvent.class, evt -> {
                        Message message = evt.getMessage();
                        String content = message.getContent();

                        log.debug("Message reçu : {}", content);

                        Mono<Void> attachmentIndexing = Flux.fromIterable(message.getAttachments())
                                .flatMap(att -> {
                                    String url = att.getUrl();
                                    String filename = att.getFilename();
                                    String contentType = att.getContentType().orElse("");

                                    long channelId = message.getChannelId().asLong();

                                    if (!contentType.isEmpty() && !contentType.startsWith("text/")) {
                                        log.info("Pièce jointe ignorée (type non texte) : {} ({})",
                                                filename, contentType);
                                        return Mono.empty();
                                    }

                                    log.info("Pièce jointe texte reçue dans le salon {} : {} ({})",
                                            channelId, filename, contentType);

                                    return httpClient
                                            .get()
                                            .uri(url)
                                            .responseSingle((res, buf) -> {
                                                int code = res.status().code();
                                                if (code < 200 || code >= 300) {
                                                    log.warn("Échec téléchargement fichier {} : HTTP {}",
                                                            filename, code);
                                                    return Mono.empty();
                                                }
                                                return buf.asString();
                                            })
                                            .doOnNext(text -> {
                                                CHANNEL_DOCUMENTS
                                                        .computeIfAbsent(channelId, k -> new ArrayList<>())
                                                        .add(text);
                                                log.info("Texte indexé depuis {} pour le salon {} ({} caractères)",
                                                        filename, channelId, text.length());
                                            })
                                            .onErrorResume(e -> {
                                                log.error("Erreur téléchargement pièce jointe {} : {}",
                                                        filename, e.getMessage(), e);
                                                return Mono.empty();
                                            })
                                            .then();
                                })
                                .then();

                        if (message.getAuthor().map(User::getId).filter(id -> id.equals(selfId)).isPresent()) {
                            log.debug("Message ignoré : vient du bot lui-même.");
                            return attachmentIndexing;
                        }

                        if (content.trim().startsWith("{")) {
                            log.debug("Message ignoré : JSON détecté.");
                            return attachmentIndexing;
                        }

                        boolean isMentioned = message.getUserMentions().stream()
                                .anyMatch(u -> u.getId().equals(selfId));
                        if (!isMentioned) {
                            log.debug("Message ignoré : pas de mention du bot.");
                            return attachmentIndexing;
                        }

                        String sanitized = content
                                .replace("<@" + selfId.asString() + ">", "")
                                .replace("<@!" + selfId.asString() + ">", "")
                                .trim();

                        if (sanitized.isBlank()) {
                            log.debug("Message ignoré : contenu vide après suppression de la mention.");
                            return attachmentIndexing;
                        }

                        log.info("Message mentionné nettoyé : {}", sanitized);

                        Mono<Void> reply = ollama.generate(sanitized)
                                .map(MessageUtils::escapeDiscordMarkdown)
                                .onErrorResume(e -> {
                                    log.error("Erreur appel modèle : {}", e.getMessage(), e);
                                    return Mono.just("Erreur interne lors de l'appel au modèle.");
                                })
                                .flatMapMany(resp -> Flux.fromIterable(MessageUtils.splitForDiscord(resp)))
                                .concatMap(part -> message.getChannel()
                                        .flatMap(ch -> ch.createMessage(part))
                                        .onErrorResume(e -> {
                                            log.error("Erreur envoi Discord : {}", e.getMessage(), e);
                                            return Mono.empty();
                                        }))
                                .then();

                        return attachmentIndexing.then(reply);
                    })
                    .onErrorResume(e -> {
                        log.error("Erreur dans handler MessageCreateEvent : {}", e.getMessage(), e);
                        return Mono.empty();
                    })
                    .then();


            /* ---------- /refresh handler ---------- */

            Mono<Void> refreshCommand = gateway.on(ChatInputInteractionEvent.class, evt -> {
                if (!"refresh".equalsIgnoreCase(evt.getCommandName())) {
                    return Mono.empty();
                }

                log.info("Interaction /refresh reçue");

                var optMember = evt.getInteraction().getMember();
                if (optMember.isEmpty()) {
                    return evt.reply()
                            .withEphemeral(true)
                            .withContent("Impossible de déterminer la guilde.");
                }

                Member member = optMember.get();
                Snowflake guildIdSnowflake = member.getGuildId();
                long guildId = guildIdSnowflake.asLong();

                log.info("Refresh demandé pour la guilde {}", guildId);

                return evt.deferReply()
                        .then(
                                gateway.getGuildById(guildIdSnowflake)
                                        .flatMap(guild -> buildGuildSnapshot(gateway, guild))
                                        .flatMap(json -> {
                                            String uri = api.refreshGuildUrl(guildId);
                                            log.info("POST refresh vers {}", uri);
                                            byte[] bytes;
                                            try {
                                                bytes = MAPPER.writeValueAsBytes(json);
                                            } catch (Exception e) {
                                                return Mono.error(e);
                                            }
                                            return HttpClient.create()
                                                    .post()
                                                    .uri(uri)
                                                    .send(Mono.just(
                                                            Unpooled.wrappedBuffer(bytes)
                                                    ))
                                                    .responseSingle((res, buf) -> {
                                                        int code = res.status().code();
                                                        return buf.asString().defaultIfEmpty("")
                                                                .flatMap(body -> {
                                                                    if (code >= 200 && code < 300) {
                                                                        return Mono.just(body.isBlank()
                                                                                ? "Refresh effectué."
                                                                                : body);
                                                                    } else {
                                                                        return Mono.error(new RuntimeException(
                                                                                "HTTP " + code + " — " + shortBody(res, body)
                                                                        ));
                                                                    }
                                                                });
                                                    });
                                        })
                                        .flatMapMany(apiResp ->
                                                Flux.fromIterable(
                                                        MessageUtils.splitForDiscord("Réponse API : " + apiResp)
                                                )
                                        )
                                        .concatMap(part -> evt.createFollowup().withContent(part))
                                        .onErrorResume(e -> {
                                            log.error("Erreur /refresh : {}", e.getMessage(), e);
                                            return Flux.empty();
                                        })

                                        .then()
                        );
                    })
                    .onErrorResume(e -> {
                        log.error("Erreur handler refresh : {}", e.getMessage(), e);
                        return Mono.empty();
                    })
                    .then();



                /* ---------- /teach handler ---------- */

            Mono<Void> teachCommand = gateway.on(ChatInputInteractionEvent.class, evt -> {
                        if (!"teach".equalsIgnoreCase(evt.getCommandName())) {
                            return Mono.empty();
                        }

                        log.info("Interaction /teach reçue");

                        String concept = evt.getOption("concept")
                                .flatMap(opt -> opt.getValue())
                                .map(v -> v.asString().trim())
                                .orElse("");

                        log.info("Concept reçu : {}", concept);

                        if (concept.isBlank()) {
                            return evt.reply()
                                    .withEphemeral(true)
                                    .withContent("Veuillez fournir un concept à expliquer.");
                        }

                        return evt.deferReply()
                                .then(
                                        ollama.generateTeaching(concept)
                                                .map(MessageUtils::escapeDiscordMarkdown)
                                                .onErrorResume(e -> {
                                                    log.error("Erreur traitement teach : {}", e.getMessage(), e);
                                                    return Mono.just("Erreur lors de l'explication du concept.");
                                                })
                                                .flatMapMany(resp -> Flux.fromIterable(MessageUtils.splitForDiscord(resp)))
                                                .concatMap(part -> evt.createFollowup().withContent(part))
                                                .then()
                                );
                    })
                    .onErrorResume(e -> {
                        log.error("Erreur handler teach : {}", e.getMessage(), e);
                        return Mono.empty();
                    })
                    .then();

            /* ---------- /translate handler ---------- */

            Mono<Void> translateCommand = gateway.on(ChatInputInteractionEvent.class, evt -> {
                        if (!"translate".equalsIgnoreCase(evt.getCommandName())) {
                            return Mono.empty();
                        }

                        log.info("Interaction /translate reçue");

                        String textToTranslate = evt.getOption("text")
                                .flatMap(opt -> opt.getValue())
                                .map(v -> v.asString().trim())
                                .orElse("");

                        log.info("Texte reçu pour traduction : {}", textToTranslate);

                        if (textToTranslate.isBlank()) {
                            return evt.reply()
                                    .withEphemeral(true)
                                    .withContent("Veuillez fournir un texte à traduire.");
                        }

                        return evt.deferReply()
                                .then(
                                        ollama.generateTranslation(textToTranslate)
                                                .map(MessageUtils::escapeDiscordMarkdown)
                                                .onErrorResume(e -> {
                                                    log.error("Erreur traduction : {}", e.getMessage(), e);
                                                    return Mono.just("Erreur lors de la traduction du texte.");
                                                })
                                                .flatMapMany(resp -> Flux.fromIterable(MessageUtils.splitForDiscord(resp)))
                                                .concatMap(part -> evt.createFollowup().withContent(part))
                                                .then()
                                );
                    })
                    .onErrorResume(e -> {
                        log.error("Erreur handler translate : {}", e.getMessage(), e);
                        return Mono.empty();
                    })
                    .then();

            /* ---------- /summarize handler ---------- */

            Mono<Void> summarizeCommand = gateway.on(ChatInputInteractionEvent.class, evt -> {
                        if (!"summarize".equalsIgnoreCase(evt.getCommandName())) {
                            return Mono.empty();
                        }

                        log.info("Interaction /summarize reçue");

                        String textToSummarize = evt.getOption("text")
                                .flatMap(opt -> opt.getValue())
                                .map(v -> v.asString().trim())
                                .orElse("");

                        log.info("Texte reçu pour résumé : {}", textToSummarize);

                        if (textToSummarize.isBlank()) {
                            return evt.reply()
                                    .withEphemeral(true)
                                    .withContent("Veuillez fournir un texte à résumer.");
                        }

                        return evt.deferReply()
                                .then(
                                        ollama.generateSummary(textToSummarize)
                                                .map(MessageUtils::escapeDiscordMarkdown)
                                                .onErrorResume(e -> {
                                                    log.error("Erreur résumé : {}", e.getMessage(), e);
                                                    return Mono.just("Erreur lors du résumé du texte.");
                                                })
                                                .flatMapMany(resp -> Flux.fromIterable(MessageUtils.splitForDiscord(resp)))
                                                .concatMap(part -> evt.createFollowup().withContent(part))
                                                .then()
                                );
                    })
                    .onErrorResume(e -> {
                        log.error("Erreur handler summarize : {}", e.getMessage(), e);
                        return Mono.empty();
                    })
                    .then();

            /* ---------- /qa handler ---------- */

            Mono<Void> qaCommand = gateway.on(ChatInputInteractionEvent.class, evt -> {
                        if (!"qa".equalsIgnoreCase(evt.getCommandName())) {
                            return Mono.empty();
                        }

                        log.info("Interaction /qa reçue");

                        String question = evt.getOption("question")
                                .flatMap(opt -> opt.getValue())
                                .map(v -> v.asString().trim())
                                .orElse("");

                        log.info("Question reçue pour /qa : {}", question);

                        if (question.isBlank()) {
                            return evt.reply()
                                    .withEphemeral(true)
                                    .withContent("Veuillez fournir une question.");
                        }

                        long channelId = evt.getInteraction().getChannelId().asLong();
                        List<String> docs = CHANNEL_DOCUMENTS.get(channelId);

                        if (docs == null || docs.isEmpty()) {
                            log.info("Aucun document indexé pour le salon {}", channelId);
                            return evt.reply()
                                    .withEphemeral(true)
                                    .withContent(
                                            "Aucun document n'a encore été indexé sur ce salon.\n" +
                                                    "Envoyez d'abord un ou plusieurs fichiers texte, puis réessayez /qa."
                                    );
                        }

                        StringBuilder contextBuilder = new StringBuilder();
                        for (String d : docs) {
                            String trimmed = d;
                            if (trimmed.length() > 2000) {
                                trimmed = trimmed.substring(0, 2000) + "\n...[tronqué]...";
                            }
                            contextBuilder.append(trimmed).append("\n\n---\n\n");
                        }
                        String context = contextBuilder.toString();

                        return evt.deferReply()
                                .then(
                                        ollama.generateQA(context, question)
                                                .map(MessageUtils::escapeDiscordMarkdown)
                                                .onErrorResume(e -> {
                                                    log.error("Erreur /qa : {}", e.getMessage(), e);
                                                    return Mono.just("Erreur lors du traitement de la question.");
                                                })
                                                .flatMapMany(resp -> Flux.fromIterable(MessageUtils.splitForDiscord(resp)))
                                                .concatMap(part -> evt.createFollowup().withContent(part))
                                                .then()
                                );
                    })
                    .onErrorResume(e -> {
                        log.error("Erreur handler qa : {}", e.getMessage(), e);
                        return Mono.empty();
                    })
                    .then();

            /* ---------- /role handler ---------- */
            Mono<Void> roleCommand = gateway.on(ChatInputInteractionEvent.class, evt -> {
                if (!"role".equalsIgnoreCase(evt.getCommandName())) {
                    return Mono.empty();
                }

                log.info("Interaction /role reçue");

                var createOpt = evt.getOption("create");
                if (createOpt.isEmpty()) {
                    log.info("Sous-commande /role create absente");
                    return Mono.empty();
                }

                String roleName = createOpt
                        .flatMap(o -> o.getOption("name"))
                        .flatMap(opt -> opt.getValue())
                        .map(v -> v.asString().trim())
                        .orElse("");

                String permissionsCsv = createOpt
                        .flatMap(o -> o.getOption("permissions"))
                        .flatMap(opt -> opt.getValue())
                        .map(v -> v.asString().trim())
                        .orElse("");

                // TODO: récupérer ces deux valeurs depuis votre contexte si nécessaire
                long membershipId = evt.getInteraction().getMember()
                        .map(m -> m.getId().asLong()) // placeholder: à remplacer par votre vrai membershipId
                        .orElse(-1L);
                int position = 0; // placeholder: à remplacer selon votre logique

                log.info("Arguments /role create: name='{}', permissions='{}', membershipId={}, position={}",
                        roleName, permissionsCsv, membershipId, position);

                if (roleName.isBlank() || permissionsCsv.isBlank() || membershipId <= 0) {
                    return evt.reply()
                            .withEphemeral(true)
                            .withContent("Paramètres requis manquants: `name`, `permissions`, `membershipId`.");
                }

                long guildId = evt.getInteraction().getGuildId().map(Snowflake::asLong).orElse(-1L);
                if (guildId <= 0) {
                    return evt.reply().withEphemeral(true).withContent("GuildId invalide.");
                }

                String base = api.roleCreateUrl(guildId);
                // Construction de l’URI avec query params attendus par le backend
                String uri = base
                        + "?membershipId=" + membershipId
                        + "&roleName=" + encode(roleName)
                        + "&position=" + position
                        + "&permissions=" + encode(permissionsCsv);

                log.info("Appel API rôle: POST {}", uri);

                return evt.deferReply()
                        .then(
                                HttpClient.create()
                                        .post()
                                        .uri(uri)
                                        .responseSingle((res, buf) -> {
                                            int code = res.status().code();
                                            return buf.asString().defaultIfEmpty("")
                                                    .flatMap(body -> {
                                                        if (code >= 200 && code < 300) {
                                                            return Mono.just(body.isBlank() ? "Rôle créé." : body);
                                                        } else {
                                                            return Mono.error(new RuntimeException("HTTP " + code + " — " + shortBody(res, body)));
                                                        }
                                                    });
                                        })
                                        .flatMapMany(apiResp ->
                                                Flux.fromIterable(MessageUtils.splitForDiscord("Réponse API : " + apiResp))
                                        )
                                        .concatMap(part -> evt.createFollowup().withContent(part))
                                        .onErrorResume(e -> evt.createFollowup().withContent(
                                                MessageUtils.splitForDiscord("Erreur lors de la création du rôle : " + e.getMessage())
                                                        .getFirst()
                                        ))
                                        .then()
                        );
            }).onErrorResume(e -> {
                log.error("Erreur handler role : {}", e.getMessage(), e);
                return Mono.empty();
            }).then();




            /* ---------- Assemblage final ---------- */

            Mono<Void> registerAll = registerTeach
                    .then(registerTranslate)
                    .then(registerSummarize)
                    .then(registerQa)
                    .then(registerRole)
                    .then(registerRefresh);

            Mono<Void> handlers = printOnLogin
                    .and(messages)
                    .and(teachCommand)
                    .and(translateCommand)
                    .and(summarizeCommand)
                    .and(qaCommand)
                    .and(roleCommand)
                    .and(refreshCommand);

            return registerAll.then(handlers);

        }).block();
    }


    private static Mono<ObjectNode> buildGuildSnapshot(discord4j.core.GatewayDiscordClient gateway, Guild guild) {
        ObjectNode root = MAPPER.createObjectNode();

        root.put("id", guild.getId().asLong());
        root.put("name", guild.getName());

        ArrayNode rolesArr = MAPPER.createArrayNode();
        ArrayNode membersArr = MAPPER.createArrayNode();

        // Rôles
        Mono<Void> rolesMono = guild.getRoles()
                .sort((r1, r2) -> Integer.compare(r1.getRawPosition(), r2.getRawPosition()))
                .doOnNext(role -> {
                    ObjectNode r = rolesArr.addObject();
                    r.put("id", role.getId().asLong());
                    r.put("name", role.getName());
                    r.put("color", role.getColor().getRGB());
                    r.put("position", role.getRawPosition());
                    r.put("permissions", role.getPermissions().getRawValue());
                    r.put("mentionable", role.isMentionable());
                })
                .then();

        // Membres
        Mono<Void> membersMono = guild.getMembers()
                .doOnNext(m -> {
                    ObjectNode mNode = membersArr.addObject();
                    mNode.put("id", m.getId().asLong());
                    mNode.put("username", m.getUsername());
                    mNode.put("discriminator", m.getDiscriminator());
                    mNode.put("displayName", m.getDisplayName());
                    ArrayNode rolesIds = mNode.putArray("roleIds");
                    m.getRoleIds().forEach(r -> rolesIds.add(r.asLong()));
                    m.getJoinTime().ifPresent(jt -> mNode.put("joinedAt", jt.toString()));
                })
                .then();

        // OwnerId (peut être vide)
        Mono<Long> ownerMono = guild.getOwner()
                .map(discord4j.core.object.entity.Member::getId)
                .map(Snowflake::asLong)
                .onErrorResume(e -> {
                    log.warn("Impossible de récupérer l'owner de la guilde {} : {}",
                            guild.getId().asLong(), e.getMessage());
                    return Mono.empty();
                });

        // On attend les rôles + membres, puis on ajoute éventuellement ownerId
        return Mono.when(rolesMono, membersMono)
                .then(
                        ownerMono
                                .doOnNext(ownerId -> root.put("ownerId", ownerId))
                                .defaultIfEmpty(-1L) // juste pour déclencher la chaîne, valeur ignorée
                                .then(Mono.fromCallable(() -> {
                                    root.set("roles", rolesArr);
                                    root.set("members", membersArr);
                                    return root;
                                }))
                );
    }


    private static String encode(String v) {
        try {
            return java.net.URLEncoder.encode(v, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return v;
        }
    }
    private static String shortBody(reactor.netty.http.client.HttpClientResponse res, String body) {
        String ct = res.responseHeaders().get("Content-Type", "");
        if (ct != null && ct.contains("html")) return "réponse HTML (masquée)";
        return body.length() > 300 ? body.substring(0, 300) + "...(tronqué)" : body;
    }
}
