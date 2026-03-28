package io.github.kryen.chancemantrackersync;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class TrackerDirectUploadServiceTest
{
    @Test
    public void servesPayloadAndAckEndpointsThroughDirectTrackerUrl() throws Exception
    {
        Path runeLiteDirectory = Files.createTempDirectory("runelite-home");
        Path profileDirectory = Files.createDirectories(runeLiteDirectory.resolve("chanceman").resolve("KryenChance"));
        Files.writeString(profileDirectory.resolve("chanceman_obtained.json"), "[1,2,3]", StandardCharsets.UTF_8);
        Files.writeString(profileDirectory.resolve("chanceman_rolled.json"), "[4,5]", StandardCharsets.UTF_8);

        TrackerDirectUploadService service = new TrackerDirectUploadService(
            new Gson(),
            runeLiteDirectory,
            TrackerDirectUploadService.DEFAULT_TRACKER_UPLOAD_URL
        );
        try
        {
            TrackerDirectUploadService.DirectUploadResult result =
                service.buildUrl("KryenChance", "{\"player\":{\"name\":\"KryenChance\"}}");

            URI trackerUri = URI.create(result.url);
            Map<String, String> params = queryParams(trackerUri);
            String bridgeUrl = params.get("bridgeUrl");
            String bridgeToken = params.get("bridgeToken");

            Assert.assertEquals("https://chanceman-tracker.github.io/upload", trackerUri.getScheme() + "://" + trackerUri.getHost() + trackerUri.getPath());
            Assert.assertNotNull(bridgeUrl);
            Assert.assertNotNull(bridgeToken);

            HttpClient client = HttpClient.newHttpClient();
            URI payloadUri = URI.create(bridgeUrl + "/payload?bridgeToken=" + bridgeToken);
            HttpResponse<String> payloadResponse = client.send(
                HttpRequest.newBuilder(payloadUri)
                    .header("Origin", "https://chanceman-tracker.github.io")
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );
            Assert.assertEquals(200, payloadResponse.statusCode());
            Assert.assertEquals("https://chanceman-tracker.github.io", payloadResponse.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
            Assert.assertEquals("true", payloadResponse.headers().firstValue("Access-Control-Allow-Private-Network").orElse(null));

            JsonObject payload = new JsonParser().parse(payloadResponse.body()).getAsJsonObject();
            Assert.assertEquals(1, payload.get("schemaVersion").getAsInt());
            Assert.assertEquals("chanceman-tracker-sync", payload.get("source").getAsString());
            Assert.assertEquals("KryenChance", payload.get("playerName").getAsString());
            Assert.assertEquals("KryenChance", payload.getAsJsonObject("trackerBlob").getAsJsonObject("player").get("name").getAsString());
            Assert.assertEquals(3, payload.getAsJsonArray("chancemanObtained").size());
            Assert.assertEquals(2, payload.getAsJsonArray("chancemanRolled").size());

            URI ackUri = URI.create(bridgeUrl + "/ack?bridgeToken=" + bridgeToken);
            HttpResponse<String> ackResponse = client.send(
                HttpRequest.newBuilder(ackUri)
                    .header("Origin", "https://chanceman-tracker.github.io")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );
            Assert.assertEquals(204, ackResponse.statusCode());
        }
        finally
        {
            service.stop();
        }
    }

    @Test
    public void stopsBridgeAfterTimeout() throws Exception
    {
        Path runeLiteDirectory = Files.createTempDirectory("runelite-home");
        Path profileDirectory = Files.createDirectories(runeLiteDirectory.resolve("chanceman").resolve("KryenChance"));
        Files.writeString(profileDirectory.resolve("chanceman_obtained.json"), "[]", StandardCharsets.UTF_8);
        Files.writeString(profileDirectory.resolve("chanceman_rolled.json"), "[]", StandardCharsets.UTF_8);

        TrackerDirectUploadService service = new TrackerDirectUploadService(
            new Gson(),
            runeLiteDirectory,
            TrackerDirectUploadService.DEFAULT_TRACKER_UPLOAD_URL,
            50
        );

        TrackerDirectUploadService.DirectUploadResult result =
            service.buildUrl("KryenChance", "{\"player\":{\"name\":\"KryenChance\"}}");

        URI trackerUri = URI.create(result.url);
        Map<String, String> params = queryParams(trackerUri);
        String bridgeUrl = params.get("bridgeUrl");
        String bridgeToken = params.get("bridgeToken");

        Thread.sleep(150);

        HttpClient client = HttpClient.newHttpClient();
        try
        {
            client.send(
                HttpRequest.newBuilder(URI.create(bridgeUrl + "/payload?bridgeToken=" + bridgeToken))
                    .header("Origin", "https://chanceman-tracker.github.io")
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );
            Assert.fail("Expected bridge server to stop after timeout");
        }
        catch (IOException expected)
        {
            // Expected: the loopback bridge should no longer be listening.
        }
        finally
        {
            service.stop();
        }
    }

    @Test
    public void resolvesProfileDirectoryUsingNormalizedName() throws IOException
    {
        Path runeLiteDirectory = Files.createTempDirectory("runelite-home");
        Path profileDirectory = Files.createDirectories(runeLiteDirectory.resolve("chanceman").resolve("Kryen Chance"));
        Files.writeString(profileDirectory.resolve("chanceman_obtained.json"), "[]", StandardCharsets.UTF_8);
        Files.writeString(profileDirectory.resolve("chanceman_rolled.json"), "[]", StandardCharsets.UTF_8);

        TrackerDirectUploadService service = new TrackerDirectUploadService(
            new Gson(),
            runeLiteDirectory,
            TrackerDirectUploadService.DEFAULT_TRACKER_UPLOAD_URL
        );
        try
        {
            TrackerDirectUploadService.DirectUploadResult result =
                service.buildUrl("KryenChance", "{\"player\":{\"name\":\"KryenChance\"}}");

            Assert.assertTrue(result.summary.contains("Kryen Chance"));
        }
        finally
        {
            service.stop();
        }
    }

    @Test
    public void rejectsNonArrayChancemanData() throws IOException
    {
        Path runeLiteDirectory = Files.createTempDirectory("runelite-home");
        Path profileDirectory = Files.createDirectories(runeLiteDirectory.resolve("chanceman").resolve("KryenChance"));
        Files.writeString(profileDirectory.resolve("chanceman_obtained.json"), "{\"bad\":true}", StandardCharsets.UTF_8);
        Files.writeString(profileDirectory.resolve("chanceman_rolled.json"), "[]", StandardCharsets.UTF_8);

        TrackerDirectUploadService service = new TrackerDirectUploadService(
            new Gson(),
            runeLiteDirectory,
            TrackerDirectUploadService.DEFAULT_TRACKER_UPLOAD_URL
        );
        try
        {
            IllegalStateException ex = Assert.assertThrows(
                IllegalStateException.class,
                () -> service.buildUrl("KryenChance", "{\"player\":{\"name\":\"KryenChance\"}}")
            );

            Assert.assertTrue(ex.getMessage().contains("must contain a JSON array"));
        }
        finally
        {
            service.stop();
        }
    }

    @Test
    public void usesConfiguredTrackerUploadUrlAndOrigin() throws Exception
    {
        Path runeLiteDirectory = Files.createTempDirectory("runelite-home");
        Path profileDirectory = Files.createDirectories(runeLiteDirectory.resolve("chanceman").resolve("KryenChance"));
        Files.writeString(profileDirectory.resolve("chanceman_obtained.json"), "[]", StandardCharsets.UTF_8);
        Files.writeString(profileDirectory.resolve("chanceman_rolled.json"), "[]", StandardCharsets.UTF_8);

        TrackerDirectUploadService service = new TrackerDirectUploadService(
            new Gson(),
            runeLiteDirectory,
            "http://localhost:5173/"
        );
        try
        {
            TrackerDirectUploadService.DirectUploadResult result =
                service.buildUrl("KryenChance", "{\"player\":{\"name\":\"KryenChance\"}}");

            URI trackerUri = URI.create(result.url);
            Map<String, String> params = queryParams(trackerUri);
            Assert.assertEquals("http://localhost:5173/", trackerUri.getScheme() + "://" + trackerUri.getHost() + ":" + trackerUri.getPort() + trackerUri.getPath());
            Assert.assertTrue(params.get("bridgeUrl").startsWith("http://127.0.0.1:"));
            Assert.assertNotNull(params.get("bridgeToken"));
            Assert.assertTrue(result.summary.contains("Tracker route: http://localhost:5173/"));
        }
        finally
        {
            service.stop();
        }
    }

    @Test
    public void supportsCorsAndPrivateNetworkPreflight() throws Exception
    {
        Path runeLiteDirectory = Files.createTempDirectory("runelite-home");
        Path profileDirectory = Files.createDirectories(runeLiteDirectory.resolve("chanceman").resolve("KryenChance"));
        Files.writeString(profileDirectory.resolve("chanceman_obtained.json"), "[]", StandardCharsets.UTF_8);
        Files.writeString(profileDirectory.resolve("chanceman_rolled.json"), "[]", StandardCharsets.UTF_8);

        TrackerDirectUploadService service = new TrackerDirectUploadService(
            new Gson(),
            runeLiteDirectory,
            TrackerDirectUploadService.DEFAULT_TRACKER_UPLOAD_URL
        );
        try
        {
            TrackerDirectUploadService.DirectUploadResult result =
                service.buildUrl("KryenChance", "{\"player\":{\"name\":\"KryenChance\"}}");

            URI trackerUri = URI.create(result.url);
            Map<String, String> params = queryParams(trackerUri);
            String bridgeUrl = params.get("bridgeUrl");
            String bridgeToken = params.get("bridgeToken");

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> preflightResponse = client.send(
                HttpRequest.newBuilder(URI.create(bridgeUrl + "/payload?bridgeToken=" + bridgeToken))
                    .header("Origin", "https://chanceman-tracker.github.io")
                    .header("Access-Control-Request-Method", "GET")
                    .header("Access-Control-Request-Private-Network", "true")
                    .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );

            Assert.assertEquals(204, preflightResponse.statusCode());
            Assert.assertEquals("https://chanceman-tracker.github.io", preflightResponse.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
            Assert.assertEquals("GET, OPTIONS", preflightResponse.headers().firstValue("Access-Control-Allow-Methods").orElse(null));
            Assert.assertEquals("true", preflightResponse.headers().firstValue("Access-Control-Allow-Private-Network").orElse(null));
        }
        finally
        {
            service.stop();
        }
    }

    @Test
    public void rejectsDisallowedOrigin() throws Exception
    {
        Path runeLiteDirectory = Files.createTempDirectory("runelite-home");
        Path profileDirectory = Files.createDirectories(runeLiteDirectory.resolve("chanceman").resolve("KryenChance"));
        Files.writeString(profileDirectory.resolve("chanceman_obtained.json"), "[]", StandardCharsets.UTF_8);
        Files.writeString(profileDirectory.resolve("chanceman_rolled.json"), "[]", StandardCharsets.UTF_8);

        TrackerDirectUploadService service = new TrackerDirectUploadService(
            new Gson(),
            runeLiteDirectory,
            TrackerDirectUploadService.DEFAULT_TRACKER_UPLOAD_URL
        );
        try
        {
            TrackerDirectUploadService.DirectUploadResult result =
                service.buildUrl("KryenChance", "{\"player\":{\"name\":\"KryenChance\"}}");

            URI trackerUri = URI.create(result.url);
            Map<String, String> params = queryParams(trackerUri);
            String bridgeUrl = params.get("bridgeUrl");
            String bridgeToken = params.get("bridgeToken");

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> payloadResponse = client.send(
                HttpRequest.newBuilder(URI.create(bridgeUrl + "/payload?bridgeToken=" + bridgeToken))
                    .header("Origin", "https://evil.example")
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );

            Assert.assertEquals(403, payloadResponse.statusCode());
        }
        finally
        {
            service.stop();
        }
    }

    private Map<String, String> queryParams(URI uri)
    {
        Map<String, String> params = new LinkedHashMap<>();
        String query = uri.getRawQuery();
        if (query == null || query.isEmpty())
        {
            return params;
        }

        String[] pairs = query.split("&");
        for (String pair : pairs)
        {
            int separatorIndex = pair.indexOf('=');
            String key = separatorIndex >= 0 ? pair.substring(0, separatorIndex) : pair;
            String value = separatorIndex >= 0 ? pair.substring(separatorIndex + 1) : "";
            params.put(
                URLDecoder.decode(key, StandardCharsets.UTF_8),
                URLDecoder.decode(value, StandardCharsets.UTF_8)
            );
        }

        return params;
    }
}
