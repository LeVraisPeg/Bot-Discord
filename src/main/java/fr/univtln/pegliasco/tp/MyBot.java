package fr.univtln.pegliasco.tp;

import discord4j.core.DiscordClient;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.common.util.Snowflake;
import io.github.cdimascio.dotenv.Dotenv;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MyBot {

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.load();
        String token = dotenv.get("DISCORD_TOKEN");
        DiscordClient client = DiscordClient.create(token);

        OllamaClient ollama = new OllamaClient("http://localhost:8080/ollama");

        client.withGateway(gateway -> {
            Snowflake selfId = gateway.getSelfId();

            Mono<Void> printOnLogin = gateway.on(ReadyEvent.class, evt ->
                    Mono.fromRunnable(() ->
                            System.out.printf("Connecté en tant que %s#%s%n",
                                    evt.getSelf().getUsername(),
                                    evt.getSelf().getDiscriminator()))
            ).then();

            Mono<Void> messages = gateway.on(MessageCreateEvent.class, evt -> {
                        Message message = evt.getMessage();
                        String content = message.getContent();
                        System.out.println("Message reçu : " + content);

                        // Ignore le bot lui-même
                        if (message.getAuthor().map(User::getId).filter(id -> id.equals(selfId)).isPresent()) {
                            return Mono.empty();
                        }
                        // Ignore payloads JSON bruts
                        if (content.trim().startsWith("{")) {
                            System.out.println("Message ignoré (JSON détecté)");
                            return Mono.empty();
                        }

                        // Répond uniquement si le bot est mentionné
                        boolean isMentioned = message.getUserMentions().stream()
                                .anyMatch(u -> u.getId().equals(selfId));
                        if (!isMentioned) {
                            System.out.println("Message ignoré (pas de mention du bot)");
                            return Mono.empty();
                        }

                        // Nettoie la mention du bot dans le contenu
                        String sanitized = content
                                .replace("<@" + selfId.asString() + ">", "")
                                .replace("<@!" + selfId.asString() + ">", "")
                                .trim();

                        if (sanitized.isBlank()) {
                            System.out.println("Message ignoré (contenu vide après suppression de la mention)");
                            return Mono.empty();
                        }

                        return ollama.generate(sanitized)
                                .map(MessageUtils::escapeDiscordMarkdown)
                                .onErrorResume(e -> {
                                    System.err.println("Erreur appel API: " + e.getMessage());
                                    return Mono.just("Erreur interne lors de l'appel au modèle.");
                                })
                                .flatMapMany(resp -> Flux.fromIterable(MessageUtils.splitForDiscord(resp)))
                                .concatMap(part -> message.getChannel()
                                        .flatMap(ch -> ch.createMessage(part))
                                        .onErrorResume(e -> {
                                            System.err.println("Erreur envoi message: " + e.getMessage());
                                            return Mono.empty();
                                        }))
                                .then();
                    })
                    .onErrorContinue((t, o) -> {
                        System.err.println("[Handler] Exception: " + t.getClass().getName() + " - " + t.getMessage());
                        t.printStackTrace();
                    })
                    .then();

            return printOnLogin.and(messages);
        }).block();
    }
}
