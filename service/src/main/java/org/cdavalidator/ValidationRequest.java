package org.cdavalidator;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;

/** Streaming decoder for the flat validation request contract. Never retains serialized JSON. */
record ValidationRequest(String xml, String profile, boolean warnings) {
    static final class InputError extends IOException {
        final int status;
        InputError(int status, String message) { super(message); this.status = status; }
    }
    static ValidationRequest read(InputStream input) throws IOException {
        return new Decoder(input).request();
    }
    private static final class Decoder {
        final Reader reader;
        int next;
        Decoder(InputStream input) throws IOException {
            var bounded = new FilterInputStream(input) {
                long remaining = Server.MAX_REQUEST_BYTES;
                void consumed(int count) throws InputError {
                    remaining -= count;
                    if (remaining < 0) throw new InputError(413, "Request exceeds size limit");
                }
                @Override public int read() throws IOException { int value = in.read(); if (value >= 0) consumed(1); return value; }
                @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                    int count = in.read(bytes, offset, (int)Math.min(length, remaining + 1));
                    if (count > 0) consumed(count);
                    return count;
                }
            };
            reader = new BufferedReader(new InputStreamReader(bounded, StandardCharsets.UTF_8.newDecoder()), 8192);
            advance();
        }
        void advance() throws IOException { next = reader.read(); }
        InputError invalid() { return new InputError(400, "Expected a JSON object with XML text, optional profile, and optional boolean includeWarnings; duplicate and unknown fields are not allowed"); }
        void whitespace() throws IOException { while (next == ' ' || next == '\n' || next == '\r' || next == '\t') advance(); }
        void expect(int value) throws IOException { if (next != value) throw invalid(); advance(); }
        // Returns one decoded UTF-16 unit, or -1 for an unescaped closing quote.
        int stringUnit() throws IOException {
            int value = next;
            if (value < 0 || value < 0x20) throw invalid();
            advance();
            if (value == '"') return -1;
            if (value != '\\') return value;
            value = next; advance();
            return switch (value) {
                case '"', '\\', '/' -> value;
                case 'b' -> '\b'; case 'f' -> '\f'; case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t';
                case 'u' -> {
                    int code = 0;
                    for (int i=0; i<4; i++) {
                        int digit = next < 128 ? Character.digit(next, 16) : -1;
                        if (digit < 0) throw invalid();
                        code = code * 16 + digit; advance();
                    }
                    yield code;
                }
                default -> throw invalid();
            };
        }
        String string(int limit, boolean xml) throws IOException {
            expect('"');
            var text = new StringBuilder(Math.min(4096, limit));
            int bytes = 0;
            for (int unit; (unit = stringUnit()) != -1;) {
                int low = -1;
                int width;
                if (Character.isHighSurrogate((char)unit)) {
                    low = stringUnit();
                    if (low < 0 || !Character.isLowSurrogate((char)low)) throw invalid();
                    width = 4;
                } else {
                    if (Character.isLowSurrogate((char)unit)) throw invalid();
                    width = unit <= 0x7f ? 1 : unit <= 0x7ff ? 2 : 3;
                }
                bytes += width;
                if (bytes > limit) throw new InputError(xml ? 413 : 400, xml ? "XML exceeds configured limit of " + limit + " bytes" : "Request field exceeds size limit");
                text.append((char)unit);
                if (low >= 0) text.append((char)low);
            }
            return text.toString();
        }
        ValidationRequest request() throws IOException {
            whitespace(); expect('{'); whitespace();
            String xml = null, profile = "ccda-5.0";
            boolean warnings = true;
            var fields = new HashSet<String>();
            if (next != '}') while (true) {
                String key = string(64, false);
                if (!fields.add(key)) throw invalid();
                whitespace(); expect(':'); whitespace();
                switch (key) {
                    case "xml" -> xml = string(ValidationEngine.MAX_BYTES, true);
                    case "profile" -> profile = string(128, false);
                    case "includeWarnings" -> {
                        if (next == 't') { for (char c : "true".toCharArray()) expect(c); warnings = true; }
                        else if (next == 'f') { for (char c : "false".toCharArray()) expect(c); warnings = false; }
                        else throw invalid();
                    }
                    default -> throw invalid();
                }
                whitespace();
                if (next != ',') break;
                advance(); whitespace();
            }
            expect('}'); whitespace();
            if (next != -1 || xml == null) throw invalid();
            return new ValidationRequest(xml, profile, warnings);
        }
    }
}
