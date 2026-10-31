package com.abdulzafer.fx;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * FX quote API - Lab 3 (jenkins-java-app-pipeline).
 *
 * <pre>
 *   GET /health                                   status and the live release
 *   GET /api/rates                                demo reference rates
 *   GET /api/convert?from=OMR&amp;to=USD&amp;amount=100   conversion, 4 decimal places
 * </pre>
 *
 * Uses only the JDK's built-in HTTP server, so the deployable is a single small JAR.
 */
public final class App {

    record Response(int status, String body) {
        static Response ok(String body) {
            return new Response(200, body);
        }

        static Response error(int status, String error, String message) {
            return new Response(status,
                    "{\"error\":" + Json.quote(error) + ",\"message\":" + Json.quote(message) + "}");
        }
    }

    private final HttpServer server;
    private final ExecutorService executor;
    private final RateTable rates;
    private final ReleaseInfo release;

    public App(String bind, int port, RateTable rates, ReleaseInfo release) throws IOException {
        this.rates = rates;
        this.release = release;
        this.server = HttpServer.create(new InetSocketAddress(bind, port), 0);
        this.executor = Executors.newFixedThreadPool(4);
        server.setExecutor(executor);
        server.createContext("/", this::handle);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(1);
        executor.shutdown();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        Response response;
        try {
            response = route(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    parseQuery(exchange.getRequestURI().getRawQuery()));
        } catch (IllegalArgumentException e) {
            response = Response.error(400, "Bad Request", e.getMessage());
        } catch (RuntimeException e) {
            response = Response.error(500, "Internal Server Error", "unexpected error");
        }
        byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(response.status(), body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    Response route(String method, String path, Map<String, String> query) {
        if (!"GET".equals(method)) {
            return Response.error(405, "Method Not Allowed", "only GET is supported");
        }
        return switch (path) {
            case "/" -> Response.ok("{\"service\":\"fx-rates-service\",\"release\":"
                    + Json.quote(release.release())
                    + ",\"endpoints\":[\"/health\",\"/api/rates\",\"/api/convert\"]}");
            case "/health" -> Response.ok(release.toJson("ok"));
            case "/api/rates" -> Response.ok(rates.toJson());
            case "/api/convert" -> convert(query);
            default -> Response.error(404, "Not Found", "no route for " + path);
        };
    }

    private Response convert(Map<String, String> query) {
        String from = query.get("from");
        String to = query.get("to");
        String rawAmount = query.get("amount");
        if (from == null || to == null || rawAmount == null) {
            throw new IllegalArgumentException("from, to and amount are required");
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(rawAmount.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("amount must be a number");
        }
        BigDecimal result = rates.convert(from, to, amount);
        return Response.ok("{\"from\":" + Json.quote(from.toUpperCase())
                + ",\"to\":" + Json.quote(to.toUpperCase())
                + ",\"amount\":" + amount.toPlainString()
                + ",\"result\":" + result.toPlainString()
                + ",\"note\":\"demo reference rates, not live market data\"}");
    }

    static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> params = new HashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            params.put(URLDecoder.decode(key, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return params;
    }

    static RateTable loadBundledRates() throws IOException {
        try (InputStream in = App.class.getResourceAsStream("/rates.properties")) {
            return RateTable.load(in);
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    public static void main(String[] args) throws IOException {
        String bind = env("APP_BIND", "127.0.0.1");
        int port = Integer.parseInt(env("APP_PORT", "5003"));
        ReleaseInfo release = ReleaseInfo.read(Path.of(env("APP_RELEASE_FILE", "RELEASE")));
        App app = new App(bind, port, loadBundledRates(), release);
        Runtime.getRuntime().addShutdownHook(new Thread(app::stop));
        app.start();
        System.out.println("fx-rates-service " + release.release() + " listening on " + bind + ":" + app.port());
    }
}
