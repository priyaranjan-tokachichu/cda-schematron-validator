package org.cdavalidator;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Small stateless HTTP adapter. Submitted documents and findings are never logged or stored. */
public final class Server implements AutoCloseable {
    final HttpServer server;
    final ValidationEngine engine;
    final ThreadPoolExecutor workers;
    final ExecutorService requests = Executors.newVirtualThreadPerTaskExecutor();
    final Semaphore admission;
    final Path web;
    final String apiToken;

    public Server(InetSocketAddress address, Path assets, Path web, int concurrency, String token) throws Exception {
        this.engine = new ValidationEngine(assets);
        this.web = web.toAbsolutePath().normalize();
        apiToken = token;
        workers = new ThreadPoolExecutor(concurrency, concurrency, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(concurrency * 2));
        admission = new Semaphore(concurrency * 3);
        server = HttpServer.create(address, 32);
        server.setExecutor(requests);
        server.createContext("/", this::handle);
    }
    public void start() { server.start(); }
    public int port() { return server.getAddress().getPort(); }

    void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            var headers = exchange.getResponseHeaders();
            headers.set("X-Content-Type-Options", "nosniff");
            headers.set("Referrer-Policy", "no-referrer");
            headers.set("Cache-Control", "no-store");
            headers.set("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self' data:; object-src 'none'; base-uri 'none'; frame-ancestors 'none'");
            String path = exchange.getRequestURI().getPath();
            if (path.startsWith("/v1/") && apiToken != null && !apiToken.isBlank()) {
                String supplied = exchange.getRequestHeaders().getFirst("Authorization");
                if (supplied == null || !MessageDigest.isEqual(("Bearer " + apiToken).getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
                    json(exchange, 401, Map.of("error", "Authentication required")); return;
                }
            }
            if (exchange.getRequestMethod().equals("GET")) {
                switch (path) {
                    case "/health/live" -> json(exchange, 200, Map.of("status", "live"));
                    case "/health/ready" -> json(exchange, 200, Map.of("status", "ready", "profiles", engine.profiles.size()));
                    case "/v1/profiles" -> json(exchange, 200, Map.of("profiles", engine.profileInfo(), "sources", engine.manifest.get("sources")));
                    case "/v1/version" -> json(exchange, 200, Map.of("version", "2.0.0-preview.1", "engine", "Saxon-HE 12.9 / SchXslt 1.10.1"));
                    case "/", "/index.html" -> file(exchange, "index.html", "text/html; charset=utf-8");
                    case "/app.js" -> file(exchange, "app.js", "text/javascript; charset=utf-8");
                    case "/style.css" -> file(exchange, "style.css", "text/css; charset=utf-8");
                    case "/openapi.json" -> file(exchange, "openapi.json", "application/json; charset=utf-8");
                    case "/architecture.html" -> file(exchange, "architecture.html", "text/html; charset=utf-8");
                    case "/sample.xml" -> file(exchange, "sample.xml", "application/xml; charset=utf-8");
                    default -> json(exchange, 404, Map.of("error", "Route not found"));
                }
            } else if (path.equals("/v1/validate") && exchange.getRequestMethod().equals("POST")) {
                validate(exchange);
            } else {
                exchange.getResponseHeaders().set("Allow", path.equals("/v1/validate") ? "POST" : "GET");
                json(exchange, 405, Map.of("error", "Method not allowed"));
            }
        } catch (Exception ignored) {
            // No request contents, filenames, XML values, or exception messages enter application logs.
        }
    }

    void validate(HttpExchange exchange) throws IOException {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        String host = exchange.getRequestHeaders().getFirst("Host");
        if (origin != null && !origin.equals("http://" + host) && !origin.equals("https://" + host)) {
            json(exchange, 403, Map.of("error", "Cross-origin submission is not allowed")); return;
        }
        if (!admission.tryAcquire()) {
            exchange.getResponseHeaders().set("Retry-After", "2");
            json(exchange, 429, Map.of("error", "Validation capacity reached; retry shortly")); return;
        }
        Future<Map<String, Object>> task = null;
        try {
            var bytes = exchange.getRequestBody().readNBytes(ValidationEngine.MAX_BYTES + 1024 * 1024 + 1);
            if (bytes.length > ValidationEngine.MAX_BYTES + 1024 * 1024) { json(exchange, 413, Map.of("error", "Request exceeds size limit")); return; }
            String content;
            try { content = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString(); }
            catch (CharacterCodingException e) { json(exchange, 400, Map.of("error", "Request must be UTF-8")); return; }
            String contentType = Optional.ofNullable(exchange.getRequestHeaders().getFirst("Content-Type")).orElse("").split(";")[0].strip();
            if (!contentType.equals("application/json")) { json(exchange, 415, Map.of("error", "Send application/json with xml and profile fields")); return; }
            JsonObject body;
            try {
                body = JsonParser.parseString(content).getAsJsonObject();
                if (!body.has("xml") || !body.get("xml").isJsonPrimitive() || !body.getAsJsonPrimitive("xml").isString()) throw new IllegalArgumentException();
                if (body.has("profile") && (!body.get("profile").isJsonPrimitive() || !body.getAsJsonPrimitive("profile").isString())) throw new IllegalArgumentException();
                if (body.has("includeWarnings") && (!body.get("includeWarnings").isJsonPrimitive() || !body.getAsJsonPrimitive("includeWarnings").isBoolean())) throw new IllegalArgumentException();
            } catch (Exception e) { json(exchange, 400, Map.of("error", "Expected a JSON object with XML text, optional profile, and optional boolean includeWarnings")); return; }
            String xml = body.get("xml").getAsString();
            String profile = body.has("profile") ? body.get("profile").getAsString() : "ccda-5.0";
            boolean warnings = !body.has("includeWarnings") || body.get("includeWarnings").getAsBoolean();
            if (xml.isBlank()) { json(exchange, 400, Map.of("error", "XML document is required")); return; }
            if (xml.getBytes(StandardCharsets.UTF_8).length > ValidationEngine.MAX_BYTES) { json(exchange, 413, Map.of("error", "XML exceeds 5 MiB limit")); return; }
            if (!engine.profiles.containsKey(profile)) { json(exchange, 422, Map.of("error", "Unknown profile", "available", engine.profiles.keySet())); return; }
            task = workers.submit(() -> engine.validate(xml, profile, warnings));
            var report = task.get(30, TimeUnit.SECONDS);
            @SuppressWarnings("unchecked") var stages = (List<Map<String, Object>>) report.get("stages");
            boolean engineFailure = stages.stream().anyMatch(stage -> "failed".equals(stage.get("status")) && "schematron".equals(stage.get("name")));
            json(exchange, engineFailure ? 500 : 200, report);
        } catch (TimeoutException e) {
            if (task != null) task.cancel(true);
            json(exchange, 504, Map.of("error", "Validation deadline exceeded", "status", "execution-error", "validationComplete", false));
        } catch (RejectedExecutionException e) {
            json(exchange, 429, Map.of("error", "Validation capacity reached"));
        } catch (Exception e) {
            json(exchange, 500, Map.of("error", "Validation could not complete", "status", "execution-error", "validationComplete", false));
        } finally { admission.release(); }
    }
    void file(HttpExchange exchange, String name, String type) throws IOException {
        Path file = web.resolve(name);
        if (!Files.isRegularFile(file)) { json(exchange, 404, Map.of("error", "File not found")); return; }
        byte[] data = Files.readAllBytes(file);
        if(name.equals("architecture.html")) {
            var css=java.util.regex.Pattern.compile("<style>(.*?)</style>",java.util.regex.Pattern.DOTALL).matcher(new String(data,StandardCharsets.UTF_8));
            if(css.find()) {
                try {
                    String hash=Base64.getEncoder().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(css.group(1).getBytes(StandardCharsets.UTF_8)));
                    String policy=exchange.getResponseHeaders().getFirst("Content-Security-Policy");
                    exchange.getResponseHeaders().set("Content-Security-Policy",policy.replace("style-src 'self'","style-src 'self' 'sha256-"+hash+"'"));
                } catch(java.security.NoSuchAlgorithmException impossible) { throw new IOException(impossible); }
            }
        }
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(200, data.length);
        exchange.getResponseBody().write(data);
    }
    static void json(HttpExchange exchange, int code, Object value) throws IOException {
        byte[] data = ValidationEngine.JSON.toJson(value).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, data.length);
        exchange.getResponseBody().write(data);
    }
    @Override public void close() { server.stop(1); workers.shutdownNow(); requests.shutdownNow(); }
    public static void main(String[] args) throws Exception {
        System.setProperty("sun.net.httpserver.maxReqTime", "35");
        System.setProperty("sun.net.httpserver.maxRspTime", "40");
        System.setProperty("jdk.httpserver.maxConnections", "128");
        String bind = System.getenv().getOrDefault("HOST", "127.0.0.1");
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        int workers = Integer.parseInt(System.getenv().getOrDefault("VALIDATION_WORKERS", "2"));
        if (workers < 1 || workers > 16) throw new IllegalArgumentException("VALIDATION_WORKERS must be 1..16");
        long start = System.nanoTime();
        var app = new Server(new InetSocketAddress(bind, port), Path.of(System.getenv().getOrDefault("ASSET_DIR", "service/assets")), Path.of(System.getenv().getOrDefault("WEB_DIR", "service/web")), workers, System.getenv("API_TOKEN"));
        Runtime.getRuntime().addShutdownHook(new Thread(app::close));
        app.start();
        System.out.printf("CDA Validator ready at http://%s:%d (profiles compiled in %.1fs)%n", bind, app.port(), (System.nanoTime()-start)/1e9);
    }
}
