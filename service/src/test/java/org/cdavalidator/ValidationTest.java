package org.cdavalidator;

import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ValidationTest {
    @TempDir static Path assets;
    static ValidationEngine engine;
    static final String GOOD = "<root><item ok='yes'/></root>";
    static final String BAD = "<root><item ok='no'/></root>";
    @BeforeAll static void prepare() throws Exception {
        Files.writeString(assets.resolve("schema.xsd"), """
          <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"><xs:element name="root"><xs:complexType><xs:sequence><xs:element name="item" minOccurs="1" maxOccurs="unbounded"><xs:complexType><xs:attribute name="ok" type="xs:string" use="required"/></xs:complexType></xs:element></xs:sequence></xs:complexType></xs:element></xs:schema>
          """);
        Files.writeString(assets.resolve("rules.sch"), """
          <schema xmlns="http://purl.oclc.org/dsdl/schematron"><phase id="errors"><active pattern="p"/></phase><phase id="warnings"><active pattern="w"/></phase><let name="expected" value="'yes'"/><pattern id="p"><rule context="item"><assert test="@ok=$expected">The item SHALL be yes.</assert><assert test="count(.) + boolean(@ok) = 2">XPath 1.0 numeric coercion SHALL work.</assert><assert test="current()/@ok">The current item SHALL carry ok.</assert></rule><rule context="other"><assert test="false()">Other SHALL fail.</assert></rule></pattern><pattern id="w"><rule context="item"><report test="@ok='no'" role="warning">Consider correcting this item.</report></rule></pattern></schema>
          """);
        Files.writeString(assets.resolve("manifest.json"), """
          {"sources":[],"profiles":[{"id":"test","name":"Test","release":"test","schema":"schema.xsd","schematron":"rules.sch","limitations":[]}]}
          """);
        engine = new ValidationEngine(assets);
    }
    @Test void reportsFailuresAndLocations() throws Exception {
        var report = engine.validate(BAD, "test", true);
        assertEquals("invalid", report.get("status"));
        assertEquals(true, report.get("validationComplete"));
        var findings = (List<Map<String,Object>>) report.get("findings");
        assertTrue(findings.stream().anyMatch(f -> "schematron".equals(f.get("stage")) && f.containsKey("xpath") && f.containsKey("line")));
        assertEquals(1L, ((Map<?,?>)report.get("summary")).get("errors"));
        assertEquals(1L, ((Map<?,?>)report.get("summary")).get("warnings"));
    }
    @Test void supportsVariablesCurrentAndBackwardsCompatibleArithmetic() throws Exception {
        var result = engine.validate(GOOD, "test", true);
        assertEquals("valid", result.get("status"));
        assertEquals(true, result.get("executionComplete"));
    }
    @Test void concurrentDocumentsCannotContaminateEachOther() throws Exception {
        try (var pool = Executors.newFixedThreadPool(4)) {
            var jobs = new ArrayList<Future<Map<String,Object>>>();
            for (int i=0;i<32;i++) { final boolean good = i%2==0; jobs.add(pool.submit(() -> engine.validate(good?GOOD:BAD,"test",true))); }
            for (int i=0;i<jobs.size();i++) assertEquals(i%2==0?"valid":"invalid",jobs.get(i).get(10,TimeUnit.SECONDS).get("status"));
        }
    }
    @Test void rejectsDtdAndMalformedXml() throws Exception {
        for (var xml : List.of("<root>", "<root/><root/>", "<!DOCTYPE root [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><root>&x;</root>")) {
            var report = engine.validate(xml,"test",true);
            assertEquals("invalid",report.get("status")); assertEquals(false,report.get("executionComplete"));
        }
    }
    @Test void rejectsExcessiveNesting() throws Exception {
        var report=engine.validate("<root>".repeat(130)+"</root>".repeat(130),"test",true);
        assertEquals(false,report.get("executionComplete"));
    }
    @Test void schemaErrorsAreFindings() throws Exception {
        var report=engine.validate("<root><item/></root>","test",true);
        assertEquals("invalid",report.get("status"));
        assertTrue(((List<Map<String,Object>>)report.get("findings")).stream().anyMatch(f->"xsd".equals(f.get("stage"))));
    }
    @Test void warningFilterDoesNotChangeErrors() throws Exception {
        var report=engine.validate(BAD,"test",false);
        assertEquals(0L,((Map<?,?>)report.get("summary")).get("warnings"));
        assertEquals(1L,((Map<?,?>)report.get("summary")).get("errors"));
    }
    @Test void noMatchedRulesIsIncomplete() throws Exception {
        var report=engine.validate("<root/>","test",true);
        assertEquals(false,report.get("validationComplete"));
    }
    @Test void streamsAndCapsLargeFailureReports() throws Exception {
        var report=engine.validate("<root>"+"<item ok='no'/>".repeat(2100)+"</root>","test",true);
        assertEquals(true,report.get("truncated"));
        assertEquals(false,report.get("executionComplete"));
        assertEquals(ValidationEngine.MAX_FINDINGS,((List<?>)report.get("findings")).size());
    }
    @Test void differentRequestsReceiveDifferentIds() throws Exception {
        assertNotEquals(engine.validate(GOOD,"test",true).get("requestId"),engine.validate(GOOD,"test",true).get("requestId"));
    }
    @Test void unknownProfileAndEmptyDocumentAreRejected() {
        assertThrows(IllegalArgumentException.class,()->engine.validate(GOOD,"unknown",true));
        assertThrows(IllegalArgumentException.class,()->engine.validate(" ","test",true));
    }
    @Test void httpContractAuthenticationAndInputValidation() throws Exception {
        try(var app=new Server(new InetSocketAddress("127.0.0.1",0), assets, Path.of("web"),1,"test-secret")) {
            app.start(); var client=HttpClient.newHttpClient(); String base="http://127.0.0.1:"+app.port();
            assertEquals(200, client.send(HttpRequest.newBuilder(URI.create(base+"/health/ready")).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(401, client.send(HttpRequest.newBuilder(URI.create(base+"/v1/profiles")).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode());
            var request=HttpRequest.newBuilder(URI.create(base+"/v1/validate")).header("Authorization","Bearer test-secret").header("Content-Type","application/json");
            var response=client.send(request.POST(HttpRequest.BodyPublishers.ofString(ValidationEngine.JSON.toJson(Map.of("xml",BAD,"profile","test")))).build(),HttpResponse.BodyHandlers.ofString());
            assertEquals(200,response.statusCode());assertEquals("invalid",JsonParser.parseString(response.body()).getAsJsonObject().get("status").getAsString());
            assertEquals(400,client.send(request.POST(HttpRequest.BodyPublishers.ofString("{broken")).build(),HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(422,client.send(request.POST(HttpRequest.BodyPublishers.ofString("{\"xml\":\"<root/>\",\"profile\":\"missing\"}")).build(),HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(403,client.send(request.header("Origin","https://untrusted.example").POST(HttpRequest.BodyPublishers.ofString("{}")).build(),HttpResponse.BodyHandlers.ofString()).statusCode());
        }
    }
}
