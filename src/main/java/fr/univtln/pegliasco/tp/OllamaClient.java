// Java
package fr.univtln.pegliasco.tp;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelOption;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class OllamaClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(30);

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
        String payload = buildPayload(userMessage);
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        Mono<ByteBuf> body = Mono.just(Unpooled.wrappedBuffer(bytes));

        return client
                .post()
                .uri(apiUrl)
                .send(body)
                .responseSingle((res, buf) -> {
                    int code = res.status().code();
                    if (code < 200 || code >= 300) {
                        return Mono.error(new IllegalStateException("HTTP " + code));
                    }
                    return buf.asByteArray()
                            .map(arr -> new String(arr, StandardCharsets.UTF_8));
                })
                .flatMap(this::extractText)
                .timeout(RESPONSE_TIMEOUT);
    }

    private Mono<String> extractText(String body) {
        try {
            JsonNode json = mapper.readTree(body);
            String value = json.path("response").isMissingNode() ? null : json.path("response").asText();
            if (value != null && !value.isBlank()) return Mono.just(value);
            if (body != null && !body.isBlank()) return Mono.just(body);
            return Mono.error(new IllegalStateException("Réponse vide"));
        } catch (Exception e) {
            if (body != null && !body.isBlank()) return Mono.just(body);
            return Mono.error(e);
        }
    }

    private String buildPayload(String userMessage) {
        return "{\"message\":" + toJsonString(userMessage) + "}";
    }

    private static String toJsonString(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
