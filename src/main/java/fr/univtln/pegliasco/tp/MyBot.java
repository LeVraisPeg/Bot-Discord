package fr.univtln.pegliasco.tp;

import discord4j.core.DiscordClient;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.common.util.Snowflake;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import io.github.cdimascio.dotenv.Dotenv;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyBot {

    private static final Logger log = LoggerFactory.getLogger(MyBot.class);

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.load();
        String token = dotenv.get("DISCORD_TOKEN");
        DiscordClient client = DiscordClient.create(token);

        OllamaClient ollama = new OllamaClient("http://localhost:8080/ollama");

        client.withGateway(gateway -> {
            Snowflake selfId = gateway.getSelfId();

            Mono<Long> applicationIdMono = gateway.getRestClient().getApplicationId();

            Mono<Void> printOnLogin = gateway.on(ReadyEvent.class, evt ->
                    Mono.fromRunnable(() ->
                            log.info("Connecté en tant que {}#{}",
                                    evt.getSelf().getUsername(),
                                    evt.getSelf().getDiscriminator()))
            ).then();

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


            Mono<Void> messages = gateway.on(MessageCreateEvent.class, evt -> {
                        Message message = evt.getMessage();
                        String content = message.getContent();

                        log.debug("Message reçu : {}", content);

                        if (message.getAuthor().map(User::getId).filter(id -> id.equals(selfId)).isPresent()) {
                            log.debug("Message ignoré : vient du bot lui-même.");
                            return Mono.empty();
                        }

                        if (content.trim().startsWith("{")) {
                            log.debug("Message ignoré : JSON détecté.");
                            return Mono.empty();
                        }

                        boolean isMentioned = message.getUserMentions().stream()
                                .anyMatch(u -> u.getId().equals(selfId));
                        if (!isMentioned) {
                            log.debug("Message ignoré : pas de mention du bot.");
                            return Mono.empty();
                        }

                        String sanitized = content
                                .replace("<@" + selfId.asString() + ">", "")
                                .replace("<@!" + selfId.asString() + ">", "")
                                .trim();

                        if (sanitized.isBlank()) {
                            log.debug("Message ignoré : contenu vide après suppression de la mention.");
                            return Mono.empty();
                        }

                        log.info("Message mentionné nettoyé : {}", sanitized);

                        return ollama.generate(sanitized)
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
                    })
                    .onErrorResume(e -> {
                        log.error("Erreur dans handler MessageCreateEvent : {}", e.getMessage(), e);
                        return Mono.empty();
                    })
                    .then();

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
                                                .flatMap(resp -> evt.createFollowup().withContent(resp))
                                                .then()
                                );
                    })
                    .onErrorResume(e -> {
                        log.error("Erreur handler teach : {}", e.getMessage(), e);
                        return Mono.empty();
                    })
                    .then();

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
                                                .flatMap(resp -> evt.createFollowup().withContent(resp))
                                                .then()
                                );
                    })
                    .onErrorResume(e -> {
                        log.error("Erreur handler translate : {}", e.getMessage(), e);
                        return Mono.empty();
                    })
                    .then();

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
                                                .flatMap(resp -> evt.createFollowup().withContent(resp))
                                                .then()
                                );
                    })
                    .onErrorResume(e -> {
                        log.error("Erreur handler summarize : {}", e.getMessage(), e);
                        return Mono.empty();
                    })
                    .then();


            return registerTeach
                    .then(registerTranslate)
                    .then(registerSummarize)
                    .then(printOnLogin)
                    .then(messages)
                    .then(teachCommand)
                    .then(translateCommand)
                    .then(summarizeCommand);

        }).block();
    }
}
