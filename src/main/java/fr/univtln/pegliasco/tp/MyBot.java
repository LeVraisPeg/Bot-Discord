package fr.univtln.pegliasco.tp;

import discord4j.core.DiscordClient;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import io.github.cdimascio.dotenv.Dotenv;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import discord4j.common.util.Snowflake;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class MyBot {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final int DISCORD_LIMIT = 2000;

    // Regex tolérante pour extraire response sur plusieurs lignes
    private static final Pattern RESPONSE_FIELD_PATTERN =
            Pattern.compile("\\\"response\\\"\\s*:\\s*\\\"(.*?)\\\"", Pattern.DOTALL);

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.load();
        String token = dotenv.get("DISCORD_TOKEN");
        DiscordClient client = DiscordClient.create(token);

        client.withGateway(gateway -> {
            Snowflake selfId = gateway.getSelfId();

            Mono<Void> printOnLogin = gateway.on(ReadyEvent.class, evt ->
                    Mono.fromRunnable(() -> System.out.printf("Connecté en tant que %s#%s%n",
                            evt.getSelf().getUsername(),
                            evt.getSelf().getDiscriminator()))
            ).then();

            Mono<Void> messages = gateway.on(MessageCreateEvent.class, evt -> {
                        Message message = evt.getMessage();
                        String content = message.getContent();
                        System.out.println("Message reçu : " + content);

                        // Ignore les messages du bot lui‑même
                        if (message.getAuthor().map(User::getId).filter(id -> id.equals(selfId)).isPresent()) {
                            return Mono.empty();
                        }

                        // Ignore les payloads JSON collés par l’utilisateur
                        if (content.trim().startsWith("{")) {
                            System.out.println("Message ignoré (JSON détecté)");
                            return Mono.empty();
                        }

                        return callOllama(content)
                                .map(MyBot::escapeDiscordMarkdown)
                                .onErrorResume(e -> {
                                    System.err.println("Erreur appel API: " + e.getMessage());
                                    return Mono.just("Erreur interne lors de l'appel au modèle.");
                                })
                                .flatMapMany(resp -> Flux.fromIterable(splitForDiscord(resp)))
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

    private static Mono<String> callOllama(String userMessage) {
        String apiUrl = "http://localhost:8080/ollama";
        String payload = buildPayload(userMessage);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        return Mono.fromCallable(() -> HTTP.send(request, HttpResponse.BodyHandlers.ofString()))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofSeconds(30))
                .flatMap(resp -> {
                    String body = resp.body();
                    System.out.println("Réponse API brute : " + body);
                    if (resp.statusCode() / 100 != 2) {
                        return Mono.error(new IllegalStateException("HTTP " + resp.statusCode()));
                    }
                    try {
                        String value = extractResponseSafely(body);
                        if (value == null || value.isBlank()) {
                            return Mono.error(new IllegalStateException("Champ `response` manquant ou vide"));
                        }
                        return Mono.just(value);
                    } catch (Exception e) {
                        return Mono.error(e);
                    }
                });
    }

    // Parsing tolérant: JSON -> regex -> corps brut
    private static String extractResponseSafely(String body) {
        // 1) JSON strict
        try {
            JsonNode json = MAPPER.readTree(body);
            JsonNode node = json.get("response");
            if (node != null && !node.isNull()) {
                return node.asText();
            }
        } catch (Exception ignore) {
            // continue
        }
        // 2) Regex multi‑lignes pour JSON "cassé" (sauts de ligne non échappés)
        Matcher m = RESPONSE_FIELD_PATTERN.matcher(body);
        if (m.find()) {
            String captured = m.group(1);
            // Unescape simple
            return captured
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t");
        }
        // 3) Fallback: corps tel quel
        return body;
    }

    private static String buildPayload(String userMessage) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("message", userMessage);
        return root.toString();
    }

    private static List<String> splitForDiscord(String text) {
        List<String> parts = new ArrayList<>();
        if (text == null) return parts;
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + DISCORD_LIMIT, text.length());
            if (end < text.length()) {
                int nl = text.lastIndexOf('\n', end - 1);
                if (nl > start + 200) {
                    end = nl + 1;
                }
            }
            parts.add(text.substring(start, end));
            start = end;
        }
        return parts;
    }

    // Échappe le Markdown Discord (basique)
    private static String escapeDiscordMarkdown(String text) {
        if (text == null) return "";
        return text
                .replace("\\", "\\\\")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("`", "\\`")
                .replace("~", "\\~")
                .replace("|", "\\|")
                .replace(">", "\\>")
                .replace("#", "\\#");
    }
}
