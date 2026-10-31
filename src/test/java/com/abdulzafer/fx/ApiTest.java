package com.abdulzafer.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Starts the real HTTP server on a random local port and calls it over HTTP. */
class ApiTest {

    private static App app;
    private static HttpClient client;
    private static String baseUrl;

    @BeforeAll
    static void startServer() throws IOException {
        ReleaseInfo release = new ReleaseInfo("00009-abc1234", "abc1234", "2026-01-01T00:00:00Z");
        app = new App("127.0.0.1", 0, App.loadBundledRates(), release);
        app.start();
        client = HttpClient.newHttpClient();
        baseUrl = "http://127.0.0.1:" + app.port();
    }

    @AfterAll
    static void stopServer() {
        app.stop();
    }

    private static HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void healthReportsRelease() throws Exception {
        HttpResponse<String> r = get("/health");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"status\":\"ok\""), r.body());
        assertTrue(r.body().contains("\"release\":\"00009-abc1234\""), r.body());
        assertTrue(r.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
    }

    @Test
    void convertsOmrToUsd() throws Exception {
        HttpResponse<String> r = get("/api/convert?from=OMR&to=USD&amount=100");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"result\":260.0780"), r.body());
    }

    @Test
    void ratesListOmr() throws Exception {
        HttpResponse<String> r = get("/api/rates");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"OMR\":0.3845"), r.body());
    }

    @Test
    void unsupportedCurrencyIsBadRequest() throws Exception {
        HttpResponse<String> r = get("/api/convert?from=OMR&to=XYZ&amount=1");
        assertEquals(400, r.statusCode());
        assertTrue(r.body().contains("unsupported currency: XYZ"), r.body());
    }

    @Test
    void nonNumericAmountIsBadRequest() throws Exception {
        assertEquals(400, get("/api/convert?from=OMR&to=USD&amount=lots").statusCode());
    }

    @Test
    void missingParametersIsBadRequest() throws Exception {
        assertEquals(400, get("/api/convert?from=OMR").statusCode());
    }

    @Test
    void unknownRouteIsNotFound() throws Exception {
        assertEquals(404, get("/nope").statusCode());
    }

    @Test
    void postIsNotAllowed() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/health"))
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build();
        assertEquals(405, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
    }
}
