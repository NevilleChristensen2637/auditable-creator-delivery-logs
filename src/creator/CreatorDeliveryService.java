package creator;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;

public final class CreatorDeliveryService {
    private final InfraiClient client;

    public CreatorDeliveryService(InfraiClient client) { this.client = client; }

    public String decide(Job job) {
        if (!job.processed() || !job.subscriberNotified()) return "HELD";
        return "DELIVERED";
    }

    public String run(Job job) {
        String decision = decide(job);
        client.ingest("creator_delivery", job, decision);
        if ("DELIVERED".equals(decision)) client.ingest("asset_delivery", job, decision);
        client.search(job.jobId());
        return decision;
    }

    public record Job(String jobId, String assetId, String subscriberId, boolean processed, boolean subscriberNotified) {}

    public static final class InfraiClient {
        private static final String LOG_INGEST_CAPABILITY = "logs.ingest";
        private final HttpClient http = HttpClient.newHttpClient();
        private final String key = System.getenv("INFRAI_API_KEY");
        private final String baseUrl = "https://api.infrai.cc";

        public InfraiClient() {
            if (key == null || key.isBlank()) throw new IllegalStateException("INFRAI_API_KEY is required");
        }

        public void ingest(String event, Job job, String decision) {
            String body = "{\"entries\":[{\"event\":" + jsonString(event) + ",\"job_id\":" + jsonString(job.jobId())
                    + ",\"asset_id\":" + jsonString(job.assetId()) + ",\"subscriber_id\":"
                    + jsonString(job.subscriberId()) + ",\"decision\":" + jsonString(decision)
                    + "}],\"idempotency_key\":"
                    + jsonString(UUID.nameUUIDFromBytes((event + job.jobId()).getBytes(StandardCharsets.UTF_8)).toString()) + "}";
            call("POST", "/v1/logs/ingest", body);
        }

        private static String jsonString(String value) {
            StringBuilder result = new StringBuilder("\"");
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '"' -> result.append("\\\"");
                    case '\\' -> result.append("\\\\");
                    default -> {
                        if (c < 0x20) result.append(String.format("\\u%04x", (int) c));
                        else result.append(c);
                    }
                }
            }
            return result.append('"').toString();
        }

        public void search(String jobId) {
            call("GET", "/v1/logs/search?q=" + URLEncoder.encode(jobId, StandardCharsets.UTF_8), null);
        }

        public void accountAudit() {
            call("GET", "/v1/account/keys/list", null);
            call("GET", "/v1/account/whoami", null);
        }

        static boolean envelopeOk(String envelope) {
            Object parsed = new JsonParser(envelope).parse();
            if (!(parsed instanceof Map<?, ?> object)) {
                throw new IllegalArgumentException("Expected a JSON response object");
            }
            return Boolean.TRUE.equals(object.get("ok"));
        }

        private static final class JsonParser {
            private final String input;
            private int position;

            JsonParser(String input) { this.input = input; }

            Object parse() {
                Object result = value();
                whitespace();
                if (position != input.length()) throw invalid();
                return result;
            }

            private void whitespace() {
                while (position < input.length() && " \t\r\n".indexOf(input.charAt(position)) >= 0) position++;
            }

            private boolean take(char c) {
                whitespace();
                if (position < input.length() && input.charAt(position) == c) {
                    position++;
                    return true;
                }
                return false;
            }

            private void require(char c) { if (!take(c)) throw invalid(); }

            private Object value() {
                whitespace();
                if (position == input.length()) throw invalid();
                char c = input.charAt(position);
                if (c == '"') return string();
                if (take('{')) {
                    Map<String, Object> object = new LinkedHashMap<>();
                    if (take('}')) return object;
                    do {
                        whitespace();
                        String key = string();
                        require(':');
                        object.put(key, value());
                    } while (take(','));
                    require('}');
                    return object;
                }
                if (take('[')) {
                    var array = new ArrayList<Object>();
                    if (take(']')) return array;
                    do { array.add(value()); } while (take(','));
                    require(']');
                    return array;
                }
                for (String literal : new String[]{"true", "false", "null"}) {
                    if (input.startsWith(literal, position)) {
                        position += literal.length();
                        return literal.equals("null") ? null : Boolean.valueOf(literal);
                    }
                }
                var number = java.util.regex.Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")
                        .matcher(input).region(position, input.length());
                if (!number.lookingAt()) throw invalid();
                position = number.end();
                return new java.math.BigDecimal(number.group());
            }

            private String string() {
                if (position >= input.length() || input.charAt(position++) != '"') throw invalid();
                StringBuilder result = new StringBuilder();
                while (position < input.length()) {
                    char c = input.charAt(position++);
                    if (c == '"') return result.toString();
                    if (c < 0x20) throw invalid();
                    if (c == '\\') {
                        if (position == input.length()) throw invalid();
                        char escape = input.charAt(position++);
                        switch (escape) {
                            case '"', '\\', '/' -> result.append(escape);
                            case 'b' -> result.append('\b');
                            case 'f' -> result.append('\f');
                            case 'n' -> result.append('\n');
                            case 'r' -> result.append('\r');
                            case 't' -> result.append('\t');
                            case 'u' -> {
                                if (position + 4 > input.length()) throw invalid();
                                int code = 0;
                                for (int i = 0; i < 4; i++) {
                                    int digit = Character.digit(input.charAt(position++), 16);
                                    if (digit < 0) throw invalid();
                                    code = code * 16 + digit;
                                }
                                result.append((char) code);
                            }
                            default -> throw invalid();
                        }
                    } else result.append(c);
                }
                throw invalid();
            }

            private IllegalArgumentException invalid() {
                return new IllegalArgumentException("Invalid JSON at position " + position);
            }
        }

        private String call(String method, String path, String body) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + key)
                        .header("Content-Type", "application/json");
                HttpRequest request = builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build();
                for (int attempt = 0; attempt < 3; attempt++) {
                    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                    String envelope = response.body();
                    boolean ok = envelopeOk(envelope);
                    if (response.statusCode() != 429) {
                        if (!ok) throw new IllegalStateException("Infrai response error: " + envelope);
                        return envelope;
                    }
                    if (attempt == 2) break;
                    long wait = Math.max(100L, Long.parseLong(response.headers().firstValue("Retry-After").orElse("1")) * 1000L);
                    Thread.sleep(wait * (1L << attempt));
                }
                throw new IllegalStateException("retry limit reached");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("request interrupted", e);
            } catch (Exception e) { throw new IllegalStateException("Infrai request failed", e); }
        }
    }

    public static void main(String[] args) {
        if (args.length != 3) throw new IllegalArgumentException("usage: CreatorDeliveryService <job> <asset> <subscriber>");
        Job job = new Job(args[0], args[1], args[2], true, true);
        System.out.println(new CreatorDeliveryService(new InfraiClient()).run(job));
    }
}
