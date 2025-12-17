package fr.univtln.pegliasco.tp;

import io.github.cdimascio.dotenv.Dotenv;

public class ApiConfig {
    private final String baseUrl;

    public ApiConfig(Dotenv dotenv) {
        String fromEnv = dotenv.get("BACKEND_BASE_URL");
        this.baseUrl = normalizeBase(fromEnv != null && !fromEnv.isBlank()
                ? fromEnv
                : "http://localhost:8080");
    }

    public String baseUrl() { return baseUrl; }

    public String ollamaUrl() { return join(baseUrl, "/ollama"); }

    public String roleCreateUrl(long discordId) {
        return baseUrl + "/guilds/discord/" + discordId + "/roles";
    }

    public String refreshGuildUrl(long discordId) {
        return baseUrl + "/guilds/discord/" + discordId + "/refresh";
    }

    private static String normalizeBase(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String join(String base, String path) {
        if (path == null || path.isEmpty()) return base;
        return path.startsWith("/") ? base + path : base + "/" + path;
    }
}
