package fr.univtln.pegliasco.tp;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.channel.ChannelOption;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufFlux;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

public class OllamaClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(120);

    private final ObjectMapper mapper;
    private final HttpClient client;
    private final String apiUrl;

    public OllamaClient(String apiUrl) {
        this.mapper = new ObjectMapper()
                .configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true)
                .configure(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true);

        this.client = HttpClient.create()
                .compress(true)
                .responseTimeout(RESPONSE_TIMEOUT)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) CONNECT_TIMEOUT.toMillis())
                .headers(h -> {
                    h.set("Content-Type", "application/json; charset=utf-8");
                    h.set("Accept", "application/json");
                    h.set("User-Agent", "MyBot/1.0 (+reactor-netty)");
                });

        this.apiUrl = apiUrl;
    }

    public Mono<String> generate(String userMessage) {
        String payload = buildPayload(userMessage, null);
        return send(payload).flatMap(this::extractText).timeout(RESPONSE_TIMEOUT);
    }

    // Prompt dédié pour /teach
    public Mono<String> generateTeaching(String concept) {
        String instruction = "Explique clairement et de façon pédagogique le concept suivant pour un public technique, " +
                "avec une structure courte: définition, pourquoi c'est utile, exemple simple, pièges courants.\nConcept: " + concept;
        String payload = buildPayload(instruction, "teach");
        return send(payload).flatMap(this::extractText).timeout(RESPONSE_TIMEOUT);
    }
    // Prompt dédié pour /translate
    public Mono<String> generateTranslation(String text) {
        String instruction = "Traduis le texte suivant en français de manière fluide et naturelle:\n\"" + text + "\"";
        String payload = buildPayload(instruction, "translate");
        return send(payload).flatMap(this::extractText).timeout(RESPONSE_TIMEOUT);
    }
    // Prompt dédié pour /summarize
    public Mono<String> generateSummary(String text) {
        String instruction = "Fais un résumé concis et clair du texte suivant en français:\n\"" + text + "\"";
        String payload = buildPayload(instruction, "summarize");
        return send(payload).flatMap(this::extractText).timeout(RESPONSE_TIMEOUT);
    }

    private Mono<String> send(String payload) {
        return client
                .post()
                .uri(apiUrl)
                .send(ByteBufFlux.fromString(Mono.just(payload)))
                .responseSingle((res, content) ->
                        content.asString().flatMap(body -> {
                            int code = res.status().code();
                            if (code < 200 || code >= 300) {
                                System.err.println("HTTP " + code + " - Corps: " + truncate(body, 512));
                                return Mono.error(new IllegalStateException("HTTP " + code));
                            }
                            return Mono.just(body);
                        })
                );
    }

    private Mono<String> extractText(String body) {
        try {
            JsonNode json = mapper.readTree(body);
            JsonNode node = json.path("response");
            if (node.isMissingNode() || node.asText().isBlank()) node = json.path("message");
            if (node.isMissingNode() || node.asText().isBlank()) node = json.path("content");

            String value = node.isMissingNode() ? null : node.asText();
            if (value != null && !value.isBlank()) return Mono.just(value);
            if (body != null && !body.isBlank()) return Mono.just(body);
            return Mono.error(new IllegalStateException("Réponse vide"));
        } catch (Exception e) {
            if (body != null && !body.isBlank()) return Mono.just(body);
            return Mono.error(e);
        }
    }

    private String buildPayload(String userMessage, String mode) {
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("message", userMessage);
            if (mode != null) {
                root.put("mode", mode);
            }
            return mapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Impossible de sérialiser la requête", e);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
