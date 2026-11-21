package org.cdavalidator;

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
    // JSON can encode each ASCII XML byte as six bytes (JSON Unicode escapes), plus metadata.
    static final int UPLOAD_QUEUE_SECONDS = 30;
    static final int MAX_REQUEST_BYTES = 6 * ValidationEngine.MAX_BYTES + 64 * 1024;
    final HttpServer server;
    final ValidationEngine engine;
    final ThreadPoolExecutor workers;
    final ExecutorService requests = Executors.newVirtualThreadPerTaskExecutor();
    final Semaphore admission;
    // Cover string-builder growth, UTF-16 inflation, final decoded strings and parser buffers.
    // Reserve before reading; only a quarter of the heap may be used for upload decoding.
    final int uploadReservationKiB = Math.toIntExact((8L * ValidationEngine.MAX_BYTES + 128 * 1024) / 1024);
    final int uploadBudgetKiB;
    final Semaphore uploadMemory;
    final Path web;
    final String apiToken;

    public Server(InetSocketAddress address, Path assets, Path web, int concurrency, String token) throws Exception {
        this(address, assets, web, concurrency, token, Runtime.getRuntime().maxMemory() / 4);
    }
    Server(InetSocketAddress address, Path assets, Path web, int concurrency, String token, long uploadBudgetBytes) throws Exception {
        uploadBudgetKiB = (int)Math.min(Integer.MAX_VALUE, uploadBudgetBytes / 1024);
        uploadMemory = new Semaphore(uploadBudgetKiB, true);
        if (uploadReservationKiB > uploadBudgetKiB) throw new IllegalArgumentException("Configured XML size requires a larger Java heap for upload decoding");
        this.engine = new ValidationEngine(assets);
        this.web = web.toAbsolutePath().normalize();
        apiToken = token;
        workers = new ThreadPoolExecutor(concurrency, concurrency, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(concurrency * 2));
        admission = new Semaphore(concurrency * 3);
        server = HttpServer.create(address, 32);
        server.setExecutor(requests);
        server.createContext("/", this::handle);
    }
    int uploadReservation(HttpExchange exchange) throws ValidationRequest.InputError {
        long encoded = MAX_REQUEST_BYTES;
        String length = exchange.getRequestHeaders().getFirst("Content-Length");
        if (length != null && exchange.getRequestHeaders().getFirst("Transfer-Encoding") == null) {
            try { encoded = Long.parseLong(length); }
            catch (NumberFormatException e) { throw new ValidationRequest.InputError(400, "Invalid Content-Length"); }
            if (encoded < 0) throw new ValidationRequest.InputError(400, "Invalid Content-Length");
            if (encoded > MAX_REQUEST_BYTES) throw new ValidationRequest.InputError(413, "Request exceeds size limit");
        }
        // HTTP framing bounds known-length bodies; chunked uploads reserve the full allowance.
        return Math.toIntExact((8L * Math.min(ValidationEngine.MAX_BYTES, encoded) + 128 * 1024 + 1023) / 1024);
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
                    case "/v1/profiles" -> json(exchange, 200, Map.of("profiles", engine.profileInfo(), "sources", engine.manifest.get("sources"), "limits", Map.of("maxXmlBytes", ValidationEngine.MAX_BYTES, "maxRequestBytes", MAX_REQUEST_BYTES, "uploadMemoryBudgetBytes", 1024L * uploadBudgetKiB, "maxConcurrentUploads", Math.min(workers.getMaximumPoolSize() * 3, uploadBudgetKiB / uploadReservationKiB), "requestCapacity", workers.getMaximumPoolSize() * 3, "uploadQueueWaitSeconds", UPLOAD_QUEUE_SECONDS)));
                    case "/v1/version" -> json(exchange, 200, Map.of("version", "2.0.0", "engine", "Saxon-HE 12.9 / SchXslt 1.10.1", "javaVersion", System.getProperty("java.version"), "javaRuntimeVersion", System.getProperty("java.runtime.version"), "javaVendor", System.getProperty("java.vendor")));
                    case "/", "/index.html" -> file(exchange, "index.html", "text/html; charset=utf-8");
                    case "/app.js" -> file(exchange, "app.js", "text/javascript; charset=utf-8");
                    case "/style.css" -> file(exchange, "style.css", "text/css; charset=utf-8");
                    case "/setup.css" -> file(exchange, "setup.css", "text/css; charset=utf-8");
                    case "/openapi.json" -> file(exchange, "openapi.json", "application/json; charset=utf-8");
                    case "/architecture.html" -> file(exchange, "architecture.html", "text/html; charset=utf-8");
                    case "/terminology.html" -> file(exchange, "terminology.html", "text/html; charset=utf-8");
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
            String contentType = Optional.ofNullable(exchange.getRequestHeaders().getFirst("Content-Type")).orElse("").split(";")[0].strip();
            if (!contentType.equals("application/json")) { json(exchange, 415, Map.of("error", "Send application/json with xml and profile fields")); return; }
            int reservation = uploadReservation(exchange);
            long queueStart = System.nanoTime();
            if (!uploadMemory.tryAcquire(reservation, UPLOAD_QUEUE_SECONDS, TimeUnit.SECONDS)) {
                exchange.getResponseHeaders().set("Retry-After", "2");
                json(exchange, 503, Map.of("error", "Upload queue wait exceeded 30 seconds; retry later")); return;
            }
            long uploadQueueWaitMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - queueStart);
            ValidationRequest body;
            try { body = ValidationRequest.read(exchange.getRequestBody()); }
            finally { uploadMemory.release(reservation); }
            String xml = body.xml();
            String profile = body.profile();
            boolean warnings = body.warnings();
            if (xml.isBlank()) { json(exchange, 400, Map.of("error", "XML document is required")); return; }
            if (!engine.profiles.containsKey(profile)) { json(exchange, 422, Map.of("error", "Unknown profile", "available", engine.profiles.keySet())); return; }
            task = workers.submit(() -> engine.validate(xml, profile, warnings));
            var report = task.get(30, TimeUnit.SECONDS);
            report.put("uploadQueueWaitMs", uploadQueueWaitMs);
            @SuppressWarnings("unchecked") var stages = (List<Map<String, Object>>) report.get("stages");
            boolean engineFailure = stages.stream().anyMatch(stage -> "failed".equals(stage.get("status")) && "schematron".equals(stage.get("name")));
            json(exchange, engineFailure ? 500 : 200, report);
        } catch (ValidationRequest.InputError e) {
            json(exchange, e.status, Map.of("error", e.getMessage()));
        } catch (CharacterCodingException e) {
            json(exchange, 400, Map.of("error", "Request must be UTF-8"));
        } catch (TimeoutException e) {
            if (task != null) task.cancel(true);
            json(exchange, 504, Map.of("error", "Validation deadline exceeded", "status", "execution-error", "validationComplete", false));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            json(exchange, 503, Map.of("error", "Queued request interrupted; service is stopping"));
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
        if (code >= 400) exchange.getResponseHeaders().set("Connection", "close");
        exchange.sendResponseHeaders(code, data.length);
        exchange.getResponseBody().write(data);
        exchange.getResponseBody().flush();
        if (code >= 400) {
            // Deliver early errors before draining unread upload bytes, without buffering them.
            // This avoids resetting a normal client's connection with its error body unflushed.
            byte[] discard = new byte[8192];
            int remaining = MAX_REQUEST_BYTES + 1;
            try {
                for (int n; remaining > 0 && (n = exchange.getRequestBody().read(discard, 0, Math.min(discard.length, remaining))) >= 0;) remaining -= n;
            } catch (IOException ignored) { /* The client may stop sending after receiving the error. */ }
        }
    }
    @Override public void close() { server.stop(1); workers.shutdownNow(); requests.shutdownNow(); }
    public static void main(String[] args) throws Exception {
        System.setProperty("sun.net.httpserver.maxReqTime", "65");
        System.setProperty("sun.net.httpserver.maxRspTime", "70");
        System.setProperty("jdk.httpserver.maxConnections", "128");
        String bind = System.getenv().getOrDefault("HOST", "127.0.0.1");
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "17643"));
        int workers = Integer.parseInt(System.getenv().getOrDefault("VALIDATION_WORKERS", "2"));
        if (workers < 1 || workers > 16) throw new IllegalArgumentException("VALIDATION_WORKERS must be 1..16");
        long start = System.nanoTime();
        var app = new Server(new InetSocketAddress(bind, port), Path.of(System.getenv().getOrDefault("ASSET_DIR", "service/assets")), Path.of(System.getenv().getOrDefault("WEB_DIR", "service/web")), workers, System.getenv("API_TOKEN"));
        Runtime.getRuntime().addShutdownHook(new Thread(app::close));
        app.start();
        System.out.printf("CDA Validator ready at http://%s:%d (profiles compiled in %.1fs)%n", bind, app.port(), (System.nanoTime()-start)/1e9);
    }
}
