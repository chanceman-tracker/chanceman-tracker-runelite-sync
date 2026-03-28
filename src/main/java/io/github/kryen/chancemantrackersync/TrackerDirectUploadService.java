package io.github.kryen.chancemantrackersync;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;

final class TrackerDirectUploadService
{
    static final String DEFAULT_TRACKER_UPLOAD_URL = "https://chanceman-tracker.github.io/upload";
    private static final String TRACKER_UPLOAD_URL_PROPERTY = "chanceman.tracker.uploadUrl";
    private static final int DIRECT_UPLOAD_SCHEMA_VERSION = 1;
    static final long DEFAULT_BRIDGE_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(2);
    private static final String LOOPBACK_HOST = "127.0.0.1";
    private static final String PAYLOAD_PATH = "/payload";
    private static final String ACK_PATH = "/ack";
    private static final String TOKEN_PARAM = "bridgeToken";
    private static final String URL_PARAM = "bridgeUrl";

    private final Gson gson;
    private final Path runeLiteDirectory;
    private final String trackerUploadUrlOverride;
    private final long bridgeTimeoutMillis;

    private HttpServer server;
    private ExecutorService serverExecutor;
    private ScheduledExecutorService timeoutExecutor;
    private ScheduledFuture<?> timeoutFuture;

    @Inject
    TrackerDirectUploadService(Gson gson)
    {
        this(gson, Path.of(System.getProperty("user.home"), ".runelite"), null, DEFAULT_BRIDGE_TIMEOUT_MILLIS);
    }

    TrackerDirectUploadService(Gson gson, Path runeLiteDirectory)
    {
        this(gson, runeLiteDirectory, null, DEFAULT_BRIDGE_TIMEOUT_MILLIS);
    }

    TrackerDirectUploadService(Gson gson, Path runeLiteDirectory, String trackerUploadUrlOverride)
    {
        this(gson, runeLiteDirectory, trackerUploadUrlOverride, DEFAULT_BRIDGE_TIMEOUT_MILLIS);
    }

    TrackerDirectUploadService(Gson gson, Path runeLiteDirectory, String trackerUploadUrlOverride, long bridgeTimeoutMillis)
    {
        this.gson = gson;
        this.runeLiteDirectory = runeLiteDirectory;
        this.trackerUploadUrlOverride = trackerUploadUrlOverride;
        this.bridgeTimeoutMillis = bridgeTimeoutMillis;
    }

    synchronized DirectUploadResult buildUrl(String playerName, String trackerBlobJson)
    {
        try
        {
            Path profileDirectory = resolveProfileDirectory(playerName);
            JsonArray obtained = readJsonArray(profileDirectory.resolve("chanceman_obtained.json"), "obtained");
            JsonArray rolled = readJsonArray(profileDirectory.resolve("chanceman_rolled.json"), "rolled");

            DirectUploadPayload payload = new DirectUploadPayload();
            payload.schemaVersion = DIRECT_UPLOAD_SCHEMA_VERSION;
            payload.generatedAt = Instant.now().toString();
            payload.source = "chanceman-tracker-sync";
            payload.playerName = playerName;
            payload.trackerBlob = new JsonParser().parse(trackerBlobJson);
            payload.chancemanObtained = obtained;
            payload.chancemanRolled = rolled;

            stop();

            String trackerUploadUrl = normalizedTrackerUploadUrl();
            String trackerOrigin = trackerOrigin(trackerUploadUrl);
            String bridgeToken = UUID.randomUUID().toString();
            BridgeInfo bridge = startBridge(gson.toJson(payload), bridgeToken, trackerOrigin);
            String directUrl = buildDirectTrackerUrl(trackerUploadUrl, bridge.baseUrl, bridgeToken);

            return new DirectUploadResult(
                directUrl,
                buildSummary(playerName, profileDirectory, obtained.size(), rolled.size(), bridge.baseUrl, trackerUploadUrl)
            );
        }
        catch (IOException ex)
        {
            throw new IllegalStateException("Failed to prepare direct upload: " + ex.getMessage(), ex);
        }
    }

    synchronized void stop()
    {
        if (server != null)
        {
            server.stop(0);
            server = null;
        }

        if (serverExecutor != null)
        {
            serverExecutor.shutdownNow();
            serverExecutor = null;
        }

        if (timeoutFuture != null)
        {
            timeoutFuture.cancel(false);
            timeoutFuture = null;
        }

        if (timeoutExecutor != null)
        {
            timeoutExecutor.shutdownNow();
            timeoutExecutor = null;
        }
    }

    private BridgeInfo startBridge(String payloadJson, String bridgeToken, String trackerOrigin) throws IOException
    {
        HttpServer bridgeServer = HttpServer.create(new InetSocketAddress(InetAddress.getByName(LOOPBACK_HOST), 0), 0);
        ExecutorService executor = Executors.newCachedThreadPool(runnable ->
        {
            Thread thread = new Thread(runnable, "tracker-direct-upload-bridge");
            thread.setDaemon(true);
            return thread;
        });
        ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor(runnable ->
        {
            Thread thread = new Thread(runnable, "tracker-direct-upload-bridge-timeout");
            thread.setDaemon(true);
            return thread;
        });
        bridgeServer.setExecutor(executor);
        bridgeServer.createContext(PAYLOAD_PATH, exchange -> handlePayload(exchange, payloadJson, bridgeToken, trackerOrigin));
        bridgeServer.createContext(ACK_PATH, exchange -> handleAck(exchange, bridgeServer, bridgeToken, trackerOrigin));
        bridgeServer.start();

        server = bridgeServer;
        serverExecutor = executor;
        timeoutExecutor = scheduledExecutor;
        scheduleBridgeShutdown(bridgeServer);
        return new BridgeInfo("http://" + LOOPBACK_HOST + ":" + bridgeServer.getAddress().getPort());
    }

    private void handlePayload(com.sun.net.httpserver.HttpExchange exchange, String payloadJson, String bridgeToken, String trackerOrigin) throws IOException
    {
        if ("OPTIONS".equals(exchange.getRequestMethod()))
        {
            handlePreflight(exchange, trackerOrigin, "GET");
            return;
        }

        if (!"GET".equals(exchange.getRequestMethod()))
        {
            respond(exchange, 405, "text/plain; charset=utf-8", "Method not allowed");
            return;
        }

        if (!isAllowedOrigin(exchange, trackerOrigin))
        {
            respond(exchange, 403, "text/plain; charset=utf-8", "Origin not allowed");
            return;
        }

        if (!bridgeToken.equals(queryParam(exchange.getRequestURI(), TOKEN_PARAM)))
        {
            respond(exchange, 403, "text/plain; charset=utf-8", "Invalid bridge token");
            return;
        }

        scheduleBridgeShutdown(server);
        applyCorsHeaders(exchange, trackerOrigin);
        respond(exchange, 200, "application/json; charset=utf-8", payloadJson);
    }

    private void handleAck(com.sun.net.httpserver.HttpExchange exchange, HttpServer bridgeServer, String bridgeToken, String trackerOrigin) throws IOException
    {
        if ("OPTIONS".equals(exchange.getRequestMethod()))
        {
            handlePreflight(exchange, trackerOrigin, "POST");
            return;
        }

        if (!"POST".equals(exchange.getRequestMethod()))
        {
            respond(exchange, 405, "text/plain; charset=utf-8", "Method not allowed");
            return;
        }

        if (!isAllowedOrigin(exchange, trackerOrigin))
        {
            respond(exchange, 403, "text/plain; charset=utf-8", "Origin not allowed");
            return;
        }

        if (!bridgeToken.equals(queryParam(exchange.getRequestURI(), TOKEN_PARAM)))
        {
            respond(exchange, 403, "text/plain; charset=utf-8", "Invalid bridge token");
            return;
        }

        applyCorsHeaders(exchange, trackerOrigin);
        exchange.sendResponseHeaders(204, -1);
        exchange.close();

        stopBridgeIfCurrent(bridgeServer);
    }

    private void handlePreflight(com.sun.net.httpserver.HttpExchange exchange, String trackerOrigin, String allowedMethod) throws IOException
    {
        String requestOrigin = exchange.getRequestHeaders().getFirst("Origin");
        if (requestOrigin == null || !trackerOrigin.equals(requestOrigin))
        {
            respond(exchange, 403, "text/plain; charset=utf-8", "Origin not allowed");
            return;
        }

        String requestedMethod = exchange.getRequestHeaders().getFirst("Access-Control-Request-Method");
        if (requestedMethod == null || !allowedMethod.equalsIgnoreCase(requestedMethod))
        {
            respond(exchange, 405, "text/plain; charset=utf-8", "Requested method not allowed");
            return;
        }

        applyCorsHeaders(exchange, trackerOrigin);
        String requestedHeaders = exchange.getRequestHeaders().getFirst("Access-Control-Request-Headers");
        if (requestedHeaders != null && !requestedHeaders.trim().isEmpty())
        {
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", requestedHeaders);
        }
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", allowedMethod + ", OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Max-Age", "300");
        if ("true".equalsIgnoreCase(exchange.getRequestHeaders().getFirst("Access-Control-Request-Private-Network")))
        {
            exchange.getResponseHeaders().set("Access-Control-Allow-Private-Network", "true");
        }
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    private boolean isAllowedOrigin(com.sun.net.httpserver.HttpExchange exchange, String trackerOrigin)
    {
        String requestOrigin = exchange.getRequestHeaders().getFirst("Origin");
        return requestOrigin != null && trackerOrigin.equals(requestOrigin);
    }

    private synchronized void scheduleBridgeShutdown(HttpServer bridgeServer)
    {
        if (bridgeTimeoutMillis <= 0 || timeoutExecutor == null)
        {
            return;
        }

        if (timeoutFuture != null)
        {
            timeoutFuture.cancel(false);
        }

        timeoutFuture = timeoutExecutor.schedule(
            () -> stopBridgeIfCurrent(bridgeServer),
            bridgeTimeoutMillis,
            TimeUnit.MILLISECONDS
        );
    }

    private void stopBridgeIfCurrent(HttpServer bridgeServer)
    {
        synchronized (this)
        {
            if (server == bridgeServer)
            {
                stop();
            }
        }
    }

    private void applyCorsHeaders(com.sun.net.httpserver.HttpExchange exchange, String trackerOrigin)
    {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", trackerOrigin);
        exchange.getResponseHeaders().set("Access-Control-Allow-Private-Network", "true");
        exchange.getResponseHeaders().set("Vary", "Origin, Access-Control-Request-Method, Access-Control-Request-Headers, Access-Control-Request-Private-Network");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, int statusCode, String contentType, String body) throws IOException
    {
        byte[] responseBody = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(statusCode, responseBody.length);
        exchange.getResponseBody().write(responseBody);
        exchange.close();
    }

    private String buildDirectTrackerUrl(String trackerUploadUrl, String bridgeBaseUrl, String bridgeToken)
    {
        String separator = trackerUploadUrl.contains("?") ? "&" : "?";
        return trackerUploadUrl
            + separator
            + URL_PARAM + "=" + URLEncoder.encode(bridgeBaseUrl, StandardCharsets.UTF_8)
            + "&"
            + TOKEN_PARAM + "=" + URLEncoder.encode(bridgeToken, StandardCharsets.UTF_8);
    }

    private Path resolveProfileDirectory(String playerName) throws IOException
    {
        Path chancemanDirectory = runeLiteDirectory.resolve("chanceman");
        if (!Files.isDirectory(chancemanDirectory))
        {
            throw new IllegalStateException("Chanceman directory not found: " + chancemanDirectory);
        }

        Path exact = chancemanDirectory.resolve(playerName);
        if (Files.isDirectory(exact))
        {
            return exact;
        }

        String normalizedPlayerName = normalizeKey(playerName);
        try (Stream<Path> stream = Files.list(chancemanDirectory))
        {
            List<Path> matches = stream
                .filter(Files::isDirectory)
                .filter(path -> normalizeKey(path.getFileName().toString()).equals(normalizedPlayerName))
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .collect(Collectors.toList());

            if (matches.size() == 1)
            {
                return matches.get(0);
            }

            if (matches.size() > 1)
            {
                String directories = matches.stream()
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.joining(", "));
                throw new IllegalStateException("Multiple Chanceman profiles matched '" + playerName + "': " + directories);
            }
        }

        throw new IllegalStateException(
            "Chanceman profile not found for '" + playerName + "' in " + chancemanDirectory
        );
    }

    private JsonArray readJsonArray(Path path, String label) throws IOException
    {
        if (!Files.isRegularFile(path))
        {
            throw new IllegalStateException("Missing Chanceman " + label + " file: " + path);
        }

        JsonElement element = new JsonParser().parse(Files.readString(path, StandardCharsets.UTF_8));
        if (!element.isJsonArray())
        {
            throw new IllegalStateException("Chanceman " + label + " file must contain a JSON array: " + path);
        }

        return element.getAsJsonArray();
    }

    private String buildSummary(String playerName, Path profileDirectory, int obtainedCount, int rolledCount, String bridgeBaseUrl, String trackerUploadUrl)
    {
        List<String> lines = new ArrayList<>();
        lines.add("Player: " + playerName);
        lines.add("Chanceman profile: " + profileDirectory.getFileName());
        lines.add("Obtained entries: " + obtainedCount);
        lines.add("Rolled entries: " + rolledCount);
        lines.add("Bridge URL: " + bridgeBaseUrl);
        lines.add("Tracker route: " + trackerUploadUrl);
        return String.join(System.lineSeparator(), lines);
    }

    private String normalizedTrackerUploadUrl()
    {
        String url = trackerUploadUrlOverride != null ? trackerUploadUrlOverride : System.getProperty(TRACKER_UPLOAD_URL_PROPERTY, DEFAULT_TRACKER_UPLOAD_URL);
        if (url == null || url.trim().isEmpty())
        {
            throw new IllegalStateException("Tracker upload URL is empty.");
        }

        String normalized = url.trim();
        URI uri = URI.create(normalized);
        if (uri.getScheme() == null || uri.getHost() == null)
        {
            throw new IllegalStateException("Tracker upload URL must be an absolute URL: " + normalized);
        }
        return normalized;
    }

    private String trackerOrigin(String trackerUploadUrl)
    {
        URI uri = URI.create(trackerUploadUrl);
        StringBuilder origin = new StringBuilder()
            .append(uri.getScheme())
            .append("://")
            .append(uri.getHost());

        if (uri.getPort() != -1)
        {
            origin.append(":").append(uri.getPort());
        }

        return origin.toString();
    }

    private String queryParam(URI uri, String key)
    {
        Map<String, String> params = new LinkedHashMap<>();
        String query = uri.getRawQuery();
        if (query == null || query.isEmpty())
        {
            return null;
        }

        String[] pairs = query.split("&");
        for (String pair : pairs)
        {
            int separatorIndex = pair.indexOf('=');
            String paramKey = separatorIndex >= 0 ? pair.substring(0, separatorIndex) : pair;
            String paramValue = separatorIndex >= 0 ? pair.substring(separatorIndex + 1) : "";
            params.put(
                URLDecoder.decode(paramKey, StandardCharsets.UTF_8),
                URLDecoder.decode(paramValue, StandardCharsets.UTF_8)
            );
        }

        return params.get(key);
    }

    private String normalizeKey(String value)
    {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    static final class DirectUploadResult
    {
        final String url;
        final String summary;

        DirectUploadResult(String url, String summary)
        {
            this.url = url;
            this.summary = summary;
        }
    }

    static final class DirectUploadPayload
    {
        int schemaVersion;
        String generatedAt;
        String source;
        String playerName;
        JsonElement trackerBlob;
        JsonArray chancemanObtained;
        JsonArray chancemanRolled;
    }

    private static final class BridgeInfo
    {
        final String baseUrl;

        private BridgeInfo(String baseUrl)
        {
            this.baseUrl = baseUrl;
        }
    }
}
