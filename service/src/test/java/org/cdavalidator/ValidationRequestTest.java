package org.cdavalidator;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ValidationRequestTest {
    static ValidationRequest decode(String json) throws IOException {
        return ValidationRequest.read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }
    @Test void decodesEscapesUnicodeAndReorderedFields() throws Exception {
        String xml = "<x a=\"quoted\\value\">é😀\n\r\t</x>";
        var request=decode(ValidationEngine.JSON.toJson(Map.of("includeWarnings",false,"profile","ccda-2.1","xml",xml)));
        assertEquals(xml,request.xml()); assertEquals("ccda-2.1",request.profile()); assertFalse(request.warnings());
        assertEquals("😀",decode("{\"xml\":\"\\uD83D\\uDE00\"}").xml());
        assertEquals("<x/>",decode("{\"\\u0078ml\":\"<x/>\"}").xml());
        assertEquals("ccda-5.0",decode(" {\"xml\":\"x\"} \n").profile());
    }
    @Test void rejectsMalformedAmbiguousAndUnboundedMetadata() {
        for (String json : new String[]{"{}", "[]", "{\"xml\":null}", "{\"xml\":1}", "{\"xml\":\"x\",}",
                "{\"xml\":\"x\",\"xml\":\"y\"}", "{\"xml\":\"x\",\"extra\":{}}", "{\"xml\":\"x\"} {}",
                "{\"xml\":\"\\uD800\"}", "{\"xml\":\"\\uDC00\"}", "{\"xml\":\"\\uZZZZ\"}",
                "{\"xml\":\"x\n\"}", "{\"xml\":\"x\",\"profile\":\""+"p".repeat(129)+"\"}",
                "{\"xml\":\"x\",\"includeWarnings\":truee}"}) {
            var error=assertThrows(ValidationRequest.InputError.class,()->decode(json),json);
            assertEquals(400,error.status,json);
        }
    }
    @Test void rejectsMalformedUtf8() {
        assertThrows(java.nio.charset.CharacterCodingException.class,()->ValidationRequest.read(new ByteArrayInputStream(new byte[]{'{','"','x','m','l','"',':','"',(byte)0xc3, '"','}'})));
    }
}
